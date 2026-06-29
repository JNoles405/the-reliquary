package com.reliquary.app.util

object AppInfo {
    // Version format AA.BB.CC — AA: major, BB: feature release, CC: small change.
    // This is the single source of truth; the Gradle build derives the Android
    // versionName/versionCode and the desktop installer version from it.
    const val VERSION = "1.0.23"
    const val REPO_URL = "https://github.com/JNoles405/the-reliquary"
    const val RELEASES_URL = "$REPO_URL/releases"
}

/** Open a URL in the platform browser. */
expect fun openUrl(url: String)
