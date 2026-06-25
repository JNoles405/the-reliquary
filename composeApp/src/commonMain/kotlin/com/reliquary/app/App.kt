package com.reliquary.app

import androidx.compose.runtime.Composable
import com.reliquary.app.di.AppContainer
import com.reliquary.app.ui.ReliquaryApp
import com.reliquary.app.ui.theme.ReliquaryTheme

@Composable
fun App(container: AppContainer) {
    ReliquaryTheme {
        ReliquaryApp(container.repository)
    }
}
