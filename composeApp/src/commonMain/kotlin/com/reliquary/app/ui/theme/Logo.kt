package com.reliquary.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** The Reliquary mark — a red gem with a white facet. Used as the desktop window icon. */
val ReliquaryLogo: ImageVector by lazy {
    ImageVector.Builder(
        name = "ReliquaryLogo",
        defaultWidth = 108.dp,
        defaultHeight = 108.dp,
        viewportWidth = 108f,
        viewportHeight = 108f,
    ).apply {
        path(fill = SolidColor(Color(0xFFE50914))) {
            moveTo(54f, 16f); lineTo(92f, 54f); lineTo(54f, 92f); lineTo(16f, 54f); close()
        }
        path(fill = SolidColor(Color(0xFFFFFFFF))) {
            moveTo(54f, 36f); lineTo(72f, 54f); lineTo(54f, 78f); lineTo(36f, 54f); close()
        }
    }.build()
}
