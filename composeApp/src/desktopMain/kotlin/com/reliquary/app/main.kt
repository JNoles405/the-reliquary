package com.reliquary.app

import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.reliquary.app.data.createDesktopDriver
import com.reliquary.app.di.AppContainer
import com.reliquary.app.ui.theme.ReliquaryLogo

fun main() {
    val container = AppContainer(createDesktopDriver())
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "The Reliquary",
            icon = rememberVectorPainter(ReliquaryLogo),
            state = rememberWindowState(width = 1280.dp, height = 820.dp),
        ) {
            App(container)
        }
    }
}
