package com.reliquary.app.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.reliquary.app.db.ReliquaryDatabase
import com.reliquary.app.domain.CollectionItem
import com.reliquary.app.domain.CustomTab
import com.reliquary.app.domain.Loan
import com.reliquary.app.domain.Person
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Single facade over the local database. Reads are exposed as Flows so the UI
 * recomposes automatically; writes are plain suspend-free calls (SQLDelight is
 * synchronous) that stamp updated_at for sync.
 */
class ReliquaryRepository(private val db: ReliquaryDatabase) {

    private val q = db.reliquaryQueries
    private val io = Dispatchers.Default

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
            item.favorite.toDbLong(),
            item.addedAt,
            item.updatedAt,
            item.deleted.toDbLong(),
        )
    }

    fun deleteItem(id: String) = q.softDeleteItem(updatedAt = nowMillis(), id = id)

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

    fun loansForItem(itemId: String): Flow<List<Loan>> =
        q.selectLoansForItem(itemId).asFlow().mapToList(io).map { rows -> rows.map { it.toDomain() } }

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
