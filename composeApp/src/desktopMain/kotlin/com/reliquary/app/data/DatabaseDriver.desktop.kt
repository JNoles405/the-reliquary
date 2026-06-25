package com.reliquary.app.data

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.reliquary.app.db.ReliquaryDatabase
import java.io.File

/**
 * Desktop driver. The JDBC SQLite driver does not auto-create the schema, so we
 * detect a fresh database file and create it once. The file lives under the
 * per-user app-data directory (APPDATA on Windows).
 */
fun createDesktopDriver(): SqlDriver {
    val dir = desktopDataDir().apply { if (!exists()) mkdirs() }
    val dbFile = File(dir, "reliquary.db")
    val freshDatabase = !dbFile.exists()
    val driver = JdbcSqliteDriver("jdbc:sqlite:${dbFile.absolutePath}")
    if (freshDatabase) {
        ReliquaryDatabase.Schema.create(driver)
    }
    return driver
}

/** Directory where The Reliquary stores its desktop database and cached covers. */
fun desktopDataDir(): File {
    val appData = System.getenv("APPDATA")
    val base = if (!appData.isNullOrBlank()) File(appData) else File(System.getProperty("user.home"), ".config")
    return File(base, "TheReliquary")
}
