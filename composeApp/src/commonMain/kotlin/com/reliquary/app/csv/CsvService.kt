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
        "Cover URL", "Description", "Notes", "Favorite", "Added",
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

    private fun encodeField(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
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
