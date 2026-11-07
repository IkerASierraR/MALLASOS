package pe.upt.mallasos.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.flow.combine
import pe.upt.mallasos.AppContainer
import pe.upt.mallasos.data.datastore.Identity
import pe.upt.mallasos.data.datastore.QueueMode
import pe.upt.mallasos.data.datastore.TestConfig
import pe.upt.mallasos.dtn.QueuePolicy
import pe.upt.mallasos.ui.screens.CreateSosScreen
import pe.upt.mallasos.ui.screens.HomeScreen
import pe.upt.mallasos.ui.screens.QueueScreen
import pe.upt.mallasos.ui.screens.SosSentScreen
import pe.upt.mallasos.ui.screens.WelcomeScreen

private object Routes {
    const val WELCOME = "welcome"
    const val HOME = "home"
    const val SOS_NEW = "sos/new"
    const val SOS_SENT = "sos/sent"
    const val QUEUE = "queue"
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
                queued = queued,
                visibleMessages = messages.size,
                onSos = { nav.navigate(Routes.SOS_NEW) },
                onMessages = { nav.navigate(Routes.QUEUE) },
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
            QueueScreen(active + closed, onBack = nav::popBackStack)
        }
    }
}
