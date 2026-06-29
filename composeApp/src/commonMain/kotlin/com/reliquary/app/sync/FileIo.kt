package com.reliquary.app.sync

/** Default location for the portable sync file (per-user app-data dir). */
expect fun defaultSyncFilePath(): String

expect fun writeTextFile(path: String, content: String)

expect fun readTextFile(path: String): String?

/** Directory for cached cover images (created if needed). */
expect fun coversDir(): String

expect fun writeBytesFile(path: String, bytes: ByteArray)

/** A saved JSON file in the app-data dir (backups / sync snapshots). */
data class BackupFile(val path: String, val name: String, val sizeBytes: Long, val modifiedAt: Long)

/** List JSON snapshots in the app-data dir, newest first. */
expect fun listBackups(): List<BackupFile>

expect fun deleteFile(path: String)
