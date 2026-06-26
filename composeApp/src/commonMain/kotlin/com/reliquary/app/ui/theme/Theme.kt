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

@Composable
fun ReliquaryTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    // The Reliquary is always dark — the cinematic look is intentional.
    MaterialTheme(
        colorScheme = ReliquaryColors,
        content = content,
    )
}
