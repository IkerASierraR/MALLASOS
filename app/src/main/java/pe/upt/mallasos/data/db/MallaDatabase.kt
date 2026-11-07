package pe.upt.mallasos.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        MessageEntity::class,
        SeenIdEntity::class,
        KnownNodeEntity::class,
        EventEntity::class,
    ],
    version = 1,
)
abstract class MallaDatabase : RoomDatabase() {
    abstract fun messages(): MessageDao
    abstract fun knownNodes(): KnownNodeDao
    abstract fun events(): EventDao

    companion object {
        fun create(context: Context): MallaDatabase =
            Room.databaseBuilder(context, MallaDatabase::class.java, "malla.db").build()
    }
}
