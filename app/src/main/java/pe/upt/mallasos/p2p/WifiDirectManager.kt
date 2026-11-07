package pe.upt.mallasos.p2p

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WpsInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDeviceList
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import pe.upt.mallasos.data.datastore.SettingsStore
import pe.upt.mallasos.log.EventLogger
import pe.upt.mallasos.log.Events
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class P2pPeer(val address: String, val name: String, val status: Int)

/** [ownerAddress] es la IP del Group Owner, normalmente `192.168.49.1`. */
data class P2pGroup(val isGroupOwner: Boolean, val ownerAddress: String)

data class P2pState(
    val supported: Boolean = true,
    val enabled: Boolean = false,
    val peers: List<P2pPeer> = emptyList(),
    val group: P2pGroup? = null,
)

/** Falla una orden al sistema; [reason] es `WifiP2pManager.ERROR`, `P2P_UNSUPPORTED` o `BUSY`. */
class P2pActionException(val action: String, val reason: Int) :
    Exception("$action rechazado (motivo $reason)")

private const val AUTO_INTENT = -1

/** `WifiP2pConfig.groupOwnerIntent` a partir de la configuración: 0, 7, 15 o automático. */
fun groupOwnerIntentValue(setting: String): Int =
    setting.toIntOrNull()?.takeIf { it in 0..15 } ?: AUTO_INTENT

/**
 * Envuelve `WifiP2pManager`. Registra T0 a T3 (`DISCOVERY_START`, `PEER_FOUND`, `CONNECT_REQUEST`
 * y `GROUP_FORMED`). Quien lo llame debe tener concedidos los permisos de Wi-Fi Direct.
 */
@SuppressLint("MissingPermission")
class WifiDirectManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val logger: EventLogger,
    private val settings: SettingsStore,
) {
    private val manager: WifiP2pManager? = context.getSystemService(WifiP2pManager::class.java)
    private var channel: WifiP2pManager.Channel? = null
    private var receiverRegistered = false

    /** Evita repetir `PEER_FOUND` dentro de un mismo ciclo de búsqueda. */
    private val peersThisCycle = mutableSetOf<String>()

    /** Distingue un cierre pedido (`SESSION_END`) de un enlace caído (`LINK_LOST`). */
    @Volatile
    private var closing = false

    private val _state = MutableStateFlow(P2pState(supported = manager != null))
    val state: StateFlow<P2pState> = _state.asStateFlow()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                    val value = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                    val enabled = value == WifiP2pManager.WIFI_P2P_STATE_ENABLED
                    _state.update { it.copy(enabled = enabled) }
                }
                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> IntentCompat.getParcelableExtra(
                    intent,
                    WifiP2pManager.EXTRA_P2P_DEVICE_LIST,
                    WifiP2pDeviceList::class.java,
                )?.let(::onPeers)
                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> IntentCompat.getParcelableExtra(
                    intent,
                    WifiP2pManager.EXTRA_WIFI_P2P_INFO,
                    WifiP2pInfo::class.java,
                )?.let(::onConnection)
            }
        }
    }

    fun start() {
        val manager = manager ?: return
        if (receiverRegistered) return
        channel = openChannel(manager)
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter().apply {
                addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
                addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
                addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        receiverRegistered = true
    }

    fun stop() {
        if (receiverRegistered) context.unregisterReceiver(receiver)
        receiverRegistered = false
        if (Build.VERSION.SDK_INT >= 27) channel?.close()
        channel = null
    }

    /** Inicia un ciclo de búsqueda (T0). El sistema avisa de los pares por `state`. */
    suspend fun discover() {
        peersThisCycle.clear()
        logger.log(Events.DISCOVERY_START)
        action("discoverPeers") { manager, channel, listener ->
            manager.discoverPeers(channel, listener)
        }
    }

    /**
     * Pide conectar con [peer] (T2). Terminar sin error solo significa que el sistema aceptó la
     * orden: el grupo aparece después en `state` (T3).
     */
    suspend fun connect(peer: P2pPeer) {
        val intent = settings.testConfig.first().goIntent
        val config = WifiP2pConfig().apply {
            deviceAddress = peer.address
            groupOwnerIntent = groupOwnerIntentValue(intent)
            wps.setup = WpsInfo.PBC
        }
        logger.log(Events.CONNECT_REQUEST, extra = peer.address)
        action("connect") { manager, channel, listener ->
            manager.connect(channel, config, listener)
        }
    }

    /** Sale del grupo actual o cancela una conexión en curso. */
    suspend fun disconnect() {
        if (_state.value.group == null) {
            action("cancelConnect") { manager, channel, listener ->
                manager.cancelConnect(channel, listener)
            }
        } else {
            closing = true
            try {
                action("removeGroup") { manager, channel, listener ->
                    manager.removeGroup(channel, listener)
                }
            } catch (error: P2pActionException) {
                closing = false
                throw error
            }
        }
    }

    private fun onPeers(list: WifiP2pDeviceList) {
        val peers = list.deviceList.map { P2pPeer(it.deviceAddress, it.deviceName, it.status) }
        _state.update { it.copy(peers = peers) }
        peers.filter { peersThisCycle.add(it.address) }.forEach { peer ->
            scope.launch { logger.log(Events.PEER_FOUND, extra = peer.address) }
        }
    }

    private fun onConnection(info: WifiP2pInfo) {
        val previous = _state.value.group
        if (info.groupFormed && previous == null) {
            val group = P2pGroup(info.isGroupOwner, info.groupOwnerAddress.hostAddress.orEmpty())
            _state.update { it.copy(group = group) }
            scope.launch { logger.log(Events.GROUP_FORMED, extra = "go=${group.isGroupOwner}") }
        } else if (!info.groupFormed && previous != null) {
            _state.update { it.copy(group = null) }
            val event = if (closing) Events.SESSION_END else Events.LINK_LOST
            closing = false
            scope.launch { logger.log(event) }
        }
    }

    private fun openChannel(manager: WifiP2pManager): WifiP2pManager.Channel =
        manager.initialize(context, Looper.getMainLooper()) {
            // El sistema cerró el canal: se reabre mientras el gestor siga activo.
            if (receiverRegistered) channel = openChannel(manager)
        }

    private suspend fun action(
        name: String,
        call: (WifiP2pManager, WifiP2pManager.Channel, WifiP2pManager.ActionListener) -> Unit,
    ) {
        val manager = manager ?: throw P2pActionException(name, WifiP2pManager.P2P_UNSUPPORTED)
        val channel = channel ?: throw P2pActionException(name, WifiP2pManager.ERROR)
        suspendCancellableCoroutine { continuation ->
            call(
                manager,
                channel,
                object : WifiP2pManager.ActionListener {
                    override fun onSuccess() = continuation.resume(Unit)

                    override fun onFailure(reason: Int) =
                        continuation.resumeWithException(P2pActionException(name, reason))
                },
            )
        }
    }
}
