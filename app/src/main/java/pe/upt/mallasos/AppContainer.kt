package pe.upt.mallasos

import android.content.Context
import pe.upt.mallasos.data.datastore.SettingsStore
import pe.upt.mallasos.data.db.MallaDatabase
import pe.upt.mallasos.domain.SosRepository
import pe.upt.mallasos.log.EventLogger

/** Inyección de dependencias manual (`plan.md` §1). */
class AppContainer(context: Context) {
    val settings = SettingsStore(context)
    val database = MallaDatabase.create(context)
    val logger = EventLogger(database.events(), settings)
    val sosRepository = SosRepository(database.messages(), settings, logger)
}
