package pe.upt.mallasos.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import pe.upt.mallasos.dtn.MessageStore

@Dao
abstract class MessageDao : MessageStore {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertMessage(message: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertSeen(seen: SeenIdEntity): Long

    /** El mensaje y su identificador se registran juntos para que un duplicado nunca se guarde (R5). */
    @Transaction
    open suspend fun insertNew(message: MessageEntity) {
        insertMessage(message)
        insertSeen(SeenIdEntity(message.uid, message.enqueuedAt))
    }

    /** Un identificador ya visto no se guarda de nuevo, aunque la copia se haya purgado (R5). */
    @Transaction
    override suspend fun insertIfNew(message: MessageEntity): Boolean {
        if (insertSeen(SeenIdEntity(message.uid, message.enqueuedAt)) == -1L) return false
        insertMessage(message)
        return true
    }

    @Query("SELECT * FROM messages WHERE state IN ('PENDING', 'CARRIED')")
    abstract override suspend fun queued(): List<MessageEntity>

    @Query("UPDATE messages SET attempts = attempts + 1, lastAttemptAt = :atMs WHERE uid = :uid")
    abstract override suspend fun recordAttempt(uid: String, atMs: Long)

    @Query("UPDATE messages SET state = 'CARRIED' WHERE uid = :uid AND state = 'PENDING'")
    abstract override suspend fun markCarried(uid: String)

    @Query("UPDATE seen_ids SET delivered = 1 WHERE uid = :uid")
    protected abstract suspend fun markSeenDelivered(uid: String)

    @Query(
        "UPDATE messages SET state = 'DELIVERED' " +
            "WHERE uid = :uid AND isOwn = 1 AND state IN ('PENDING', 'CARRIED')",
    )
    protected abstract suspend fun markOwnDelivered(uid: String)

    @Query(
        "DELETE FROM messages " +
            "WHERE uid = :uid AND isOwn = 0 AND state IN ('PENDING', 'CARRIED')",
    )
    protected abstract suspend fun deleteCarried(uid: String): Int

    /** La lápida se crea aunque nunca se haya tenido el SOS, para rechazar copias tardías. */
    @Transaction
    override suspend fun confirmDelivered(uid: String, atMs: Long): Boolean {
        insertSeen(SeenIdEntity(uid, atMs, delivered = true))
        markSeenDelivered(uid)
        markOwnDelivered(uid)
        return deleteCarried(uid) > 0
    }

    @Query("SELECT * FROM messages WHERE type IN (1, 2) ORDER BY enqueuedAt DESC")
    abstract fun observeVisible(): Flow<List<MessageEntity>>

    @Query(
        "SELECT COUNT(*) FROM messages WHERE type IN (1, 2) AND state IN ('PENDING', 'CARRIED')",
    )
    abstract fun observeQueued(): Flow<Int>
}

@Dao
interface KnownNodeDao {
    @Upsert
    suspend fun upsert(node: KnownNodeEntity)

    @Query("SELECT * FROM known_nodes ORDER BY lastSeenAt DESC")
    fun observeAll(): Flow<List<KnownNodeEntity>>
}

@Dao
interface EventDao {
    @Insert
    suspend fun insert(event: EventEntity)

    @Query("SELECT * FROM events WHERE uid = :uid ORDER BY tsMs")
    fun observeForMessage(uid: String): Flow<List<EventEntity>>
}
