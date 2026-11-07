package pe.upt.mallasos

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import pe.upt.mallasos.data.datastore.SettingsStore
import pe.upt.mallasos.data.db.MallaDatabase
import pe.upt.mallasos.domain.SosRepository
import pe.upt.mallasos.dtn.MessageExchange
import pe.upt.mallasos.log.EventLogger
import pe.upt.mallasos.p2p.ContactScheduler
import pe.upt.mallasos.p2p.PeerContact
import pe.upt.mallasos.p2p.SessionRunner
import pe.upt.mallasos.p2p.WifiDirectManager

/** Inyección de dependencias manual (`plan.md` §1). */
class AppContainer(context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settings = SettingsStore(context)
    val database = MallaDatabase.create(context)
    val logger = EventLogger(database.events(), settings)
    val sosRepository = SosRepository(database.messages(), settings, logger)

    val wifiDirect = WifiDirectManager(context, scope, logger, settings)
    val contacts = ContactScheduler(
        wifiDirect,
        PeerContact(
            SessionRunner(),
            settings,
            database.knownNodes(),
            MessageExchange(database.messages(), logger),
            logger,
        ),
    )

    /** Lo actualiza `MallaService`; Inicio lo usa para mostrar la franja de estado. */
    val serviceRunning = MutableStateFlow(false)
}
