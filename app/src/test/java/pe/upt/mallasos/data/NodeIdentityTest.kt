package pe.upt.mallasos.data

import org.junit.Assert.assertEquals
import org.junit.Test
import pe.upt.mallasos.data.datastore.formatNodeId
import pe.upt.mallasos.data.datastore.generateNodeId
import pe.upt.mallasos.data.datastore.nextSequence
import kotlin.random.Random

class NodeIdentityTest {

    /** Devuelve primero los valores reservados y luego uno válido. */
    private class ScriptedRandom(vararg values: Int) : Random() {
        private val queue = ArrayDeque(values.toList())
        override fun nextBits(bitCount: Int) = queue.removeFirst()
        override fun nextInt() = queue.removeFirst()
    }

    @Test
    fun `el ID nunca vale FFFFFFFF ni 00000000`() {
        assertEquals(42, generateNodeId(ScriptedRandom(0, -1, 0, 42)))
    }

    @Test
    fun `el ID se muestra en dos bloques`() {
        assertEquals("7A3F·91C2", formatNodeId(0x7A3F91C2))
        assertEquals("FFFF·FFFE", formatNodeId(-2))
    }

    @Test
    fun `la secuencia vuelve a cero tras FFFF`() {
        assertEquals(1, nextSequence(0))
        assertEquals(0, nextSequence(0xFFFF))
    }
}
