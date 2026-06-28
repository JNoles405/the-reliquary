package com.reliquary.app.util

import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import java.awt.GraphicsEnvironment
import java.awt.Rectangle

/** Holds the live WindowState so settings can flip the window mode at runtime. */
object DesktopWindowHolder {
    var state: WindowState? = null
}

/**
 * The desktop work area — the screen minus the taskbar — in logical pixels,
 * which equal Compose dp on desktop (density already folds in the OS scale).
 */
fun workArea(): Rectangle = GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds

/**
 * Fill the work area as a normal (floating) window so an undecorated window sits
 * ABOVE the Windows taskbar. We avoid WindowPlacement.Maximized because, for
 * undecorated windows, it covers the taskbar.
 */
fun WindowState.fillWorkArea() {
    val b = workArea()
    placement = WindowPlacement.Floating
    position = WindowPosition(b.x.dp, b.y.dp)
    size = DpSize(b.width.dp, b.height.dp)
}

/** Restore to a centered, smaller window. */
fun WindowState.restoreWindowed() {
    placement = WindowPlacement.Floating
    position = WindowPosition(Alignment.Center)
    size = DpSize(1280.dp, 820.dp)
}

/** True when the window currently fills (≈) the work area. */
fun WindowState.isFilling(): Boolean = size.width.value >= workArea().width - 8

actual fun isDesktopPlatform(): Boolean = true

actual fun setFullscreen(fullscreen: Boolean) {
    val s = DesktopWindowHolder.state ?: return
    if (fullscreen) s.fillWorkArea() else s.restoreWindowed()
}
