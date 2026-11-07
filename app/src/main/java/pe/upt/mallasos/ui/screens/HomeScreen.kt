package pe.upt.mallasos.ui.screens

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import pe.upt.mallasos.R
import pe.upt.mallasos.data.datastore.Identity
import pe.upt.mallasos.data.datastore.NodeMode
import pe.upt.mallasos.data.datastore.formatNodeId
import pe.upt.mallasos.domain.SECRET_TAPS
import pe.upt.mallasos.domain.secretTapsHint
import pe.upt.mallasos.ui.components.IconBubble
import pe.upt.mallasos.ui.components.MallaCard
import pe.upt.mallasos.ui.components.Pill
import pe.upt.mallasos.ui.components.ScreenPadding
import pe.upt.mallasos.ui.components.SectionTitle
import pe.upt.mallasos.ui.components.SosButton
import pe.upt.mallasos.ui.theme.TechnicalText
import pe.upt.mallasos.ui.theme.malla

@Composable
fun HomeScreen(
    identity: Identity,
    network: NetworkStatus,
    queued: Int,
    visibleMessages: Int,
    onSos: () -> Unit,
    onMessages: () -> Unit,
    onNeighbors: () -> Unit,
    onPost: () -> Unit,
    onSecretSettings: () -> Unit,
    testMode: Boolean,
    panelPending: Int,
    onReactivate: () -> Unit,
) {
    var taps by rememberSaveable { mutableIntStateOf(0) }
    val context = LocalContext.current
    var permissions by remember { mutableStateOf(hasNetworkPermissions(context)) }
    LifecycleResumeEffect(Unit) {
        permissions = hasNetworkPermissions(context)
        onPauseOrDispose { }
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = ScreenPadding, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("MALLA-SOS", style = MaterialTheme.typography.headlineSmall)
            Pill(
                "Modo: ${identity.mode.label}",
                R.drawable.ic_tune,
                MaterialTheme.colorScheme.onPrimaryContainer,
                MaterialTheme.colorScheme.primaryContainer,
            )
        }
        ServiceBanner(network, permissions, queued, onReactivate) { openAppSettings(context) }
        if (testMode) {
            Pill("Modo pruebas activo", R.drawable.ic_tune, malla.neutral, malla.neutralContainer)
        }
        SosButton(
            onClick = onSos,
            minHeight = 168.dp,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            "Toca para describir tu emergencia. Se guarda aunque no haya nadie cerca.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        SectionTitle("Comunicaciones", Modifier.padding(top = 8.dp))
        ShortcutTile(
            icon = R.drawable.ic_inbox,
            title = "Mis mensajes",
            subtitle = if (visibleMessages == 0) "Sin mensajes" else "$visibleMessages en total · $queued en cola",
            onClick = onMessages,
        )
        ShortcutTile(
            icon = R.drawable.ic_wifi_tethering,
            title = "Vecinos",
            subtitle = if (network.active) "${network.neighbors} cerca ahora" else "Sin búsqueda activa",
            onClick = onNeighbors,
        )
        if (identity.mode == NodeMode.PUESTO) {
            ShortcutTile(
                icon = R.drawable.ic_apartment,
                title = "Panel del Puesto",
                subtitle = if (panelPending == 0) "Sin SOS por atender" else "$panelPending sin atender",
                onClick = onPost,
            )
        }
        Spacer(Modifier.weight(1f))
        // Siempre ocupa su línea: si el pie se moviera, los toques siguientes caerían fuera.
        Text(
            secretTapsHint(taps).orEmpty(),
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "MALLA-SOS v1.0 · ID ${formatNodeId(identity.nodeId)}",
            style = TechnicalText,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    taps++
                    if (taps >= SECRET_TAPS) {
                        taps = 0
                        onSecretSettings()
                    }
                },
        )
    }
}

/** Estado real de la red; lo calcula `MallaNavHost` a partir del servicio y de Wi-Fi Direct. */
data class NetworkStatus(
    val serviceRunning: Boolean,
    val supported: Boolean,
    val enabled: Boolean,
    val neighbors: Int,
) {
    val active: Boolean get() = serviceRunning && supported && enabled
}

@Composable
private fun ServiceBanner(
    network: NetworkStatus,
    permissions: Boolean,
    queued: Int,
    onReactivate: () -> Unit,
    onPermissions: () -> Unit,
) {
    val neighbors = if (network.neighbors == 1) "1 vecino" else "${network.neighbors} vecinos"
    val text = when {
        !permissions -> "Faltan permisos · $queued en cola"
        !network.serviceRunning -> "Red detenida · $queued en cola"
        !network.supported -> "Este teléfono no tiene Wi-Fi Direct · $queued en cola"
        !network.enabled -> "Wi-Fi apagado · $queued en cola"
        else -> "Red activa · $neighbors · $queued en cola"
    }
    val healthy = network.active && permissions
    val content = if (healthy) malla.carried else malla.warning
    val container = if (healthy) malla.carriedContainer else malla.warningContainer
    Row(
        Modifier
            .fillMaxWidth()
            .background(container, MaterialTheme.shapes.medium)
            .padding(12.dp)
            .semantics { stateDescription = text },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            painterResource(
                if (healthy) R.drawable.ic_wifi_tethering else R.drawable.ic_wifi_off,
            ),
            null,
            tint = content,
        )
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = content,
            modifier = Modifier.weight(1f),
        )
        if (!permissions) {
            TextButton(onClick = onPermissions) {
                Text("Resolver", color = content)
            }
        } else if (!network.serviceRunning) {
            TextButton(onClick = onReactivate) {
                Text("Reactivar", color = content)
            }
        }
    }
}

@Composable
private fun ShortcutTile(
    @DrawableRes icon: Int,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null,
) {
    MallaCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IconBubble(icon)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (onClick != null) {
                Icon(
                    painterResource(R.drawable.ic_chevron_right),
                    null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
