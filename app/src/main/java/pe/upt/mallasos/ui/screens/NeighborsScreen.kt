package pe.upt.mallasos.ui.screens

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import pe.upt.mallasos.R
import pe.upt.mallasos.data.datastore.GoPolicy
import pe.upt.mallasos.data.datastore.NodeMode
import pe.upt.mallasos.data.datastore.formatNodeId
import pe.upt.mallasos.data.db.KnownNodeEntity
import pe.upt.mallasos.domain.LinkTimings
import pe.upt.mallasos.p2p.P2pPeer
import pe.upt.mallasos.p2p.P2pState
import pe.upt.mallasos.ui.components.IconBubble
import pe.upt.mallasos.ui.components.MallaCard
import pe.upt.mallasos.ui.components.MallaTopBar
import pe.upt.mallasos.ui.components.PrimaryButton
import pe.upt.mallasos.ui.components.ScreenPadding
import pe.upt.mallasos.ui.components.SectionTitle
import pe.upt.mallasos.ui.theme.TechnicalText
import pe.upt.mallasos.ui.theme.malla

/** Valores de `WifiP2pDevice.status`. */
private fun peerStatusLabel(status: Int): String = when (status) {
    0 -> "Conectado"
    1 -> "Conectando"
    2 -> "Falló"
    3 -> "Encontrado"
    else -> "No disponible"
}

@Composable
fun NeighborsScreen(
    p2p: P2pState,
    serviceRunning: Boolean,
    locationEnabled: Boolean,
    mode: NodeMode,
    goIntent: Int,
    onGoIntent: (Int) -> Unit,
    connectedNow: Int,
    timings: LinkTimings?,
    knownNodes: List<KnownNodeEntity>,
    onSearch: () -> Unit,
    onWifiSettings: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        MallaTopBar("Vecinos y conexión", onBack)
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ScreenPadding, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!p2p.supported) {
                Warning("Este teléfono no tiene Wi-Fi Direct")
            } else if (!p2p.enabled) {
                Warning("Wi-Fi apagado", "Activar Wi-Fi", onWifiSettings)
            }
            if (!locationEnabled) {
                Warning("Ubicación del sistema apagada: muchos teléfonos no buscan pares sin ella")
            }
            LinkCard(p2p, serviceRunning, mode, connectedNow)
            GoIntentCard(mode, goIntent, onGoIntent)
            TimingsCard(timings)
            if (mode != NodeMode.RESCATISTA) {
                PrimaryButton(
                    "Buscar ahora",
                    onSearch,
                    enabled = p2p.supported && p2p.enabled,
                    icon = R.drawable.ic_wifi_tethering,
                )
            }
            SectionTitle("Cerca ahora", trailing = p2p.peers.size.toString())
            if (p2p.peers.isEmpty()) {
                Hint("Nadie cerca. Sigue buscando…")
            } else {
                p2p.peers.forEach { PeerRow(it) }
            }
            SectionTitle("Conocidos", trailing = knownNodes.size.toString())
            if (knownNodes.isEmpty()) {
                Hint("Todavía no se ha saludado a ningún teléfono.")
            } else {
                knownNodes.forEach { KnownRow(it) }
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun Warning(text: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(malla.warningContainer, MaterialTheme.shapes.medium)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(painterResource(R.drawable.ic_warning), null, tint = malla.warning)
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = malla.warning,
            modifier = Modifier.weight(1f),
        )
        if (action != null) {
            TextButton(onClick = onAction) { Text(action, color = malla.warning) }
        }
    }
}

