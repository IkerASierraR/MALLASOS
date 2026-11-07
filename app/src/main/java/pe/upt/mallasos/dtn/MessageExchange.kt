package pe.upt.mallasos.dtn

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import pe.upt.mallasos.codec.AckFinalPayload
import pe.upt.mallasos.codec.Frame
import pe.upt.mallasos.codec.Header
import pe.upt.mallasos.codec.MessageType
import pe.upt.mallasos.codec.PayloadCodec
import pe.upt.mallasos.codec.Priority
import pe.upt.mallasos.codec.Uid
import pe.upt.mallasos.data.datastore.DtnMode
import pe.upt.mallasos.data.datastore.QueueMode
import pe.upt.mallasos.data.db.MessageEntity
import pe.upt.mallasos.data.db.MessageState
import pe.upt.mallasos.log.EventSink
import pe.upt.mallasos.log.Events
import pe.upt.mallasos.p2p.LocalNode
import pe.upt.mallasos.p2p.PeerSession
import java.io.EOFException
import java.util.concurrent.ConcurrentHashMap

/** Lo que el intercambio necesita de la cola; lo implementa `MessageDao`. */
interface MessageStore {
    /** Mensajes en estado `PENDING` o `CARRIED`. */
    suspend fun queued(): List<MessageEntity>

    /** Devuelve `false` si el identificador ya se había visto. */
    suspend fun insertIfNew(message: MessageEntity): Boolean

    suspend fun recordAttempt(uid: String, atMs: Long)

    /** El salto quedó hecho: de `PENDING` pasa a `CARRIED`. */
    suspend fun markCarried(uid: String)

    /**
     * Llegó el ACK final: el SOS propio pasa a `DELIVERED`, una copia ajena se borra y queda la
     * lápida. Devuelve `true` si se borró una copia.
     */
    suspend fun confirmDelivered(uid: String, atMs: Long): Boolean
}

/** Esperas del DTN propuesto (`plan.md` §3): 3 s por ACK y reintentos tras 1, 2 y 4 s. */
data class AckTimings(
    val ackTimeoutMs: Long = 3_000,
    val retryDelaysMs: List<Long> = listOf(1_000, 2_000, 4_000),
)

/** Parámetros de un contacto. [ttlHours] y [hops] se aplican a los ACK finales que se generen. */
data class ExchangeConfig(
    val local: LocalNode,
    val dtnMode: DtnMode,
    val queueMode: QueueMode,
    val ttlHours: Int,
    val hops: Int,
)

/**
 * Intercambio de mensajes tras el HELLO. Cada lado envía su cola y, al vaciarla, un `SUMMARY` sin
 * contenido. Con el `SUMMARY` del vecino ya leído, escribe lo que le quede, cierra su envío y sigue
 * leyendo hasta que el vecino cierre el suyo: así ningún ACK en camino encuentra el socket cerrado.
 *
 * Solo una corrutina escribe en el socket y otra lee sin bloquearse nunca al escribir: si los dos
 * lados se quedaran escribiendo con el búfer TCP lleno, ninguno leería y se bloquearían.
 */
