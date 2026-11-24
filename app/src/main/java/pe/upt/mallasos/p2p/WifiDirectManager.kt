package pe.upt.mallasos.p2p

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager.FEATURE_WIFI_DIRECT
import android.net.MacAddress
import android.net.wifi.WpsInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDeviceList
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import pe.upt.mallasos.data.datastore.NodeMode
import pe.upt.mallasos.data.datastore.SettingsStore
import pe.upt.mallasos.data.datastore.goIntentFor
import pe.upt.mallasos.log.EventLogger
import pe.upt.mallasos.log.Events

/** [isGroupOwner] indica que el par ya sostiene un grupo, como el de un Rescatista. */
data class P2pPeer(
    val address: String,
    val name: String,
    val status: Int,
    val isGroupOwner: Boolean = false,
)

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

/**
 * Red fija del grupo del Rescatista (API 29+). Con nombre y clave conocidos, los demás se unen sin
 * que nadie acepte una invitación en pantalla. No es un secreto: el enlace solo transporta
 * mensajes que la app ya valida, y la autenticación queda para E5.
 */
const val RESCUE_NETWORK_NAME = "DIRECT-MS-Rescate"
const val RESCUE_PASSPHRASE = "mallasos-rescate"

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
) : P2pLink {
    private val supported = context.packageManager.hasSystemFeature(FEATURE_WIFI_DIRECT)
    private val manager: WifiP2pManager? =
        if (supported) context.getSystemService(WifiP2pManager::class.java) else null
    private var channel: WifiP2pManager.Channel? = null
    private var receiverRegistered = false

    /** Grupos ajenos que no aceptaron la red fija: se les vuelve a llamar con el método clásico. */
    private val classicOnly = ConcurrentHashMap.newKeySet<String>()

    /** Último par llamado con la red fija; si su grupo no se forma, pasa a [classicOnly]. */
    @Volatile
    private var lastFixedAttempt: String? = null

    /** Evita repetir `PEER_FOUND` dentro de un mismo ciclo de búsqueda. */
    private val peersThisCycle = ConcurrentHashMap.newKeySet<String>()

    /** Distingue un cierre pedido (`SESSION_END`) de un enlace caído (`LINK_LOST`). */
    @Volatile
    private var closing = false

    private val _state = MutableStateFlow(P2pState(supported = manager != null))
    override val state: StateFlow<P2pState> = _state.asStateFlow()

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
            // Son difusiones protegidas: solo las envía el sistema. Con NOT_EXPORTED, las versiones
            // anteriores a API 33 descartan el estado inicial, que llega como difusión sticky.
            ContextCompat.RECEIVER_EXPORTED,
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
    override suspend fun discover() {
        peersThisCycle.clear()
        logger.log(Events.DISCOVERY_START)
        action("discoverPeers") { manager, channel, listener ->
            manager.discoverPeers(channel, listener)
        }
    }

    /** Mantiene visible al Group Owner sin registrar un nuevo ciclo T0 en el CSV. */
    override suspend fun announce() {
        action("discoverPeers") { manager, channel, listener ->
            manager.discoverPeers(channel, listener)
        }
    }

    /**
     * El Rescatista forma su propio grupo sin negociar: siempre queda como Group Owner. Desde API 29
     * usa la red fija; antes, Android elige nombre y clave y los demás entran con la invitación
     * clásica.
     */
    override suspend fun createGroup() {
        logger.log(Events.CONNECT_REQUEST, extra = "createGroup")
        if (Build.VERSION.SDK_INT >= 29) {
            val config = WifiP2pConfig.Builder()
                .setNetworkName(RESCUE_NETWORK_NAME)
                .setPassphrase(RESCUE_PASSPHRASE)
                .enablePersistentMode(false)
                .setGroupOperatingBand(WifiP2pConfig.GROUP_OWNER_BAND_2GHZ)
                .build()
            action("createGroup") { manager, channel, listener ->
                manager.createGroup(channel, config, listener)
            }
        } else {
            action("createGroup") { manager, channel, listener ->
                manager.createGroup(channel, listener)
            }
        }
    }

    /** "Buscar ahora" de la pantalla Vecinos: un fallo solo significa esperar al ciclo siguiente. */
    fun searchNow() {
        scope.launch {
            try {
                discover()
            } catch (_: P2pActionException) {
                // El Wi-Fi está apagado o el sistema está ocupado: la pantalla ya lo muestra.
            }
        }
    }

    /**
     * Pide conectar con [peer] (T2). Terminar sin error solo significa que el sistema aceptó la
     * orden: el grupo aparece después en `state` (T3).
     */
    override suspend fun connect(peer: P2pPeer) {
        val mode = settings.identity.first()?.mode ?: NodeMode.AYUDANTE
        val intent = settings.testConfig.first().goIntentFor(mode)
        val fixed = peer.isGroupOwner && Build.VERSION.SDK_INT >= 29 &&
            peer.address !in classicOnly
        val config = if (fixed) {
            // Se une al grupo ya formado del Rescatista: no hay negociación ni intent.
            WifiP2pConfig.Builder()
                .setNetworkName(RESCUE_NETWORK_NAME)
                .setPassphrase(RESCUE_PASSPHRASE)
                .setDeviceAddress(MacAddress.fromString(peer.address))
                .build()
        } else {
            WifiP2pConfig().apply {
                deviceAddress = peer.address
                groupOwnerIntent = intent
                wps.setup = WpsInfo.PBC
            }
        }
        lastFixedAttempt = if (fixed) peer.address else null
        logger.log(Events.CONNECT_REQUEST, extra = peer.address)
        action("connect") { manager, channel, listener ->
            manager.connect(channel, config, listener)
        }
    }

    /** Sale del grupo actual o cancela una conexión en curso. */
    override suspend fun disconnect() {
        if (_state.value.group == null) {
            // La red fija no funcionó con ese grupo (otra app, o un Rescatista con Android 8 o 9).
            lastFixedAttempt?.let(classicOnly::add)
            lastFixedAttempt = null
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
        val peers = list.deviceList.map {
            P2pPeer(it.deviceAddress, it.deviceName, it.status, it.isGroupOwner)
        }
        _state.update { it.copy(peers = peers) }
        peers.filter { peersThisCycle.add(it.address) }.forEach { peer ->
            scope.launch { logger.log(Events.PEER_FOUND, extra = peer.address) }
        }
    }

    private fun onConnection(info: WifiP2pInfo) {
        val previous = _state.value.group
        if (info.groupFormed) lastFixedAttempt = null
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
            try {
                call(
                    manager,
                    channel,
                    object : WifiP2pManager.ActionListener {
                        override fun onSuccess() = continuation.resume(Unit)

                        override fun onFailure(reason: Int) =
                            continuation.resumeWithException(P2pActionException(name, reason))
                    },
                )
            } catch (_: SecurityException) {
                // El usuario puede no haber concedido los permisos en la bienvenida.
                continuation.resumeWithException(P2pActionException(name, WifiP2pManager.ERROR))
            }
        }
    }
}
