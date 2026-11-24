package pe.upt.mallasos.log

import kotlinx.coroutines.flow.first
import pe.upt.mallasos.data.datastore.SettingsStore
import pe.upt.mallasos.data.datastore.goIntentFor
import pe.upt.mallasos.data.db.EventDao
import pe.upt.mallasos.data.db.EventEntity

/** Nombres fijos de `plan.md` §6: no cambiarlos sin registrar una decisión. */
object Events {
    const val CREATED = "CREATED"
    const val ENQUEUED = "ENQUEUED"
    const val SENT = "SENT"
    const val RECEIVED = "RECEIVED"
    const val RETRY = "RETRY"
    const val ACK_HOP_RX = "ACK_HOP_RX"
    const val DUPLICATE_DROPPED = "DUPLICATE_DROPPED"
    const val DELIVERED_FINAL = "DELIVERED_FINAL"
    const val ACK_FINAL_RX = "ACK_FINAL_RX"
    const val PURGED = "PURGED"
    const val EXPIRED = "EXPIRED"
    const val QUEUE_EVICTED = "QUEUE_EVICTED"
    const val CLOCK_OFFSET = "CLOCK_OFFSET"
    const val DISCOVERY_START = "DISCOVERY_START"
    const val PEER_FOUND = "PEER_FOUND"
    const val CONNECT_REQUEST = "CONNECT_REQUEST"
    const val GROUP_FORMED = "GROUP_FORMED"
    const val SESSION_END = "SESSION_END"
    const val LINK_LOST = "LINK_LOST"
}

/** Destino de los eventos; separado de [EventLogger] para probar la capa DTN sin Android. */
interface EventSink {
    suspend fun log(
        event: String,
        uid: String? = null,
        peerId: Int? = null,
        hopsLeft: Int? = null,
        extra: String? = null,
    )
}

/** Registra los eventos que luego se exportan a CSV (R10). */
class EventLogger(
    private val dao: EventDao,
    private val settings: SettingsStore,
) : EventSink {
    override suspend fun log(
        event: String,
        uid: String?,
        peerId: Int?,
        hopsLeft: Int?,
        extra: String?,
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
                goIntent = config.goIntentFor(identity.mode).toString(),
                extra = extra,
            ),
        )
    }
}
