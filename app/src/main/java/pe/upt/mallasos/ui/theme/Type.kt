package pe.upt.mallasos.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import pe.upt.mallasos.R

private fun jost(weight: FontWeight) = Font(
    resId = R.font.jost,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

/** Jost va empaquetada en el APK (licencia OFL), por lo que no depende de Internet. */
val Jost = FontFamily(
    jost(FontWeight.Normal),
    jost(FontWeight.Medium),
    jost(FontWeight.SemiBold),
    jost(FontWeight.Bold),
)

private fun style(size: Int, line: Int, weight: FontWeight) = TextStyle(
    fontFamily = Jost,
    fontSize = size.sp,
    lineHeight = line.sp,
    fontWeight = weight,
)

val MallaTypography = Typography(
    headlineLarge = style(32, 40, FontWeight.Bold),
    headlineMedium = style(28, 36, FontWeight.SemiBold),
    headlineSmall = style(24, 32, FontWeight.SemiBold),
    titleLarge = style(22, 30, FontWeight.SemiBold),
    titleMedium = style(18, 26, FontWeight.SemiBold),
    titleSmall = style(16, 24, FontWeight.SemiBold),
    bodyLarge = style(16, 24, FontWeight.Normal),
    bodyMedium = style(15, 22, FontWeight.Normal),
    bodySmall = style(13, 19, FontWeight.Normal),
    labelLarge = style(14, 20, FontWeight.Medium),
    labelMedium = style(13, 18, FontWeight.Medium),
    labelSmall = style(12, 17, FontWeight.Medium),
)

/** Texto del botón SOS (design.md §2). */
val SosButtonText = style(28, 36, FontWeight.Bold)

/** Datos técnicos: identificadores y contadores. */
val TechnicalText = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 13.sp,
    lineHeight = 18.sp,
)
