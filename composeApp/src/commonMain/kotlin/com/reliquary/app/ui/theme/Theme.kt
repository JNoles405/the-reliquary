package com.reliquary.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Cinematic dark palette with a teal accent: near-black, faintly teal-tinted
// canvas and surfaces, with a vivid teal for accents and highlights.
val ReliquaryTeal = Color(0xFF14B8A6)
val ReliquaryBlack = Color(0xFF0E1413)
val ReliquarySurface = Color(0xFF16201E)
val ReliquarySurfaceVariant = Color(0xFF22302D)
val ReliquaryOnDark = Color(0xFFF2F5F4)
val ReliquaryMuted = Color(0xFF9DB2AD)

private val ReliquaryColors = darkColorScheme(
    primary = ReliquaryTeal,
    onPrimary = Color(0xFF00201C),
    secondary = ReliquaryTeal,
    background = ReliquaryBlack,
    onBackground = ReliquaryOnDark,
    surface = ReliquarySurface,
    onSurface = ReliquaryOnDark,
    surfaceVariant = ReliquarySurfaceVariant,
    onSurfaceVariant = ReliquaryMuted,
)

/** Selectable accent swatches for the theme picker. */
data class AccentOption(val name: String, val color: Color)

val ACCENTS: List<AccentOption> = listOf(
    AccentOption("Teal", ReliquaryTeal),
    AccentOption("Crimson", Color(0xFFE50914)),
    AccentOption("Blue", Color(0xFF3B82F6)),
    AccentOption("Violet", Color(0xFF8B5CF6)),
    AccentOption("Green", Color(0xFF22C55E)),
    AccentOption("Amber", Color(0xFFF59E0B)),
    AccentOption("Pink", Color(0xFFEC4899)),
)

/** Six-digit RGB hex (e.g. "14B8A6") for persisting an accent choice. */
fun Color.toRgbHex(): String {
    fun channel(v: Float) = (v * 255f).toInt().coerceIn(0, 255).toString(16).padStart(2, '0')
    return (channel(red) + channel(green) + channel(blue)).uppercase()
}

fun accentFromHex(hex: String?): Color =
    hex?.let { runCatching { Color(("FF$it").toLong(16)) }.getOrNull() } ?: ReliquaryTeal

@Composable
fun ReliquaryTheme(
    accent: Color = ReliquaryTeal,
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    // The Reliquary is always dark — the cinematic look is intentional. Only the
    // accent (primary) is user-configurable.
    MaterialTheme(
        colorScheme = ReliquaryColors.copy(primary = accent, secondary = accent),
        content = content,
    )
}
