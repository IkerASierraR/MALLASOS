package pe.upt.mallasos.dtn

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import pe.upt.mallasos.codec.ANY_POST
import pe.upt.mallasos.codec.EmergencyType
import pe.upt.mallasos.codec.Frame
import pe.upt.mallasos.codec.Header
import pe.upt.mallasos.codec.MalformedMessageException
import pe.upt.mallasos.codec.MessageType
import pe.upt.mallasos.codec.PayloadCodec
import pe.upt.mallasos.codec.Priority
import pe.upt.mallasos.codec.SosPayload
import pe.upt.mallasos.codec.Uid
import pe.upt.mallasos.data.datastore.DtnMode
import pe.upt.mallasos.data.datastore.NodeMode
import pe.upt.mallasos.data.datastore.QueueMode
import pe.upt.mallasos.data.db.MessageEntity
import pe.upt.mallasos.data.db.MessageState
import pe.upt.mallasos.log.EventSink
import pe.upt.mallasos.log.Events
import pe.upt.mallasos.p2p.FrameIo
import pe.upt.mallasos.p2p.LocalNode
import pe.upt.mallasos.p2p.PeerSession
import pe.upt.mallasos.p2p.RemoteNode
import pe.upt.mallasos.p2p.SessionRunner
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

class MessageExchangeTest {

    private val now = 1_794_000_000_000L
    private val ana = LocalNode(0x0A0A0A0A, "Ana", NodeMode.NORMAL)
    private val beto = LocalNode(0x0B0B0B0B, "Beto", NodeMode.NORMAL)
    private val puesto = LocalNode(0x0D0D0D0D, "Puesto", NodeMode.PUESTO)
    private val fast = AckTimings(ackTimeoutMs = 100, retryDelaysMs = listOf(5, 10, 20))

    private val sosBytes = PayloadCodec.encodeSos(
        SosPayload(EmergencyType.ATRAPADO, 2, 80, null, "Pabellón B, piso 2", "Ayuda"),
    )

    private data class Logged(val event: String, val uid: String?, val peer: Int?, val hops: Int?)

    /** Teléfono en memoria: cola, `seen_ids` y eventos. */
    private inner class Phone(val node: LocalNode) : MessageStore, EventSink {
        val messages = ConcurrentHashMap<String, MessageEntity>()
        val seen = ConcurrentHashMap.newKeySet<String>()
        val logged = CopyOnWriteArrayList<Logged>()
        val attempts = ConcurrentHashMap<String, Int>()
        private val sequence = AtomicInteger(1_000)
        val exchange = MessageExchange(this, this, { sequence.incrementAndGet() }, fast) { now }

        fun config(mode: QueueMode = QueueMode.PRIORIDAD, dtn: DtnMode = DtnMode.PROPUESTO) =
            ExchangeConfig(node, dtn, mode, ttlHours = 24, hops = 5)

        fun create(
            seq: Int,
            priority: Int = Priority.SOS,
            enqueuedAt: Long = 0,
            dest: Int = ANY_POST,
        ): MessageEntity {
            val uid = Uid(node.nodeId, seq).hex
            val message = MessageEntity(
                uid = uid,
                type = if (priority == Priority.SOS) MessageType.SOS.code else MessageType.NORMAL.code,
                priority = priority,
                originId = node.nodeId,
                destId = dest,
                seq = seq,
                createdAt = now,
                ttlHours = 24,
                hopsLeft = 5,
                payload = sosBytes,
                state = MessageState.PENDING,
                isOwn = true,
                enqueuedAt = enqueuedAt,
            )
            messages[uid] = message
            seen += uid
            return message
        }

        override suspend fun queued() = messages.values.filter {
            it.state == MessageState.PENDING || it.state == MessageState.CARRIED
        }

        override suspend fun insertIfNew(message: MessageEntity): Boolean {
            if (!seen.add(message.uid)) return false
            messages[message.uid] = message
            return true
        }

        override suspend fun recordAttempt(uid: String, atMs: Long) {
            attempts.merge(uid, 1, Int::plus)
        }

        private fun withState(uid: String, state: MessageState) {
            val old = messages[uid] ?: return
            messages[uid] = MessageEntity(
                old.uid, old.type, old.priority, old.originId, old.destId, old.seq, old.createdAt,
                old.ttlHours, old.hopsLeft, old.payload, state, old.isOwn, old.enqueuedAt,
                receivedFrom = old.receivedFrom,
            )
        }

        override suspend fun markCarried(uid: String) {
            if (messages[uid]?.state == MessageState.PENDING) withState(uid, MessageState.CARRIED)
        }

        override suspend fun confirmDelivered(uid: String, atMs: Long): Boolean {
            seen += uid
            val copy = messages[uid] ?: return false
            if (copy.state == MessageState.DELIVERED) return false
            if (copy.isOwn) {
                withState(uid, MessageState.DELIVERED)
                return false
            }
            messages.remove(uid)
            return true
        }

        override suspend fun log(
            event: String,
            uid: String?,
            peerId: Int?,
            hopsLeft: Int?,
            extra: String?,
        ) {
            logged += Logged(event, uid, peerId, hopsLeft)
        }

        fun events(name: String) = logged.filter { it.event == name }
    }

