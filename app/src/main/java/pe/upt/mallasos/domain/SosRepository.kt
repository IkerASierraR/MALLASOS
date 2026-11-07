package pe.upt.mallasos.domain

import kotlinx.coroutines.flow.first
import pe.upt.mallasos.codec.ANY_POST
import pe.upt.mallasos.codec.MessageType
import pe.upt.mallasos.codec.PayloadCodec
import pe.upt.mallasos.codec.Priority
import pe.upt.mallasos.codec.SosPayload
import pe.upt.mallasos.codec.Uid
import pe.upt.mallasos.data.datastore.SettingsStore
import pe.upt.mallasos.data.db.MessageDao
import pe.upt.mallasos.data.db.MessageEntity
import pe.upt.mallasos.data.db.MessageState
import pe.upt.mallasos.log.EventLogger
import pe.upt.mallasos.log.Events

class SosRepository(
    private val messages: MessageDao,
    private val settings: SettingsStore,
    private val logger: EventLogger,
) {
    /** Guarda el SOS en la cola aunque no haya nadie cerca (`spec.md` §4). */
    suspend fun create(draft: SosDraft, batteryPercent: Int): String {
        val emergency = requireNotNull(draft.emergency) { "SOS sin tipo de emergencia" }
        val identity = settings.requireIdentity()
        val config = settings.testConfig.first()
        val now = System.currentTimeMillis()
        val seq = settings.takeSequence()
        val uid = Uid(identity.nodeId, seq).hex
        val payload = PayloadCodec.encodeSos(
            SosPayload(
                emergency = emergency,
                people = draft.people,
                batteryPercent = batteryPercent.coerceIn(0, 100),
                position = draft.position,
                locationText = draft.locationText.trim(),
                message = draft.message.trim(),
            ),
        )
        messages.insertNew(
            MessageEntity(
                uid = uid,
                type = MessageType.SOS.code,
                priority = Priority.SOS,
                originId = identity.nodeId,
                destId = draft.destId,
                seq = seq,
                createdAt = now,
                ttlHours = config.ttlHours,
                hopsLeft = config.hops,
                payload = payload,
                state = MessageState.PENDING,
                isOwn = true,
                enqueuedAt = now,
            ),
        )
        logger.log(Events.CREATED, uid = uid, hopsLeft = config.hops, extra = "SOS")
        logger.log(Events.ENQUEUED, uid = uid, hopsLeft = config.hops)
        return uid
    }

    /** Tráfico de prueba: los mensajes normales no son un chat (`progress.md`, decisiones). */
    suspend fun createNormal(sizeBytes: Int) {
        val identity = settings.requireIdentity()
        val config = settings.testConfig.first()
        val now = System.currentTimeMillis()
        val seq = settings.takeSequence()
        val uid = Uid(identity.nodeId, seq).hex
        messages.insertNew(
            MessageEntity(
                uid = uid,
                type = MessageType.NORMAL.code,
                priority = Priority.NORMAL,
                originId = identity.nodeId,
                destId = ANY_POST,
                seq = seq,
                createdAt = now,
                ttlHours = config.ttlHours,
                hopsLeft = config.hops,
                payload = ByteArray(sizeBytes),
                state = MessageState.PENDING,
                isOwn = true,
                enqueuedAt = now,
            ),
        )
        logger.log(Events.CREATED, uid = uid, hopsLeft = config.hops, extra = "NORMAL")
        logger.log(Events.ENQUEUED, uid = uid, hopsLeft = config.hops)
    }
}
