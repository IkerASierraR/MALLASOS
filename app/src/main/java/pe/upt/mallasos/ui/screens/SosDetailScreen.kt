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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import pe.upt.mallasos.R
import pe.upt.mallasos.codec.ANY_POST
import pe.upt.mallasos.codec.MessageType
import pe.upt.mallasos.codec.PayloadCodec
import pe.upt.mallasos.data.db.MessageEntity
import pe.upt.mallasos.domain.TimelineEntry
import pe.upt.mallasos.ui.components.ChipStatus
import pe.upt.mallasos.ui.components.EmptyState
import pe.upt.mallasos.ui.components.MallaCard
import pe.upt.mallasos.ui.components.MallaTopBar
import pe.upt.mallasos.ui.components.Pill
import pe.upt.mallasos.ui.components.PrimaryButton
import pe.upt.mallasos.ui.components.ScreenPadding
import pe.upt.mallasos.ui.components.SectionTitle
import pe.upt.mallasos.ui.components.SosBadge
import pe.upt.mallasos.ui.components.StatusChip
import pe.upt.mallasos.ui.theme.TechnicalText
import pe.upt.mallasos.ui.theme.malla

private val clockFormat = DateTimeFormatter.ofPattern("HH:mm:ss")
private val dateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")

private fun Long.formatted(format: DateTimeFormatter): String =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).format(format)

private fun ttlLeft(message: MessageEntity): String {
    val left = message.createdAt + message.ttlHours * 3_600_000L - System.currentTimeMillis()
    return when {
        left <= 0 -> "Vencido"
        left < 3_600_000L -> "${left / 60_000} min"
        else -> "${left / 3_600_000L} h"
    }
}

/** [message] es `null` si la copia se purgó al llegar el ACK final. */
@Composable
fun SosDetailScreen(
    message: MessageEntity?,
    timeline: List<TimelineEntry>,
    aliasOf: (Int) -> String,
    canAttend: Boolean,
    onAttended: (uid: String, attended: Boolean) -> Unit,
    onBack: () -> Unit,
    onSendLora: ((MessageEntity) -> Unit)? = null,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        MallaTopBar("Detalle del SOS", onBack)
        if (message == null) {
            EmptyState(
                icon = R.drawable.ic_task_alt,
                title = "Copia borrada",
                help = "El mensaje ya se entregó y este teléfono no guarda su copia.",
            )
            return@Column
        }
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ScreenPadding, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Header(message)
            Data(message, aliasOf)
            SectionTitle("Recorrido")
            Timeline(timeline)
            if (canAttend) {
                PrimaryButton(
                    text = if (message.attended) "Deshacer atendido" else "Marcar atendido",
                    onClick = { onAttended(message.uid, !message.attended) },
                    icon = R.drawable.ic_task_alt,
                )
            }
            if (onSendLora != null && message.type == MessageType.SOS.code) {
                OutlinedButton(
                    onClick = { onSendLora(message) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) { Text("Reenviar por LoRa al puesto") }
            }
            Text(
                "${message.uid.take(8)}·${message.uid.drop(8)}",
                style = TechnicalText,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Header(message: MessageEntity) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (message.type == MessageType.SOS.code) {
            SosBadge()
            Text(
                PayloadCodec.decodeSos(message.payload).emergency.label,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
        } else {
            Pill("Prueba", null, malla.neutral, malla.neutralContainer)
            Text(
                "Mensaje normal",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
        }
        StatusChip(if (message.attended) ChipStatus.Attended else message.state.chip)
    }
}

@Composable
private fun Data(message: MessageEntity, aliasOf: (Int) -> String) {
    MallaCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (message.type == MessageType.SOS.code) {
                val sos = PayloadCodec.decodeSos(message.payload)
                DataRow("Personas", sos.people.toString())
                DataRow("Ubicación", sos.locationText)
                DataRow(
                    "GPS",
                    sos.position?.let { "%.5f, %.5f".format(it.latitude, it.longitude) }
                        ?: "Sin posición",
                )
                DataRow("Batería", "${sos.batteryPercent} %")
                if (sos.message.isNotEmpty()) DataRow("Mensaje", sos.message)
            }
            DataRow("Origen", aliasOf(message.originId))
            DataRow(
                "Destino",
                if (message.destId == ANY_POST) "Cualquier rescatista" else aliasOf(message.destId),
            )
            DataRow("Creado", message.createdAt.formatted(dateFormat))
            DataRow("Tiempo de vida", ttlLeft(message))
            DataRow("Saltos restantes", message.hopsLeft.toString())
        }
    }
}

@Composable
private fun DataRow(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.4f),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.6f))
    }
}

@Composable
private fun Timeline(entries: List<TimelineEntry>) {
    if (entries.isEmpty()) {
        Text(
            "Sin eventos registrados",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        entries.forEach { entry ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    Modifier
                        .size(10.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                )
                Text(
                    entry.label,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    entry.tsMs.formatted(clockFormat),
                    style = TechnicalText,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