    private fun runner(port: Int) = SessionRunner(
        port = port,
        acceptTimeoutMs = 5_000,
        readTimeoutMs = 5_000,
        connectAttempts = 40,
        retryDelayMs = 25,
    )

    private fun freePort() = ServerSocket(0).use { it.localPort }

    /** Un contacto completo por TCP real: [owner] como Group Owner y [client] como cliente. */
    private fun contact(
        owner: Phone,
        client: Phone,
        mode: QueueMode = QueueMode.PRIORIDAD,
        dtn: DtnMode = DtnMode.PROPUESTO,
    ) = runBlocking {
        val port = freePort()
        val server = async(Dispatchers.IO) {
            runner(port).serve(owner.node) { owner.exchange.run(it, owner.config(mode, dtn)) }
        }
        runner(port).connect("127.0.0.1", client.node) {
            client.exchange.run(it, client.config(mode, dtn))
        }
        server.await()
    }

    /** Vecino controlado a mano: [onFrame] decide qué responder a cada mensaje. */
    private fun contactWithScript(
        phone: Phone,
        dtn: DtnMode = DtnMode.PROPUESTO,
        onFrame: (Frame, PeerSession) -> Unit,
    ) = runBlocking {
        val port = freePort()
        val server = async(Dispatchers.IO) {
            runner(port).serve(phone.node) { phone.exchange.run(it, phone.config(dtn = dtn)) }
        }
        runner(port).connect("127.0.0.1", beto) { session ->
            while (true) {
                val frame = session.frames.read()
                if (frame.header.type == MessageType.SUMMARY) break
                onFrame(frame, session)
            }
            writeEnd(session, beto)
        }
        server.await()
    }

    private fun writeEnd(session: PeerSession, from: LocalNode) {
        session.frames.write(
            Header(
                MessageType.SUMMARY, Priority.CONTROL, from.nodeId, 0, session.remote.nodeId,
                1, 0, 0, 0,
            ),
            ByteArray(0),
        )
    }

    private fun ack(frame: Frame, session: PeerSession) {
        val payload = PayloadCodec.encodeAckHop(frame.header.uid)
        session.frames.write(
            Header(
                MessageType.ACK_HOP, Priority.CONTROL, beto.nodeId, 0, session.remote.nodeId,
                1, 0, 0, payload.size,
            ),
            payload,
        )
    }

    @Test
    fun `un SOS de A aparece en la cola de B con un salto menos`() {
        val a = Phone(ana)
        val b = Phone(beto)
        val sos = a.create(seq = 1)

        contact(a, b)

        val carried = b.messages.getValue(sos.uid)
        assertEquals(4, carried.hopsLeft)
        assertEquals(MessageState.CARRIED, carried.state)
        assertFalse(carried.isOwn)
        assertEquals(ana.nodeId, carried.receivedFrom)
        assertEquals(now, carried.createdAt)
        assertArrayEquals(sosBytes, carried.payload)
    }

    @Test
    fun `con ACK salto el emisor conserva su copia como CARRIED`() {
        val a = Phone(ana)
        val sos = a.create(seq = 1)

        contact(a, Phone(beto))

        val kept = a.messages.getValue(sos.uid)
        assertEquals(5, kept.hopsLeft)
        assertEquals(MessageState.CARRIED, kept.state)
        assertEquals(listOf(Logged(Events.ACK_HOP_RX, sos.uid, beto.nodeId, null)), a.events(Events.ACK_HOP_RX))
        assertTrue(a.events(Events.RETRY).isEmpty())
    }

