package com.reliquary.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.style.LineHeightStyle

// Cinematic dark palette with a teal accent: near-black, faintly teal-tinted
// canvas and surfaces, with a vivid teal for accents and highlights.
val ReliquaryTeal = Color(0xFF14B8A6)
val ReliquaryBlack = Color(0xFF0E1413)
val ReliquarySurface = Color(0xFF16201E)
val ReliquarySurfaceVariant = Color(0xFF22302D)
val ReliquaryOnDark = Color(0xFFF2F5F4)
val ReliquaryMuted = Color(0xFF9DB2AD)

/** Linear blend between two opaque colors (t = 0 → base, t = 1 → tint). */
private fun mix(base: Color, tint: Color, t: Float): Color = Color(
    red = base.red + (tint.red - base.red) * t,
    green = base.green + (tint.green - base.green) * t,
    blue = base.blue + (tint.blue - base.blue) * t,
    alpha = 1f,
)

/**
 * Builds the whole dark palette from the chosen accent, so picking an accent
 * re-tints the entire app (canvas, surfaces, dividers) — not just a few
 * highlights. Darker neutrals carry a faint wash of the accent's hue.
 */
fun reliquaryColorScheme(accent: Color) = darkColorScheme(
    primary = accent,
    onPrimary = if (accent.luminance() > 0.55f) Color(0xFF0A0A0A) else Color.White,
    secondary = accent,
    onSecondary = if (accent.luminance() > 0.55f) Color(0xFF0A0A0A) else Color.White,
    background = mix(ReliquaryInk, accent, 0.05f),
    onBackground = ReliquaryOnDark,
    surface = mix(ReliquaryInk, accent, 0.10f),
    onSurface = ReliquaryOnDark,
    surfaceVariant = mix(ReliquaryInk, accent, 0.20f),
    onSurfaceVariant = ReliquaryMuted,
)

/** Neutral ink the accent is blended into for the dark canvas/surfaces. */
private val ReliquaryInk = Color(0xFF0B0E0D)

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
    // The Reliquary is always dark — the cinematic look is intentional. The accent
    // drives the whole palette (see reliquaryColorScheme).
    MaterialTheme(colorScheme = reliquaryColorScheme(accent)) {
        // Center text within its line box everywhere, so labels in pills, tags,
        // tabs and buttons sit optically centered instead of riding high.
        val centered = LocalTextStyle.current.copy(
            lineHeightStyle = LineHeightStyle(
                alignment = LineHeightStyle.Alignment.Center,
                trim = LineHeightStyle.Trim.Both,
            ),
        )
        CompositionLocalProvider(LocalTextStyle provides centered, content = content)
    }
}
