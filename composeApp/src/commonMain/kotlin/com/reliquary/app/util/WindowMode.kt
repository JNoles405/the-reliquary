package com.reliquary.app.util

/** Setting key for desktop window mode ("windowed" = floating; anything else = fullscreen). */
const val WINDOW_MODE_SETTING = "ui.windowMode"

/** True on the desktop target (used to show desktop-only settings). */
expect fun isDesktopPlatform(): Boolean

/** Apply the window mode live on desktop; no-op on Android. */
expect fun setFullscreen(fullscreen: Boolean)
