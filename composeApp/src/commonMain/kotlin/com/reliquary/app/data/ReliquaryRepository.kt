package com.reliquary.app.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.reliquary.app.db.ReliquaryDatabase
import com.reliquary.app.domain.CollectionItem
import com.reliquary.app.domain.CustomTab
import com.reliquary.app.domain.EDITION_FIELDS
import com.reliquary.app.domain.Loan
import com.reliquary.app.domain.Person
import com.reliquary.app.domain.Status
import com.reliquary.app.domain.VALUE_FIELDS
import com.reliquary.app.domain.parseTags
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Result of an import: the resulting item and whether it was newly created. */
data class ImportOutcome(val item: CollectionItem, val isNew: Boolean)

/**
 * Single facade over the local database. Reads are exposed as Flows so the UI
 * recomposes automatically; writes are plain suspend-free calls (SQLDelight is
 * synchronous) that stamp updated_at for sync.
 */
class ReliquaryRepository(private val db: ReliquaryDatabase) {

    private val q = db.reliquaryQueries
    private val io = Dispatchers.Default
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // ---- Items -------------------------------------------------------------

    fun itemsByType(mediaType: String): Flow<List<CollectionItem>> =
        q.selectItemsByType(mediaType).asFlow().mapToList(io).map { rows -> rows.map { it.toDomain() } }

    fun itemsByCustomTab(customTabId: String): Flow<List<CollectionItem>> =
        q.selectItemsByCustomTab(customTabId).asFlow().mapToList(io).map { rows -> rows.map { it.toDomain() } }

    fun recentItems(limit: Long = 20): Flow<List<CollectionItem>> =
        q.selectRecentItems(limit).asFlow().mapToList(io).map { rows -> rows.map { it.toDomain() } }

    fun countByType(mediaType: String): Flow<Long> =
        q.countItemsByType(mediaType).asFlow().mapToOne(io)

    fun searchItems(query: String): Flow<List<CollectionItem>> =
        q.searchItems(query).asFlow().mapToList(io).map { rows -> rows.map { it.toDomain() } }

    fun getItem(id: String): CollectionItem? =
        q.selectItemById(id).executeAsOneOrNull()?.toDomain()

    fun itemFlow(id: String): Flow<CollectionItem?> =
        q.selectItemById(id).asFlow().mapToOneOrNull(io).map { it?.toDomain() }

    fun upsertItem(item: CollectionItem) {
        q.upsertItem(
            item.id,
            item.mediaType,
            item.customTabId,
            item.title,
            item.sortTitle,
            item.subtitle,
            item.creators,
            item.releaseYear,
            item.description,
            item.coverPath,
            item.coverUrl,
            item.barcode,
            item.identifierType,
            item.identifier,
            item.rating,
            item.genres,
            item.format,
            item.location,
            item.extraJson,
            item.notes,
            item.status,
            item.wanted.toDbLong(),
            item.tags,
            item.favorite.toDbLong(),
            item.addedAt,
            item.updatedAt,
            item.deleted.toDbLong(),
        )
    }

    fun deleteItem(id: String) = q.softDeleteItem(updatedAt = nowMillis(), id = id)

    /** Soft-deleted items, newest first (the recycle bin). */
    fun deletedItems(): List<CollectionItem> =
        allItems().filter { it.deleted }.sortedByDescending { it.updatedAt }

    fun restoreItem(id: String) {
        getItem(id)?.let { upsertItem(it.copy(deleted = false, updatedAt = nowMillis())) }
    }

    /** Permanently remove an item from the database. */
    fun purgeItem(id: String) = q.hardDeleteItem(id)

    /** Set or clear an item's status. */
    fun updateStatus(itemId: String, status: String?) {
        val item = getItem(itemId) ?: return
        // Stamp a finished date the first time an item reaches a "done" status; clear it otherwise.
        val extras = decodeExtras(item.extraJson).toMutableMap()
        if (status in Status.DONE) {
            if (!extras.containsKey("_finishedAt")) extras["_finishedAt"] = nowMillis().toString()
        } else {
            extras.remove("_finishedAt")
        }
        val extraJson = if (extras.isEmpty()) null else json.encodeToString(extras)
        upsertItem(item.copy(status = status, extraJson = extraJson, updatedAt = nowMillis()))
    }

