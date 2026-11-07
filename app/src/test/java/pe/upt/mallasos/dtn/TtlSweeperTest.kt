package pe.upt.mallasos.dtn

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pe.upt.mallasos.codec.MessageType
import pe.upt.mallasos.data.db.MessageEntity
import pe.upt.mallasos.data.db.MessageState
import pe.upt.mallasos.log.EventSink
import pe.upt.mallasos.log.Events

class TtlSweeperTest {

    private val now = 100 * 3_600_000L
    private val hour = 3_600_000L

    private fun message(
        uid: String,
        type: MessageType = MessageType.SOS,
        isOwn: Boolean = false,
        enqueuedAt: Long = 0,
        createdAt: Long = now,
        ttlHours: Int = 24,
    ) = MessageEntity(
        uid = uid,
        type = type.code,
        priority = 1,
        originId = 1,
        destId = -1,
        seq = 0,
        createdAt = createdAt,
        ttlHours = ttlHours,
        hopsLeft = 5,
        payload = ByteArray(0),
        state = MessageState.CARRIED,
        isOwn = isOwn,
        enqueuedAt = enqueuedAt,
    )

    private class FakeStore(val messages: List<MessageEntity>) : QueueStore, EventSink {
        val expired = mutableListOf<String>()
        val deleted = mutableListOf<String>()
        val logged = mutableListOf<Pair<String, String?>>()
        var purgedBefore: Long? = null

        override suspend fun queued() = messages
        override suspend fun markExpired(uid: String) {
            expired += uid
        }

        override suspend fun deleteMessage(uid: String) {
            deleted += uid
        }

        override suspend fun purgeSeenBefore(beforeMs: Long) {
            purgedBefore = beforeMs
        }

        override suspend fun log(
            event: String,
            uid: String?,
            peerId: Int?,
            hopsLeft: Int?,
            extra: String?,
        ) {
            logged += event to uid
        }
    }

    private fun sweep(store: FakeStore, capacity: Int = 500) = runBlocking {
        TtlSweeper(store, store, { 25 * hour }, capacity) { now }.sweep()
    }

    @Test
    fun `vence lo que supera su tiempo de vida`() {
        val store = FakeStore(
            listOf(
                message("viejo", createdAt = now - 2 * hour, ttlHours = 2),
                message("vigente", createdAt = now - 2 * hour, ttlHours = 3),
            ),
        )

        sweep(store)

        assertEquals(listOf("viejo"), store.expired)
        assertEquals(listOf(Events.EXPIRED to "viejo"), store.logged)
    }

    @Test
    fun `purga las lapidas pasada la retencion`() {
        val store = FakeStore(emptyList())

        sweep(store)

        assertEquals(now - 25 * hour, store.purgedBefore)
    }

    @Test
    fun `sin exceso de cola no descarta nada`() {
        val messages = List(3) { message("m$it") }
        assertTrue(TtlSweeper.evictions(messages, capacity = 3).isEmpty())
    }

    @Test
    fun `descarta primero los normales mas antiguos`() {
        val messages = listOf(
            message("sos-ajeno", enqueuedAt = 1),
            message("normal-nuevo", MessageType.NORMAL, enqueuedAt = 3),
            message("normal-viejo", MessageType.NORMAL, enqueuedAt = 2),
        )
        assertEquals(
            listOf("normal-viejo"),
            TtlSweeper.evictions(messages, capacity = 2).map { it.uid },
        )
    }

    @Test
    fun `luego los SOS ajenos mas antiguos`() {
        val messages = listOf(
            message("normal", MessageType.NORMAL, enqueuedAt = 9),
            message("ajeno-nuevo", enqueuedAt = 2),
            message("ajeno-viejo", enqueuedAt = 1),
        )
        assertEquals(
            listOf("normal", "ajeno-viejo"),
            TtlSweeper.evictions(messages, capacity = 1).map { it.uid },
        )
    }

    @Test
    fun `nunca descarta un SOS propio aunque la cola siga llena`() {
        val messages = listOf(
            message("propio-1", isOwn = true),
            message("propio-2", isOwn = true),
            message("ajeno"),
        )
        assertEquals(
            listOf("ajeno"),
            TtlSweeper.evictions(messages, capacity = 1).map { it.uid },
        )
    }

    @Test
    fun `aplica el limite y registra cada descarte`() {
        val store = FakeStore(
            listOf(message("normal", MessageType.NORMAL), message("sos", isOwn = true)),
        )

        sweep(store, capacity = 1)

        assertEquals(listOf("normal"), store.deleted)
        assertEquals(listOf(Events.QUEUE_EVICTED to "normal"), store.logged)
    }
}
