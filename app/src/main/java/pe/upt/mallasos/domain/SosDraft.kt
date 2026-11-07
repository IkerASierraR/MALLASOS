package pe.upt.mallasos.domain

import pe.upt.mallasos.codec.ANY_POST
import pe.upt.mallasos.codec.EmergencyType
import pe.upt.mallasos.codec.GpsPosition
import pe.upt.mallasos.codec.MAX_LOCATION_BYTES
import pe.upt.mallasos.codec.MAX_MESSAGE_BYTES

const val MAX_LOCATION_CHARS = 40
const val MAX_MESSAGE_CHARS = 100
const val MIN_ALIAS_CHARS = 2
const val MAX_ALIAS_CHARS = 20

data class SosDraft(
    val emergency: EmergencyType? = null,
    val people: Int = 1,
    val locationText: String = "",
    val message: String = "",
    val destId: Int = ANY_POST,
    val position: GpsPosition? = null,
) {
    val locationTooLong: Boolean get() = exceeds(locationText, MAX_LOCATION_CHARS, MAX_LOCATION_BYTES)
    val messageTooLong: Boolean get() = exceeds(message, MAX_MESSAGE_CHARS, MAX_MESSAGE_BYTES)

    /** Motivo por el que no se puede enviar, o `null` si está completo. */
    val missing: String?
        get() = when {
            emergency == null && locationText.isBlank() -> "Elige el tipo y escribe dónde estás"
            emergency == null -> "Elige el tipo de emergencia"
            locationText.isBlank() -> "Escribe dónde estás"
            locationTooLong || messageTooLong -> "Texto demasiado largo (tildes o emojis ocupan más)"
            else -> null
        }

    private fun exceeds(text: String, maxChars: Int, maxBytes: Int) =
        text.codePointCount(0, text.length) > maxChars || text.toByteArray().size > maxBytes
}

fun isValidAlias(alias: String): Boolean =
    alias.trim().codePointCount(0, alias.trim().length) in MIN_ALIAS_CHARS..MAX_ALIAS_CHARS
