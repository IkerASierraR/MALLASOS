package pe.upt.mallasos.log

import kotlinx.coroutines.flow.first
import pe.upt.mallasos.data.datastore.SettingsStore
import pe.upt.mallasos.data.db.EventDao
import pe.upt.mallasos.data.db.EventEntity

/** Nombres fijos de `plan.md` §6: no cambiarlos sin registrar una decisión. */
object Events {
    const val CREATED = "CREATED"
    const val ENQUEUED = "ENQUEUED"
}

/** Registra los eventos que luego se exportan a CSV (R10). */
class EventLogger(
    private val dao: EventDao,
    private val settings: SettingsStore,
) {
    suspend fun log(
        event: String,
        uid: String? = null,
        peerId: Int? = null,
        hopsLeft: Int? = null,
        extra: String? = null,
    ) {
        val identity = settings.requireIdentity()
        val config = settings.testConfig.first()
        dao.insert(
            EventEntity(
                tsMs = System.currentTimeMillis(),
                runLabel = config.runLabel,
                nodeId = identity.nodeId,
                event = event,
                uid = uid,
                peerId = peerId,
                hopsLeft = hopsLeft,
                dtnMode = config.dtnMode.name,
                queueMode = config.queueMode.name,
                goIntent = config.goIntent,
                extra = extra,
            ),
        )
    }
}
