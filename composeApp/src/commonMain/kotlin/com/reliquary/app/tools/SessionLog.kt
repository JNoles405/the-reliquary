package com.reliquary.app.tools

import com.reliquary.app.metadata.ReliquaryJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

/** One logged play/watch/read session for an item. */
@Serializable
data class Session(val at: Long, val note: String = "")

/** Stores a per-item list of sessions inside the item's extras under [KEY]. */
object SessionLog {
    const val KEY = "_sessions"

    fun parse(json: String?): List<Session> = json
        ?.let { runCatching { ReliquaryJson.decodeFromString<List<Session>>(it) }.getOrNull() }
        ?.sortedByDescending { it.at }
        ?: emptyList()

    /** Encode a session list back to JSON, or null when empty (so the key is dropped). */
    fun encode(sessions: List<Session>): String? =
        if (sessions.isEmpty()) null else ReliquaryJson.encodeToString(sessions.sortedByDescending { it.at })
}
