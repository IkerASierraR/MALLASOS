package pe.upt.mallasos.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pe.upt.mallasos.data.datastore.GoPolicy
import pe.upt.mallasos.data.datastore.NodeMode
import pe.upt.mallasos.data.datastore.TestConfig
import pe.upt.mallasos.data.datastore.goIntentFor
import pe.upt.mallasos.data.datastore.withGoIntent

class RolesTest {

    @Test
    fun `los modos guardados por versiones anteriores se traducen`() {
        assertEquals(NodeMode.AYUDANTE, NodeMode.fromStored("NORMAL"))
        assertEquals(NodeMode.RESCATISTA, NodeMode.fromStored("PUESTO"))
        assertEquals(NodeMode.HERIDO, NodeMode.fromStored("HERIDO"))
        assertEquals(NodeMode.AYUDANTE, NodeMode.fromStored(null))
    }

    @Test
    fun `los codigos del HELLO siguen siendo compatibles`() {
        assertEquals(0, NodeMode.AYUDANTE.code)
        assertEquals(1, NodeMode.RESCATISTA.code)
        assertEquals(2, NodeMode.HERIDO.code)
    }

    @Test
    fun `el rescatista siempre usa 15 y no se puede cambiar`() {
        val config = TestConfig().withGoIntent(NodeMode.RESCATISTA, 3)
        assertEquals(15, config.goIntentFor(NodeMode.RESCATISTA))
        assertFalse(GoPolicy.isEditable(NodeMode.RESCATISTA))
    }

    @Test
    fun `el ayudante elige de 0 a 15`() {
        val ayudante = NodeMode.AYUDANTE
        assertEquals(12, TestConfig().withGoIntent(ayudante, 12).goIntentFor(ayudante))
        assertEquals(15, TestConfig().withGoIntent(ayudante, 40).goIntentFor(ayudante))
        assertTrue(GoPolicy.isEditable(ayudante))
    }

    @Test
    fun `el herido queda en un rango bajo`() {
        val config = TestConfig().withGoIntent(NodeMode.HERIDO, 10)
        assertEquals(3, config.goIntentFor(NodeMode.HERIDO))
        assertEquals(0, TestConfig().goIntentFor(NodeMode.HERIDO))
        // Un valor guardado fuera de rango se recorta al leerlo.
        assertEquals(3, TestConfig(goIntentHerido = 9).goIntentFor(NodeMode.HERIDO))
    }

    @Test
    fun `cada rol guarda su propio valor`() {
        val config = TestConfig()
            .withGoIntent(NodeMode.AYUDANTE, 11)
            .withGoIntent(NodeMode.HERIDO, 2)
        assertEquals(11, config.goIntentFor(NodeMode.AYUDANTE))
        assertEquals(2, config.goIntentFor(NodeMode.HERIDO))
    }
}
