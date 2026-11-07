package pe.upt.mallasos.p2p

import pe.upt.mallasos.codec.Frame
import pe.upt.mallasos.codec.HEADER_SIZE
import pe.upt.mallasos.codec.Header
import pe.upt.mallasos.codec.MessageCodec
import java.io.DataInputStream
import java.io.InputStream
import java.io.OutputStream

/** Framing sobre TCP: 19 bytes de cabecera y luego el contenido que esta indica (`plan.md` §4). */
class FrameIo(input: InputStream, private val output: OutputStream) {

    private val input = DataInputStream(input)

    /** Lanza `EOFException` si el enlace se corta a mitad de un mensaje. */
    fun read(): Frame {
        val headerBytes = ByteArray(HEADER_SIZE)
        input.readFully(headerBytes)
        val header = MessageCodec.decodeHeader(headerBytes)
        val payload = ByteArray(header.payloadLength)
        input.readFully(payload)
        return Frame(header, payload)
    }

    fun write(header: Header, payload: ByteArray) {
        output.write(MessageCodec.encode(header, payload))
        output.flush()
    }
}
