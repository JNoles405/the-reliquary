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
