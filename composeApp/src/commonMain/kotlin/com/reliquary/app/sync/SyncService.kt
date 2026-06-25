package com.reliquary.app.sync

import com.reliquary.app.data.ReliquaryRepository
import com.reliquary.app.data.nowMillis
import com.reliquary.app.domain.CollectionItem
import com.reliquary.app.domain.CustomTab
import com.reliquary.app.domain.Loan
import com.reliquary.app.domain.Person
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** The portable, human-readable library snapshot exchanged between devices. */
@Serializable
data class SyncBundle(
    val version: Int = 1,
    val exportedAt: Long,
    val items: List<CollectionItem>,
    val people: List<Person>,
    val loans: List<Loan>,
    val customTabs: List<CustomTab>,
)

/** How many records each table accepted during an import merge. */
data class SyncResult(val items: Int, val people: Int, val loans: Int, val customTabs: Int) {
    val total: Int get() = items + people + loans + customTabs
}

/**
 * Reads/writes the whole library as a JSON bundle and merges an incoming bundle
 * with last-write-wins per record (by updated_at). Soft-deleted rows are
 * included so deletions propagate. API keys are intentionally never exported.
 */
class SyncService(private val repository: ReliquaryRepository) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun exportJson(): String = json.encodeToString(
        SyncBundle(
            exportedAt = nowMillis(),
            items = repository.allItems(),
            people = repository.allPeople(),
            loans = repository.allLoans(),
            customTabs = repository.allCustomTabs(),
        ),
    )

    fun importJson(text: String): SyncResult {
        val bundle = json.decodeFromString<SyncBundle>(text)

        val existingItems = repository.allItems().associateBy { it.id }
        var items = 0
        bundle.items.forEach { incoming ->
            val existing = existingItems[incoming.id]
            if (existing == null || incoming.updatedAt >= existing.updatedAt) {
                repository.upsertItem(incoming); items++
            }
        }

        val existingPeople = repository.allPeople().associateBy { it.id }
        var people = 0
        bundle.people.forEach { incoming ->
            val existing = existingPeople[incoming.id]
            if (existing == null || incoming.updatedAt >= existing.updatedAt) {
                repository.upsertPerson(incoming); people++
            }
        }

        val existingLoans = repository.allLoans().associateBy { it.id }
        var loans = 0
        bundle.loans.forEach { incoming ->
            val existing = existingLoans[incoming.id]
            if (existing == null || incoming.updatedAt >= existing.updatedAt) {
                repository.upsertLoan(incoming); loans++
            }
        }

        val existingTabs = repository.allCustomTabs().associateBy { it.id }
        var tabs = 0
        bundle.customTabs.forEach { incoming ->
            val existing = existingTabs[incoming.id]
            if (existing == null || incoming.updatedAt >= existing.updatedAt) {
                repository.upsertCustomTab(incoming); tabs++
            }
        }

        return SyncResult(items, people, loans, tabs)
    }
}
