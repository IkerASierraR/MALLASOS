package pe.upt.mallasos.dtn

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import pe.upt.mallasos.codec.ANY_POST
import pe.upt.mallasos.codec.EmergencyType
import pe.upt.mallasos.codec.Header
import pe.upt.mallasos.codec.MalformedMessageException
import pe.upt.mallasos.codec.MessageType
import pe.upt.mallasos.codec.PayloadCodec
import pe.upt.mallasos.codec.Priority
import pe.upt.mallasos.codec.SosPayload
import pe.upt.mallasos.codec.Uid
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
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

class MessageExchangeTest {

    private val now = 1_794_000_000_000L
    private val ana = LocalNode(0x0A0A0A0A, "Ana", NodeMode.NORMAL)
    private val beto = LocalNode(0x0B0B0B0B, "Beto", NodeMode.NORMAL)
    private val puesto = LocalNode(0x0D0D0D0D, "Puesto", NodeMode.PUESTO)

    private val sosBytes = PayloadCodec.encodeSos(
        SosPayload(EmergencyType.ATRAPADO, 2, 80, null, "Pabellón B, piso 2", "Ayuda"),
    )

    private data class Logged(val event: String, val uid: String?, val peer: Int?, val hops: Int?)

    /** Teléfono en memoria: cola, `seen_ids` y eventos. */
    private inner class Phone(val node: LocalNode) : MessageStore, EventSink {
        val messages = ConcurrentHashMap<String, MessageEntity>()
        val seen = ConcurrentHashMap.newKeySet<String>()
        val logged = CopyOnWriteArrayList<Logged>()
        val exchange = MessageExchange(this, this) { now }

        fun create(seq: Int, priority: Int = Priority.SOS, enqueuedAt: Long = 0): MessageEntity {
            val uid = Uid(node.nodeId, seq).hex
            val message = MessageEntity(
                uid = uid,
                type = if (priority == Priority.SOS) MessageType.SOS.code else MessageType.NORMAL.code,
                priority = priority,
                originId = node.nodeId,
                destId = ANY_POST,
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

        override suspend fun queued() = messages.values.toList()

        override suspend fun insertIfNew(message: MessageEntity): Boolean {
            if (!seen.add(message.uid)) return false
            messages[message.uid] = message
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

    /** Un contacto completo por TCP real: [owner] como Group Owner y [client] como cliente. */
    private fun contact(owner: Phone, client: Phone, mode: QueueMode = QueueMode.PRIORIDAD) =
        runBlocking {
            val port = ServerSocket(0).use { it.localPort }
            val server = async(Dispatchers.IO) {
                runner(port).serve(owner.node) { owner.exchange.run(it, mode) }
            }
            runner(port).connect("127.0.0.1", client.node) { client.exchange.run(it, mode) }
            server.await()
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
        assertEquals(sos.ttlHours, carried.ttlHours)
    }

    @Test
    fun `quien envia conserva su copia sin cambios`() {
        val a = Phone(ana)
        val sos = a.create(seq = 1)

        contact(a, Phone(beto))

        val kept = a.messages.getValue(sos.uid)
        assertEquals(5, kept.hopsLeft)
        assertEquals(MessageState.PENDING, kept.state)
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
    fun `el mensaje pasa de A a D por contactos sucesivos`() {
        val a = Phone(ana)
        val b = Phone(beto)
        val d = Phone(puesto)
        val sos = a.create(seq = 1)

        contact(a, b)
        contact(d, b)

        val delivered = d.messages.getValue(sos.uid)
        assertEquals(3, delivered.hopsLeft)
        assertEquals(beto.nodeId, delivered.receivedFrom)
        assertEquals(listOf(Logged(Events.SENT, sos.uid, puesto.nodeId, 3)), b.events(Events.SENT))
    }

    @Test
    fun `un mensaje repetido no se guarda de nuevo`() {
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
    fun `registra envio y llegada con el vecino y los saltos`() {
        val a = Phone(ana)
        val b = Phone(beto)
        val sos = a.create(seq = 1)

        contact(a, b)

        assertEquals(listOf(Logged(Events.SENT, sos.uid, beto.nodeId, 4)), a.events(Events.SENT))
        assertEquals(
            listOf(
                Logged(Events.RECEIVED, sos.uid, ana.nodeId, 4),
                Logged(Events.ENQUEUED, sos.uid, ana.nodeId, 4),
            ),
            b.logged.toList(),
        )
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

    @Test(timeout = 10_000)
    fun `dos colas que no caben en el buffer no se bloquean entre si`() {
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
            val first = async { a.exchange.run(session(beto, toA, toB), QueueMode.PRIORIDAD) }
            b.exchange.run(session(ana, toB, toA), QueueMode.PRIORIDAD)
            first.await()
        }

        assertEquals(40, a.messages.size)
        assertEquals(40, b.messages.size)
    }

    @Test
    fun `un SOS mal formado no se guarda`() {
        val port = ServerSocket(0).use { it.localPort }
        val b = Phone(beto)
        val garbage = byteArrayOf(99)
        val header = Header(
            type = MessageType.SOS,
            priority = Priority.SOS,
            originId = ana.nodeId,
            seq = 1,
            destId = ANY_POST,
            timestampSeconds = now / 1000,
            ttlHours = 24,
            hopsLeft = 4,
            payloadLength = garbage.size,
        )
        assertThrows(MalformedMessageException::class.java) {
            runBlocking {
                val client = async(Dispatchers.IO) {
                    runCatching {
                        runner(port).connect("127.0.0.1", ana) { session ->
                            session.frames.write(header, garbage)
                            session.finishSending()
                            session.frames.readOrNull()
                        }
                    }
                }
                try {
                    runner(port).serve(beto) { b.exchange.run(it, QueueMode.PRIORIDAD) }
                } finally {
                    client.await()
                }
            }
        }
        assertTrue(b.messages.isEmpty())
    }
}
