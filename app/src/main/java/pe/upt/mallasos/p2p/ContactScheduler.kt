package pe.upt.mallasos.p2p

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.random.Random

/** Valores de `WifiP2pDevice.status`. */
const val PEER_STATUS_CONNECTED = 0
const val PEER_STATUS_AVAILABLE = 3

/** Enlace Wi-Fi Direct que el planificador necesita; lo implementa `WifiDirectManager`. */
interface P2pLink {
    val state: StateFlow<P2pState>
    suspend fun discover()
    suspend fun connect(peer: P2pPeer)
    suspend fun disconnect()
}

/** Lo que ocurre dentro de un grupo ya formado. Debe capturar sus propios fallos de red. */
fun interface ContactSession {
    suspend fun run(group: P2pGroup)
}

data class ContactTimings(
    val discoveryCycleMs: Long = 15_000,
    val cooldownMs: Long = 60_000,
    val groupTimeoutMs: Long = 20_000,
    val sessionTimeoutMs: Long = 30_000,
    val disconnectTimeoutMs: Long = 5_000,
    val maxJitterMs: Long = 2_000,
)

/** Primer vecino disponible que no se haya contactado durante el enfriamiento (`plan.md` §3). */
fun selectPeer(
    peers: List<P2pPeer>,
    lastContact: Map<String, Long>,
    nowMs: Long,
    cooldownMs: Long,
): P2pPeer? = peers.firstOrNull { peer ->
    val last = lastContact[peer.address]
    peer.status == PEER_STATUS_AVAILABLE && (last == null || nowMs - last >= cooldownMs)
}

/**
 * Ciclo de contactos: buscar, elegir un vecino, conectar, ejecutar la sesión y desconectar.
 * Atiende también los grupos que forma el otro teléfono, y solo mantiene un vecino a la vez.
 */
class ContactScheduler(
    private val link: P2pLink,
    private val session: ContactSession,
    private val timings: ContactTimings = ContactTimings(),
    private val clockMs: () -> Long = System::currentTimeMillis,
    private val random: Random = Random.Default,
) {
    private val lastContact = mutableMapOf<String, Long>()

    private sealed interface Next {
        data class Join(val group: P2pGroup) : Next
        data class Call(val peer: P2pPeer) : Next
    }

    /** Se ejecuta hasta que se cancela. */
    suspend fun run() {
        while (true) {
            link.state.first { it.enabled }
            cycle()
        }
    }

    private suspend fun cycle() {
        link.state.value.group?.let { group ->
            contact(group)
            return
        }
        try {
            link.discover()
        } catch (_: P2pActionException) {
            delay(timings.discoveryCycleMs)
            return
        }
        val next = withTimeoutOrNull(timings.discoveryCycleMs) {
            link.state.mapNotNull { state ->
                state.group?.let(Next::Join)
                    ?: selectPeer(state.peers, lastContact, clockMs(), timings.cooldownMs)
                        ?.let(Next::Call)
            }.first()
        }
        when (next) {
            null -> Unit
            is Next.Join -> contact(next.group)
            is Next.Call -> call(next.peer)
        }
    }

    private suspend fun call(peer: P2pPeer) {
        // Si dos teléfonos se ven a la vez, la espera aleatoria evita que ambos llamen juntos.
        delay(random.nextLong(timings.maxJitterMs + 1))
        link.state.value.group?.let { group ->
            contact(group)
            return
        }
        lastContact[peer.address] = clockMs()
        try {
            link.connect(peer)
        } catch (_: P2pActionException) {
            return
        }
        val group = withTimeoutOrNull(timings.groupTimeoutMs) {
            link.state.mapNotNull { it.group }.first()
        }
        if (group == null) disconnectQuietly() else contact(group)
    }

    private suspend fun contact(group: P2pGroup) {
        try {
            withTimeoutOrNull(timings.sessionTimeoutMs) { session.run(group) }
        } finally {
            // También se cierra si el servicio se detiene a mitad de la sesión.
            withContext(NonCancellable) {
                markConnectedPeers()
                disconnectQuietly()
                withTimeoutOrNull(timings.disconnectTimeoutMs) {
                    link.state.first { it.group == null }
                }
            }
        }
    }

    /** El vecino que inició el grupo también entra en enfriamiento, para no repetir el par. */
    private fun markConnectedPeers() {
        val now = clockMs()
        link.state.value.peers
            .filter { it.status == PEER_STATUS_CONNECTED }
            .forEach { lastContact[it.address] = now }
    }

    private suspend fun disconnectQuietly() {
        try {
            link.disconnect()
        } catch (_: P2pActionException) {
            // Sin grupo que cerrar: el siguiente ciclo parte de cero.
        }
    }
}
