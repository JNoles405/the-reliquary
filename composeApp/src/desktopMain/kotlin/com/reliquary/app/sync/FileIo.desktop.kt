package com.reliquary.app.sync

import com.reliquary.app.data.desktopDataDir
import java.io.File

actual fun defaultSyncFilePath(): String =
    File(desktopDataDir().apply { if (!exists()) mkdirs() }, "reliquary-sync.json").absolutePath

actual fun writeTextFile(path: String, content: String) {
    File(path).writeText(content)
}

actual fun readTextFile(path: String): String? =
    File(path).takeIf { it.exists() }?.readText()

actual fun coversDir(): String =
    File(desktopDataDir(), "covers").apply { if (!exists()) mkdirs() }.absolutePath

actual fun writeBytesFile(path: String, bytes: ByteArray) {
    File(path).writeBytes(bytes)
}

actual fun listBackups(): List<BackupFile> =
    desktopDataDir().listFiles { f -> f.isFile && f.extension == "json" }
        ?.map { BackupFile(it.absolutePath, it.name, it.length(), it.lastModified()) }
        ?.sortedByDescending { it.modifiedAt }
        ?: emptyList()

actual fun deleteFile(path: String) {
    File(path).delete()
}
