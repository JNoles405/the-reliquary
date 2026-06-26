package com.reliquary.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.reliquary.app.data.createDesktopDriver
import com.reliquary.app.di.AppContainer
import com.reliquary.app.ui.theme.ReliquaryLogo
import com.reliquary.app.util.DesktopWindowHolder
import com.reliquary.app.util.WINDOW_MODE_SETTING

fun main() {
    val container = AppContainer(createDesktopDriver())
    val openFullscreen = container.repository.getSetting(WINDOW_MODE_SETTING) != "windowed"
    application {
        val state = rememberWindowState(
            placement = if (openFullscreen) WindowPlacement.Fullscreen else WindowPlacement.Floating,
            width = 1280.dp,
            height = 820.dp,
        )
        SideEffect { DesktopWindowHolder.state = state }
        Window(
            onCloseRequest = ::exitApplication,
            title = "The Reliquary",
            icon = rememberVectorPainter(ReliquaryLogo),
            state = state,
            undecorated = true,
            resizable = true,
        ) {
            Column(Modifier.fillMaxSize()) {
                if (state.placement != WindowPlacement.Fullscreen) {
                    ReliquaryTitleBar(state, onClose = ::exitApplication)
                }
                App(container)
            }
        }
    }
}
