package com.reliquary.app.tools

import com.reliquary.app.data.ReliquaryRepository
import com.reliquary.app.metadata.ReliquaryJson
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

/** Tracks the most-recently-opened item ids (newest first) for the Home rail. */
object RecentlyViewed {
    private const val KEY = "recent.viewed"

    fun load(repository: ReliquaryRepository): List<String> = repository.getSetting(KEY)
        ?.let { runCatching { ReliquaryJson.decodeFromString<List<String>>(it) }.getOrNull() } ?: emptyList()

    fun record(repository: ReliquaryRepository, id: String) {
        val updated = (listOf(id) + load(repository).filter { it != id }).take(24)
        repository.setSetting(KEY, ReliquaryJson.encodeToString(updated))
    }
}
