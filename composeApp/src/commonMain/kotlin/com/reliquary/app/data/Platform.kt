package com.reliquary.app.data

/** Current wall-clock time in epoch milliseconds. */
expect fun nowMillis(): Long

/** A new globally-unique identifier (UUID string) for a database row. */
expect fun newId(): String
