package pe.upt.mallasos.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.launch
import pe.upt.mallasos.R
import pe.upt.mallasos.data.datastore.Identity
import pe.upt.mallasos.data.datastore.NodeMode
import pe.upt.mallasos.data.datastore.formatNodeId
import pe.upt.mallasos.domain.MAX_ALIAS_CHARS
import pe.upt.mallasos.domain.isValidAlias
import pe.upt.mallasos.ui.components.MallaCard
import pe.upt.mallasos.ui.components.Pill
import pe.upt.mallasos.ui.components.PrimaryButton
import pe.upt.mallasos.ui.components.ScreenPadding
import pe.upt.mallasos.ui.theme.TechnicalText
import pe.upt.mallasos.ui.theme.malla

@Composable
fun WelcomeScreen(
    identity: Identity,
    onComplete: suspend (alias: String, mode: NodeMode) -> Unit,
) {
    var step by rememberSaveable { mutableIntStateOf(1) }
    var alias by rememberSaveable { mutableStateOf(identity.alias) }
    var mode by rememberSaveable { mutableStateOf(NodeMode.NORMAL) }
    val scope = rememberCoroutineScope()

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = ScreenPadding),
    ) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    painterResource(R.drawable.ic_hub),
                    null,
                    Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text("MALLA-SOS", style = MaterialTheme.typography.headlineMedium)
            }
            Text(
                "Pide ayuda y lleva mensajes de otros, sin Internet ni señal celular.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            StepIndicator(step)
            when (step) {
                1 -> AliasStep(alias, { alias = it }, identity.nodeId)
                2 -> ModeStep(mode) { mode = it }
                else -> PermissionsStep()
            }
        }
        when (step) {
            1, 2 -> PrimaryButton(
                "Continuar",
                onClick = { step++ },
                enabled = step != 1 || isValidAlias(alias),
            )
            else -> PrimaryButton(
                "Activar red",
                onClick = { scope.launch { onComplete(alias.trim(), mode) } },
                icon = R.drawable.ic_wifi_tethering,
            )
        }
        if (step > 1) {
            OutlinedButton(
                onClick = { step-- },
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                shape = MaterialTheme.shapes.medium,
            ) {
                Text("Volver")
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun StepIndicator(step: Int) {
    val labels = listOf("Identidad", "Modo", "Permisos")
    MallaCard(
        Modifier.fillMaxWidth(),
        container = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Configuración inicial", style = MaterialTheme.typography.titleSmall)
                Text("Paso $step de 3", style = MaterialTheme.typography.labelMedium)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                labels.forEachIndexed { index, _ ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(6.dp)
                            .background(
                                if (index < step) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                },
                                CircleShape,
                            ),
                    )
                }
            }
            Text(
                "$step. ${labels[step - 1]}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun AliasStep(alias: String, onAlias: (String) -> Unit, nodeId: Int) {
    Text("¿Cómo te llamas en la red?", style = MaterialTheme.typography.titleLarge)
    Text(
        "Otros teléfonos cercanos verán este nombre.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    OutlinedTextField(
        value = alias,
        onValueChange = { if (it.length <= MAX_ALIAS_CHARS) onAlias(it) },
        label = { Text("Alias") },
        singleLine = true,
        leadingIcon = { Icon(painterResource(R.drawable.ic_badge), null) },
        suffix = { Text("${alias.length}/$MAX_ALIAS_CHARS", style = TechnicalText) },
        supportingText = { Text("Entre 2 y 20 caracteres") },
        modifier = Modifier.fillMaxWidth(),
    )
    MallaCard(Modifier.fillMaxWidth(), container = MaterialTheme.colorScheme.surfaceVariant) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                painterResource(R.drawable.ic_fingerprint),
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("ID: ${formatNodeId(nodeId)}", style = TechnicalText)
        }
    }
}

@Composable
private fun ModeStep(mode: NodeMode, onMode: (NodeMode) -> Unit) {
    Text("¿Cómo usarás este teléfono?", style = MaterialTheme.typography.titleLarge)
    ModeCard(
        title = "Normal",
        description = "Puedo pedir ayuda y llevar mensajes.",
        selected = mode == NodeMode.NORMAL,
        onClick = { onMode(NodeMode.NORMAL) },
    )
    ModeCard(
        title = "Puesto de coordinación",
        description = "Recibo y confirmo SOS.",
        selected = mode == NodeMode.PUESTO,
        onClick = { onMode(NodeMode.PUESTO) },
    )
}