    @Test
    fun `sin ACK reintenta tres veces y deja el mensaje pendiente`() {
        val a = Phone(ana)
        val first = a.create(seq = 1, enqueuedAt = 1)
        val second = a.create(seq = 2, enqueuedAt = 2)
        val seen = CopyOnWriteArrayList<String>()

        contactWithScript(a) { frame, _ -> seen += frame.header.uid.hex }

        assertEquals(List(4) { first.uid }, seen.toList())
        assertEquals(1, a.events(Events.SENT).size)
        assertEquals(3, a.events(Events.RETRY).size)
        assertEquals(4, a.attempts[first.uid])
        assertEquals(MessageState.PENDING, a.messages.getValue(first.uid).state)
        assertEquals(MessageState.PENDING, a.messages.getValue(second.uid).state)
    }

    @Test
    fun `un ACK en el segundo intento confirma el salto`() {
        val a = Phone(ana)
        val sos = a.create(seq = 1)
        val copies = AtomicInteger()

        contactWithScript(a) { frame, session ->
            if (copies.incrementAndGet() == 2) ack(frame, session)
        }

        assertEquals(2, copies.get())
        assertEquals(1, a.events(Events.RETRY).size)
        assertEquals(MessageState.CARRIED, a.messages.getValue(sos.uid).state)
    }

    @Test
    fun `el modo basico no espera ACK ni reintenta`() {
        val a = Phone(ana)
        val sos = a.create(seq = 1)
        val copies = AtomicInteger()

        contactWithScript(a, DtnMode.BASICO) { _, _ -> copies.incrementAndGet() }

        assertEquals(1, copies.get())
        assertTrue(a.events(Events.RETRY).isEmpty())
        assertEquals(MessageState.CARRIED, a.messages.getValue(sos.uid).state)
    }

    @Test
    fun `un duplicado no se guarda pero recibe su ACK salto`() {
        val a = Phone(ana)
        val b = Phone(beto)
        val sos = a.create(seq = 1)

        contact(a, b)
        val first = b.messages.getValue(sos.uid)
        contact(a, b)

        assertEquals(first, b.messages.getValue(sos.uid))
        assertEquals(1, b.events(Events.RECEIVED).size)
        assertEquals(
            listOf(Logged(Events.DUPLICATE_DROPPED, sos.uid, ana.nodeId, 4)),
            b.events(Events.DUPLICATE_DROPPED),
        )
        assertEquals(2, a.events(Events.ACK_HOP_RX).size)
    }

    @Test
    fun `ambos lados intercambian sus colas en la misma sesion`() {
        val a = Phone(ana)
        val b = Phone(beto)
        val fromA = a.create(seq = 1)
        val fromB = b.create(seq = 1)

        contact(a, b)

        assertTrue(a.messages.containsKey(fromB.uid))
        assertTrue(b.messages.containsKey(fromA.uid))
    }

    @Test
    fun `no devuelve el mensaje a quien se lo entrego`() {
        val a = Phone(ana)
        val b = Phone(beto)
        a.create(seq = 1)

        contact(a, b)
        contact(b, a)

        assertTrue(b.events(Events.SENT).isEmpty())
    }

    @Test
    fun `con prioridad estricta los SOS salen antes que los normales`() {
        val a = Phone(ana)
        val b = Phone(beto)
        val normal = a.create(seq = 1, priority = Priority.NORMAL, enqueuedAt = 1)
        val sos = a.create(seq = 2, enqueuedAt = 2)

        contact(a, b)

        assertEquals(listOf(sos.uid, normal.uid), b.events(Events.RECEIVED).map { it.uid })
    }

    @Test
    fun `por orden de llegada sale primero el mas antiguo`() {
        val a = Phone(ana)
        val b = Phone(beto)
        val normal = a.create(seq = 1, priority = Priority.NORMAL, enqueuedAt = 1)
        val sos = a.create(seq = 2, enqueuedAt = 2)

        contact(a, b, QueueMode.FIFO)

        assertEquals(listOf(normal.uid, sos.uid), b.events(Events.RECEIVED).map { it.uid })
    }

