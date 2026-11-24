package pe.upt.mallasos.p2p

import java.io.IOException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import pe.upt.mallasos.codec.MalformedMessageException
import pe.upt.mallasos.data.datastore.NodeMode
import pe.upt.mallasos.data.datastore.SettingsStore
import pe.upt.mallasos.data.db.KnownNodeDao
import pe.upt.mallasos.data.db.KnownNodeEntity
import pe.upt.mallasos.dtn.ExchangeConfig
import pe.upt.mallasos.dtn.MessageExchange
import pe.upt.mallasos.log.EventLogger
import pe.upt.mallasos.log.Events

/**
 * Sesión de un contacto: HELLO, registro del vecino e intercambio de mensajes. Como Rescatista,
 * [host] atiende a varios teléfonos a la vez, cada uno con el límite de [sessionTimeoutMs].
 */
class PeerContact(
    private val runner: SessionRunner,
    private val settings: SettingsStore,
    private val knownNodes: KnownNodeDao,
    private val exchange: MessageExchange,
    private val logger: EventLogger,
    private val sessionTimeoutMs: Long = 30_000,
) : ContactSession, HostSession {

    private val _activeSessions = MutableStateFlow(0)

    /** Teléfonos intercambiando ahora con este Rescatista. */
    val activeSessions: StateFlow<Int> = _activeSessions.asStateFlow()

    override suspend fun host() {
        val identity = settings.requireIdentity()
        val local = LocalNode(identity.nodeId, identity.alias, identity.mode)
        try {
            runner.serveMany(
                local,
                onError = { error ->
                    val cause = if (error is MalformedMessageException) {
                        "malformed"
                    } else {
                        error.javaClass.simpleName
                    }
                    logFailure(cause)
                },
            ) { session ->
                _activeSessions.update { it + 1 }
                try {
                    withTimeoutOrNull(sessionTimeoutMs) { onSession(local, session) }
                } finally {
                    _activeSessions.update { it - 1 }
                }
            }
        } catch (error: IOException) {
            logFailure("server:${error.javaClass.simpleName}")
        }
    }

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
        val now = System.currentTimeMillis()
        // Incluye la demora del HELLO; basta para corregir relojes en el análisis (`plan.md` §6).
        val clockOffsetMs = remote.clockMs - now
        logger.log(Events.CLOCK_OFFSET, peerId = remote.nodeId, extra = clockOffsetMs.toString())
        knownNodes.upsert(
            KnownNodeEntity(
                nodeId = remote.nodeId,
                alias = remote.alias,
                isRescuer = remote.mode == NodeMode.RESCATISTA,
                lastSeenAt = now,
                clockOffsetMs = clockOffsetMs,
            ),
        )
        val config = settings.testConfig.first()
        exchange.run(
            session,
            ExchangeConfig(
                local,
                config.dtnMode,
                config.queueMode,
                config.ttlHours,
                config.hops,
                config.cutAfterBytes,
                // Corta una sola vez: la corrida mide qué pasa en el contacto siguiente.
                onCut = { settings.updateTestConfig { it.copy(cutAfterBytes = 0) } },
            ),
        )
    }

    /** Se registra también si el límite de sesión cortó el enlace y canceló esta corrutina. */
    private suspend fun logFailure(cause: String) = withContext(NonCancellable) {
        logger.log(Events.LINK_LOST, extra = "session:$cause")
    }
}
