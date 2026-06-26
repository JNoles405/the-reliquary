package com.reliquary.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.crossfade
import com.reliquary.app.di.AppContainer
import com.reliquary.app.ui.ReliquaryApp
import com.reliquary.app.ui.theme.ReliquaryTheme
import com.reliquary.app.ui.theme.accentFromHex

const val ACCENT_SETTING = "ui.accent"

@Composable
fun App(container: AppContainer) {
    // Coil needs an explicit network fetcher on non-Android targets; wire it to
    // our shared Ktor client so cover art loads on both Windows and Android.
    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context)
            .components { add(KtorNetworkFetcherFactory()) }
            .crossfade(true)
            .build()
    }
    var accentHex by remember { mutableStateOf(container.repository.getSetting(ACCENT_SETTING)) }
    ReliquaryTheme(accent = accentFromHex(accentHex)) {
        ReliquaryApp(
            container = container,
            onAccentChange = { hex ->
                accentHex = hex
                container.repository.setSetting(ACCENT_SETTING, hex)
            },
        )
    }
}
