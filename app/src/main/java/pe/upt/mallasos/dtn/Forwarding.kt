package pe.upt.mallasos.dtn

import pe.upt.mallasos.codec.ANY_POST
import pe.upt.mallasos.codec.Header
import pe.upt.mallasos.codec.MessageType
import pe.upt.mallasos.data.datastore.NodeMode
import pe.upt.mallasos.data.datastore.QueueMode
import pe.upt.mallasos.data.db.MessageEntity
import pe.upt.mallasos.p2p.RemoteNode

private const val MS_PER_HOUR = 3_600_000L

/** Qué mensajes de la cola se pasan a un vecino y con qué cabecera (R6, R7 y R8). */
object Forwarding {

    /** El vecino es el destinatario final: su ID, o cualquier puesto si está en modo Puesto (R2). */
    fun isFinalDestination(message: MessageEntity, peer: RemoteNode): Boolean =
        message.destId == peer.nodeId || (message.destId == ANY_POST && peer.mode == NodeMode.PUESTO)

    fun canForward(message: MessageEntity, peer: RemoteNode, nowMs: Long): Boolean {
        val expired = nowMs >= message.createdAt + message.ttlHours * MS_PER_HOUR
        val cameFromPeer = message.originId == peer.nodeId || message.receivedFrom == peer.nodeId
        val hopsAllowed = message.hopsLeft > 0 || isFinalDestination(message, peer)
        return !expired && !cameFromPeer && hopsAllowed
    }

    /** Cola en orden de salida, sin lo que este vecino no debe recibir. */
    fun outgoing(
        queued: List<MessageEntity>,
        peer: RemoteNode,
        mode: QueueMode,
        nowMs: Long,
    ): List<MessageEntity> =
        QueuePolicy.order(queued, mode).filter { canForward(it, peer, nowMs) }

    /** Cabecera para el vecino: cada reenvío descuenta un salto, sin bajar de 0 (R8). */
    fun header(message: MessageEntity): Header = Header(
        type = requireNotNull(MessageType.fromCode(message.type)) { "Tipo ${message.type}" },
        priority = message.priority,
        originId = message.originId,
        seq = message.seq,
        destId = message.destId,
        timestampSeconds = message.createdAt / 1000,
        ttlHours = message.ttlHours,
        hopsLeft = (message.hopsLeft - 1).coerceAtLeast(0),
        payloadLength = message.payload.size,
    )
}