@Composable
private fun LinkCard(p2p: P2pState, serviceRunning: Boolean, mode: NodeMode, connectedNow: Int) {
    val group = p2p.group
    val hosting = mode == NodeMode.RESCATISTA
    val state = when {
        hosting && group?.isGroupOwner == true -> "Grupo propio activo"
        hosting && serviceRunning && p2p.enabled -> "Creando grupo"
        group != null -> "Conectado"
        p2p.peers.any { it.status == 1 } -> "Conectando"
        serviceRunning && p2p.enabled -> "Buscando"
        else -> "Libre"
    }
    MallaCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            TechRow("Rol", mode.label)
            TechRow("Estado", state)
            if (hosting) {
                TechRow("Intercambiando ahora", connectedNow.toString())
            }
            if (group != null && !hosting) {
                val owner = p2p.peers.firstOrNull { it.status == 0 }?.name.orEmpty()
                TechRow(
                    "En el grupo",
                    when {
                        group.isGroupOwner -> "Este teléfono es Group Owner"
                        owner.isBlank() -> "Cliente"
                        else -> "Cliente de $owner"
                    },
                )
            }
            if (group != null) TechRow("IP del Group Owner", group.ownerAddress)
        }
    }
}

/**
 * Preferencia de ser Group Owner. El Rescatista no la elige: crea su grupo. Herido y Ayudante la
 * ajustan dentro del rango de su rol; vale para las conexiones siguientes.
 */
@Composable
private fun GoIntentCard(mode: NodeMode, value: Int, onChange: (Int) -> Unit) {
    val range = GoPolicy.range(mode)
    MallaCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            TechRow("Intent de Group Owner", "$value de ${GoPolicy.MAX}")
            if (!GoPolicy.isEditable(mode)) {
                Hint("Como Rescatista, este teléfono crea su propio grupo y siempre es el centro.")
                return@Column
            }
            var draft by remember(value) { mutableFloatStateOf(value.toFloat()) }
            Slider(
                value = draft,
                onValueChange = { draft = it },
                onValueChangeFinished = { onChange(draft.toInt()) },
                valueRange = range.first.toFloat()..range.last.toFloat(),
                steps = (range.last - range.first - 1).coerceAtLeast(0),
                modifier = Modifier.semantics { stateDescription = "${draft.toInt()}" },
            )
            Hint(
                if (mode == NodeMode.HERIDO) {
                    "Como Herido va de ${range.first} a ${range.last}: así tu teléfono casi nunca " +
                        "sostiene el grupo y ahorra batería."
                } else {
                    "Más alto: más probable que este teléfono sea el centro del grupo. Más bajo: " +
                        "se une al de otro."
                },
            )
        }
    }
}

@Composable
private fun TimingsCard(timings: LinkTimings?) {
    fun ms(value: Long?) = value?.let { "$it ms" } ?: "—"
    MallaCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Últimos tiempos", style = MaterialTheme.typography.titleSmall)
            TechRow("T0 → T1", ms(timings?.t0ToT1))
            TechRow("T1 → T2", ms(timings?.t1ToT2))
            TechRow("T2 → T3", ms(timings?.t2ToT3))
            TechRow("Total", ms(timings?.total))
        }
    }
}

@Composable
private fun TechRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = TechnicalText)
    }
}

@Composable
private fun PeerRow(peer: P2pPeer) {
    NodeRow(
        icon = if (peer.status == 0) R.drawable.ic_router else R.drawable.ic_wifi_tethering,
        title = peer.name.ifBlank { "Sin nombre" },
        detail = if (peer.isGroupOwner) "${peer.address} · grupo abierto" else peer.address,
        status = peerStatusLabel(peer.status),
    )
}

@Composable
private fun KnownRow(node: KnownNodeEntity) {
    NodeRow(
        icon = if (node.isRescuer) R.drawable.ic_apartment else R.drawable.ic_person,
        title = node.alias,
        detail = formatNodeId(node.nodeId),
        status = "${if (node.isRescuer) "Rescatista" else "Persona"} · visto ${timeAgo(node.lastSeenAt)}",
    )
}

@Composable
private fun NodeRow(@DrawableRes icon: Int, title: String, detail: String, status: String) {
    MallaCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IconBubble(icon)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    detail,
                    style = TechnicalText,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    status,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
