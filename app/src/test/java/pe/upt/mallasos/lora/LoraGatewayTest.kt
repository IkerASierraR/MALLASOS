package pe.upt.mallasos.lora

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class LoraGatewayTest {

    private class FakeBoard(initial: LoraLinkState) : LoraLink {
        override val state = MutableStateFlow(initial)
        val sent = mutableListOf<LoraReport>()
        var accepts = true

        override suspend fun connect() {
            state.value = LoraLinkState.CONECTADO
        }

        override suspend fun disconnect() {
            state.value = LoraLinkState.DESCONECTADO
        }

        override suspend fun send(report: LoraReport): Boolean {
            if (accepts) sent += report
            return accepts
        }
    }

    @Test
    fun `sin placa el reporte queda en espera`() = runBlocking {
        val gateway = LoraGateway(PendingBoardLink()) { 1_000 }
        val report = gateway.submit(LoraReportType.ZONA, 3, "  Zona norte  ")
        assertEquals(LoraSendStatus.EN_ESPERA, report.status)
        assertEquals("Zona norte", report.text)
        assertEquals(listOf(report), gateway.outbox.value)
    }

    @Test
    fun `con la placa conectada sale al instante`() = runBlocking {
        val board = FakeBoard(LoraLinkState.CONECTADO)
        val gateway = LoraGateway(board) { 1_000 }
        val report = gateway.submit(LoraReportType.SOS, 2, "Atrapado", sosUid = "0A0A0A0A0001")
        assertEquals(LoraSendStatus.ENVIADO, report.status)
        assertEquals("0A0A0A0A0001", board.sent.single().sosUid)
    }

    @Test
    fun `al conectar la placa envia lo pendiente en orden`() = runBlocking {
        val board = FakeBoard(LoraLinkState.DESCONECTADO)
        val gateway = LoraGateway(board) { 1_000 }
        gateway.submit(LoraReportType.ZONA, 1, "primero")
        gateway.submit(LoraReportType.RECURSOS, 1, "segundo")
        board.connect()
        gateway.flush()
        assertEquals(listOf("primero", "segundo"), board.sent.map { it.text })
        assertEquals(setOf(LoraSendStatus.ENVIADO), gateway.outbox.value.map { it.status }.toSet())
    }

    @Test
    fun `si la placa rechaza queda como fallido y se reintenta`() = runBlocking {
        val board = FakeBoard(LoraLinkState.CONECTADO)
        board.accepts = false
        val gateway = LoraGateway(board) { 1_000 }
        assertEquals(LoraSendStatus.FALLIDO, gateway.submit(LoraReportType.OTRO, 1, "x").status)
        board.accepts = true
        gateway.flush()
        assertEquals(LoraSendStatus.ENVIADO, gateway.outbox.value.single().status)
    }
}
