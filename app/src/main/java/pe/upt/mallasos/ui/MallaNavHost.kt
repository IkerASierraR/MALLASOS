package pe.upt.mallasos.ui

import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.location.LocationManagerCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import pe.upt.mallasos.AppContainer
import pe.upt.mallasos.data.datastore.Identity
import pe.upt.mallasos.data.datastore.NodeMode
import pe.upt.mallasos.data.datastore.TestConfig
import pe.upt.mallasos.data.datastore.formatNodeId
import pe.upt.mallasos.domain.isTestModeActive
import pe.upt.mallasos.domain.latestLinkTimings
import pe.upt.mallasos.domain.postPanelItems
import pe.upt.mallasos.domain.timelineEntries
import pe.upt.mallasos.dtn.QueuePolicy
import pe.upt.mallasos.log.CsvExport
import pe.upt.mallasos.service.MallaService
import pe.upt.mallasos.ui.screens.CreateSosScreen
import pe.upt.mallasos.ui.screens.HomeScreen
import pe.upt.mallasos.ui.screens.NeighborsScreen
import pe.upt.mallasos.ui.screens.NetworkStatus
import pe.upt.mallasos.ui.screens.PostPanelScreen
import pe.upt.mallasos.ui.screens.QueueScreen
import pe.upt.mallasos.ui.screens.SosDetailScreen
import pe.upt.mallasos.ui.screens.SosSentScreen
import pe.upt.mallasos.ui.screens.TestSettingsScreen
import pe.upt.mallasos.ui.screens.WelcomeScreen

private object Routes {
    const val WELCOME = "welcome"
    const val HOME = "home"
    const val SOS_NEW = "sos/new"
    const val SOS_SENT = "sos/sent"
    const val QUEUE = "queue"
    const val POST = "post"
    const val SOS_DETAIL = "sos/{uid}"
    const val NEIGHBORS = "neighbors"
    const val SETTINGS = "settings/test"

    fun sosDetail(uid: String) = "sos/$uid"
}

private fun shareCsv(context: Context, export: CsvExport) {
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/csv")
        .putExtra(Intent.EXTRA_STREAM, export.shareUri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, "Compartir CSV"))
}

@Composable
fun MallaRoot(container: AppContainer) {
    val identity by container.settings.identity.collectAsState(initial = null)
    LaunchedEffect(Unit) { container.settings.ensureNodeId() }
    val current = identity
    if (current == null) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
    } else {
        MallaNavHost(container, current)
    }
}

