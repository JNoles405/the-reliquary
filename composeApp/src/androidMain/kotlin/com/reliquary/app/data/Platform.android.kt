package com.reliquary.app.data

import java.util.UUID

actual fun nowMillis(): Long = System.currentTimeMillis()

actual fun newId(): String = UUID.randomUUID().toString()
