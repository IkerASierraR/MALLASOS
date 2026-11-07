package pe.upt.mallasos.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import pe.upt.mallasos.MallaApp

/** Reactiva la red al reiniciar el teléfono, si la bienvenida ya se completó. */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        val settings = (context.applicationContext as MallaApp).container.settings
        CoroutineScope(Dispatchers.Default).launch {
            try {
                if (settings.identity.first()?.onboarded == true) MallaService.start(context)
            } finally {
                pending.finish()
            }
        }
    }
}
