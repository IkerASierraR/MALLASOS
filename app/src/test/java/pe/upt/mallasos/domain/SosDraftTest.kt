package pe.upt.mallasos.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pe.upt.mallasos.codec.EmergencyType

class SosDraftTest {

    private val complete = SosDraft(emergency = EmergencyType.HERIDO, locationText = "Pabellón B")

    @Test
    fun `un borrador completo se puede enviar`() {
        assertNull(complete.missing)
    }

    @Test
    fun `explica que falta`() {
        assertEquals("Elige el tipo y escribe dónde estás", SosDraft().missing)
        assertEquals("Elige el tipo de emergencia", complete.copy(emergency = null).missing)
        assertEquals("Escribe dónde estás", complete.copy(locationText = "   ").missing)
    }

    @Test
    fun `la ubicacion admite 40 caracteres`() {
        assertFalse(complete.copy(locationText = "a".repeat(40)).locationTooLong)
        assertTrue(complete.copy(locationText = "a".repeat(41)).locationTooLong)
    }

    @Test
    fun `los emojis pueden superar el limite en bytes sin superar el de caracteres`() {
        val draft = complete.copy(message = "😀".repeat(41))
        assertTrue(draft.messageTooLong)
        assertEquals("Texto demasiado largo (tildes o emojis ocupan más)", draft.missing)
    }

    @Test
    fun `el alias tiene entre 2 y 20 caracteres`() {
        assertFalse(isValidAlias("A"))
        assertTrue(isValidAlias("Iker"))
        assertTrue(isValidAlias("a".repeat(20)))
        assertFalse(isValidAlias("a".repeat(21)))
    }
}
