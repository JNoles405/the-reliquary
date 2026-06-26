package com.reliquary.app.ui.library

import com.reliquary.app.data.ReliquaryRepository
import com.reliquary.app.metadata.ReliquaryJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

/** A saved combination of sort + filters in the library. */
@Serializable
data class SmartView(
    val name: String,
    val sort: String,
    val favorites: Boolean = false,
    val onLoan: Boolean = false,
    val unfinished: Boolean = false,
    val wishlist: Boolean = false,
    val genre: String? = null,
)

/** Persists saved views as a JSON list in the settings table (shared across tabs). */
class SmartViewsStore(private val repository: ReliquaryRepository) {
    private val key = "ui.smartViews"

    fun list(): List<SmartView> = repository.getSetting(key)
        ?.let { runCatching { ReliquaryJson.decodeFromString<List<SmartView>>(it) }.getOrNull() }
        .orEmpty()

    fun save(view: SmartView) {
        val updated = list().filter { it.name != view.name } + view
        repository.setSetting(key, ReliquaryJson.encodeToString(updated.sortedBy { it.name.lowercase() }))
    }

    fun delete(name: String) {
        repository.setSetting(key, ReliquaryJson.encodeToString(list().filter { it.name != name }))
    }
}
