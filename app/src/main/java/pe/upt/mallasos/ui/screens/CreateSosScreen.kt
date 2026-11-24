package pe.upt.mallasos.ui.screens

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import pe.upt.mallasos.R
import pe.upt.mallasos.codec.ANY_POST
import pe.upt.mallasos.codec.EmergencyType
import pe.upt.mallasos.codec.GpsPosition
import pe.upt.mallasos.data.datastore.formatNodeId
import pe.upt.mallasos.data.db.KnownNodeEntity
import pe.upt.mallasos.domain.MAX_LOCATION_CHARS
import pe.upt.mallasos.domain.MAX_MESSAGE_CHARS
import pe.upt.mallasos.domain.SosDraft
import pe.upt.mallasos.ui.components.ChipStatus
import pe.upt.mallasos.ui.components.MallaCard
import pe.upt.mallasos.ui.components.MallaTopBar
import pe.upt.mallasos.ui.components.PrimaryButton
import pe.upt.mallasos.ui.components.ScreenPadding
import pe.upt.mallasos.ui.components.SectionTitle
import pe.upt.mallasos.ui.components.SosButton
import pe.upt.mallasos.ui.components.StatusChip
import pe.upt.mallasos.ui.theme.TechnicalText
import pe.upt.mallasos.ui.theme.malla

@DrawableRes
fun EmergencyType.icon(): Int = when (this) {
    EmergencyType.HERIDO -> R.drawable.ic_personal_injury
    EmergencyType.ATRAPADO -> R.drawable.ic_lock
    EmergencyType.MEDICO -> R.drawable.ic_medical_services
    EmergencyType.INCENDIO -> R.drawable.ic_local_fire_department
    EmergencyType.OTRO -> R.drawable.ic_help
}

@Composable
fun CreateSosScreen(
    knownNodes: List<KnownNodeEntity>,
    onBack: () -> Unit,
    onSend: suspend (SosDraft, batteryPercent: Int) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val gps = rememberGpsStatus()
    val battery = remember { batteryPercent(context) }
    var draft by remember { mutableStateOf(SosDraft()) }
    var sending by remember { mutableStateOf(false) }

    LaunchedEffect(gps) {
        (gps as? GpsStatus.Ready)?.let {
            draft = draft.copy(position = GpsPosition(it.location.latitude, it.location.longitude))
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .imePadding(),
    ) {
        MallaTopBar("Crear SOS", onBack)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ScreenPadding, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionTitle("1. Tipo de emergencia")
            EmergencyGrid(draft.emergency) { draft = draft.copy(emergency = it) }

            SectionTitle("2. Personas afectadas")
            PeopleCounter(draft.people) { draft = draft.copy(people = it) }

            SectionTitle("3. Ubicación", trailing = "${draft.locationText.length}/$MAX_LOCATION_CHARS")
            GpsLine(gps)
            OutlinedTextField(
                value = draft.locationText,
                onValueChange = { draft = draft.copy(locationText = it) },
                label = { Text("¿Dónde estás?") },
                placeholder = { Text("Pabellón B, piso 2") },
                singleLine = true,
                isError = draft.locationTooLong,
                modifier = Modifier.fillMaxWidth(),
            )

            SectionTitle("4. Mensaje (opcional)", trailing = "${draft.message.length}/$MAX_MESSAGE_CHARS")
            OutlinedTextField(
                value = draft.message,
                onValueChange = { draft = draft.copy(message = it) },
                minLines = 2,
                maxLines = 4,
                isError = draft.messageTooLong,
                modifier = Modifier.fillMaxWidth(),
            )

            SectionTitle("5. Destino")
            DestinationSelector(draft.destId, knownNodes) { draft = draft.copy(destId = it) }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    painterResource(R.drawable.ic_battery_full),
                    null,
                    Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Batería del teléfono: $battery %",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Surface(tonalElevation = 2.dp) {
            SosButton(
                onClick = {
                    sending = true
                    scope.launch { onSend(draft, battery) }
                },
                enabled = draft.missing == null && !sending,
                disabledReason = draft.missing,
                minHeight = 64.dp,
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(ScreenPadding),
            )
        }
    }
}

