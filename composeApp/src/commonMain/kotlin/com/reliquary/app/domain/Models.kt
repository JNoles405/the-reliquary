package com.reliquary.app.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * A single piece of media in the collection. Mirrors the `item` table but uses
 * idiomatic Kotlin types (Boolean instead of 0/1, camelCase). Mapping lives in
 * the data layer. Serializable so it can travel in a sync bundle.
 */
@Serializable
data class CollectionItem(
    val id: String,
    val mediaType: String,
    val customTabId: String? = null,
    val title: String,
    val sortTitle: String? = null,
    val subtitle: String? = null,
    val creators: String? = null,
    val releaseYear: Long? = null,
    val description: String? = null,
    // Device-local cached image path — never synced (each device caches its own).
    @Transient val coverPath: String? = null,
    val coverUrl: String? = null,
    val barcode: String? = null,
    val identifierType: String? = null,
    val identifier: String? = null,
    val rating: Double? = null,
    val genres: String? = null,
    val format: String? = null,
    val location: String? = null,
    val extraJson: String? = null,
    val notes: String? = null,
    val favorite: Boolean = false,
    val addedAt: Long = 0,
    val updatedAt: Long = 0,
    val deleted: Boolean = false,
) {
    /** Cover image source for display — local cached path wins over remote url. */
    val coverImage: String? get() = coverPath ?: coverUrl
}

@Serializable
data class Person(
    val id: String,
    val name: String,
    val contact: String? = null,
    val notes: String? = null,
    val updatedAt: Long = 0,
    val deleted: Boolean = false,
)

@Serializable
data class Loan(
    val id: String,
    val itemId: String,
    val personId: String,
    val loanedAt: Long,
    val dueAt: Long? = null,
    val returnedAt: Long? = null,
    val notes: String? = null,
    val updatedAt: Long = 0,
    val deleted: Boolean = false,
) {
    val isActive: Boolean get() = returnedAt == null && !deleted
    fun isOverdue(now: Long): Boolean = isActive && dueAt != null && dueAt < now
}

@Serializable
data class CustomTab(
    val id: String,
    val name: String,
    val icon: String? = null,
    val position: Long = 0,
    val supportsBarcode: Boolean = false,
    val updatedAt: Long = 0,
    val deleted: Boolean = false,
)
