package pe.upt.mallasos.p2p

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import pe.upt.mallasos.codec.MalformedMessageException
import pe.upt.mallasos.data.datastore.NodeMode
import pe.upt.mallasos.data.datastore.SettingsStore
import pe.upt.mallasos.data.db.KnownNodeDao
import pe.upt.mallasos.data.db.KnownNodeEntity
import pe.upt.mallasos.dtn.ExchangeConfig
import pe.upt.mallasos.dtn.MessageExchange
import pe.upt.mallasos.log.EventLogger
import pe.upt.mallasos.log.Events
import java.io.IOException

/** Sesión de un contacto: HELLO, registro del vecino e intercambio de mensajes. */
class PeerContact(
    private val runner: SessionRunner,
    private val settings: SettingsStore,
    private val knownNodes: KnownNodeDao,
    private val exchange: MessageExchange,
    private val logger: EventLogger,
) : ContactSession {

    override suspend fun run(group: P2pGroup) {
        val identity = settings.requireIdentity()
        val local = LocalNode(identity.nodeId, identity.alias, identity.mode)
        try {
            if (group.isGroupOwner) {
                runner.serve(local) { onSession(local, it) }
            } else {
                runner.connect(group.ownerAddress, local) { onSession(local, it) }
            }
        } catch (error: IOException) {
            logFailure(error.javaClass.simpleName)
        } catch (_: MalformedMessageException) {
            logFailure("malformed")
        }
    }

    private suspend fun onSession(local: LocalNode, session: PeerSession) {
        val remote = session.remote
        knownNodes.upsert(
            KnownNodeEntity(
                nodeId = remote.nodeId,
                alias = remote.alias,
                isPuesto = remote.mode == NodeMode.PUESTO,
                lastSeenAt = System.currentTimeMillis(),
            ),
        )
        val config = settings.testConfig.first()
        exchange.run(
            session,
            ExchangeConfig(local, config.dtnMode, config.queueMode, config.ttlHours, config.hops),
        )
    }

    /** Se registra también si el límite de sesión cortó el enlace y canceló esta corrutina. */
    private suspend fun logFailure(cause: String) = withContext(NonCancellable) {
        logger.log(Events.LINK_LOST, extra = "session:$cause")
    }
}
