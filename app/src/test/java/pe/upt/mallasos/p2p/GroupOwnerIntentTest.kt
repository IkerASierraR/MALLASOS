package pe.upt.mallasos.p2p

import org.junit.Assert.assertEquals
import org.junit.Test

class GroupOwnerIntentTest {

    @Test
    fun `los valores de la configuracion pasan tal cual`() {
        assertEquals(0, groupOwnerIntentValue("0"))
        assertEquals(7, groupOwnerIntentValue("7"))
        assertEquals(15, groupOwnerIntentValue("15"))
    }

    @Test
    fun `automatico o invalido deja decidir al sistema`() {
        assertEquals(-1, groupOwnerIntentValue("AUTO"))
        assertEquals(-1, groupOwnerIntentValue("16"))
        assertEquals(-1, groupOwnerIntentValue(""))
    }
}