@Composable
private fun ModeCard(title: String, description: String, selected: Boolean, onClick: () -> Unit) {
    MallaCard(
        Modifier.fillMaxWidth(),
        onClick = onClick,
        border = if (selected) MaterialTheme.colorScheme.primary else malla.cardBorder,
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                painterResource(
                    if (selected) {
                        R.drawable.ic_radio_button_checked
                    } else {
                        R.drawable.ic_radio_button_unchecked
                    },
                ),
                if (selected) "Seleccionado" else "Sin seleccionar",
                tint = MaterialTheme.colorScheme.primary,
            )
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(description, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

private data class PermissionState(
    val location: Boolean,
    val nearby: Boolean,
    val notifications: Boolean,
    val battery: Boolean,
    val systemLocation: Boolean,
)

private fun Context.granted(permission: String) =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

private fun readPermissions(context: Context): PermissionState {
    val power = context.getSystemService(PowerManager::class.java)
    val location = context.getSystemService(LocationManager::class.java)
    return PermissionState(
        location = context.granted(Manifest.permission.ACCESS_FINE_LOCATION),
        nearby = Build.VERSION.SDK_INT < 33 || context.granted(Manifest.permission.NEARBY_WIFI_DEVICES),
        notifications = Build.VERSION.SDK_INT < 33 ||
            context.granted(Manifest.permission.POST_NOTIFICATIONS),
        battery = power.isIgnoringBatteryOptimizations(context.packageName),
        systemLocation = location.isProviderEnabled(LocationManager.GPS_PROVIDER),
    )
}

@SuppressLint("BatteryLife") // La exención es necesaria: el teléfono lleva mensajes en el bolsillo.
@Composable
private fun PermissionsStep() {
    val context = LocalContext.current
    var state by remember { mutableStateOf(readPermissions(context)) }
    LifecycleResumeEffect(Unit) {
        state = readPermissions(context)
        onPauseOrDispose { }
    }
    val request = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { state = readPermissions(context) }
    val openSettings = {
        context.startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null),
            ),
        )
    }
    val requestable = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= 33) {
            add(Manifest.permission.NEARBY_WIFI_DEVICES)
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Text("Permisos para la red", style = MaterialTheme.typography.titleLarge)
    Text(
        "Hacen falta para encontrar teléfonos cercanos sin Internet.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (!state.location || !state.nearby || !state.notifications) {
        PrimaryButton("Conceder permisos", { request.launch(requestable.toTypedArray()) })
    }
    PermissionRow(R.drawable.ic_location_on, "Ubicación", state.location, openSettings)
    PermissionRow(R.drawable.ic_near_me, "Dispositivos cercanos", state.nearby, openSettings)
    PermissionRow(R.drawable.ic_notifications, "Notificaciones", state.notifications, openSettings)
    PermissionRow(R.drawable.ic_battery_full, "Exención de batería", state.battery) {
        context.startActivity(
            Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:${context.packageName}"),
            ),
        )
    }
    PermissionRow(R.drawable.ic_my_location, "Ubicación del sistema encendida", state.systemLocation) {
        context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
    }
}

@Composable
private fun PermissionRow(
    @DrawableRes icon: Int,
    title: String,
    ok: Boolean,
    onFix: () -> Unit,
) {
    MallaCard(
        Modifier.fillMaxWidth(),
        container = if (ok) {
            MaterialTheme.colorScheme.surfaceContainerLowest
        } else {
            malla.warningContainer
        },
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painterResource(icon),
                    null,
                    tint = if (ok) MaterialTheme.colorScheme.primary else malla.warning,
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                if (ok) {
                    Pill("Listo", R.drawable.ic_check_circle, malla.delivered, Color.Transparent)
                } else {
                    Pill("Falta", R.drawable.ic_warning, malla.warning, Color.Transparent)
                }
            }
            if (!ok) {
                OutlinedButton(
                    onClick = onFix,
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Icon(painterResource(R.drawable.ic_open_in_new), null, Modifier.size(18.dp))
                    Text(" Abrir ajustes")
                }
            }
        }
    }
}
