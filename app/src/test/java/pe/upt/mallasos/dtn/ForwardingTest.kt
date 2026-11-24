package pe.upt.mallasos.dtn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pe.upt.mallasos.codec.ANY_POST
import pe.upt.mallasos.codec.MessageType
import pe.upt.mallasos.codec.Priority
import pe.upt.mallasos.data.datastore.DtnMode
import pe.upt.mallasos.data.datastore.NodeMode
import pe.upt.mallasos.data.datastore.QueueMode
import pe.upt.mallasos.data.db.MessageEntity
import pe.upt.mallasos.data.db.MessageState
import pe.upt.mallasos.p2p.RemoteNode

class ForwardingTest {

    private val now = 10_000_000L
    private val hour = 3_600_000L
    private val voluntario = RemoteNode(0x0B, "Beto", NodeMode.AYUDANTE, 0)
    private val puesto = RemoteNode(0x0D, "Puesto", NodeMode.RESCATISTA, 0)

    private fun message(
        uid: String = "m",
        origin: Int = 0x0A,
        dest: Int = ANY_POST,
        hops: Int = 5,
        ttlHours: Int = 24,
        createdAt: Long = now,
        receivedFrom: Int? = null,
        priority: Int = Priority.SOS,
        enqueuedAt: Long = 0,
    ) = MessageEntity(
        uid = uid,
        type = MessageType.SOS.code,
        priority = priority,
        originId = origin,
        destId = dest,
        seq = 7,
        createdAt = createdAt,
        ttlHours = ttlHours,
        hopsLeft = hops,
        payload = byteArrayOf(1, 2, 3),
        state = MessageState.PENDING,
        isOwn = true,
        enqueuedAt = enqueuedAt,
        receivedFrom = receivedFrom,
    )

    @Test
    fun `se reenvia mientras queden saltos`() {
        assertTrue(Forwarding.canForward(message(hops = 1), voluntario, now))
    }

    @Test
    fun `sin saltos no se reenvia a un intermediario`() {
        assertFalse(Forwarding.canForward(message(hops = 0), voluntario, now))
    }

    @Test
    fun `sin saltos se entrega a un puesto si va a cualquier puesto`() {
        assertTrue(Forwarding.canForward(message(hops = 0), puesto, now))
    }

    @Test
    fun `sin saltos se entrega al destinatario concreto`() {
        assertTrue(Forwarding.canForward(message(hops = 0, dest = 0x0B), voluntario, now))
    }

    @Test
    fun `un puesto que no es el destino concreto no cuenta como destino`() {
        assertFalse(Forwarding.canForward(message(hops = 0, dest = 0x0B), puesto, now))
    }

    @Test
    fun `no se devuelve al origen ni a quien lo entrego`() {
        assertFalse(Forwarding.canForward(message(origin = 0x0B), voluntario, now))
        assertFalse(Forwarding.canForward(message(receivedFrom = 0x0B), voluntario, now))
    }

    @Test
    fun `un mensaje vencido no sale aunque siga en cola`() {
        val created = now - 2 * hour
        assertFalse(Forwarding.canForward(message(ttlHours = 2, createdAt = created), voluntario, now))
        assertTrue(Forwarding.canForward(message(ttlHours = 3, createdAt = created), voluntario, now))
    }

    @Test
    fun `la salida respeta el orden de la cola`() {
        val queued = listOf(
            message("normal", priority = Priority.NORMAL, enqueuedAt = 1),
            message("sin-saltos", hops = 0, enqueuedAt = 2),
            message("sos", enqueuedAt = 3),
        )
        assertEquals(
            listOf("sos", "normal"),
            Forwarding.outgoing(queued, voluntario, QueueMode.PRIORIDAD, now).map { it.uid },
        )
        assertEquals(
            listOf("normal", "sos"),
            Forwarding.outgoing(queued, voluntario, QueueMode.FIFO, now).map { it.uid },
        )
    }

    @Test
    fun `el modo basico no repite lo ya enviado salvo el ACK final`() {
        val sent = MessageEntity(
            "enviado", MessageType.SOS.code, Priority.SOS, 0x0A, ANY_POST, 1, now, 24, 5,
            ByteArray(0), MessageState.CARRIED, true, 0, attempts = 1,
        )
        val ack = MessageEntity(
            "ack", MessageType.ACK_FINAL.code, Priority.CONTROL, 0x0A, 0x0C, 2, now, 24, 5,
            ByteArray(0), MessageState.PENDING, true, 0, attempts = 1,
        )
        val queued = listOf(sent, ack, message("nuevo"))
        assertEquals(
            listOf("ack", "nuevo"),
            Forwarding.outgoing(queued, voluntario, QueueMode.PRIORIDAD, now, DtnMode.BASICO)
                .map { it.uid },
        )
        assertEquals(
            listOf("ack", "enviado", "nuevo"),
            Forwarding.outgoing(queued, voluntario, QueueMode.PRIORIDAD, now, DtnMode.PROPUESTO)
                .map { it.uid },
        )
    }

    @Test
    fun `la cabecera descuenta un salto y conserva el mensaje`() {
        val header = Forwarding.header(message(hops = 5, createdAt = 1_794_000_000_500L))
        assertEquals(4, header.hopsLeft)
        assertEquals(MessageType.SOS, header.type)
        assertEquals(0x0A, header.originId)
        assertEquals(7, header.seq)
        assertEquals(ANY_POST, header.destId)
        assertEquals(1_794_000_000L, header.timestampSeconds)
        assertEquals(3, header.payloadLength)
    }

    @Test
    fun `los saltos no bajan de cero`() {
        assertEquals(0, Forwarding.header(message(hops = 0)).hopsLeft)
    }
}
