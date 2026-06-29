package com.reliquary.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.crossfade
import com.reliquary.app.data.ReliquaryRepository
import com.reliquary.app.di.AppContainer
import com.reliquary.app.ui.ReliquaryApp
import com.reliquary.app.ui.theme.ReliquaryTheme
import com.reliquary.app.ui.theme.accentFromHex

const val ACCENT_SETTING = "ui.accent"
const val THEME_SETTING = "ui.theme"
const val TEXT_SCALE_SETTING = "ui.textScale"

/** Live UI preferences (accent, dark/light, text scale), backed by settings. */
object UiPrefs {
    var accentHex by mutableStateOf<String?>(null)
    var dark by mutableStateOf(true)
    var textScale by mutableStateOf(1f)
    private var loaded = false

    fun ensureLoaded(repository: ReliquaryRepository) {
        if (loaded) return
        accentHex = repository.getSetting(ACCENT_SETTING)
        dark = repository.getSetting(THEME_SETTING) != "light"
        textScale = repository.getSetting(TEXT_SCALE_SETTING)?.toFloatOrNull() ?: 1f
        loaded = true
    }
}

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
    remember { UiPrefs.ensureLoaded(container.repository) }

    ReliquaryTheme(accent = accentFromHex(UiPrefs.accentHex), dark = UiPrefs.dark) {
        val base = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(base.density, UiPrefs.textScale)) {
            ReliquaryApp(
                container = container,
                onAccentChange = { hex ->
                    UiPrefs.accentHex = hex
                    container.repository.setSetting(ACCENT_SETTING, hex)
                },
            )
        }
    }
}
