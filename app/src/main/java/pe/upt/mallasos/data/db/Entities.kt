package pe.upt.mallasos.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class MessageState { PENDING, CARRIED, DELIVERED, EXPIRED }

@Entity(tableName = "messages", indices = [Index("state")])
class MessageEntity(
    @PrimaryKey val uid: String,
    val type: Int,
    val priority: Int,
    val originId: Int,
    val destId: Int,
    val seq: Int,
    val createdAt: Long,
    val ttlHours: Int,
    val hopsLeft: Int,
    val payload: ByteArray,
    val state: MessageState,
    val isOwn: Boolean,
    val enqueuedAt: Long,
    val attempts: Int = 0,
    val lastAttemptAt: Long = 0,
    val receivedFrom: Int? = null,
    val attended: Boolean = false,
)

@Entity(tableName = "seen_ids")
data class SeenIdEntity(
    @PrimaryKey val uid: String,
    val firstSeenAt: Long,
    val delivered: Boolean = false,
)

@Entity(tableName = "known_nodes")
data class KnownNodeEntity(
    @PrimaryKey val nodeId: Int,
    val alias: String,
    /** La columna conserva su nombre anterior para no migrar la base de datos. */
    @ColumnInfo(name = "isPuesto") val isRescuer: Boolean,
    val lastSeenAt: Long,
    val lastRssiOrDistanceNote: String? = null,
    val clockOffsetMs: Long = 0,
)

@Entity(tableName = "events", indices = [Index("uid")])
data class EventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tsMs: Long,
    val runLabel: String,
    val nodeId: Int,
    val event: String,
    val uid: String? = null,
    val peerId: Int? = null,
    val hopsLeft: Int? = null,
    val dtnMode: String,
    val queueMode: String,
    val goIntent: String,
    val extra: String? = null,
)