@Composable
private fun MallaNavHost(container: AppContainer, identity: Identity) {
    val nav = rememberNavController()
    val messageDao = container.database.messages()
    val messages by messageDao.observeVisible().collectAsState(initial = emptyList())
    val queued by messageDao.observeQueued().collectAsState(initial = 0)
    val config by container.settings.testConfig.collectAsState(initial = TestConfig())
    val knownNodes by container.database.knownNodes().observeAll()
        .collectAsState(initial = emptyList())
    val serviceRunning by container.serviceRunning.collectAsState()
    val p2p by container.wifiDirect.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val aliases = remember(knownNodes, identity) {
        knownNodes.associate { it.nodeId to it.alias } + (identity.nodeId to "este teléfono")
    }
    val aliasOf: (Int) -> String = { id -> aliases[id] ?: formatNodeId(id) }
    val setAttended: (String, Boolean) -> Unit = { uid, attended ->
        scope.launch { messageDao.setAttended(uid, attended) }
    }

    LaunchedEffect(identity.onboarded) {
        if (identity.onboarded) MallaService.start(context)
    }

    NavHost(
        navController = nav,
        startDestination = remember { if (identity.onboarded) Routes.HOME else Routes.WELCOME },
    ) {
        composable(Routes.WELCOME) {
            WelcomeScreen(identity) { alias, mode ->
                container.settings.completeOnboarding(alias, mode)
                nav.navigate(Routes.HOME) {
                    popUpTo(Routes.WELCOME) { inclusive = true }
                }
            }
        }
        composable(Routes.HOME) {
            HomeScreen(
                identity = identity,
                network = NetworkStatus(serviceRunning, p2p.supported, p2p.enabled, p2p.peers.size),
                queued = queued,
                visibleMessages = messages.size,
                onSos = { nav.navigate(Routes.SOS_NEW) },
                onMessages = { nav.navigate(Routes.QUEUE) },
                onNeighbors = { nav.navigate(Routes.NEIGHBORS) },
                onPost = { nav.navigate(Routes.POST) },
                onSecretSettings = { nav.navigate(Routes.SETTINGS) },
                testMode = isTestModeActive(config),
                panelPending = postPanelItems(messages).count { !it.attended },
                onReactivate = { MallaService.start(context) },
            )
        }
        composable(Routes.SOS_NEW) {
            CreateSosScreen(
                knownNodes = knownNodes,
                onBack = nav::popBackStack,
                onSend = { draft, battery ->
                    container.sosRepository.create(draft, battery)
                    nav.navigate(Routes.SOS_SENT) {
                        popUpTo(Routes.SOS_NEW) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.SOS_SENT) {
            SosSentScreen(
                onMessages = {
                    nav.navigate(Routes.QUEUE) {
                        popUpTo(Routes.SOS_SENT) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.QUEUE) {
            val active = QueuePolicy.order(messages, config.queueMode)
            val closed = messages.filterNot { message -> active.any { it.uid == message.uid } }
            QueueScreen(
                active + closed,
                onOpen = { nav.navigate(Routes.sosDetail(it)) },
                onBack = nav::popBackStack,
            )
        }
        composable(Routes.POST) {
            PostPanelScreen(
                messages = messages,
                aliasOf = aliasOf,
                onOpen = { nav.navigate(Routes.sosDetail(it)) },
                onAttended = setAttended,
                onBack = nav::popBackStack,
            )
        }
        composable(Routes.SOS_DETAIL) { entry ->
            val uid = entry.arguments?.getString("uid").orEmpty()
            val message by remember(uid) { messageDao.observeMessage(uid) }
                .collectAsState(initial = null)
            val events by remember(uid) { container.database.events().observeForMessage(uid) }
                .collectAsState(initial = emptyList())
            SosDetailScreen(
                message = message,
                timeline = timelineEntries(events, aliasOf),
                aliasOf = aliasOf,
                canAttend = identity.mode == NodeMode.PUESTO &&
                    message?.let { postPanelItems(listOf(it)).isNotEmpty() } == true,
                onAttended = setAttended,
                onBack = nav::popBackStack,
            )
        }
        composable(Routes.NEIGHBORS) {
            val linkEvents by remember { container.database.events().observeLinkEvents() }
                .collectAsState(initial = emptyList())
            NeighborsScreen(
                p2p = p2p,
                serviceRunning = serviceRunning,
                locationEnabled = LocationManagerCompat.isLocationEnabled(
                    context.getSystemService(LocationManager::class.java),
                ),
                goIntent = config.goIntent,
                timings = latestLinkTimings(linkEvents),
                knownNodes = knownNodes,
                onSearch = container.wifiDirect::searchNow,
                onWifiSettings = {
                    context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
                },
                onBack = nav::popBackStack,
            )
        }
        composable(Routes.SETTINGS) {
            val eventCount by remember { container.database.events().observeCount() }
                .collectAsState(initial = 0)
            TestSettingsScreen(
                identity = identity,
                config = config,
                eventCount = eventCount,
                onChange = { change -> scope.launch { container.settings.updateTestConfig(change) } },
                onMode = { mode -> scope.launch { container.settings.setMode(mode) } },
                onExport = {
                    val export = container.csvExporter.export(identity.nodeId)
                    shareCsv(context, export)
                    if (export.savedToDownloads) {
                        "${export.rows} eventos en Descargas/MALLA-SOS"
                    } else {
                        "${export.rows} eventos listos para compartir"
                    }
                },
                onClearLog = { scope.launch { container.database.events().deleteAll() } },
                onBack = nav::popBackStack,
            )
        }
    }
}
