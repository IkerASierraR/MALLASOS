package pe.upt.mallasos.codec

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class PayloadCodecTest {

    private val sos = SosPayload(
        emergency = EmergencyType.ATRAPADO,
        people = 99,
        batteryPercent = 100,
        position = GpsPosition(-18.0065679, -70.2462741),
        locationText = "Pabellón B, piso 2",
        message = "Niño atrapado",
    )

    @Test
    fun `SOS con GPS ida y vuelta`() {
        assertEquals(sos, PayloadCodec.decodeSos(PayloadCodec.encodeSos(sos)))
    }

    @Test
    fun `SOS sin GPS ni mensaje`() {
        val plain = sos.copy(position = null, message = "")
        val decoded = PayloadCodec.decodeSos(PayloadCodec.encodeSos(plain))
        assertNull(decoded.position)
        assertEquals("", decoded.message)
    }

    @Test
    fun `el contenido maximo de un SOS cabe en 254 bytes`() {
        val full = sos.copy(locationText = "a".repeat(80), message = "b".repeat(160))
        assertEquals(254, PayloadCodec.encodeSos(full).size)
    }

    @Test
    fun `los limites se aplican en bytes y no en caracteres`() {
        val tildes = sos.copy(locationText = "á".repeat(41))
        assertThrows(IllegalArgumentException::class.java) { PayloadCodec.encodeSos(tildes) }
    }

    @Test
    fun `rechaza contenido truncado`() {
        val bytes = PayloadCodec.encodeSos(sos)
        assertThrows(MalformedMessageException::class.java) {
            PayloadCodec.decodeSos(bytes.copyOf(bytes.size - 3))
        }
    }

    @Test
    fun `rechaza valores fuera de rango`() {
        val noPeople = PayloadCodec.encodeSos(sos).also { it[1] = 0 }
        assertThrows(MalformedMessageException::class.java) { PayloadCodec.decodeSos(noPeople) }
        val unknownType = PayloadCodec.encodeSos(sos).also { it[0] = 9 }
        assertThrows(MalformedMessageException::class.java) { PayloadCodec.decodeSos(unknownType) }
    }

    @Test
    fun `ACK salto a salto ocupa 6 bytes`() {
        val uid = Uid(0x7A3F91C2, 0xFFFF)
        val bytes = PayloadCodec.encodeAckHop(uid)
        assertEquals(6, bytes.size)
        assertEquals(uid, PayloadCodec.decodeAckHop(bytes))
    }

    @Test
    fun `ACK final ocupa 10 bytes`() {
        val ack = AckFinalPayload(Uid(1, 2), -559038737)
        val bytes = PayloadCodec.encodeAckFinal(ack)
        assertEquals(10, bytes.size)
        assertEquals(ack, PayloadCodec.decodeAckFinal(bytes))
    }

    @Test
    fun `HELLO ida y vuelta con alias acentuado`() {
        val hello = HelloPayload(0x7A3F91C2, 1, 1_794_000_000_000L, "Núñez")
        val bytes = PayloadCodec.encodeHello(hello)
        assertEquals(14 + "Núñez".toByteArray().size, bytes.size)
        assertEquals(hello, PayloadCodec.decodeHello(bytes))
    }
}
