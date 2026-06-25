package com.reliquary.app.data

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.reliquary.app.db.ReliquaryDatabase

/** Android driver — AndroidSqliteDriver handles schema creation and migrations. */
fun createAndroidDriver(context: Context): SqlDriver =
    AndroidSqliteDriver(ReliquaryDatabase.Schema, context, "reliquary.db")
