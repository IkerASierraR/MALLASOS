package pe.upt.mallasos.p2p

import pe.upt.mallasos.codec.Frame
import pe.upt.mallasos.codec.HEADER_SIZE
import pe.upt.mallasos.codec.Header
import pe.upt.mallasos.codec.MessageCodec
import java.io.DataInputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream

/** Framing sobre TCP: 19 bytes de cabecera y luego el contenido que esta indica (`plan.md` §4). */
class FrameIo(input: InputStream, private val output: OutputStream) {

    private val input = DataInputStream(input)

    /** Lanza `EOFException` si el enlace se corta a mitad de un mensaje. */
    fun read(): Frame = readOrNull() ?: throw EOFException()

    /** Lee un byte suelto; `null` al terminar. Sirve para comprobar el corte de E2. */
    fun readByteForTest(): Int? = input.read().takeIf { it >= 0 }

    /** Devuelve `null` si el vecino cerró su envío entre dos mensajes. */
    fun readOrNull(): Frame? {
        val first = input.read()
        if (first < 0) return null
        val headerBytes = ByteArray(HEADER_SIZE)
        headerBytes[0] = first.toByte()
        input.readFully(headerBytes, 1, HEADER_SIZE - 1)
        val header = MessageCodec.decodeHeader(headerBytes)
        val payload = ByteArray(header.payloadLength)
        input.readFully(payload)
        return Frame(header, payload)
    }

    fun write(header: Header, payload: ByteArray) = writeRaw(MessageCodec.encode(header, payload))

    /** Admite un mensaje incompleto: así el corte de E2 deja el enlace a mitad de un mensaje. */
    fun writeRaw(bytes: ByteArray) {
        output.write(bytes)
        output.flush()
    }
}