@Composable
private fun EmergencyGrid(selected: EmergencyType?, onSelect: (EmergencyType) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            EmergencyType.HERIDO to EmergencyType.ATRAPADO,
            EmergencyType.MEDICO to EmergencyType.INCENDIO,
        ).forEach { (left, right) ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EmergencyButton(left, selected == left, Modifier.weight(1f), onSelect)
                EmergencyButton(right, selected == right, Modifier.weight(1f), onSelect)
            }
        }
        EmergencyButton(
            EmergencyType.OTRO,
            selected == EmergencyType.OTRO,
            Modifier.fillMaxWidth(),
            onSelect,
        )
    }
}

@Composable
private fun EmergencyButton(
    type: EmergencyType,
    selected: Boolean,
    modifier: Modifier,
    onSelect: (EmergencyType) -> Unit,
) {
    Surface(
        onClick = { onSelect(type) },
        modifier = modifier
            .heightIn(min = 88.dp)
            .semantics { this.selected = selected },
        shape = MaterialTheme.shapes.medium,
        color = if (selected) malla.sosContainer else MaterialTheme.colorScheme.surfaceVariant,
        border = if (selected) BorderStroke(2.dp, malla.sos) else null,
    ) {
        Column(
            Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                painterResource(type.icon()),
                null,
                Modifier.size(32.dp),
                tint = if (selected) malla.sos else MaterialTheme.colorScheme.primary,
            )
            Text(
                type.label,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
internal fun PeopleCounter(people: Int, onChange: (Int) -> Unit) {
    MallaCard(Modifier.fillMaxWidth(), container = MaterialTheme.colorScheme.surfaceVariant) {
        Row(
            Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedIconButton(
                onClick = { onChange(people - 1) },
                enabled = people > 1,
                modifier = Modifier.size(56.dp),
            ) {
                Icon(painterResource(R.drawable.ic_remove), "Una persona menos")
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "$people",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    if (people == 1) "persona" else "personas",
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            OutlinedIconButton(
                onClick = { onChange(people + 1) },
                enabled = people < 99,
                modifier = Modifier.size(56.dp),
            ) {
                Icon(painterResource(R.drawable.ic_add), "Una persona más")
            }
        }
    }
}

@Composable
private fun GpsLine(gps: GpsStatus) {
    val text = when (gps) {
        GpsStatus.Searching -> "GPS: buscando…"
        GpsStatus.Unavailable -> "Sin GPS"
        is GpsStatus.Ready -> "GPS: listo ±${gps.location.accuracy.toInt()} m"
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            painterResource(R.drawable.ic_my_location),
            null,
            Modifier.size(18.dp),
            tint = if (gps is GpsStatus.Ready) malla.delivered else malla.neutral,
        )
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun DestinationSelector(
    destId: Int,
    nodes: List<KnownNodeEntity>,
    onSelect: (Int) -> Unit,
) {
    var picking by remember { mutableStateOf(false) }
    val selectedNode = nodes.firstOrNull { it.nodeId == destId }
    MallaCard(Modifier.fillMaxWidth(), onClick = { picking = true }) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                painterResource(R.drawable.ic_wifi_tethering),
                null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(Modifier.weight(1f)) {
                Text(
                    selectedNode?.alias ?: "Cualquier rescatista",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    "Elegir teléfono…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(painterResource(R.drawable.ic_chevron_right), null)
        }
    }
    if (picking) {
        AlertDialog(
            onDismissRequest = { picking = false },
            confirmButton = { TextButton({ picking = false }) { Text("Cerrar") } },
            title = { Text("Destino") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    DestinationOption("Cualquier rescatista", null) {
                        onSelect(ANY_POST)
                        picking = false
                    }
                    if (nodes.isEmpty()) {
                        Text(
                            "Aún no hay teléfonos conocidos.",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                    nodes.forEach { node ->
                        DestinationOption(node.alias, formatNodeId(node.nodeId)) {
                            onSelect(node.nodeId)
                            picking = false
                        }
                    }
                }
            },
        )
    }
}

@Composable
private fun DestinationOption(title: String, id: String?, onClick: () -> Unit) {
    Surface(onClick = onClick, Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Column(Modifier.padding(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (id != null) Text(id, style = TechnicalText)
        }
    }
}

@Composable
fun SosSentScreen(onMessages: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(ScreenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        StatusChip(ChipStatus.Pending)
        Text("SOS guardado", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Se enviará al encontrar a alguien cerca.",
            style = MaterialTheme.typography.bodyLarge,
        )
        PrimaryButton("Ver mis mensajes", onMessages)
    }
}
