package pe.upt.mallasos.codec

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MessageCodecTest {

    private val header = Header(
        type = MessageType.SOS,
        priority = Priority.SOS,
        originId = 0x7A3F91C2,
        seq = 0xFFFE,
        destId = ANY_POST,
        timestampSeconds = 0xF0000000L,
        ttlHours = 24,
        hopsLeft = 5,
        payloadLength = 3,
    )

    @Test
    fun `la cabecera ocupa 19 bytes en big-endian`() {
        val bytes = MessageCodec.encode(header, byteArrayOf(1, 2, 3))
        val expected = intArrayOf(
            0x11, 0x01,
            0x7A, 0x3F, 0x91, 0xC2,
            0xFF, 0xFE,
            0xFF, 0xFF, 0xFF, 0xFF,
            0xF0, 0x00, 0x00, 0x00,
            24, 5, 3,
            1, 2, 3,
        ).map { it.toByte() }.toByteArray()
        assertArrayEquals(expected, bytes)
    }

    @Test
    fun `decodifica los valores sin signo`() {
        val frame = MessageCodec.decode(MessageCodec.encode(header, byteArrayOf(1, 2, 3)))
        assertEquals(header, frame.header)
        assertArrayEquals(byteArrayOf(1, 2, 3), frame.payload)
    }

    @Test
    fun `admite el contenido maximo de 255 bytes`() {
        val payload = ByteArray(MAX_PAYLOAD_SIZE)
        val frame = MessageCodec.decode(
            MessageCodec.encode(header.copy(payloadLength = payload.size), payload),
        )
        assertEquals(255, frame.header.payloadLength)
    }

    @Test
    fun `el identificador unico combina origen y secuencia`() {
        assertEquals("7A3F91C2FFFE", header.uid.hex)
    }

    @Test
    fun `rechaza una version desconocida`() {
        val bytes = MessageCodec.encode(header, byteArrayOf(1, 2, 3))
        bytes[0] = 0x21
        assertThrows(MalformedMessageException::class.java) { MessageCodec.decode(bytes) }
    }

    @Test
    fun `rechaza un tipo desconocido`() {
        val bytes = MessageCodec.encode(header, byteArrayOf(1, 2, 3))
        bytes[0] = 0x1F
        assertThrows(MalformedMessageException::class.java) { MessageCodec.decode(bytes) }
    }

    @Test
    fun `rechaza una prioridad fuera de rango`() {
        val bytes = MessageCodec.encode(header, byteArrayOf(1, 2, 3))
        bytes[1] = 3
        assertThrows(MalformedMessageException::class.java) { MessageCodec.decode(bytes) }
    }

    @Test
    fun `rechaza un mensaje truncado`() {
        val bytes = MessageCodec.encode(header, byteArrayOf(1, 2, 3))
        assertThrows(MalformedMessageException::class.java) {
            MessageCodec.decode(bytes.copyOf(bytes.size - 1))
        }
        assertThrows(MalformedMessageException::class.java) {
            MessageCodec.decodeHeader(bytes.copyOf(10))
        }
    }
}
