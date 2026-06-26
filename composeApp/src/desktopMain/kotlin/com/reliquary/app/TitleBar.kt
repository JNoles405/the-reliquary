package com.reliquary.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import com.reliquary.app.ui.theme.ReliquaryBlack
import com.reliquary.app.ui.theme.ReliquaryMuted
import com.reliquary.app.ui.theme.ReliquaryOnDark
import com.reliquary.app.ui.theme.ReliquaryTeal
import java.awt.MouseInfo

/** A dark, themed replacement for the native window title bar (undecorated window). */
@Composable
fun FrameWindowScope.ReliquaryTitleBar(state: WindowState, onClose: () -> Unit) {
    val composeWindow = window
    Row(
        Modifier
            .fillMaxWidth()
            .height(36.dp)
            .background(ReliquaryBlack)
            // Drag the window by tracking absolute mouse movement (no WindowDraggableArea in CMP 1.7).
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    if (state.placement == WindowPlacement.Maximized) return@awaitEachGesture
                    val startMouse = MouseInfo.getPointerInfo().location
                    val startX = composeWindow.x
                    val startY = composeWindow.y
                    drag(down.id) {
                        val now = MouseInfo.getPointerInfo().location
                        composeWindow.setLocation(startX + (now.x - startMouse.x), startY + (now.y - startMouse.y))
                    }
                }
            }
            .padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(12.dp).background(ReliquaryTeal))
        Spacer(Modifier.width(8.dp))
        Text("The Reliquary", color = ReliquaryOnDark, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.weight(1f))
        CaptionButton(Icons.Filled.Remove) { state.isMinimized = true }
        CaptionButton(Icons.Filled.CropSquare) {
            state.placement = if (state.placement == WindowPlacement.Maximized) {
                WindowPlacement.Floating
            } else {
                WindowPlacement.Maximized
            }
        }
        CaptionButton(Icons.Filled.Close, hoverClose = true, onClick = onClose)
    }
}

@Composable
private fun CaptionButton(icon: ImageVector, hoverClose: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxHeight().width(46.dp).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (hoverClose) Color(0xFFE57373) else ReliquaryMuted,
            modifier = Modifier.size(16.dp),
        )
    }
}
