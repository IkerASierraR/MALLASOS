package pe.upt.mallasos.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import pe.upt.mallasos.R
import pe.upt.mallasos.lora.LoraLinkState
import pe.upt.mallasos.lora.LoraReport
import pe.upt.mallasos.lora.LoraReportType
import pe.upt.mallasos.lora.LoraSendStatus
import pe.upt.mallasos.ui.components.MallaCard
import pe.upt.mallasos.ui.components.MallaTopBar
import pe.upt.mallasos.ui.components.Pill
import pe.upt.mallasos.ui.components.PrimaryButton
import pe.upt.mallasos.ui.components.ScreenPadding
import pe.upt.mallasos.ui.components.SectionTitle
import pe.upt.mallasos.ui.components.SegmentedToggle
import pe.upt.mallasos.ui.theme.TechnicalText
import pe.upt.mallasos.ui.theme.malla

/** Texto libre de un reporte LoRa; el límite real se fijará con el formato de la placa. */
const val MAX_LORA_TEXT = 60

/** Tipos que el Rescatista redacta a mano; el SOS reenviado sale desde el detalle del SOS. */
private val COMPOSABLE_TYPES = LoraReportType.entries.filter { it != LoraReportType.SOS }

/** Conexión LoRa del Rescatista: placa por Bluetooth LE y reportes hacia el puesto. */
@Composable
fun LoraScreen(
    linkState: LoraLinkState,
    outbox: List<LoraReport>,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onSend: suspend (type: LoraReportType, people: Int, text: String) -> Unit,
    onBack: () -> Unit,
) {
    var typeIndex by rememberSaveable { mutableIntStateOf(0) }
    var people by rememberSaveable { mutableIntStateOf(1) }
    var text by rememberSaveable { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        MallaTopBar("Conexión LoRa", onBack)
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ScreenPadding, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BoardCard(linkState, onConnect, onDisconnect)

            SectionTitle("Enviar al puesto")
            SegmentedToggle(
                options = COMPOSABLE_TYPES.map { it.label },
                selected = typeIndex,
                onSelect = { typeIndex = it },
            )
            PeopleCounter(people) { people = it }
            OutlinedTextField(
                value = text,
                onValueChange = { if (it.length <= MAX_LORA_TEXT) text = it },
                label = { Text("Detalle") },
                placeholder = { Text("Zona norte: 3 heridos, falta agua") },
                supportingText = { Text("${text.length}/$MAX_LORA_TEXT") },
                minLines = 2,
                maxLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            PrimaryButton(
                text = "Enviar por LoRa",
                onClick = {
                    scope.launch {
                        onSend(COMPOSABLE_TYPES[typeIndex], people, text)
                        text = ""
                        people = 1
                    }
                },
                enabled = text.isNotBlank(),
                icon = R.drawable.ic_cell_tower,
            )
            Text(
                if (linkState == LoraLinkState.CONECTADO) {
                    "Sale en cuanto lo aceptes."
                } else {
                    "Sin placa conectada, el reporte queda en espera y sale al conectarla."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SectionTitle("Reportes", trailing = outbox.size.toString())
            if (outbox.isEmpty()) {
                Text(
                    "Todavía no se ha enviado nada por LoRa.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                outbox.forEach { ReportRow(it) }
            }
        }
    }
}

@Composable
private fun BoardCard(state: LoraLinkState, onConnect: () -> Unit, onDisconnect: () -> Unit) {
    MallaCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Placa LoRa (Bluetooth)", style = MaterialTheme.typography.titleSmall)
                val connected = state == LoraLinkState.CONECTADO
                Pill(
                    state.label,
                    null,
                    if (connected) malla.delivered else malla.warning,
                    if (connected) malla.deliveredContainer else malla.warningContainer,
                )
            }
            when (state) {
                LoraLinkState.SIN_PLACA -> Text(
                    "La conexión se activará cuando la placa ESP32 + LoRa esté lista y se defina " +
                        "su formato. Mientras tanto, los reportes quedan en espera.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LoraLinkState.CONECTADO -> OutlinedButton(
                    onClick = onDisconnect,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Desconectar") }
                LoraLinkState.BUSCANDO -> Text(
                    "Acerca la placa al teléfono…",
                    style = MaterialTheme.typography.bodySmall,
                )
                LoraLinkState.DESCONECTADO -> PrimaryButton(
                    "Conectar placa",
                    onConnect,
                    icon = R.drawable.ic_bolt,
                )
            }
        }
    }
}

@Composable
private fun ReportRow(report: LoraReport) {
    val (content, container) = when (report.status) {
        LoraSendStatus.ENVIADO -> malla.delivered to malla.deliveredContainer
        LoraSendStatus.EN_ESPERA -> malla.pending to malla.pendingContainer
        LoraSendStatus.FALLIDO -> malla.warning to malla.warningContainer
    }
    MallaCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(report.type.label, style = MaterialTheme.typography.titleSmall)
                Pill(report.status.label, null, content, container)
            }
            val who = if (report.people == 1) "1 persona" else "${report.people} personas"
            Text(
                listOf(who, report.text).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                timeAgo(report.createdAt),
                style = TechnicalText,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
