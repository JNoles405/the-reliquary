package com.reliquary.app.csv

import com.reliquary.app.data.ReliquaryRepository
import com.reliquary.app.data.newId
import com.reliquary.app.data.nowMillis
import com.reliquary.app.domain.CollectionItem
import com.reliquary.app.domain.MediaType
import com.reliquary.app.util.formatDate

/**
 * CSV import/export, separate from the JSON sync format. Export produces a
 * spreadsheet-friendly file; import is lenient about column names so libraries
 * from Collectorz/CLZ or a spreadsheet map cleanly.
 */
class CsvService(private val repository: ReliquaryRepository) {

    private val exportColumns = listOf(
        "Title", "Subtitle", "Creators", "Media Type", "Year", "Genres", "Format",
        "Identifier Type", "Identifier", "Barcode", "Rating", "Location",
        "Cover URL", "Description", "Notes", "Status", "Tags", "Wishlist", "Favorite", "Added",
    )

    fun exportCsv(): String {
        val rows = buildList {
            add(exportColumns)
            repository.allItems().filter { !it.deleted }.forEach { item ->
                add(
                    listOf(
                        item.title,
                        item.subtitle.orEmpty(),
                        item.creators.orEmpty(),
                        friendlyType(item.mediaType),
                        item.releaseYear?.toString().orEmpty(),
                        item.genres.orEmpty(),
                        item.format.orEmpty(),
                        item.identifierType.orEmpty(),
                        item.identifier.orEmpty(),
                        item.barcode.orEmpty(),
                        item.rating?.toString().orEmpty(),
                        item.location.orEmpty(),
                        item.coverUrl.orEmpty(),
                        item.description.orEmpty(),
                        item.notes.orEmpty(),
                        item.status.orEmpty(),
                        item.tags.orEmpty(),
                        if (item.wanted) "Yes" else "",
                        if (item.favorite) "Yes" else "",
                        formatDate(item.addedAt),
                    ),
                )
            }
        }
        return rows.joinToString("\r\n") { row -> row.joinToString(",") { encodeField(it) } }
    }

    /** Returns how many rows were imported. [defaultType] is used when a row has no category column. */
    fun importCsv(text: String, defaultType: MediaType): Int {
        val rows = parseCsv(text)
        if (rows.size < 2) return 0
        val header = rows.first().map { it.trim().lowercase() }
        fun cell(row: List<String>, vararg names: String): String? {
            for (name in names) {
                val idx = header.indexOf(name)
                if (idx >= 0 && idx < row.size) row[idx].trim().takeIf { it.isNotBlank() }?.let { return it }
            }
            return null
        }

        var imported = 0
        for (row in rows.drop(1)) {
            val title = cell(row, "title", "name") ?: continue
            val now = nowMillis()
            val typeName = cell(row, "media type", "mediatype", "category", "type")
                ?.let { v -> MediaType.entries.firstOrNull { it.displayName.equals(v, true) || it.name.equals(v, true) } }
                ?.name ?: defaultType.name
            repository.upsertItem(
                CollectionItem(
                    id = newId(),
                    mediaType = typeName,
                    title = title,
                    subtitle = cell(row, "subtitle"),
                    creators = cell(row, "creators", "author", "authors", "director", "artist"),
                    releaseYear = cell(row, "year", "release year", "releaseyear")
                        ?.let { Regex("\\d{4}").find(it)?.value?.toLongOrNull() },
                    description = cell(row, "description", "plot", "overview", "synopsis"),
                    coverUrl = cell(row, "cover url", "coverurl", "cover", "image", "poster"),
                    barcode = cell(row, "barcode", "upc", "ean"),
                    identifierType = cell(row, "identifier type", "identifiertype"),
                    identifier = cell(row, "identifier", "isbn", "catalog"),
                    rating = cell(row, "rating", "imdb", "score")?.let { Regex("[0-9.]+").find(it)?.value?.toDoubleOrNull() },
                    genres = cell(row, "genres", "genre"),
                    format = cell(row, "format", "edition"),
                    location = cell(row, "location", "shelf"),
                    notes = cell(row, "notes", "comments"),
                    status = cell(row, "status"),
                    tags = cell(row, "tags"),
                    wanted = cell(row, "wishlist", "wanted")?.lowercase() in setOf("yes", "true", "1", "y"),
                    favorite = cell(row, "favorite", "favourite")?.lowercase() in setOf("yes", "true", "1", "y"),
                    addedAt = now,
                    updatedAt = now,
                ),
            )
            imported++
        }
        return imported
    }

    private fun friendlyType(mediaType: String): String =
        MediaType.entries.firstOrNull { it.name == mediaType }?.displayName ?: "Custom"

