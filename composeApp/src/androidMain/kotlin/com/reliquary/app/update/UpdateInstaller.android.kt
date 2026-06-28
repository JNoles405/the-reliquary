package com.reliquary.app.update

import com.reliquary.app.util.AppInfo
import com.reliquary.app.util.openUrl

// Android updates go through normal distribution (sideloaded APK / store), so we
// just send the user to the releases page rather than self-installing.
actual fun isAutoUpdateSupported(): Boolean = false

actual suspend fun downloadAndInstallUpdate(url: String, onProgress: (Float) -> Unit): String? {
    openUrl(url.ifBlank { AppInfo.RELEASES_URL })
    return null
}
