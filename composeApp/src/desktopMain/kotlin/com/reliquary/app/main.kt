package com.reliquary.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.reliquary.app.data.createDesktopDriver
import com.reliquary.app.data.nowMillis
import com.reliquary.app.di.AppContainer
import com.reliquary.app.ui.CommandPalette
import com.reliquary.app.ui.theme.ReliquaryLogo
import com.reliquary.app.util.DesktopWindowHolder
import com.reliquary.app.util.WINDOW_MODE_SETTING
import com.reliquary.app.util.showDesktopNotification
import com.reliquary.app.util.workArea

fun main() {
    val container = AppContainer(createDesktopDriver())
    val openMaximized = container.repository.getSetting(WINDOW_MODE_SETTING) != "windowed"
    // Compute the work area (screen minus taskbar) up front so the window opens at
    // the right size. We size it to the work area as a normal floating window —
    // rather than WindowPlacement.Maximized, which (for an undecorated window)
    // covers the Windows taskbar.
    val wa = workArea()
    // Nudge about overdue loans at startup.
    val overdue = container.repository.activeLoansNow().count { it.isOverdue(nowMillis()) }
    if (overdue > 0) {
        showDesktopNotification(
            "The Reliquary — overdue loans",
            "$overdue ${if (overdue == 1) "item is" else "items are"} overdue. Open Loans to follow up.",
        )
    }
    application {
        val state = rememberWindowState(
            placement = WindowPlacement.Floating,
            position = if (openMaximized) WindowPosition(wa.x.dp, wa.y.dp) else WindowPosition(Alignment.Center),
            size = if (openMaximized) DpSize(wa.width.dp, wa.height.dp) else DpSize(1280.dp, 820.dp),
        )
        SideEffect { DesktopWindowHolder.state = state }
        Window(
            onCloseRequest = ::exitApplication,
            title = "The Reliquary",
            icon = rememberVectorPainter(ReliquaryLogo),
            state = state,
            undecorated = true,
            resizable = true,
            onKeyEvent = { e ->
                if (e.type == KeyEventType.KeyDown && e.isCtrlPressed && e.key == Key.K) {
                    CommandPalette.open = true
                    true
                } else {
                    false
                }
            },
        ) {
            Column(Modifier.fillMaxSize()) {
                // Always show the custom title bar so the window controls (incl. close)
                // are available even when maximized.
                ReliquaryTitleBar(state, onClose = ::exitApplication)
                App(container)
            }
        }
    }
}