    @Test
    fun `un puesto confirma y A lo ve entregado en la misma sesion`() {
        val a = Phone(ana)
        val d = Phone(puesto)
        val sos = a.create(seq = 1)

        contact(d, a)

        assertEquals(MessageState.DELIVERED, d.messages.getValue(sos.uid).state)
        assertEquals(1, d.events(Events.DELIVERED_FINAL).size)
        assertEquals(MessageState.DELIVERED, a.messages.getValue(sos.uid).state)
        assertEquals(
            listOf(sos.uid),
            a.events(Events.ACK_FINAL_RX).map { it.uid },
        )
    }

    @Test
    fun `el ACK final de un puesto borra las copias del recorrido A B D`() {
        val a = Phone(ana)
        val b = Phone(beto)
        val d = Phone(puesto)
        val sos = a.create(seq = 1)

        contact(a, b)
        contact(d, b)

        assertEquals(3, d.messages.getValue(sos.uid).hopsLeft)
        assertNull(b.messages[sos.uid])
        assertEquals(listOf(sos.uid), b.events(Events.PURGED).map { it.uid })
        assertEquals(MessageState.CARRIED, a.messages.getValue(sos.uid).state)

        contact(a, b)

        assertEquals(MessageState.DELIVERED, a.messages.getValue(sos.uid).state)
        assertNull(b.messages[sos.uid])
    }

    @Test
    fun `tras el ACK final una copia que llega tarde se descarta`() {
        val a = Phone(ana)
        val b = Phone(beto)
        val d = Phone(puesto)
        val sos = a.create(seq = 1)
        contact(a, b)
        contact(d, b)

        contact(a, b)

        assertNull(b.messages[sos.uid])
        assertEquals(listOf(sos.uid), b.events(Events.DUPLICATE_DROPPED).map { it.uid })
    }

    @Test
    fun `el modo Normal no confirma lo dirigido a cualquier puesto`() {
        val a = Phone(ana)
        val b = Phone(beto)
        val sos = a.create(seq = 1)

        contact(a, b)

        assertTrue(b.events(Events.DELIVERED_FINAL).isEmpty())
        assertEquals(MessageState.CARRIED, a.messages.getValue(sos.uid).state)
        assertEquals(sos.uid, b.queuedUid())
    }

    private fun Phone.queuedUid() = runBlocking { queued().single().uid }

    @Test
    fun `un telefono concreto confirma lo dirigido a su ID`() {
        val a = Phone(ana)
        val b = Phone(beto)
        val sos = a.create(seq = 1, dest = beto.nodeId)

        contact(a, b)

        assertEquals(MessageState.DELIVERED, b.messages.getValue(sos.uid).state)
        assertEquals(MessageState.DELIVERED, a.messages.getValue(sos.uid).state)
    }

    @Test
    fun `un mensaje normal no genera ACK final`() {
        val a = Phone(ana)
        val d = Phone(puesto)
        val normal = a.create(seq = 1, priority = Priority.NORMAL)

        contact(a, d)

        assertEquals(MessageState.CARRIED, d.messages.getValue(normal.uid).state)
        assertTrue(a.events(Events.ACK_FINAL_RX).isEmpty())
    }

    @Test
    fun `el ACK final sigue en la cola del puesto para llegar a otros`() {
        val a = Phone(ana)
        val d = Phone(puesto)
        a.create(seq = 1)

        contact(a, d)

        val ack = runBlocking { d.queued() }.single()
        assertEquals(MessageType.ACK_FINAL.code, ack.type)
        assertEquals(Priority.CONTROL, ack.priority)
        assertEquals(ana.nodeId, ack.destId)
    }

    @Test
    fun `en modo basico el ACK final llega en la misma sesion`() {
        val a = Phone(ana)
        val d = Phone(puesto)
        val sos = a.create(seq = 1)

        repeat(30) {
            d.seen.remove(sos.uid)
            d.messages.remove(sos.uid)
            a.messages[sos.uid] = a.messages.getValue(sos.uid).let { old ->
                MessageEntity(
                    old.uid, old.type, old.priority, old.originId, old.destId, old.seq,
                    old.createdAt, old.ttlHours, old.hopsLeft, old.payload, MessageState.PENDING,
                    old.isOwn, old.enqueuedAt,
                )
            }

            // Sin el cierre ordenado, el puesto escribe en un socket ya cerrado y esto lanza.
            contact(d, a, dtn = DtnMode.BASICO)

            assertEquals(MessageState.DELIVERED, a.messages.getValue(sos.uid).state)
        }
    }

