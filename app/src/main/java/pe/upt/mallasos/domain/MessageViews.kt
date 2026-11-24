package pe.upt.mallasos.domain

import pe.upt.mallasos.codec.MessageType
import pe.upt.mallasos.data.datastore.TestConfig
import pe.upt.mallasos.data.db.EventEntity
import pe.upt.mallasos.data.db.MessageEntity
import pe.upt.mallasos.data.db.MessageState
import pe.upt.mallasos.log.Events

/** SOS recibidos por este Rescatista: primero sin atender y, dentro de cada grupo, el más antiguo. */
fun postPanelItems(messages: List<MessageEntity>): List<MessageEntity> = messages
    .filter {
        it.type == MessageType.SOS.code && it.state == MessageState.DELIVERED && !it.isOwn
    }
    .sortedWith(compareBy<MessageEntity> { it.attended }.thenBy { it.enqueuedAt })

data class TimelineEntry(val tsMs: Long, val label: String)

/** Recorrido de un mensaje para el Detalle del SOS. [aliasOf] nombra a un nodo. */
fun timelineEntries(events: List<EventEntity>, aliasOf: (Int) -> String): List<TimelineEntry> =
    events.mapNotNull { event ->
        val peer = event.peerId?.let(aliasOf) ?: "un vecino"
        val label = when (event.event) {
            Events.CREATED -> "Creado"
            Events.SENT -> "Enviado a $peer"
            Events.RETRY -> "Reintento hacia $peer"
            Events.ACK_HOP_RX -> "Entregado a $peer (ACK salto)"
            Events.RECEIVED -> "Recibido de $peer"
            Events.DUPLICATE_DROPPED -> "Copia repetida de $peer descartada"
            Events.DELIVERED_FINAL -> "Entregado a este rescatista"
            Events.ACK_FINAL_RX -> "Confirmado por el destino (ACK final vía $peer)"
            Events.PURGED -> "Copia local borrada"
            Events.EXPIRED -> "Vencido"
            Events.QUEUE_EVICTED -> "Descartado por cola llena"
            else -> null
        }
        label?.let { TimelineEntry(event.tsMs, it) }
    }

/** Tiempos del último grupo formado (E1). Son `null` si este teléfono no vio esa etapa. */
data class LinkTimings(val t0ToT1: Long?, val t1ToT2: Long?, val t2ToT3: Long?, val total: Long?)

/** [events] va del más reciente al más antiguo. */
fun latestLinkTimings(events: List<EventEntity>): LinkTimings? {
    val formed = events.indexOfFirst { it.event == Events.GROUP_FORMED }
    if (formed < 0) return null
    val t3 = events[formed].tsMs
    val earlier = events.drop(formed + 1)
        .takeWhile { it.event != Events.SESSION_END && it.event != Events.GROUP_FORMED }
    // Cada ciclo repite T0: el que cuenta es el último antes de conectar.
    val t2 = earlier.firstOrNull { it.event == Events.CONNECT_REQUEST }?.tsMs
    val beforeConnect = earlier.dropWhile { it.event != Events.CONNECT_REQUEST }
    val t1 = beforeConnect.firstOrNull { it.event == Events.PEER_FOUND }?.tsMs
    val t0 = beforeConnect.firstOrNull { it.event == Events.DISCOVERY_START }?.tsMs
    fun between(from: Long?, to: Long?) = if (from != null && to != null) to - from else null
    return LinkTimings(between(t0, t1), between(t1, t2), between(t2, t3), between(t0, t3))
}

/** La franja "Modo pruebas activo" no depende de la etiqueta de corrida, que es solo un nombre. */
fun isTestModeActive(config: TestConfig): Boolean =
    // El intent de Group Owner es un ajuste del rol, no de las pruebas.
    config.copy(
        runLabel = "",
        goIntentAyudante = TestConfig().goIntentAyudante,
        goIntentHerido = TestConfig().goIntentHerido,
    ) != TestConfig()

const val SECRET_TAPS = 7

/** Texto de los toques sobre la versión: avisa a partir del cuarto (`design.md` §4.2). */
fun secretTapsHint(taps: Int): String? {
    val left = SECRET_TAPS - taps
    return when {
        taps < 4 || left <= 0 -> null
        left == 1 -> "Falta 1 toque"
        else -> "Faltan $left toques"
    }
}
