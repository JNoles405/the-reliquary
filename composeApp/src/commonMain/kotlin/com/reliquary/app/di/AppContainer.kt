package com.reliquary.app.di

import app.cash.sqldelight.db.SqlDriver
import com.reliquary.app.data.ReliquaryRepository
import com.reliquary.app.db.ReliquaryDatabase

/**
 * Lightweight manual DI. Each platform builds its own [SqlDriver] (Android needs
 * a Context, desktop uses a JDBC file) and hands it here; everything downstream
 * is shared common code.
 */
class AppContainer(driver: SqlDriver) {
    val database: ReliquaryDatabase = ReliquaryDatabase(driver)
    val repository: ReliquaryRepository = ReliquaryRepository(database)
}
