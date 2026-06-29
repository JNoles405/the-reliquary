package com.reliquary.app.util

import com.reliquary.app.data.desktopDataDir
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

actual fun pickAndStoreImage(): String? {
    val dialog = FileDialog(null as Frame?, "Choose an image", FileDialog.LOAD)
    dialog.setFilenameFilter { _, name ->
        name.lowercase().matches(Regex(".*\\.(png|jpg|jpeg|gif|webp|bmp)"))
    }
    dialog.isVisible = true
    val dir = dialog.directory
    val name = dialog.file
    if (dir == null || name == null) return null
    return runCatching {
        val src = File(dir, name)
        val photos = File(desktopDataDir(), "photos").apply { if (!exists()) mkdirs() }
        val ext = src.extension.ifBlank { "img" }
        val dest = File(photos, "${System.nanoTime()}.$ext")
        src.copyTo(dest, overwrite = true)
        dest.absolutePath
    }.getOrNull()
}
