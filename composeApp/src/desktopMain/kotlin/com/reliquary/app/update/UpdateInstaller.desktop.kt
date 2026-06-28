package com.reliquary.app.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.system.exitProcess

actual fun isAutoUpdateSupported(): Boolean = true

actual suspend fun downloadAndInstallUpdate(url: String, onProgress: (Float) -> Unit): String? =
    withContext(Dispatchers.IO) {
        try {
            val target = File(System.getProperty("java.io.tmpdir"), "TheReliquary-update.msi")
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 30_000
                readTimeout = 60_000
            }
            conn.connect()
            val total = conn.contentLengthLong
            conn.inputStream.use { input ->
                target.outputStream().use { out ->
                    val buf = ByteArray(1 shl 16)
                    var read = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        read += n
                        if (total > 0) onProgress((read.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
            onProgress(1f)
            // Launch the MSI with a progress-only UI. The shared upgrade GUID makes
            // it replace the installed version in place; then quit so files unlock.
            ProcessBuilder("msiexec", "/i", target.absolutePath, "/passive").start()
            exitProcess(0)
        } catch (e: Throwable) {
            "Update failed: ${e.message ?: "download error"}"
        }
    }
