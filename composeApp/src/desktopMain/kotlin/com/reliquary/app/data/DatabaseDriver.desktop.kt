package com.reliquary.app.data

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.reliquary.app.db.ReliquaryDatabase
import java.io.File

/**
 * Desktop driver. The JDBC SQLite driver does not manage schema versions, so we
 * track PRAGMA user_version ourselves: create the latest schema for a fresh DB,
 * or run SQLDelight migrations for an existing one. The file lives under the
 * per-user app-data directory (APPDATA on Windows).
 */
fun createDesktopDriver(): SqlDriver {
    val dir = desktopDataDir().apply { if (!exists()) mkdirs() }
    val dbFile = File(dir, "reliquary.db")
    val freshDatabase = !dbFile.exists()
    val driver = JdbcSqliteDriver("jdbc:sqlite:${dbFile.absolutePath}")
    val schema = ReliquaryDatabase.Schema
    val target = schema.version
    if (freshDatabase) {
        schema.create(driver)
        setUserVersion(driver, target)
    } else {
        val current = getUserVersion(driver)
        if (current < target) {
            schema.migrate(driver, current, target)
            setUserVersion(driver, target)
        }
    }
    return driver
}

private fun getUserVersion(driver: SqlDriver): Long =
    driver.executeQuery(null, "PRAGMA user_version", { cursor ->
        cursor.next()
        QueryResult.Value(cursor.getLong(0) ?: 0L)
    }, 0).value

private fun setUserVersion(driver: SqlDriver, version: Long) {
    driver.execute(null, "PRAGMA user_version = $version", 0)
}

/** Directory where The Reliquary stores its desktop database and cached covers. */
fun desktopDataDir(): File {
    val appData = System.getenv("APPDATA")
    val base = if (!appData.isNullOrBlank()) File(appData) else File(System.getProperty("user.home"), ".config")
    return File(base, "TheReliquary")
}