    /** Append a tag to an item (deduped). */
    fun addTag(itemId: String, tag: String) {
        val item = getItem(itemId) ?: return
        val clean = tag.trim()
        if (clean.isBlank()) return
        val tags = (parseTags(item.tags) + clean).distinct().joinToString(", ")
        upsertItem(item.copy(tags = tags, updatedAt = nowMillis()))
    }

    /** Rename a tag across the whole collection, merging into any existing tag of the new name. */
    fun renameTag(oldTag: String, newTag: String): Int {
        val from = oldTag.trim()
        val to = newTag.trim()
        if (from.isBlank() || to.isBlank() || from.equals(to, ignoreCase = true)) return 0
        var changed = 0
        allItems().filter { !it.deleted }.forEach { item ->
            val tags = parseTags(item.tags)
            if (tags.any { it.equals(from, ignoreCase = true) }) {
                val updated = tags.map { if (it.equals(from, ignoreCase = true)) to else it }
                    .distinct().joinToString(", ")
                upsertItem(item.copy(tags = updated, updatedAt = nowMillis()))
                changed++
            }
        }
        return changed
    }

    /** Set or clear a single extras key on an item, preserving the rest. */
    fun setExtra(itemId: String, key: String, value: String?) {
        val item = getItem(itemId) ?: return
        val map = decodeExtras(item.extraJson).toMutableMap()
        if (value.isNullOrBlank()) map.remove(key) else map[key] = value
        val extraJson = if (map.isEmpty()) null else json.encodeToString(map)
        upsertItem(item.copy(extraJson = extraJson, updatedAt = nowMillis()))
    }

    /** A random owned, unfinished item to suggest — falls back to any owned item. */
    fun surprisePick(): CollectionItem? {
        val all = allItems().filter { !it.deleted }
        val owned = all.filter { !it.wanted }
        val unfinished = owned.filter { it.status !in Status.DONE }
        return unfinished.ifEmpty { owned }.ifEmpty { all }.randomOrNull()
    }

    // ---- Sync (all rows including soft-deleted) ----------------------------

    fun allItems(): List<CollectionItem> = q.selectAllItems().executeAsList().map { it.toDomain() }

    // ---- Import with duplicate detection -----------------------------------

    /** Find an existing item that the candidate likely duplicates, or null. */
    fun findDuplicate(candidate: CollectionItem): CollectionItem? {
        val all = allItems().filter { !it.deleted }
        candidate.barcode?.takeIf { it.isNotBlank() }
            ?.let { bc -> all.firstOrNull { it.barcode == bc } }?.let { return it }
        if (!candidate.identifier.isNullOrBlank() && !candidate.identifierType.isNullOrBlank()) {
            all.firstOrNull {
                it.mediaType == candidate.mediaType &&
                    it.identifier == candidate.identifier &&
                    it.identifierType == candidate.identifierType
            }?.let { return it }
        }
        val key = normalizeTitle(candidate.title)
        return all.firstOrNull {
            it.mediaType == candidate.mediaType &&
                normalizeTitle(it.title) == key &&
                it.releaseYear == candidate.releaseYear
        }
    }

    /** Insert the candidate, or update the existing duplicate while preserving user data. */
    fun importOrUpdate(candidate: CollectionItem): ImportOutcome {
        val existing = findDuplicate(candidate)
        if (existing == null) {
            upsertItem(candidate)
            return ImportOutcome(candidate, isNew = true)
        }
        // Field-level merge: the candidate fills in fields it has; the existing item
        // keeps everything else (so a sparse source can't wipe rich data).
        val merged = existing.copy(
            subtitle = candidate.subtitle ?: existing.subtitle,
            creators = candidate.creators ?: existing.creators,
            releaseYear = candidate.releaseYear ?: existing.releaseYear,
            description = candidate.description ?: existing.description,
            coverUrl = candidate.coverUrl ?: existing.coverUrl,
            barcode = candidate.barcode ?: existing.barcode,
            identifierType = candidate.identifierType ?: existing.identifierType,
            identifier = candidate.identifier ?: existing.identifier,
            rating = candidate.rating ?: existing.rating,
            genres = candidate.genres ?: existing.genres,
            format = candidate.format ?: existing.format,
            location = existing.location ?: candidate.location,
            notes = existing.notes ?: candidate.notes,
            // User-owned fields win; the candidate only fills gaps.
            status = existing.status ?: candidate.status,
            wanted = existing.wanted,
            tags = existing.tags ?: candidate.tags,
            extraJson = mergeExtras(existing.extraJson, candidate.extraJson),
            updatedAt = nowMillis(),
        )
        upsertItem(merged)
        return ImportOutcome(merged, isNew = false)
    }

