package pe.upt.mallasos.p2p

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import pe.upt.mallasos.codec.ANY_POST
import pe.upt.mallasos.codec.Header
import pe.upt.mallasos.codec.HelloPayload
import pe.upt.mallasos.codec.MalformedMessageException
import pe.upt.mallasos.codec.MessageCodec
import pe.upt.mallasos.codec.MessageType
import pe.upt.mallasos.codec.PayloadCodec
import pe.upt.mallasos.codec.Priority
import pe.upt.mallasos.data.datastore.NodeMode
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException

class SessionRunnerTest {

    private val puesto = LocalNode(0x0D0D0D0D, "Puesto", NodeMode.PUESTO)
    private val persona = LocalNode(0x0A0A0A0A, "Núñez", NodeMode.NORMAL)

    private fun freePort() = ServerSocket(0).use { it.localPort }

    private fun runner(port: Int) = SessionRunner(
        port = port,
        acceptTimeoutMs = 2_000,
        readTimeoutMs = 500,
        connectAttempts = 20,
        retryDelayMs = 50,
        clockMs = { 1_794_000_000_000L },
    )

    private fun helloFrame(
        origin: Int,
        nodeId: Int = origin,
        mode: Int = 0,
        type: MessageType = MessageType.HELLO,
    ): ByteArray {
        val payload = PayloadCodec.encodeHello(HelloPayload(nodeId, mode, 1L, "Falso"))
        return MessageCodec.encode(
            Header(
                type = type,
                priority = Priority.CONTROL,
                originId = origin,
                seq = 0,
                destId = ANY_POST,
                timestampSeconds = 1,
                ttlHours = 0,
                hopsLeft = 0,
                payloadLength = payload.size,
            ),
            payload,
        )
    }

    /** Cliente manual que envía [bytes] y luego espera a que el servidor cierre. */
    private fun rawClient(port: Int, bytes: ByteArray) = Thread {
        var socket: Socket? = null
        while (socket == null) {
            socket = runCatching { Socket("127.0.0.1", port) }.getOrNull()
        }
        socket.use {
            it.getOutputStream().write(bytes)
            it.getOutputStream().flush()
            runCatching { it.getInputStream().readBytes() }
        }
    }.apply {
        isDaemon = true
        start()
    }

    @Test
    fun `ambos lados conocen al vecino tras el HELLO`() = runBlocking {
        val port = freePort()
        val server = async(Dispatchers.IO) { runner(port).serve(puesto) { it.remote } }
        val client = runner(port).connect("127.0.0.1", persona) { it.remote }

        assertEquals(
            RemoteNode(persona.nodeId, "Núñez", NodeMode.NORMAL, 1_794_000_000_000L),
            server.await(),
        )
        assertEquals(
            RemoteNode(puesto.nodeId, "Puesto", NodeMode.PUESTO, 1_794_000_000_000L),
            client,
        )
    }

    @Test
    fun `la sesion deja el canal listo para mensajes`() = runBlocking {
        val port = freePort()
        val header = Header(
            type = MessageType.ACK_HOP,
            priority = Priority.CONTROL,
            originId = persona.nodeId,
            seq = 7,
            destId = puesto.nodeId,
            timestampSeconds = 1,
            ttlHours = 1,
            hopsLeft = 1,
            payloadLength = 2,
        )
        val server = async(Dispatchers.IO) {
            runner(port).serve(puesto) { it.frames.read() }
        }
        runner(port).connect("127.0.0.1", persona) { it.frames.write(header, byteArrayOf(4, 2)) }

        val frame = server.await()
        assertEquals(header, frame.header)
        assertArrayEquals(byteArrayOf(4, 2), frame.payload)
    }

    @Test
    fun `el cliente reintenta hasta que el Group Owner abre el puerto`() = runBlocking {
        val port = freePort()
        val client = async(Dispatchers.IO) {
            runner(port).connect("127.0.0.1", persona) { it.remote.nodeId }
        }
        delay(200)
        withContext(Dispatchers.IO) { runner(port).serve(puesto) { } }
        assertEquals(puesto.nodeId, client.await())
    }

    @Test
    fun `el cliente se rinde si nunca hay servidor`() {
        val impatient = SessionRunner(port = freePort(), connectAttempts = 2, retryDelayMs = 1)
        assertThrows(IOException::class.java) {
            runBlocking { impatient.connect("127.0.0.1", persona) { } }
        }
    }

    @Test
    fun `el servidor deja de esperar sin vecino`() {
        val alone = SessionRunner(port = freePort(), acceptTimeoutMs = 50)
        assertThrows(SocketTimeoutException::class.java) {
            runBlocking { alone.serve(puesto) { } }
        }
    }

    private fun assertRejected(bytes: ByteArray) {
        val port = freePort()
        rawClient(port, bytes)
        assertThrows(MalformedMessageException::class.java) {
            runBlocking { runner(port).serve(puesto) { } }
        }
    }

    @Test
    fun `rechaza un primer mensaje que no es HELLO`() {
        assertRejected(helloFrame(origin = 5, type = MessageType.ACK_HOP))
    }

    @Test
    fun `rechaza un HELLO cuyo ID no coincide con el origen`() {
        assertRejected(helloFrame(origin = 5, nodeId = 6))
    }

    @Test
    fun `rechaza IDs reservados o el propio`() {
        assertRejected(helloFrame(origin = 0))
        assertRejected(helloFrame(origin = ANY_POST))
        assertRejected(helloFrame(origin = puesto.nodeId))
    }

    @Test
    fun `rechaza un modo desconocido`() {
        assertRejected(helloFrame(origin = 5, mode = 9))
    }

    @Test
    fun `un vecino mudo agota la lectura`() {
        val port = freePort()
        rawClient(port, ByteArray(0))
        assertThrows(SocketTimeoutException::class.java) {
            runBlocking { runner(port).serve(puesto) { } }
        }
    }

    @Test
    fun `detecta un corte a mitad de mensaje`() {
        val frames = FrameIo(
            ByteArrayInputStream(helloFrame(origin = 5).copyOf(25)),
            ByteArrayOutputStream(),
        )
        assertThrows(EOFException::class.java) { frames.read() }
    }
}
