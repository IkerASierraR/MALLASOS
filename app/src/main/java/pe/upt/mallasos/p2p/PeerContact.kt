package pe.upt.mallasos.p2p

import pe.upt.mallasos.codec.MalformedMessageException
import pe.upt.mallasos.data.datastore.NodeMode
import pe.upt.mallasos.data.datastore.SettingsStore
import pe.upt.mallasos.data.db.KnownNodeDao
import pe.upt.mallasos.data.db.KnownNodeEntity
import pe.upt.mallasos.log.EventLogger
import pe.upt.mallasos.log.Events
import java.io.IOException

/** Sesión de un contacto: HELLO y registro del vecino. El intercambio de mensajes llega con T2.8. */
class PeerContact(
    private val runner: SessionRunner,
    private val settings: SettingsStore,
    private val knownNodes: KnownNodeDao,
    private val logger: EventLogger,
) : ContactSession {

    override suspend fun run(group: P2pGroup) {
        val identity = settings.requireIdentity()
        val local = LocalNode(identity.nodeId, identity.alias, identity.mode)
        try {
            val remote = if (group.isGroupOwner) {
                runner.serve(local) { it.remote }
            } else {
                runner.connect(group.ownerAddress, local) { it.remote }
            }
            knownNodes.upsert(
                KnownNodeEntity(
                    nodeId = remote.nodeId,
                    alias = remote.alias,
                    isPuesto = remote.mode == NodeMode.PUESTO,
                    lastSeenAt = System.currentTimeMillis(),
                ),
            )
        } catch (error: IOException) {
            logger.log(Events.LINK_LOST, extra = "session:${error.javaClass.simpleName}")
        } catch (_: MalformedMessageException) {
            logger.log(Events.LINK_LOST, extra = "session:malformed")
        }
    }
}
