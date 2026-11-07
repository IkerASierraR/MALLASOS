package pe.upt.mallasos.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pe.upt.mallasos.R
import pe.upt.mallasos.codec.MessageType
import pe.upt.mallasos.codec.PayloadCodec
import pe.upt.mallasos.codec.SosPayload
import pe.upt.mallasos.data.db.MessageEntity
import pe.upt.mallasos.data.db.MessageState
import pe.upt.mallasos.ui.components.ChipStatus
import pe.upt.mallasos.ui.components.EmptyState
import pe.upt.mallasos.ui.components.MallaCard
import pe.upt.mallasos.ui.components.MallaTopBar
import pe.upt.mallasos.ui.components.Pill
import pe.upt.mallasos.ui.components.ScreenPadding
import pe.upt.mallasos.ui.components.SosBadge
import pe.upt.mallasos.ui.components.StatusChip
import pe.upt.mallasos.ui.theme.malla

private val MessageState.chip: ChipStatus
    get() = when (this) {
        MessageState.PENDING -> ChipStatus.Pending
        MessageState.CARRIED -> ChipStatus.Carried
        MessageState.DELIVERED -> ChipStatus.Delivered
        MessageState.EXPIRED -> ChipStatus.Expired
    }

/** Antigüedad en texto, por ejemplo "hace 3 min". */
fun timeAgo(fromMs: Long, nowMs: Long = System.currentTimeMillis()): String {
    val minutes = ((nowMs - fromMs) / 60_000).coerceAtLeast(0)
    return when {
        minutes < 1 -> "hace un momento"
        minutes < 60 -> "hace $minutes min"
        minutes < 1440 -> "hace ${minutes / 60} h"
        else -> "hace ${minutes / 1440} d"
    }
}

/**
 * [ordered] ya viene en el orden de salida de la cola; los mensajes cerrados van al final.
 */
@Composable
fun QueueScreen(ordered: List<MessageEntity>, onBack: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var filter by rememberSaveable { mutableStateOf<MessageState?>(null) }
    val forTab = ordered.filter { it.isOwn == (tab == 0) }
    val nextUid = forTab.firstOrNull {
        it.state == MessageState.PENDING || it.state == MessageState.CARRIED
    }?.uid
    val visible = forTab.filter { filter == null || it.state == filter }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        MallaTopBar("Mis mensajes", onBack)
        PrimaryTabRow(selectedTabIndex = tab) {
            listOf("Míos", "Llevando").forEachIndexed { index, title ->
                Tab(
                    selected = tab == index,
                    onClick = { tab = index },
                    text = { Text(title, style = MaterialTheme.typography.titleSmall) },
                )
            }
        }
        Row(
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = ScreenPadding, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MessageState.entries.forEach { state ->
                FilterChip(
                    selected = filter == state,
                    onClick = { filter = if (filter == state) null else state },
                    label = { Text(state.chip.label) },
                    leadingIcon = {
                        Icon(painterResource(state.chip.icon), null, Modifier.size(18.dp))
                    },
                )
            }
        }
        if (visible.isEmpty()) {
            EmptyState(
                icon = R.drawable.ic_inbox,
                title = if (tab == 0) "No has enviado SOS" else "No llevas mensajes de otros",
                help = "Aquí verás qué pasó con cada mensaje.",
            )
        } else {
            LazyColumn(
                Modifier
                    .fillMaxSize()
                    .navigationBarsPadding(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = ScreenPadding,
                    end = ScreenPadding,
                    bottom = 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(visible, key = { it.uid }) { message ->
                    MessageCard(message, isNext = message.uid == nextUid)
                }
            }
        }
    }
}

@Composable
private fun MessageCard(message: MessageEntity, isNext: Boolean) {
    val sos: SosPayload? = if (message.type == MessageType.SOS.code) {
        PayloadCodec.decodeSos(message.payload)
    } else {
        null
    }
    MallaCard(
        Modifier.fillMaxWidth(),
        border = when {
            !isNext -> malla.cardBorder
            sos != null -> malla.sos
            else -> MaterialTheme.colorScheme.primary
        },
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (isNext) {
                Pill(
                    "Siguiente en salir",
                    R.drawable.ic_bolt,
                    MaterialTheme.colorScheme.onPrimaryContainer,
                    MaterialTheme.colorScheme.primaryContainer,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (sos != null) {
                    SosBadge()
                    Text(
                        "${sos.emergency.label} · ${sos.people} " +
                            if (sos.people == 1) "persona" else "personas",
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Pill("Prueba", null, malla.neutral, malla.neutralContainer)
                    Text(
                        "Mensaje normal",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                }
                StatusChip(message.state.chip)
            }
            if (sos != null) {
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
                if (sos.message.isNotEmpty()) {
                    Text(
                        sos.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                "${timeAgo(message.createdAt)} · Saltos restantes: ${message.hopsLeft}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
