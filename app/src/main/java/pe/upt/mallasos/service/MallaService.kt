package pe.upt.mallasos.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import pe.upt.mallasos.MainActivity
import pe.upt.mallasos.MallaApp
import pe.upt.mallasos.R

/** Mantiene la red activa con la pantalla apagada (`plan.md` §7). */
class MallaService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val container by lazy { (application as MallaApp).container }
    private var wakeLock: PowerManager.WakeLock? = null
    private var started = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        if (!goForeground()) {
            stopSelf()
            return
        }
        started = true
        acquireWakeLock()
        container.wifiDirect.start()
        container.serviceRunning.value = true
        val contacts = scope.launch { container.contacts.run() }
        contacts.invokeOnCompletion { container.wifiDirect.stop() }
        scope.launch {
            container.database.messages().observeQueued().distinctUntilChanged().collect { queued ->
                getSystemService(NotificationManager::class.java)
                    .notify(NOTIFICATION_ID, buildNotification(queued))
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        if (started) {
            container.serviceRunning.value = false
            scope.cancel()
            wakeLock?.release()
        }
        super.onDestroy()
    }

    /**
     * El tipo `location` deja buscar pares con la pantalla apagada, pero el sistema puede negarlo
     * sin permiso de ubicación o al iniciar el teléfono. En ese caso se sigue solo con
     * `connectedDevice`.
     */
    private fun goForeground(): Boolean {
        val connected = ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        val withLocation = connected or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        return startForegroundAs(withLocation) || startForegroundAs(connected)
    }

    private fun startForegroundAs(types: Int): Boolean = try {
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(0), types)
        true
    } catch (_: SecurityException) {
        false
    } catch (_: IllegalStateException) {
        false
    }

    // Sin tiempo límite: el teléfono debe seguir llevando mensajes hasta que se detenga el servicio.
    @SuppressLint("WakelockTimeout")
    private fun acquireWakeLock() {
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "mallasos:red")
            .apply {
                setReferenceCounted(false)
                acquire()
            }
    }

    private fun createChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Red MALLA-SOS",
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    private fun buildNotification(queued: Int): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_hub)
            .setContentTitle("MALLA-SOS activo · $queued en cola")
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "red"
        private const val NOTIFICATION_ID = 1

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, MallaService::class.java),
            )
        }
    }
}