    @Test
    fun `un vecino que termina sin esperar ACK no rompe la sesion`() {
        val d = Phone(puesto)
        val port = freePort()
        val payload = sosBytes
        val header = Header(
            MessageType.SOS, Priority.SOS, ana.nodeId, 1, ANY_POST, now / 1000, 24, 4, payload.size,
        )
        val received = CopyOnWriteArrayList<MessageType>()

        runBlocking {
            val server = async(Dispatchers.IO) {
                runner(port).serve(puesto) { d.exchange.run(it, d.config()) }
            }
            runner(port).connect("127.0.0.1", ana) { session ->
                session.frames.write(header, payload)
                writeEnd(session, ana)
                session.finishSending()
                while (true) received += (session.frames.readOrNull() ?: break).header.type
            }
            server.await()
        }

        assertTrue(MessageType.ACK_FINAL in received)
        assertTrue(MessageType.ACK_HOP in received)
        assertTrue(MessageType.SUMMARY in received)
    }

    /** Canal con un búfer pequeño: como un búfer TCP lleno, bloquea al que escribe. */
    private class BoundedPipe {
        private val bytes = ArrayBlockingQueue<Int>(64)
        val input = object : InputStream() {
            override fun read(): Int = bytes.take()
        }
        val output = object : OutputStream() {
            override fun write(b: Int) = bytes.put(b and 0xFF)
        }

        fun close() = bytes.put(-1)
    }

    private fun exchangeOverSmallBuffers(dtn: DtnMode) {
        val a = Phone(ana)
        val b = Phone(beto)
        repeat(20) {
            a.create(seq = it)
            b.create(seq = it)
        }
        val toB = BoundedPipe()
        val toA = BoundedPipe()
        fun session(peer: LocalNode, input: BoundedPipe, output: BoundedPipe) = PeerSession(
            RemoteNode(peer.nodeId, peer.alias, peer.mode, now),
            FrameIo(input.input, output.output),
            output::close,
        )

        runBlocking(Dispatchers.IO) {
            val first = async { a.exchange.run(session(beto, toA, toB), a.config(dtn = dtn)) }
            b.exchange.run(session(ana, toB, toA), b.config(dtn = dtn))
            first.await()
        }

        assertEquals(40, a.messages.size)
        assertEquals(40, b.messages.size)
    }

    @Test(timeout = 10_000)
    fun `dos colas que no caben en el buffer no se bloquean con ACK`() {
        exchangeOverSmallBuffers(DtnMode.PROPUESTO)
    }

    @Test(timeout = 10_000)
    fun `dos colas que no caben en el buffer no se bloquean sin ACK`() {
        exchangeOverSmallBuffers(DtnMode.BASICO)
    }

    @Test
    fun `un SOS mal formado no se guarda ni se confirma`() {
        val port = freePort()
        val b = Phone(beto)
        val garbage = byteArrayOf(99)
        val header = Header(
            MessageType.SOS, Priority.SOS, ana.nodeId, 1, ANY_POST, now / 1000, 24, 4, garbage.size,
        )
        assertThrows(MalformedMessageException::class.java) {
            runBlocking {
                val client = async(Dispatchers.IO) {
                    runCatching {
                        runner(port).connect("127.0.0.1", ana) { session ->
                            session.frames.write(header, garbage)
                            session.frames.readOrNull()
                        }
                    }
                }
                try {
                    runner(port).serve(beto) { b.exchange.run(it, b.config()) }
                } finally {
                    client.await()
                }
            }
        }
        assertTrue(b.messages.isEmpty())
    }

    @Test
    fun `un vecino que cierra sin terminar es un enlace perdido`() {
        val port = freePort()
        val b = Phone(beto)
        val error = runCatching {
            runBlocking {
                val client = async(Dispatchers.IO) {
                    runner(port).connect("127.0.0.1", ana) { }
                }
                try {
                    runner(port).serve(beto) { b.exchange.run(it, b.config()) }
                } finally {
                    client.await()
                }
            }
        }.exceptionOrNull()
        assertTrue(error is IOException)
    }
}
