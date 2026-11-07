package pe.upt.mallasos.dtn

import kotlinx.coroutines.delay
import pe.upt.mallasos.codec.MessageType
import pe.upt.mallasos.data.db.MessageEntity
import pe.upt.mallasos.log.EventSink
import pe.upt.mallasos.log.Events

const val QUEUE_CAPACITY = 500

private const val MS_PER_HOUR = 3_600_000L

/** Mantenimiento de la cola; lo implementa `MessageDao`. */
interface QueueStore {
    suspend fun queued(): List<MessageEntity>
    suspend fun markExpired(uid: String)
    suspend fun deleteMessage(uid: String)

    /** Borra las lápidas anteriores a [beforeMs]. */
    suspend fun purgeSeenBefore(beforeMs: Long)
}

/** Vence mensajes (R7), purga lápidas y aplica el límite de la cola (`plan.md` §5). */
class TtlSweeper(
    private val store: QueueStore,
    private val events: EventSink,
    private val seenRetentionMs: suspend () -> Long,
    private val capacity: Int = QUEUE_CAPACITY,
    private val intervalMs: Long = 60_000,
    private val clockMs: () -> Long = System::currentTimeMillis,
) {
    /** Se ejecuta hasta que se cancela. */
    suspend fun run() {
        while (true) {
            sweep()
            delay(intervalMs)
        }
    }

    suspend fun sweep() {
        val now = clockMs()
        val (expired, active) = store.queued().partition {
            now >= it.createdAt + it.ttlHours * MS_PER_HOUR
        }
        expired.forEach {
            store.markExpired(it.uid)
            events.log(Events.EXPIRED, it.uid, hopsLeft = it.hopsLeft)
        }
        evictions(active, capacity).forEach {
            store.deleteMessage(it.uid)
            events.log(Events.QUEUE_EVICTED, it.uid, hopsLeft = it.hopsLeft)
        }
        store.purgeSeenBefore(now - seenRetentionMs())
    }

    companion object {
        /**
         * Lo que sale si la cola supera [capacity]: primero los normales, luego los mensajes ajenos
         * y al final los ACK propios, cada grupo del más antiguo al más nuevo. Un SOS propio nunca.
         */
        fun evictions(active: List<MessageEntity>, capacity: Int): List<MessageEntity> {
            val overflow = active.size - capacity
            if (overflow <= 0) return emptyList()
            return active
                .filterNot { it.isOwn && it.type == MessageType.SOS.code }
                .sortedWith(compareBy(::evictionRank).thenBy { it.enqueuedAt })
                .take(overflow)
        }

        private fun evictionRank(message: MessageEntity): Int = when {
            message.type == MessageType.NORMAL.code -> 0
            !message.isOwn -> 1
            else -> 2
        }
    }
}
