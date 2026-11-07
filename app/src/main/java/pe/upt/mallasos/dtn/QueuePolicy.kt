package pe.upt.mallasos.dtn

import pe.upt.mallasos.codec.Priority
import pe.upt.mallasos.data.datastore.QueueMode
import pe.upt.mallasos.data.db.MessageEntity
import pe.upt.mallasos.data.db.MessageState

/** Orden de salida de la cola (R6). */
object QueuePolicy {

    fun order(messages: List<MessageEntity>, mode: QueueMode): List<MessageEntity> {
        val rank: (MessageEntity) -> Int = when (mode) {
            QueueMode.PRIORIDAD -> { message -> message.priority }
            // Por orden de llegada, solo los mensajes de control conservan su prioridad.
            QueueMode.FIFO -> { message -> if (message.priority == Priority.CONTROL) 0 else 1 }
        }
        return messages
            .filter { it.state == MessageState.PENDING || it.state == MessageState.CARRIED }
            .sortedWith(compareBy(rank).thenBy { it.enqueuedAt })
    }
}
