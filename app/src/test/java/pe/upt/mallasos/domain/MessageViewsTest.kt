package pe.upt.mallasos.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pe.upt.mallasos.codec.MessageType
import pe.upt.mallasos.data.datastore.DtnMode
import pe.upt.mallasos.data.datastore.TestConfig
import pe.upt.mallasos.data.db.EventEntity
import pe.upt.mallasos.data.db.MessageEntity
import pe.upt.mallasos.data.db.MessageState
import pe.upt.mallasos.log.Events

class MessageViewsTest {

    private fun message(
        uid: String,
        state: MessageState = MessageState.DELIVERED,
        isOwn: Boolean = false,
        type: MessageType = MessageType.SOS,
        attended: Boolean = false,
        enqueuedAt: Long = 0,
    ) = MessageEntity(
        uid = uid,
        type = type.code,
        priority = 1,
        originId = 1,
        destId = -1,
        seq = 0,
        createdAt = 0,
        ttlHours = 24,
        hopsLeft = 3,
        payload = ByteArray(0),
        state = state,
        isOwn = isOwn,
        enqueuedAt = enqueuedAt,
        attended = attended,
    )

    private fun event(name: String, ts: Long = 0, peer: Int? = null) = EventEntity(
        tsMs = ts,
        runLabel = "",
        nodeId = 1,
        event = name,
        peerId = peer,
        dtnMode = "PROPUESTO",
        queueMode = "PRIORIDAD",
        goIntent = "AUTO",
    )

    @Test
    fun `el panel solo muestra SOS entregados a este puesto`() {
        val items = postPanelItems(
            listOf(
                message("recibido"),
                message("propio", isOwn = true),
                message("llevado", MessageState.CARRIED),
                message("normal", type = MessageType.NORMAL),
            ),
        )
        assertEquals(listOf("recibido"), items.map { it.uid })
    }

    @Test
    fun `el panel pone primero lo no atendido y luego lo mas antiguo`() {
        val items = postPanelItems(
            listOf(
                message("atendido", attended = true, enqueuedAt = 1),
                message("nuevo", enqueuedAt = 3),
                message("viejo", enqueuedAt = 2),
            ),
        )
        assertEquals(listOf("viejo", "nuevo", "atendido"), items.map { it.uid })
    }

    @Test
    fun `la linea de tiempo nombra a los vecinos y omite lo interno`() {
        val entries = timelineEntries(
            listOf(
                event(Events.CREATED, 1),
                event(Events.ENQUEUED, 2),
                event(Events.ACK_HOP_RX, 3, peer = 7),
                event(Events.ACK_FINAL_RX, 4, peer = 7),
            ),
        ) { "Beto" }
        assertEquals(
            listOf(
                TimelineEntry(1, "Creado"),
                TimelineEntry(3, "Entregado a Beto (ACK salto)"),
                TimelineEntry(4, "Confirmado por el destino (ACK final vía Beto)"),
            ),
            entries,
        )
    }

    @Test
    fun `calcula los tiempos del ultimo grupo con el ultimo T0`() {
        val timings = latestLinkTimings(
            listOf(
                event(Events.GROUP_FORMED, 1_900),
                event(Events.CONNECT_REQUEST, 1_500),
                event(Events.PEER_FOUND, 1_200),
                event(Events.DISCOVERY_START, 1_000),
                event(Events.DISCOVERY_START, 100),
            ),
        )
        assertEquals(LinkTimings(200, 300, 400, 900), timings)
    }

    @Test
    fun `no inventa etapas si el grupo lo formo el vecino`() {
        val timings = latestLinkTimings(
            listOf(
                event(Events.GROUP_FORMED, 2_000),
                event(Events.DISCOVERY_START, 1_000),
            ),
        )
        assertEquals(LinkTimings(null, null, null, null), timings)
    }

    @Test
    fun `no usa eventos de una sesion anterior`() {
        val timings = latestLinkTimings(
            listOf(
                event(Events.GROUP_FORMED, 5_000),
                event(Events.SESSION_END, 4_000),
                event(Events.CONNECT_REQUEST, 3_000),
            ),
        )
        assertEquals(LinkTimings(null, null, null, null), timings)
    }

    @Test
    fun `sin grupos no hay tiempos`() {
        assertNull(latestLinkTimings(listOf(event(Events.DISCOVERY_START))))
    }

    @Test
    fun `la etiqueta de corrida no activa el modo pruebas`() {
        assertFalse(isTestModeActive(TestConfig(runLabel = "E2-01")))
        assertTrue(isTestModeActive(TestConfig(dtnMode = DtnMode.BASICO)))
    }

    @Test
    fun `avisa de los toques a partir del cuarto`() {
        assertNull(secretTapsHint(3))
        assertEquals("Faltan 3 toques", secretTapsHint(4))
        assertEquals("Falta 1 toque", secretTapsHint(6))
        assertNull(secretTapsHint(7))
    }
}
