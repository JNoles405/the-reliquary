package com.reliquary.app.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp

/**
 * Let a child ignore [padding] of horizontal content-padding from its parent so
 * it spans edge to edge (e.g. a full-bleed hero inside a padded list).
 */
fun Modifier.edgeToEdgeHorizontal(padding: Dp): Modifier = this.layout { measurable, constraints ->
    val extra = padding.roundToPx() * 2
    val width = constraints.maxWidth + extra
    val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
    layout(placeable.width, placeable.height) { placeable.placeRelative(-padding.roundToPx(), 0) }
}
