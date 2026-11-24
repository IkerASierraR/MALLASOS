package pe.upt.mallasos.ui.screens

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import pe.upt.mallasos.R
import pe.upt.mallasos.codec.PayloadCodec
import pe.upt.mallasos.data.db.MessageEntity
import pe.upt.mallasos.domain.postPanelItems
import pe.upt.mallasos.ui.components.ChipStatus
import pe.upt.mallasos.ui.components.EmptyState
import pe.upt.mallasos.ui.components.MallaCard
import pe.upt.mallasos.ui.components.MallaTopBar
import pe.upt.mallasos.ui.components.Pill
import pe.upt.mallasos.ui.components.ScreenPadding
import pe.upt.mallasos.ui.components.SosBadge
import pe.upt.mallasos.ui.components.StatusChip
import pe.upt.mallasos.ui.theme.malla

/** Texto de la cantidad de personas, común a las tarjetas de SOS. */
fun peopleLabel(people: Int): String = if (people == 1) "1 persona" else "$people personas"

@Composable
fun PostPanelScreen(
    messages: List<MessageEntity>,
    aliasOf: (Int) -> String,
    onOpen: (String) -> Unit,
    onAttended: (uid: String, attended: Boolean) -> Unit,
    onBack: () -> Unit,
) {
    val items = postPanelItems(messages)
    val pending = items.count { !it.attended }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        MallaTopBar("Panel del Rescatista", onBack)
        NewSosAlert(items.size)
        Text(
            "${items.size} SOS · $pending sin atender",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = ScreenPadding, vertical = 8.dp),
        )
        if (items.isEmpty()) {
            EmptyState(
                icon = R.drawable.ic_apartment,
                title = "Sin SOS recibidos",
                help = "La red está escuchando.",
            )
        } else {
            LazyColumn(
                Modifier
                    .fillMaxSize()
                    .navigationBarsPadding(),
                contentPadding = PaddingValues(
                    start = ScreenPadding,
                    end = ScreenPadding,
                    bottom = 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(items, key = { it.uid }) { message ->
                    PostCard(message, aliasOf, onOpen, onAttended)
                }
            }
        }
    }
}

/** Con la pantalla abierta, un SOS nuevo vibra y muestra un aviso (`design.md` §4.5). */
@Composable
private fun NewSosAlert(count: Int) {
    val context = LocalContext.current
    var seen by rememberSaveable { mutableIntStateOf(count) }
    var showAlert by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(count) {
        if (count > seen) {
            showAlert = true
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            vibrator.vibrate(VibrationEffect.createOneShot(400, VibrationEffect.DEFAULT_AMPLITUDE))
        }
        seen = count
    }
    if (showAlert) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(malla.sosContainer)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SosBadge()
            Text(
                "SOS nuevo recibido",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = { showAlert = false }) { Text("Visto") }
        }
    }
}

@Composable
private fun PostCard(
    message: MessageEntity,
    aliasOf: (Int) -> String,
    onOpen: (String) -> Unit,
    onAttended: (uid: String, attended: Boolean) -> Unit,
) {
    val sos = PayloadCodec.decodeSos(message.payload)
    MallaCard(
        Modifier.fillMaxWidth(),
        onClick = { onOpen(message.uid) },
        border = if (message.attended) malla.cardBorder else MaterialTheme.colorScheme.primary,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SosBadge()
                Text(
                    "${sos.emergency.label} · ${peopleLabel(sos.people)}",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                if (message.attended) StatusChip(ChipStatus.Attended)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    painterResource(R.drawable.ic_location_on),
                    null,
                    Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(sos.locationText, style = MaterialTheme.typography.bodyMedium)
            }
            sos.position?.let {
                Text(
                    "GPS: %.5f, %.5f".format(it.latitude, it.longitude),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "De ${aliasOf(message.originId)} · Saltos restantes: ${message.hopsLeft} · " +
                    "Recibido ${timeAgo(message.enqueuedAt)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Pill("Confirmado ✓", null, malla.delivered, malla.deliveredContainer)
                OutlinedButton(onClick = { onAttended(message.uid, !message.attended) }) {
                    Text(if (message.attended) "Deshacer" else "Marcar atendido")
                }
            }
        }
    }
}
