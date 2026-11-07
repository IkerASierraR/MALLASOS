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

    @Query("SELECT * FROM messages WHERE type IN (1, 2) ORDER BY enqueuedAt DESC")
    abstract fun observeVisible(): Flow<List<MessageEntity>>

    @Query("SELECT COUNT(*) FROM messages WHERE state IN ('PENDING', 'CARRIED')")
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
