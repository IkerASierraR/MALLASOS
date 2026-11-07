package pe.upt.mallasos.p2p

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pe.upt.mallasos.codec.ANY_POST
import pe.upt.mallasos.codec.Header
import pe.upt.mallasos.codec.HelloPayload
import pe.upt.mallasos.codec.MalformedMessageException
import pe.upt.mallasos.codec.MessageType
import pe.upt.mallasos.codec.PayloadCodec
import pe.upt.mallasos.codec.Priority
import pe.upt.mallasos.data.datastore.NodeMode
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

const val SESSION_PORT = 8988

/** Datos propios que se anuncian en el HELLO. */
data class LocalNode(val nodeId: Int, val alias: String, val mode: NodeMode)

data class RemoteNode(val nodeId: Int, val alias: String, val mode: NodeMode, val clockMs: Long)

/**
 * Sesión abierta tras el HELLO; la capa DTN intercambia aquí sus mensajes. [finishSending] avisa al
 * vecino de que no se enviará nada más, sin dejar de leer lo que aún tenga para este teléfono.
 */
class PeerSession(
    val remote: RemoteNode,
    val frames: FrameIo,
    val finishSending: () -> Unit = {},
)

/**
 * Sesión TCP entre dos teléfonos: el Group Owner llama a [serve] y el cliente a [connect].
 * Ambos lanzan `IOException` si el enlace falla y `MalformedMessageException` si el HELLO no es
 * válido. El socket se cierra siempre al terminar.
 */
class SessionRunner(
    private val port: Int = SESSION_PORT,
    private val acceptTimeoutMs: Int = 15_000,
    private val readTimeoutMs: Int = 5_000,
    private val connectAttempts: Int = 5,
    private val retryDelayMs: Long = 500,
    private val clockMs: () -> Long = System::currentTimeMillis,
) {
    suspend fun <T> serve(local: LocalNode, onSession: suspend (PeerSession) -> T): T =
        withContext(Dispatchers.IO) {
            val server = ServerSocket().apply {
                reuseAddress = true
                soTimeout = acceptTimeoutMs
                bind(InetSocketAddress(port))
            }
            val socket = server.use { it.accept() }
            run(socket, local, onSession)
        }

    /** El servidor del Group Owner puede tardar en abrir: se reintenta unas cuantas veces. */
    suspend fun <T> connect(
        host: String,
        local: LocalNode,
        onSession: suspend (PeerSession) -> T,
    ): T =
        withContext(Dispatchers.IO) {
            val socket = open(host)
            run(socket, local, onSession)
        }

    private suspend fun open(host: String): Socket {
        var attempt = 1
        while (true) {
            val socket = Socket()
            try {
                socket.connect(InetSocketAddress(host, port), readTimeoutMs)
                return socket
            } catch (error: IOException) {
                socket.close()
                if (attempt == connectAttempts) throw error
            }
            attempt++
            delay(retryDelayMs)
        }
    }

    private suspend fun <T> run(
        socket: Socket,
        local: LocalNode,
        onSession: suspend (PeerSession) -> T,
    ): T = socket.use {
        coroutineScope {
            // Las lecturas y escrituras bloqueadas ignoran la cancelación: cerrar el socket las
            // libera, y así se respeta el límite de sesión.
            val closer = launch(start = CoroutineStart.UNDISPATCHED) {
                try {
                    awaitCancellation()
                } finally {
                    socket.close()
                }
            }
            try {
                socket.soTimeout = readTimeoutMs
                val frames = FrameIo(socket.getInputStream(), socket.getOutputStream())
                sendHello(frames, local)
                val remote = receiveHello(frames, local)
                onSession(PeerSession(remote, frames, socket::shutdownOutput))
            } finally {
                closer.cancel()
            }
        }
    }

    private fun sendHello(frames: FrameIo, local: LocalNode) {
        val now = clockMs()
        val payload = PayloadCodec.encodeHello(
            HelloPayload(local.nodeId, local.mode.code, now, local.alias),
        )
        // El HELLO no se reenvía ni se guarda: no consume secuencia, TTL ni saltos.
        val header = Header(
            type = MessageType.HELLO,
            priority = Priority.CONTROL,
            originId = local.nodeId,
            seq = 0,
            destId = ANY_POST,
            timestampSeconds = now / 1000,
            ttlHours = 0,
            hopsLeft = 0,
            payloadLength = payload.size,
        )
        frames.write(header, payload)
    }

    private fun receiveHello(frames: FrameIo, local: LocalNode): RemoteNode {
        val frame = frames.read()
        if (frame.header.type != MessageType.HELLO) {
            throw MalformedMessageException("Se esperaba HELLO y llegó ${frame.header.type}")
        }
        val hello = PayloadCodec.decodeHello(frame.payload)
        if (hello.nodeId != frame.header.originId) {
            throw MalformedMessageException("El ID del HELLO no coincide con su origen")
        }
        if (hello.nodeId == 0 || hello.nodeId == ANY_POST || hello.nodeId == local.nodeId) {
            throw MalformedMessageException("ID de vecino no válido")
        }
        val mode = NodeMode.entries.firstOrNull { it.code == hello.mode }
            ?: throw MalformedMessageException("Modo ${hello.mode}")
        return RemoteNode(hello.nodeId, hello.alias, mode, hello.clockMs)
    }
}