    /** Import using an explicit field→column-index mapping (for headers we don't auto-recognize). */
    fun importCsvMapped(text: String, defaultType: MediaType, mapping: Map<String, Int>): Int {
        val rows = parseCsv(text)
        if (rows.size < 2) return 0
        fun cell(row: List<String>, field: String): String? {
            val idx = mapping[field] ?: return null
            return row.getOrNull(idx)?.trim()?.takeIf { it.isNotBlank() }
        }
        val yes = setOf("yes", "true", "1", "y")
        var imported = 0
        for (row in rows.drop(1)) {
            val title = cell(row, "Title") ?: continue
            val now = nowMillis()
            val typeName = cell(row, "Media Type")
                ?.let { v -> MediaType.entries.firstOrNull { it.displayName.equals(v, true) || it.name.equals(v, true) } }
                ?.name ?: defaultType.name
            repository.upsertItem(
                CollectionItem(
                    id = newId(),
                    mediaType = typeName,
                    title = title,
                    subtitle = cell(row, "Subtitle"),
                    creators = cell(row, "Creators"),
                    releaseYear = cell(row, "Year")?.let { Regex("\\d{4}").find(it)?.value?.toLongOrNull() },
                    description = cell(row, "Description"),
                    coverUrl = cell(row, "Cover URL"),
                    barcode = cell(row, "Barcode"),
                    identifierType = cell(row, "Identifier Type"),
                    identifier = cell(row, "Identifier"),
                    rating = cell(row, "Rating")?.let { Regex("[0-9.]+").find(it)?.value?.toDoubleOrNull() },
                    genres = cell(row, "Genres"),
                    format = cell(row, "Format"),
                    location = cell(row, "Location"),
                    notes = cell(row, "Notes"),
                    status = cell(row, "Status"),
                    tags = cell(row, "Tags"),
                    wanted = cell(row, "Wishlist")?.lowercase() in yes,
                    favorite = cell(row, "Favorite")?.lowercase() in yes,
                    addedAt = now,
                    updatedAt = now,
                ),
            )
            imported++
        }
        return imported
    }

    private fun encodeField(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
}

/** Target fields the importer understands, with header aliases used for auto-mapping. */
val CSV_TARGET_FIELDS: List<Pair<String, List<String>>> = listOf(
    "Title" to listOf("title", "name"),
    "Media Type" to listOf("media type", "mediatype", "category", "type"),
    "Year" to listOf("year", "release year", "releaseyear"),
    "Creators" to listOf("creators", "author", "authors", "director", "artist"),
    "Genres" to listOf("genres", "genre"),
    "Format" to listOf("format", "edition"),
    "Subtitle" to listOf("subtitle"),
    "Identifier Type" to listOf("identifier type", "identifiertype"),
    "Identifier" to listOf("identifier", "isbn", "catalog"),
    "Barcode" to listOf("barcode", "upc", "ean"),
    "Rating" to listOf("rating", "imdb", "score"),
    "Location" to listOf("location", "shelf"),
    "Cover URL" to listOf("cover url", "coverurl", "cover", "image", "poster"),
    "Description" to listOf("description", "plot", "overview", "synopsis"),
    "Notes" to listOf("notes", "comments"),
    "Status" to listOf("status"),
    "Tags" to listOf("tags"),
    "Wishlist" to listOf("wishlist", "wanted"),
    "Favorite" to listOf("favorite", "favourite"),
)

/** Best-guess mapping of target field → column index from the header row. */
fun autoMapColumns(headers: List<String>): Map<String, Int> {
    val lower = headers.map { it.trim().lowercase() }
    return CSV_TARGET_FIELDS.mapNotNull { (field, aliases) ->
        val idx = lower.indexOfFirst { h -> aliases.any { it == h } }
            .let { if (it >= 0) it else lower.indexOfFirst { h -> aliases.any { h.contains(it) } } }
        if (idx >= 0) field to idx else null
    }.toMap()
}

/** Minimal RFC-4180 CSV parser: handles quoted fields, escaped quotes, and CRLF/LF. */
fun parseCsv(text: String): List<List<String>> {
    val rows = mutableListOf<List<String>>()
    var row = mutableListOf<String>()
    val field = StringBuilder()
    var inQuotes = false
    var i = 0
    while (i < text.length) {
        val c = text[i]
        if (inQuotes) {
            when {
                c == '"' && i + 1 < text.length && text[i + 1] == '"' -> { field.append('"'); i++ }
                c == '"' -> inQuotes = false
                else -> field.append(c)
            }
        } else {
            when (c) {
                '"' -> inQuotes = true
                ',' -> { row.add(field.toString()); field.clear() }
                '\n' -> { row.add(field.toString()); rows.add(row); row = mutableListOf(); field.clear() }
                '\r' -> Unit
                else -> field.append(c)
            }
        }
        i++
    }
    row.add(field.toString())
    if (row.size > 1 || row.first().isNotBlank()) rows.add(row)
    return rows
}
