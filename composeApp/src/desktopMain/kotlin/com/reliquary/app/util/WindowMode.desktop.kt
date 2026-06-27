package com.reliquary.app.util

import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState

/** Holds the live WindowState so settings can flip fullscreen at runtime. */
object DesktopWindowHolder {
    var state: WindowState? = null
}

actual fun isDesktopPlatform(): Boolean = true

actual fun setFullscreen(fullscreen: Boolean) {
    // "fullscreen" here means the big mode: maximized to the work area so the
    // window stays above the Windows taskbar rather than covering it.
    DesktopWindowHolder.state?.placement =
        if (fullscreen) WindowPlacement.Maximized else WindowPlacement.Floating
}
