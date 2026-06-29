package com.reliquary.app.util

import java.awt.Color
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.image.BufferedImage

actual fun showDesktopNotification(title: String, message: String) {
    if (!SystemTray.isSupported()) return
    runCatching {
        val tray = SystemTray.getSystemTray()
        val img = BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB)
        img.createGraphics().apply {
            color = Color(0x14, 0xB8, 0xA6)
            fillOval(2, 2, 12, 12)
            dispose()
        }
        val icon = TrayIcon(img, "The Reliquary").apply { isImageAutoSize = true }
        tray.add(icon)
        icon.displayMessage(title, message, TrayIcon.MessageType.INFO)
        // Remove shortly after so we don't leave a persistent tray icon.
        Thread {
            runCatching { Thread.sleep(12_000) }
            runCatching { tray.remove(icon) }
        }.apply { isDaemon = true }.start()
    }
}
