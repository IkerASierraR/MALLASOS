package pe.upt.mallasos.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Tokens de `design.md` §2 que Material 3 no cubre. El rojo `sos` es exclusivo de elementos SOS (R9). */
@Immutable
data class MallaColors(
    val sos: Color,
    val onSos: Color,
    val sosContainer: Color,
    val pending: Color,
    val pendingContainer: Color,
    val delivered: Color,
    val deliveredContainer: Color,
    val carried: Color,
    val carriedContainer: Color,
    val neutral: Color,
    val neutralContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val cardBorder: Color,
)

val LightMallaColors = MallaColors(
    sos = Color(0xFFC62828),
    onSos = Color(0xFFFFFFFF),
    sosContainer = Color(0xFFFCE8E6),
    pending = Color(0xFF8A5300),
    pendingContainer = Color(0xFFFFF0D4),
    delivered = Color(0xFF2E7D32),
    deliveredContainer = Color(0xFFE8F5E9),
    carried = Color(0xFF3D5A80),
    carriedContainer = Color(0xFFE2E9F3),
    neutral = Color(0xFF5F6368),
    neutralContainer = Color(0xFFF1F3F4),
    warning = Color(0xFF8A5300),
    warningContainer = Color(0xFFFFF0D4),
    cardBorder = Color(0xFFDCE2EA),
)

val DarkMallaColors = MallaColors(
    sos = Color(0xFFFF6B66),
    onSos = Color(0xFF3B0000),
    sosContainer = Color(0xFF4A1412),
    pending = Color(0xFFFFC56B),
    pendingContainer = Color(0xFF3D2A0A),
    delivered = Color(0xFF7FD48A),
    deliveredContainer = Color(0xFF17331B),
    carried = Color(0xFFA9C1E8),
    carriedContainer = Color(0xFF23304A),
    neutral = Color(0xFFC4C7CC),
    neutralContainer = Color(0xFF2A2E33),
    warning = Color(0xFFFFC56B),
    warningContainer = Color(0xFF3D2A0A),
    cardBorder = Color(0xFF343A42),
)

val LocalMallaColors = staticCompositionLocalOf { LightMallaColors }

// El slot `error` de Material 3 usa ámbar: los errores nunca son rojos (design.md §1.2).
val LightScheme: ColorScheme = lightColorScheme(
    primary = Color(0xFF1F4E79),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD1E4FF),
    onPrimaryContainer = Color(0xFF001D35),
    secondary = Color(0xFF3D5A80),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD3E3FF),
    onSecondaryContainer = Color(0xFF001C39),
    background = Color(0xFFFAFAFC),
    onBackground = Color(0xFF181C20),
    surface = Color(0xFFFAFAFC),
    onSurface = Color(0xFF181C20),
    surfaceVariant = Color(0xFFEEF1F5),
    onSurfaceVariant = Color(0xFF42474F),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF4F6F9),
    surfaceContainer = Color(0xFFEEF1F5),
    surfaceContainerHigh = Color(0xFFE5E8EE),
    outline = Color(0xFF72777F),
    outlineVariant = Color(0xFFC2C7D0),
    error = Color(0xFF8A5300),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFF0D4),
    onErrorContainer = Color(0xFF8A5300),
)

val DarkScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFF9CC3EE),
    onPrimary = Color(0xFF0B2540),
    primaryContainer = Color(0xFF1F4E79),
    onPrimaryContainer = Color(0xFFD1E4FF),
    secondary = Color(0xFFA9C1E8),
    onSecondary = Color(0xFF0B2540),
    secondaryContainer = Color(0xFF2A486D),
    onSecondaryContainer = Color(0xFFD3E3FF),
    background = Color(0xFF121417),
    onBackground = Color(0xFFE2E4E8),
    surface = Color(0xFF121417),
    onSurface = Color(0xFFE2E4E8),
    surfaceVariant = Color(0xFF1E2227),
    onSurfaceVariant = Color(0xFFC4C7CC),
    surfaceContainerLowest = Color(0xFF0D0F12),
    surfaceContainerLow = Color(0xFF191C20),
    surfaceContainer = Color(0xFF1E2227),
    surfaceContainerHigh = Color(0xFF282C32),
    outline = Color(0xFF8C9199),
    outlineVariant = Color(0xFF42474F),
    error = Color(0xFFFFC56B),
    onError = Color(0xFF3D2A0A),
    errorContainer = Color(0xFF3D2A0A),
    onErrorContainer = Color(0xFFFFC56B),
)