class MessageExchange(
    private val store: MessageStore,
    private val events: EventSink,
    private val nextSequence: suspend () -> Int,
    private val timings: AckTimings = AckTimings(),
    private val clockMs: () -> Long = System::currentTimeMillis,
) {
    suspend fun run(session: PeerSession, config: ExchangeConfig) =
        Contact(session, config).run()

    private class Outgoing(val header: Header, val payload: ByteArray) {
        val written = CompletableDeferred<Unit>()
    }

    private inner class Contact(
        private val session: PeerSession,
        private val config: ExchangeConfig,
    ) {
        private val peer = session.remote
        private val localId = config.local.nodeId
        private val outbox = Channel<Outgoing>(Channel.UNLIMITED)
        private val awaitingAck = ConcurrentHashMap<String, CompletableDeferred<Unit>>()
        private val sent = ConcurrentHashMap.newKeySet<String>()
        private val peerEnded = CompletableDeferred<Unit>()

        suspend fun run() = coroutineScope {
            val writer = launch {
                for (item in outbox) {
                    session.frames.write(item.header, item.payload)
                    item.written.complete(Unit)
                }
            }
            val reader = launch { readAll() }

            sendQueue()
            outbox.send(Outgoing(controlHeader(MessageType.SUMMARY, 0), ByteArray(0)))
            peerEnded.await()

            outbox.close()
            writer.join()
            session.finishSending()
            reader.join()
        }

        private fun controlHeader(type: MessageType, payloadLength: Int) = Header(
            type = type,
            priority = Priority.CONTROL,
            originId = localId,
            seq = 0,
            destId = peer.nodeId,
            timestampSeconds = clockMs() / 1000,
            ttlHours = 0,
            hopsLeft = 0,
            payloadLength = payloadLength,
        )

        /** Tras cerrar el envío ya no se responde: el mensaje sigue en la cola del otro lado. */
        private fun enqueue(item: Outgoing) {
            outbox.trySend(item)
        }

        private suspend fun write(header: Header, payload: ByteArray) {
            val item = Outgoing(header, payload)
            outbox.send(item)
            item.written.await()
        }

        // La cola se relee porque puede crecer durante la sesión.
        private suspend fun sendQueue() {
            while (true) {
                val pending = Forwarding.outgoing(
                    store.queued(),
                    peer,
                    config.queueMode,
                    clockMs(),
                ).filter { it.uid !in sent }
                if (pending.isEmpty()) return
                for (message in pending) {
                    if (!sent.add(message.uid)) continue
                    // Si un mensaje agota sus reintentos el enlace no sirve: el resto espera.
                    if (!forward(message)) return
                }
            }
        }

        private suspend fun forward(message: MessageEntity): Boolean = when (config.dtnMode) {
            DtnMode.PROPUESTO -> forwardWithAck(message)
            DtnMode.BASICO -> {
                // R3: el salto se da por bueno al escribirlo, y no se reintenta.
                writeAttempt(message, Events.SENT)
                store.markCarried(message.uid)
                true
            }
        }

        private suspend fun writeAttempt(message: MessageEntity, event: String) {
            val header = Forwarding.header(message)
            write(header, message.payload)
            store.recordAttempt(message.uid, clockMs())
            events.log(event, message.uid, peer.nodeId, header.hopsLeft)
        }

        private suspend fun forwardWithAck(message: MessageEntity): Boolean {
            val ack = CompletableDeferred<Unit>()
            awaitingAck[message.uid] = ack
            try {
                if (awaitAck(message, ack, Events.SENT)) return true
                for (wait in timings.retryDelaysMs) {
                    delay(wait)
                    if (awaitAck(message, ack, Events.RETRY)) return true
                }
                return false
            } finally {
                awaitingAck.remove(message.uid)
            }
        }

        private suspend fun awaitAck(
            message: MessageEntity,
            ack: CompletableDeferred<Unit>,
            event: String,
        ): Boolean {
            if (!ack.isCompleted) writeAttempt(message, event)
            withTimeoutOrNull(timings.ackTimeoutMs) { ack.await() } ?: return false
            store.markCarried(message.uid)
            return true
        }

        private suspend fun readAll() {
            while (true) {
                val frame = session.frames.readOrNull()
                if (frame == null) {
                    if (peerEnded.isCompleted) return
                    throw EOFException("El vecino cerró sin terminar")
                }
                when (frame.header.type) {
                    MessageType.SOS, MessageType.NORMAL -> receiveMessage(frame)
                    MessageType.ACK_FINAL -> receiveAckFinal(frame)
                    MessageType.ACK_HOP -> receiveAckHop(frame)
                    MessageType.SUMMARY -> if (frame.payload.isEmpty()) peerEnded.complete(Unit)
                    MessageType.HELLO -> Unit
                }
            }
        }

        private suspend fun receiveAckHop(frame: Frame) {
            val uid = PayloadCodec.decodeAckHop(frame.payload).hex
            events.log(Events.ACK_HOP_RX, uid, peer.nodeId)
            awaitingAck[uid]?.complete(Unit)
        }

        /** Responde siempre, incluso a un duplicado (R3): el vecino no sabe qué tiene este lado. */
        private fun sendAckHop(frame: Frame) {
            val payload = PayloadCodec.encodeAckHop(frame.header.uid)
            enqueue(Outgoing(controlHeader(MessageType.ACK_HOP, payload.size), payload))
        }

        private fun received(frame: Frame, state: MessageState): MessageEntity {
            val header = frame.header
            return MessageEntity(
                uid = header.uid.hex,
                type = header.type.code,
                priority = header.priority,
                originId = header.originId,
                destId = header.destId,
                seq = header.seq,
                createdAt = header.timestampSeconds * 1000,
                ttlHours = header.ttlHours,
                hopsLeft = header.hopsLeft,
                payload = frame.payload,
                state = state,
                isOwn = false,
                enqueuedAt = clockMs(),
                receivedFrom = peer.nodeId,
            )
        }

        private suspend fun receiveMessage(frame: Frame) {
            val header = frame.header
            // Lanza `MalformedMessageException` antes de guardar nada.
            if (header.type == MessageType.SOS) PayloadCodec.decodeSos(frame.payload)
            val isFinal = header.type == MessageType.SOS &&
                Forwarding.isDestination(header.destId, localId, config.local.mode)
            val state = if (isFinal) MessageState.DELIVERED else MessageState.CARRIED
            val uid = header.uid.hex
            if (store.insertIfNew(received(frame, state))) {
                events.log(Events.RECEIVED, uid, peer.nodeId, header.hopsLeft)
                if (isFinal) {
                    events.log(Events.DELIVERED_FINAL, uid, peer.nodeId, header.hopsLeft)
                    // Antes del ACK salto: quien espera ese ACK aún no puede terminar la sesión.
                    sendAckFinal(header)
                } else {
                    events.log(Events.ENQUEUED, uid, peer.nodeId, header.hopsLeft)
                }
            } else {
                events.log(Events.DUPLICATE_DROPPED, uid, peer.nodeId, header.hopsLeft)
            }
            sendAckHop(frame)
        }

        /** Entra en la cola para llegar a quien lleve copias y sale ya hacia este vecino (R2). */
        private suspend fun sendAckFinal(sos: Header) {
            val now = clockMs()
            val seq = nextSequence()
            val uid = Uid(localId, seq).hex
            val ack = MessageEntity(
                uid = uid,
                type = MessageType.ACK_FINAL.code,
                priority = Priority.CONTROL,
                originId = localId,
                destId = sos.originId,
                seq = seq,
                createdAt = now,
                ttlHours = config.ttlHours,
                hopsLeft = config.hops,
                payload = PayloadCodec.encodeAckFinal(AckFinalPayload(sos.uid, localId)),
                state = MessageState.PENDING,
                isOwn = true,
                enqueuedAt = now,
            )
            store.insertIfNew(ack)
            events.log(Events.ENQUEUED, uid, peer.nodeId, ack.hopsLeft)
            if (Forwarding.canForward(ack, peer, now) && sent.add(uid)) {
                val header = Forwarding.header(ack)
                enqueue(Outgoing(header, ack.payload))
                store.recordAttempt(uid, now)
                events.log(Events.SENT, uid, peer.nodeId, header.hopsLeft)
            }
        }

        private suspend fun receiveAckFinal(frame: Frame) {
            val header = frame.header
            val confirmed = PayloadCodec.decodeAckFinal(frame.payload).confirmed.hex
            if (store.insertIfNew(received(frame, MessageState.CARRIED))) {
                events.log(Events.ACK_FINAL_RX, confirmed, peer.nodeId, header.hopsLeft)
                if (store.confirmDelivered(confirmed, clockMs())) {
                    events.log(Events.PURGED, confirmed, peer.nodeId)
                }
                events.log(Events.ENQUEUED, header.uid.hex, peer.nodeId, header.hopsLeft)
            } else {
                events.log(Events.DUPLICATE_DROPPED, header.uid.hex, peer.nodeId, header.hopsLeft)
            }
            sendAckHop(frame)
        }
    }
}