    private fun normalizeTitle(s: String): String = s.lowercase().filter { it.isLetterOrDigit() }

    private fun decodeExtras(jsonStr: String?): Map<String, String> = jsonStr
        ?.let { runCatching { json.decodeFromString<Map<String, String>>(it) }.getOrNull() } ?: emptyMap()

    /** Overlay the candidate's extras onto the existing ones, but keep the user's
     *  edition fields and status from the existing item. */
    private fun mergeExtras(existingJson: String?, candidateJson: String?): String? {
        val existing = decodeExtras(existingJson)
        val merged = existing.toMutableMap()
        decodeExtras(candidateJson).forEach { (key, value) -> if (value.isNotBlank()) merged[key] = value }
        (EDITION_FIELDS + VALUE_FIELDS).forEach { key -> existing[key]?.let { merged[key] = it } }
        return if (merged.isEmpty()) null else json.encodeToString(merged)
    }
    fun allPeople(): List<Person> = q.selectAllPeopleForSync().executeAsList().map { it.toDomain() }
    fun allLoans(): List<Loan> = q.selectAllLoansForSync().executeAsList().map { it.toDomain() }
    fun allCustomTabs(): List<CustomTab> = q.selectAllCustomTabsForSync().executeAsList().map { it.toDomain() }

    // ---- People ------------------------------------------------------------

    fun people(): Flow<List<Person>> =
        q.selectAllPeople().asFlow().mapToList(io).map { rows -> rows.map { it.toDomain() } }

    fun getPerson(id: String): Person? = q.selectPersonById(id).executeAsOneOrNull()?.toDomain()

    fun upsertPerson(person: Person) {
        q.upsertPerson(person.id, person.name, person.contact, person.notes, person.updatedAt, person.deleted.toDbLong())
    }

    fun deletePerson(id: String) = q.softDeletePerson(updatedAt = nowMillis(), id = id)

    // ---- Loans -------------------------------------------------------------

    fun activeLoans(): Flow<List<Loan>> =
        q.selectActiveLoans().asFlow().mapToList(io).map { rows -> rows.map { it.toDomain() } }

    fun activeLoansNow(): List<Loan> = q.selectActiveLoans().executeAsList().map { it.toDomain() }

    fun loansForItem(itemId: String): Flow<List<Loan>> =
        q.selectLoansForItem(itemId).asFlow().mapToList(io).map { rows -> rows.map { it.toDomain() } }

    fun loansForPerson(personId: String): Flow<List<Loan>> =
        q.selectLoansForPerson(personId).asFlow().mapToList(io).map { rows -> rows.map { it.toDomain() } }

    fun activeLoanForItem(itemId: String): Loan? =
        q.selectActiveLoanForItem(itemId).executeAsOneOrNull()?.toDomain()

    fun upsertLoan(loan: Loan) {
        q.upsertLoan(
            loan.id,
            loan.itemId,
            loan.personId,
            loan.loanedAt,
            loan.dueAt,
            loan.returnedAt,
            loan.notes,
            loan.updatedAt,
            loan.deleted.toDbLong(),
        )
    }

    fun markLoanReturned(id: String) =
        q.markLoanReturned(returnedAt = nowMillis(), updatedAt = nowMillis(), id = id)

    fun deleteLoan(id: String) = q.softDeleteLoan(updatedAt = nowMillis(), id = id)

    // ---- Custom tabs -------------------------------------------------------

    fun customTabs(): Flow<List<CustomTab>> =
        q.selectAllCustomTabs().asFlow().mapToList(io).map { rows -> rows.map { it.toDomain() } }

    fun upsertCustomTab(tab: CustomTab) {
        q.upsertCustomTab(
            tab.id,
            tab.name,
            tab.icon,
            tab.position,
            tab.supportsBarcode.toDbLong(),
            tab.updatedAt,
            tab.deleted.toDbLong(),
        )
    }

    fun deleteCustomTab(id: String) = q.softDeleteCustomTab(updatedAt = nowMillis(), id = id)

    // ---- Settings ----------------------------------------------------------

    // selectSetting wraps its single nullable column; `value` is escaped to `value_`.
    fun getSetting(key: String): String? = q.selectSetting(key).executeAsOneOrNull()?.value_

    fun setSetting(key: String, value: String?) = q.upsertSetting(key, value)

    fun deleteSetting(key: String) = q.deleteSetting(key)
}
