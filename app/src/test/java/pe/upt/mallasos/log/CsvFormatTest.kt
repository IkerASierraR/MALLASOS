package pe.upt.mallasos.log

import org.junit.Assert.assertEquals
import org.junit.Test
import pe.upt.mallasos.data.db.EventEntity

class CsvFormatTest {

    private fun event(extra: String? = null, runLabel: String = "E2-01") = EventEntity(
        tsMs = 1_794_000_000_123L,
        runLabel = runLabel,
        nodeId = 0x7A3F91C2,
        event = Events.SENT,
        uid = "7A3F91C20012",
        peerId = -1,
        hopsLeft = 4,
        dtnMode = "PROPUESTO",
        queueMode = "PRIORIDAD",
        goIntent = "AUTO",
        extra = extra,
    )

    @Test
    fun `el encabezado coincide con el plan`() {
        assertEquals(
            "ts_ms,run_label,node_id,event,uid,peer_id,hops_left,dtn_mode,queue_mode,go_intent,extra",
            CsvFormat.HEADER,
        )
    }

    @Test
    fun `una fila sigue el orden del encabezado`() {
        assertEquals(
            "1794000000123,E2-01,7A3F91C2,SENT,7A3F91C20012,FFFFFFFF,4,PROPUESTO,PRIORIDAD,AUTO,",
            CsvFormat.row(event()),
        )
    }

    @Test
    fun `los campos vacios quedan vacios`() {
        val row = CsvFormat.row(
            EventEntity(
                tsMs = 1,
                runLabel = "",
                nodeId = 1,
                event = Events.DISCOVERY_START,
                dtnMode = "BASICO",
                queueMode = "FIFO",
                goIntent = "0",
            ),
        )
        assertEquals("1,,00000001,DISCOVERY_START,,,,BASICO,FIFO,0,", row)
    }

    @Test
    fun `escapa comas comillas y saltos de linea`() {
        assertEquals("\"a,b\"", CsvFormat.row(event(extra = "a,b")).split(",", limit = 11).last())
        assertEquals(
            "\"di \"\"x\"\"\"",
            CsvFormat.row(event(extra = "di \"x\"")).split(",", limit = 11).last(),
        )
        assertEquals(
            "\"a\nb\"",
            CsvFormat.row(event(extra = "a\nb")).split(",", limit = 11).last(),
        )
    }

    @Test
    fun `el documento tiene encabezado y una linea por evento`() {
        val lines = CsvFormat.document(listOf(event(), event())).trimEnd().split("\n")
        assertEquals(3, lines.size)
        assertEquals(CsvFormat.HEADER, lines.first())
    }
}
