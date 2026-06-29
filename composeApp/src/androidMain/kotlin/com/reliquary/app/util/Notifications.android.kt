package com.reliquary.app.util

// Android notifications need a channel + runtime permission; left as a no-op for now.
actual fun showDesktopNotification(title: String, message: String) {}
