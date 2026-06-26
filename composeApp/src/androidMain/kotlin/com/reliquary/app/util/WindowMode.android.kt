package com.reliquary.app.util

actual fun isDesktopPlatform(): Boolean = false

actual fun setFullscreen(fullscreen: Boolean) {
    // Android apps are always full-window; nothing to do.
}
