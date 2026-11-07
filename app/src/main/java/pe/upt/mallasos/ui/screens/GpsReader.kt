package pe.upt.mallasos.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.BatteryManager
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

sealed interface GpsStatus {
    data object Unavailable : GpsStatus
    data object Searching : GpsStatus
    data class Ready(val location: Location) : GpsStatus
}

private const val MAX_FIX_AGE_MS = 5 * 60 * 1000L

/** Posición por `LocationManager`, sin Google Play Services (`plan.md` §1). */
@SuppressLint("MissingPermission")
@Composable
fun rememberGpsStatus(): GpsStatus {
    val context = LocalContext.current
    var status by remember { mutableStateOf<GpsStatus>(GpsStatus.Searching) }
    DisposableEffect(context) {
        val manager = context.getSystemService(LocationManager::class.java)
        val allowed = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val enabled = allowed && manager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val listener = LocationListener { status = GpsStatus.Ready(it) }
        if (enabled) {
            manager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?.takeIf { System.currentTimeMillis() - it.time < MAX_FIX_AGE_MS }
                ?.let { status = GpsStatus.Ready(it) }
            manager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                2_000L,
                0f,
                listener,
                Looper.getMainLooper(),
            )
        } else {
            status = GpsStatus.Unavailable
        }
        onDispose { if (enabled) manager.removeUpdates(listener) }
    }
    return status
}

fun batteryPercent(context: Context): Int =
    context.getSystemService(BatteryManager::class.java)
        .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
