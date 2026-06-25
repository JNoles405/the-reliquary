package com.reliquary.app.sync

import android.content.Context
import java.io.File

/**
 * Holds the application context for file access. Set once from MainActivity
 * before the app container is built.
 */
object AndroidAppContext {
    lateinit var context: Context
}

actual fun defaultSyncFilePath(): String {
    val dir = AndroidAppContext.context.getExternalFilesDir(null)
    return File(dir, "reliquary-sync.json").absolutePath
}

actual fun writeTextFile(path: String, content: String) {
    File(path).writeText(content)
}

actual fun readTextFile(path: String): String? =
    File(path).takeIf { it.exists() }?.readText()
