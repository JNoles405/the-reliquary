package com.reliquary.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Netflix-inspired palette: near-black canvas, signature red accent.
val ReliquaryRed = Color(0xFFE50914)
val ReliquaryBlack = Color(0xFF141414)
val ReliquarySurface = Color(0xFF1F1F1F)
val ReliquarySurfaceVariant = Color(0xFF2A2A2A)
val ReliquaryOnDark = Color(0xFFF5F5F5)
val ReliquaryMuted = Color(0xFFB3B3B3)

private val ReliquaryColors = darkColorScheme(
    primary = ReliquaryRed,
    onPrimary = ReliquaryOnDark,
    secondary = ReliquaryRed,
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
