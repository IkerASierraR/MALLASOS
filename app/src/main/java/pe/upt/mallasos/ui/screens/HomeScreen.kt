package pe.upt.mallasos.ui.screens

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import pe.upt.mallasos.R
import pe.upt.mallasos.data.datastore.Identity
import pe.upt.mallasos.data.datastore.NodeMode
import pe.upt.mallasos.data.datastore.formatNodeId
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
    queued: Int,
    visibleMessages: Int,
    onSos: () -> Unit,
    onMessages: () -> Unit,
) {
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
        ServiceBanner(queued)
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
            subtitle = "Disponible al conectar la red",
        )
        if (identity.mode == NodeMode.PUESTO) {
            ShortcutTile(
                icon = R.drawable.ic_apartment,
                title = "Panel del Puesto",
                subtitle = "Disponible al conectar la red",
            )
        }
        Spacer(Modifier.weight(1f))
        Text(
            "MALLA-SOS v1.0 · ID ${formatNodeId(identity.nodeId)}",
            style = TechnicalText,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Mientras no exista `MallaService` (T1.7) la red se muestra detenida: nunca se finge actividad. */
@Composable
private fun ServiceBanner(queued: Int) {
    val text = "Red detenida · 0 vecinos · $queued en cola"
    Row(
        Modifier
            .fillMaxWidth()
            .background(malla.warningContainer, MaterialTheme.shapes.medium)
            .padding(12.dp)
            .semantics { stateDescription = text },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(painterResource(R.drawable.ic_wifi_off), null, tint = malla.warning)
        Text(text, style = MaterialTheme.typography.labelLarge, color = malla.warning)
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
