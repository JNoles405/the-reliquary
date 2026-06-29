package com.reliquary.app.util

/** Show a lightweight OS notification (desktop tray balloon); no-op where unsupported. */
expect fun showDesktopNotification(title: String, message: String)
