package pe.upt.mallasos.codec

import java.nio.BufferUnderflowException
import java.nio.ByteBuffer

const val MAX_LOCATION_BYTES = 80
const val MAX_MESSAGE_BYTES = 160
const val MAX_ALIAS_BYTES = 80

enum class EmergencyType(val code: Int, val label: String) {
    HERIDO(1, "Herido"),
    ATRAPADO(2, "Atrapado"),
    MEDICO(3, "Médico"),
    INCENDIO(4, "Incendio"),
    OTRO(5, "Otro");

    companion object {
        fun fromCode(code: Int) = entries.firstOrNull { it.code == code }
    }
}

data class GpsPosition(val latitude: Double, val longitude: Double)

data class SosPayload(
    val emergency: EmergencyType,
    val people: Int,
    val batteryPercent: Int,
    val position: GpsPosition?,
    val locationText: String,
    val message: String,
)

data class AckFinalPayload(val confirmed: Uid, val receiverId: Int)

data class HelloPayload(val nodeId: Int, val mode: Int, val clockMs: Long, val alias: String)

/** Contenidos binarios de cada tipo de mensaje (`plan.md` §4). */
object PayloadCodec {

    private const val FLAG_HAS_GPS = 1
    private const val COORDINATE_SCALE = 1e7

    fun encodeSos(sos: SosPayload): ByteArray {
        val location = sos.locationText.toByteArray()
        val message = sos.message.toByteArray()
        require(location.size <= MAX_LOCATION_BYTES) { "Ubicación de ${location.size} bytes" }
        require(message.size <= MAX_MESSAGE_BYTES) { "Mensaje de ${message.size} bytes" }
        return ByteBuffer.allocate(12 + 1 + location.size + 1 + message.size)
            .put(sos.emergency.code.toByte())
            .put(sos.people.toByte())
            .put(sos.batteryPercent.toByte())
            .put((if (sos.position != null) FLAG_HAS_GPS else 0).toByte())
            .putInt(sos.position?.let { Math.round(it.latitude * COORDINATE_SCALE).toInt() } ?: 0)
            .putInt(sos.position?.let { Math.round(it.longitude * COORDINATE_SCALE).toInt() } ?: 0)
            .put(location.size.toByte())
            .put(location)
            .put(message.size.toByte())
            .put(message)
            .array()
    }

    fun decodeSos(bytes: ByteArray): SosPayload = parsing {
        val buffer = ByteBuffer.wrap(bytes)
        val emergencyCode = buffer.get().toInt() and 0xFF
        val emergency = EmergencyType.fromCode(emergencyCode)
            ?: throw MalformedMessageException("Emergencia $emergencyCode")
        val people = buffer.get().toInt() and 0xFF
        if (people !in 1..99) throw MalformedMessageException("Personas $people")
        val battery = buffer.get().toInt() and 0xFF
        if (battery > 100) throw MalformedMessageException("Batería $battery")
        val hasGps = (buffer.get().toInt() and FLAG_HAS_GPS) != 0
        val latitude = buffer.getInt()
        val longitude = buffer.getInt()
        SosPayload(
            emergency = emergency,
            people = people,
            batteryPercent = battery,
            position = if (hasGps) {
                GpsPosition(latitude / COORDINATE_SCALE, longitude / COORDINATE_SCALE)
            } else {
                null
            },
            locationText = buffer.readText(MAX_LOCATION_BYTES),
            message = buffer.readText(MAX_MESSAGE_BYTES),
        )
    }

    fun encodeAckHop(uid: Uid): ByteArray =
        ByteBuffer.allocate(6).putUid(uid).array()

    fun decodeAckHop(bytes: ByteArray): Uid = parsing { ByteBuffer.wrap(bytes).readUid() }

    fun encodeAckFinal(ack: AckFinalPayload): ByteArray =
        ByteBuffer.allocate(10).putUid(ack.confirmed).putInt(ack.receiverId).array()

    fun decodeAckFinal(bytes: ByteArray): AckFinalPayload = parsing {
        val buffer = ByteBuffer.wrap(bytes)
        AckFinalPayload(buffer.readUid(), buffer.getInt())
    }

    fun encodeHello(hello: HelloPayload): ByteArray {
        val alias = hello.alias.toByteArray()
        require(alias.size <= MAX_ALIAS_BYTES) { "Alias de ${alias.size} bytes" }
        return ByteBuffer.allocate(14 + alias.size)
            .putInt(hello.nodeId)
            .put(hello.mode.toByte())
            .putLong(hello.clockMs)
            .put(alias.size.toByte())
            .put(alias)
            .array()
    }

    fun decodeHello(bytes: ByteArray): HelloPayload = parsing {
        val buffer = ByteBuffer.wrap(bytes)
        HelloPayload(
            nodeId = buffer.getInt(),
            mode = buffer.get().toInt() and 0xFF,
            clockMs = buffer.getLong(),
            alias = buffer.readText(MAX_ALIAS_BYTES),
        )
    }

    private fun ByteBuffer.putUid(uid: Uid): ByteBuffer =
        putInt(uid.originId).putShort(uid.seq.toShort())

    private fun ByteBuffer.readUid() = Uid(getInt(), getShort().toInt() and 0xFFFF)

    private fun ByteBuffer.readText(maxBytes: Int): String {
        val length = get().toInt() and 0xFF
        if (length > maxBytes) throw MalformedMessageException("Texto de $length bytes")
        return ByteArray(length).also { get(it) }.decodeToString()
    }

    private inline fun <T> parsing(block: () -> T): T = try {
        block()
    } catch (_: BufferUnderflowException) {
        throw MalformedMessageException("Contenido truncado")
    }
}
