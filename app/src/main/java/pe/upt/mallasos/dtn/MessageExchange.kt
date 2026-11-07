package pe.upt.mallasos.dtn

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import pe.upt.mallasos.codec.Frame
import pe.upt.mallasos.codec.MessageType
import pe.upt.mallasos.codec.PayloadCodec
import pe.upt.mallasos.data.datastore.QueueMode
import pe.upt.mallasos.data.db.MessageEntity
import pe.upt.mallasos.data.db.MessageState
import pe.upt.mallasos.log.EventSink
import pe.upt.mallasos.log.Events
import pe.upt.mallasos.p2p.PeerSession
import pe.upt.mallasos.p2p.RemoteNode

/** Lo que el intercambio necesita de la cola; lo implementa `MessageDao`. */
interface MessageStore {
    /** Mensajes en estado `PENDING` o `CARRIED`. */
    suspend fun queued(): List<MessageEntity>

    /** Devuelve `false` si el identificador ya se había visto. */
    suspend fun insertIfNew(message: MessageEntity): Boolean
}

/**
 * Intercambio de mensajes tras el HELLO (T2.8). Cada lado envía su cola y cierra su envío, y la
 * sesión acaba cuando ambos lo han hecho. Se envía y se recibe a la vez: si ambos enviaran primero
 * podrían bloquearse con el búfer TCP lleno.
 *
 * Quien envía conserva su copia y su estado (R4): el ACK y los reintentos llegan con T2.2.
 */
class MessageExchange(
    private val store: MessageStore,
    private val events: EventSink,
    private val clockMs: () -> Long = System::currentTimeMillis,
) {
    suspend fun run(session: PeerSession, queueMode: QueueMode) = coroutineScope {
        launch {
            sendQueue(session, queueMode)
            session.finishSending()
        }
        receiveAll(session)
    }

    private suspend fun sendQueue(session: PeerSession, queueMode: QueueMode) {
        val peer = session.remote
        Forwarding.outgoing(store.queued(), peer, queueMode, clockMs()).forEach { message ->
            currentCoroutineContext().ensureActive()
            val header = Forwarding.header(message)
            session.frames.write(header, message.payload)
            events.log(Events.SENT, message.uid, peer.nodeId, header.hopsLeft)
        }
    }

    private suspend fun receiveAll(session: PeerSession) {
        while (true) {
            currentCoroutineContext().ensureActive()
            val frame = session.frames.readOrNull() ?: return
            when (frame.header.type) {
                MessageType.SOS, MessageType.NORMAL -> receive(frame, session.remote)
                // Los ACK llegan con T2.2 y T2.5.
                else -> Unit
            }
        }
    }

    private suspend fun receive(frame: Frame, peer: RemoteNode) {
        val header = frame.header
        // Lanza `MalformedMessageException` antes de guardar nada.
        if (header.type == MessageType.SOS) PayloadCodec.decodeSos(frame.payload)
        val uid = header.uid.hex
        val isNew = store.insertIfNew(
            MessageEntity(
                uid = uid,
                type = header.type.code,
                priority = header.priority,
                originId = header.originId,
                destId = header.destId,
                seq = header.seq,
                createdAt = header.timestampSeconds * 1000,
                ttlHours = header.ttlHours,
                hopsLeft = header.hopsLeft,
                payload = frame.payload,
                state = MessageState.CARRIED,
                isOwn = false,
                enqueuedAt = clockMs(),
                receivedFrom = peer.nodeId,
            ),
        )
        if (isNew) {
            events.log(Events.RECEIVED, uid, peer.nodeId, header.hopsLeft)
            events.log(Events.ENQUEUED, uid, peer.nodeId, header.hopsLeft)
        } else {
            events.log(Events.DUPLICATE_DROPPED, uid, peer.nodeId, header.hopsLeft)
        }
    }
}
