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

actual fun coversDir(): String =
    File(AndroidAppContext.context.filesDir, "covers").apply { if (!exists()) mkdirs() }.absolutePath

actual fun writeBytesFile(path: String, bytes: ByteArray) {
    File(path).writeBytes(bytes)
}

actual fun listBackups(): List<BackupFile> {
    val dir = AndroidAppContext.context.getExternalFilesDir(null) ?: return emptyList()
    return dir.listFiles { f -> f.isFile && f.extension == "json" }
        ?.map { BackupFile(it.absolutePath, it.name, it.length(), it.lastModified()) }
        ?.sortedByDescending { it.modifiedAt }
        ?: emptyList()
}

actual fun deleteFile(path: String) {
    File(path).delete()
}
