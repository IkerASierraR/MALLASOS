package pe.upt.mallasos.dtn

import org.junit.Assert.assertEquals
import org.junit.Test
import pe.upt.mallasos.codec.Priority
import pe.upt.mallasos.data.datastore.QueueMode
import pe.upt.mallasos.data.db.MessageEntity
import pe.upt.mallasos.data.db.MessageState

class QueuePolicyTest {

    private fun message(
        uid: String,
        priority: Int,
        enqueuedAt: Long,
        state: MessageState = MessageState.PENDING,
    ) = MessageEntity(
        uid = uid,
        type = 1,
        priority = priority,
        originId = 1,
        destId = -1,
        seq = 0,
        createdAt = 0,
        ttlHours = 24,
        hopsLeft = 5,
        payload = ByteArray(0),
        state = state,
        isOwn = true,
        enqueuedAt = enqueuedAt,
    )

    private val mixed = listOf(
        message("normal-1", Priority.NORMAL, 1),
        message("sos-2", Priority.SOS, 2),
        message("control-3", Priority.CONTROL, 3),
        message("sos-4", Priority.SOS, 4),
        message("normal-5", Priority.NORMAL, 5),
    )

    @Test
    fun `prioridad estricta control SOS normal`() {
        assertEquals(
            listOf("control-3", "sos-2", "sos-4", "normal-1", "normal-5"),
            QueuePolicy.order(mixed, QueueMode.PRIORIDAD).map { it.uid },
        )
    }

    @Test
    fun `orden de llegada mezcla SOS y normales`() {
        assertEquals(
            listOf("control-3", "normal-1", "sos-2", "sos-4", "normal-5"),
            QueuePolicy.order(mixed, QueueMode.FIFO).map { it.uid },
        )
    }

    @Test
    fun `excluye entregados y vencidos`() {
        val closed = listOf(
            message("entregado", Priority.SOS, 1, MessageState.DELIVERED),
            message("vencido", Priority.SOS, 2, MessageState.EXPIRED),
            message("llevado", Priority.SOS, 3, MessageState.CARRIED),
        )
        assertEquals(
            listOf("llevado"),
            QueuePolicy.order(closed, QueueMode.PRIORIDAD).map { it.uid },
        )
    }
}
