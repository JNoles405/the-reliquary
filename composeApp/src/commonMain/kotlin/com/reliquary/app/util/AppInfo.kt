package com.reliquary.app.util

object AppInfo {
    const val VERSION = "0.1.0"
    const val REPO_URL = "https://github.com/JNoles405/the-reliquary"
    const val RELEASES_URL = "$REPO_URL/releases"
}

/** Open a URL in the platform browser. */
expect fun openUrl(url: String)
