package pe.upt.mallasos.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import pe.upt.mallasos.R
import pe.upt.mallasos.data.datastore.DtnMode
import pe.upt.mallasos.data.datastore.Identity
import pe.upt.mallasos.data.datastore.NodeMode
import pe.upt.mallasos.data.datastore.QueueMode
import pe.upt.mallasos.data.datastore.TestConfig
import pe.upt.mallasos.ui.components.MallaCard
import pe.upt.mallasos.ui.components.MallaTopBar
import pe.upt.mallasos.ui.components.PrimaryButton
import pe.upt.mallasos.ui.components.ScreenPadding
import pe.upt.mallasos.ui.components.SectionTitle
import pe.upt.mallasos.ui.components.SegmentedToggle
import pe.upt.mallasos.ui.theme.TechnicalText

private val GO_INTENTS = listOf("0", "7", "15", "AUTO")

@Composable
fun TestSettingsScreen(
    identity: Identity,
    config: TestConfig,
    eventCount: Int,
    onChange: ((TestConfig) -> TestConfig) -> Unit,
    onMode: (NodeMode) -> Unit,
    onExport: suspend () -> String,
    onClearLog: () -> Unit,
    onBack: () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var askClear by rememberSaveable { mutableStateOf(false) }
    var askMode by rememberSaveable { mutableStateOf<NodeMode?>(null) }

    fun announce(text: String) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(text)
        }
    }

    fun apply(text: String, change: (TestConfig) -> TestConfig) {
        onChange(change)
        announce("Aplicado: $text")
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            MallaTopBar("Modo pruebas", onBack)
            Column(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = ScreenPadding, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    "Cambia el comportamiento de la red",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Block("Capa DTN") {
                    SegmentedToggle(
                        options = listOf("Propuesta (ACK + reintentos)", "Básica (sin ACK)"),
                        selected = DtnMode.entries.indexOf(config.dtnMode),
                        onSelect = { index ->
                            val mode = DtnMode.entries[index]
                            val name = if (mode == DtnMode.BASICO) "básica" else "propuesta"
                            apply("DTN $name") { it.copy(dtnMode = mode) }
                        },
                    )
                }
                Block("Corte de enlace (E2)") {
                    NumberField("Cortar tras N bytes (0 = apagado)", config.cutAfterBytes, 0..9_999) {
                        value -> onChange { it.copy(cutAfterBytes = value) }
                    }
                    Text(
                        "Se aplica a un solo contacto y vuelve a 0.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Block("Cola") {
                    SegmentedToggle(
                        options = listOf("Prioridad estricta", "Orden de llegada"),
                        selected = QueueMode.entries.indexOf(config.queueMode),
                        onSelect = { index ->
                            val mode = QueueMode.entries[index]
                            val name = if (mode == QueueMode.FIFO) "orden de llegada" else "prioridad"
                            apply("cola por $name") { it.copy(queueMode = mode) }
                        },
                    )
                }
                Block("Intent de Group Owner") {
                    SegmentedToggle(
                        options = listOf("0", "7", "15", "Auto"),
                        selected = GO_INTENTS.indexOf(config.goIntent).coerceAtLeast(3),
                        onSelect = { index ->
                            apply("intent ${GO_INTENTS[index]}") { it.copy(goIntent = GO_INTENTS[index]) }
                        },
                    )
                }
                Block("Generador de tráfico") {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .toggleable(
                                value = config.trafficEnabled,
                                role = Role.Switch,
                                onValueChange = { on ->
                                    val text = if (on) "generador activo" else "generador apagado"
                                    apply(text) { it.copy(trafficEnabled = on) }
                                },
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Mensajes normales", style = MaterialTheme.typography.bodyLarge)
                        Switch(checked = config.trafficEnabled, onCheckedChange = null)
                    }
                    NumberField("Cada (segundos)", config.trafficEverySeconds, 1..3_600) { value ->
                        onChange { it.copy(trafficEverySeconds = value) }
                    }
                    NumberField("Tamaño (bytes)", config.trafficSizeBytes, 0..255) { value ->
                        onChange { it.copy(trafficSizeBytes = value) }
                    }
                }
                Block("Registro") {
                    var label by rememberSaveable(config.runLabel) { mutableStateOf(config.runLabel) }
                    OutlinedTextField(
                        value = label,
                        onValueChange = { text ->
                            label = text
                            onChange { it.copy(runLabel = text.trim()) }
                        },
                        label = { Text("Etiqueta de corrida") },
                        placeholder = { Text("E2-propuesto-07") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(if (eventCount == 1) "1 evento" else "$eventCount eventos", style = TechnicalText)
                    PrimaryButton(
                        "Exportar CSV",
                        onClick = { scope.launch { announce(onExport()) } },
                        enabled = eventCount > 0,
                        icon = R.drawable.ic_open_in_new,
                    )
                    OutlinedButton(
                        onClick = { askClear = true },
                        enabled = eventCount > 0,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) { Text("Borrar registro") }
                }
                Block("Modo del teléfono") {
                    SegmentedToggle(
                        options = NodeMode.entries.map { it.label },
                        selected = NodeMode.entries.indexOf(identity.mode),
                        onSelect = { index ->
                            val mode = NodeMode.entries[index]
                            if (mode != identity.mode) askMode = mode
                        },
                    )
                }
                Block("Valores por defecto del SOS") {
                    NumberField("Tiempo de vida (horas)", config.ttlHours, 1..255) { value ->
                        onChange { it.copy(ttlHours = value) }
                    }
                    NumberField("Saltos", config.hops, 0..255) { value ->
                        onChange { it.copy(hops = value) }
                    }
                }
            }
        }
        SnackbarHost(
            snackbar,
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding(),
        )
    }

    if (askClear) {
        Confirm(
            title = "¿Borrar el registro?",
            text = "Se eliminarán $eventCount eventos. Exporta el CSV antes si aún lo necesitas.",
            confirm = "Borrar",
            onConfirm = {
                askClear = false
                onClearLog()
                announce("Registro borrado")
            },
            onDismiss = { askClear = false },
        )
    }
    askMode?.let { mode ->
        Confirm(
            title = "¿Cambiar a modo ${mode.label}?",
            text = if (mode == NodeMode.PUESTO) {
                "Este teléfono confirmará los SOS dirigidos a cualquier puesto."
            } else {
                "Este teléfono dejará de confirmar los SOS dirigidos a cualquier puesto."
            },
            confirm = "Cambiar",
            onConfirm = {
                askMode = null
                onMode(mode)
                announce("Aplicado: modo ${mode.label}")
            },
            onDismiss = { askMode = null },
        )
    }
}

@Composable
private fun Block(title: String, content: @Composable () -> Unit) {
    MallaCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle(title)
            content()
        }
    }
}

/** Solo aplica valores dentro de [range]; mientras tanto el campo se marca como erróneo. */
@Composable
private fun NumberField(label: String, value: Int, range: IntRange, onValue: (Int) -> Unit) {
    var text by rememberSaveable(value) { mutableStateOf(value.toString()) }
    val valid = text.toIntOrNull()?.let { it in range } == true
    OutlinedTextField(
        value = text,
        onValueChange = { input ->
            text = input.filter(Char::isDigit).take(4)
            text.toIntOrNull()?.takeIf { it in range }?.let(onValue)
        },
        label = { Text(label) },
        isError = !valid,
        supportingText = if (valid) null else ({ Text("De ${range.first} a ${range.last}") }),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun Confirm(
    title: String,
    text: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
