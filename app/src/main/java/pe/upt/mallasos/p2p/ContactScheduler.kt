package pe.upt.mallasos.p2p

import kotlin.random.Random
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Valores de `WifiP2pDevice.status`. */
const val PEER_STATUS_CONNECTED = 0
const val PEER_STATUS_AVAILABLE = 3

/** Enlace Wi-Fi Direct que el planificador necesita; lo implementa `WifiDirectManager`. */
interface P2pLink {
    val state: StateFlow<P2pState>
    suspend fun discover()
    suspend fun connect(peer: P2pPeer)
    suspend fun disconnect()

    /** Forma un grupo propio como Group Owner, sin negociar (modo Rescatista). */
    suspend fun createGroup()

    /** Vuelve a anunciarse para que los demás lo encuentren, sin iniciar un ciclo medido. */
    suspend fun announce()
}

/** Lo que ocurre dentro de un grupo ya formado. Debe capturar sus propios fallos de red. */
fun interface ContactSession {
    suspend fun run(group: P2pGroup)
}

/**
 * Servidor del Rescatista: atiende a varios teléfonos a la vez dentro de su grupo hasta que se
 * cancela. Debe capturar sus propios fallos de red.
 */
fun interface HostSession {
    suspend fun host()
}

data class ContactTimings(
    val discoveryCycleMs: Long = 15_000,
    val cooldownMs: Long = 60_000,
    // Algunos teléfonos piden aceptar la invitación a mano: se deja tiempo para hacerlo.
    val groupTimeoutMs: Long = 30_000,
    val sessionTimeoutMs: Long = 30_000,
    val disconnectTimeoutMs: Long = 5_000,
    val maxJitterMs: Long = 2_000,
)

/**
 * Primer vecino disponible que no se haya contactado durante el enfriamiento (`plan.md` §3). Los
 * Group Owner (Rescatistas) van primero: entregarles un SOS cierra el recorrido.
 */
fun selectPeer(
    peers: List<P2pPeer>,
    lastContact: Map<String, Long>,
    nowMs: Long,
    cooldownMs: Long,
): P2pPeer? = peers.sortedByDescending { it.isGroupOwner }.firstOrNull { peer ->
    val last = lastContact[peer.address]
    peer.status == PEER_STATUS_AVAILABLE && (last == null || nowMs - last >= cooldownMs)
}

/**
 * Herido y Ayudante: ciclo de contactos (buscar, elegir un vecino, conectar, ejecutar la sesión y
 * desconectar), un vecino a la vez; atiende también los grupos que forma el otro teléfono.
 *
 * Rescatista ([hostMode] en `true`): crea su propio grupo, lo mantiene encendido, se anuncia cada
 * ciclo y deja que [host] atienda a todos los que se unan. Al salir del modo cierra el grupo.
 */
class ContactScheduler(
    private val link: P2pLink,
    private val session: ContactSession,
    private val timings: ContactTimings = ContactTimings(),
    private val clockMs: () -> Long = System::currentTimeMillis,
    private val random: Random = Random.Default,
    private val hostMode: Flow<Boolean> = flowOf(false),
    private val host: HostSession = HostSession { awaitCancellation() },
) {
    private val lastContact = mutableMapOf<String, Long>()

    private sealed interface Next {
        data class Join(val group: P2pGroup) : Next
        data class Call(val peer: P2pPeer) : Next
    }

    /** Se ejecuta hasta que se cancela. Cambiar de rol o apagar el Wi-Fi reinicia el bucle. */
    suspend fun run() {
        combine(
            link.state.map { it.enabled }.distinctUntilChanged(),
            hostMode.distinctUntilChanged(),
        ) { enabled, hosting -> enabled to hosting }
            .collectLatest { (enabled, hosting) ->
                when {
                    !enabled -> Unit
                    hosting -> hostLoop()
                    else -> while (true) cycle()
                }
            }
    }

    private suspend fun hostLoop() {
        try {
            while (true) hostOnce()
        } finally {
            withContext(NonCancellable) {
                if (link.state.value.group != null) disconnectQuietly()
            }
        }
    }

    /** Asegura el grupo propio y lo atiende hasta que se pierde. */
    private suspend fun hostOnce() {
        val current = link.state.value.group
        if (current != null && !current.isGroupOwner) {
            // Venía como cliente de otro grupo: primero sale para poder crear el suyo.
            disconnectQuietly()
            withTimeoutOrNull(timings.disconnectTimeoutMs) { link.state.first { it.group == null } }
            return
        }
        val group = current ?: run {
            try {
                link.createGroup()
            } catch (_: P2pActionException) {
                delay(timings.discoveryCycleMs)
                return
            }
            withTimeoutOrNull(timings.groupTimeoutMs) {
                link.state.mapNotNull { it.group }.first()
            } ?: return
        }
        if (!group.isGroupOwner) return
        coroutineScope {
            val announcer = launch {
                while (true) {
                    try {
                        link.announce()
                    } catch (_: P2pActionException) {
                        // El sistema estaba ocupado: se reintenta en el ciclo siguiente.
                    }
                    delay(timings.discoveryCycleMs)
                }
            }
            val server = launch {
                while (true) {
                    host.host()
                    // El servidor terminó por un fallo: se reabre tras una pausa breve.
                    delay(timings.maxJitterMs.coerceAtLeast(500))
                }
            }
            link.state.first { it.group == null }
            announcer.cancel()
            server.cancel()
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
