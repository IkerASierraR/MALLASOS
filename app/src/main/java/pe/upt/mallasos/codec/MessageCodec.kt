package pe.upt.mallasos.codec

import java.nio.ByteBuffer

const val PROTOCOL_VERSION = 1
const val HEADER_SIZE = 19
const val MAX_PAYLOAD_SIZE = 255

/** Destino "cualquier puesto" (`FFFFFFFF`). */
const val ANY_POST: Int = -1

enum class MessageType(val code: Int) {
    SOS(1), NORMAL(2), ACK_HOP(3), ACK_FINAL(4), HELLO(5), SUMMARY(6);

    companion object {
        fun fromCode(code: Int) = entries.firstOrNull { it.code == code }
    }
}

object Priority {
    const val CONTROL = 0
    const val SOS = 1
    const val NORMAL = 2
}

class MalformedMessageException(message: String) : Exception(message)

data class Header(
    val type: MessageType,
    val priority: Int,
    val originId: Int,
    val seq: Int,
    val destId: Int,
    val timestampSeconds: Long,
    val ttlHours: Int,
    val hopsLeft: Int,
    val payloadLength: Int,
) {
    val uid: Uid get() = Uid(originId, seq)
}

/** Identificador único: origen (4 bytes) + secuencia (2 bytes) (R5). */
data class Uid(val originId: Int, val seq: Int) {
    val hex: String get() = "%08X%04X".format(originId, seq)
}

class Frame(val header: Header, val payload: ByteArray)

/** Cabecera binaria big-endian de 19 bytes (`plan.md` §4). */
object MessageCodec {

    fun encode(header: Header, payload: ByteArray): ByteArray {
        require(payload.size <= MAX_PAYLOAD_SIZE) { "Contenido de ${payload.size} bytes" }
        require(payload.size == header.payloadLength) { "Longitud inconsistente" }
        require(header.seq in 0..0xFFFF) { "Secuencia fuera de rango" }
        require(header.timestampSeconds in 0..0xFFFFFFFFL) { "Marca de tiempo fuera de rango" }
        require(header.ttlHours in 0..0xFF && header.hopsLeft in 0..0xFF) {
            "TTL o saltos fuera de rango"
        }
        return ByteBuffer.allocate(HEADER_SIZE + payload.size)
            .put(((PROTOCOL_VERSION shl 4) or header.type.code).toByte())
            .put(header.priority.toByte())
            .putInt(header.originId)
            .putShort(header.seq.toShort())
            .putInt(header.destId)
            .putInt(header.timestampSeconds.toInt())
            .put(header.ttlHours.toByte())
            .put(header.hopsLeft.toByte())
            .put(header.payloadLength.toByte())
            .put(payload)
            .array()
    }

    /** Interpreta los 19 bytes de cabecera; el framing TCP lee después `payloadLength` bytes. */
    fun decodeHeader(bytes: ByteArray): Header {
        if (bytes.size < HEADER_SIZE) throw MalformedMessageException("Cabecera incompleta")
        val buffer = ByteBuffer.wrap(bytes, 0, HEADER_SIZE)
        val versionAndType = buffer.get().toInt() and 0xFF
        val version = versionAndType ushr 4
        if (version != PROTOCOL_VERSION) throw MalformedMessageException("Versión $version")
        val typeCode = versionAndType and 0x0F
        val type = MessageType.fromCode(typeCode)
            ?: throw MalformedMessageException("Tipo $typeCode")
        val priority = buffer.get().toInt() and 0xFF
        if (priority > Priority.NORMAL) throw MalformedMessageException("Prioridad $priority")
        return Header(
            type = type,
            priority = priority,
            originId = buffer.getInt(),
            seq = buffer.getShort().toInt() and 0xFFFF,
            destId = buffer.getInt(),
            timestampSeconds = buffer.getInt().toLong() and 0xFFFFFFFFL,
            ttlHours = buffer.get().toInt() and 0xFF,
            hopsLeft = buffer.get().toInt() and 0xFF,
            payloadLength = buffer.get().toInt() and 0xFF,
        )
    }

    fun decode(bytes: ByteArray): Frame {
        val header = decodeHeader(bytes)
        if (bytes.size != HEADER_SIZE + header.payloadLength) {
            throw MalformedMessageException("Se esperaban ${header.payloadLength} bytes de contenido")
        }
        return Frame(header, bytes.copyOfRange(HEADER_SIZE, bytes.size))
    }
}
