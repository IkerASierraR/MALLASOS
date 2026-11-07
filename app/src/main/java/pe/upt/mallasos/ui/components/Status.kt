package pe.upt.mallasos.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import pe.upt.mallasos.R
import pe.upt.mallasos.ui.theme.malla

enum class ChipStatus(val label: String, @param:DrawableRes val icon: Int) {
    Pending("Pendiente", R.drawable.ic_schedule),
    Carried("En tránsito", R.drawable.ic_directions_walk),
    Delivered("Entregado", R.drawable.ic_check_circle),
    Attended("Atendido", R.drawable.ic_task_alt),
    Expired("Vencido", R.drawable.ic_hourglass_disabled),
}

/** Todo estado se muestra con icono, texto y color (R9). */
@Composable
fun StatusChip(status: ChipStatus, modifier: Modifier = Modifier) {
    val (content, container) = when (status) {
        ChipStatus.Pending -> malla.pending to malla.pendingContainer
        ChipStatus.Carried -> malla.carried to malla.carriedContainer
        ChipStatus.Delivered, ChipStatus.Attended -> malla.delivered to malla.deliveredContainer
        ChipStatus.Expired -> malla.neutral to malla.neutralContainer
    }
    Pill(status.label, status.icon, content, container, modifier)
}

@Composable
fun Pill(
    text: String,
    @DrawableRes icon: Int?,
    content: Color,
    container: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .background(container, CircleShape)
            .heightIn(min = 32.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) {
            Icon(painterResource(icon), null, Modifier.size(18.dp), tint = content)
        }
        Text(text, style = MaterialTheme.typography.labelMedium, color = content)
    }
}

/** Etiqueta roja del tipo SOS. */
@Composable
fun SosBadge(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(malla.sos, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("SOS", style = MaterialTheme.typography.labelLarge, color = malla.onSos)
    }
}
