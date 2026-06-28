package com.reliquary.app.update

/** True where in-app download-and-install is supported (desktop/Windows). */
expect fun isAutoUpdateSupported(): Boolean

/**
 * Download the installer at [url] and launch it, then quit the app so it can
 * upgrade in place. Reports download progress 0f..1f via [onProgress].
 *
 * On success this does not return (the process exits). On failure it returns a
 * short error message. Where auto-install isn't supported, this opens the
 * releases page in the browser and returns null.
 */
expect suspend fun downloadAndInstallUpdate(url: String, onProgress: (Float) -> Unit): String?
