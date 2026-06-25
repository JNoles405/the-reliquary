package com.reliquary.app

import androidx.compose.runtime.Composable
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.crossfade
import com.reliquary.app.di.AppContainer
import com.reliquary.app.ui.ReliquaryApp
import com.reliquary.app.ui.theme.ReliquaryTheme

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
    ReliquaryTheme {
        ReliquaryApp(container)
    }
}
