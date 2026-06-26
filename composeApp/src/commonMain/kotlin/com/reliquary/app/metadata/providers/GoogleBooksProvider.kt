package com.reliquary.app.metadata.providers

import com.reliquary.app.domain.MediaType
import com.reliquary.app.metadata.MetadataProvider
import com.reliquary.app.metadata.MetadataResult
import com.reliquary.app.metadata.ReliquaryJson
import com.reliquary.app.metadata.array
import com.reliquary.app.metadata.long
import com.reliquary.app.metadata.obj
import com.reliquary.app.metadata.string
import com.reliquary.app.metadata.strings
import com.reliquary.app.metadata.yearFrom
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.encodeURLParameter
import kotlinx.serialization.json.JsonObject

/** Keyless book metadata via the Google Books volumes API. */
class GoogleBooksProvider(private val client: HttpClient) : MetadataProvider {
    override val id = "googlebooks"
    override val displayName = "Google Books"
    override val mediaType = MediaType.BOOKS

    override suspend fun search(query: String): List<MetadataResult> = query(query.encodeURLParameter())

    override suspend fun lookupByBarcode(barcode: String): List<MetadataResult> {
        val isbn = barcode.filter { it.isDigit() || it == 'X' || it == 'x' }
        if (isbn.isBlank()) return emptyList()
        return query("isbn:$isbn")
    }

    private suspend fun query(encodedQuery: String): List<MetadataResult> {
        val url = "https://www.googleapis.com/books/v1/volumes?maxResults=20&q=$encodedQuery"
        val items = ReliquaryJson.parseToJsonElement(client.get(url).bodyAsText())
            .obj()?.array("items") ?: return emptyList()
        return items.mapNotNull { it.obj()?.get("volumeInfo")?.obj()?.toResult() }
    }

    private fun JsonObject.toResult(): MetadataResult? {
        val title = string("title") ?: return null
        val isbn = array("industryIdentifiers")
            ?.mapNotNull { it.obj() }
            ?.firstOrNull { it.string("type")?.startsWith("ISBN") == true }
            ?.string("identifier")
        val thumbnail = this["imageLinks"]?.obj()
            ?.let { it.string("thumbnail") ?: it.string("smallThumbnail") }
            ?.replace("http://", "https://")
        return MetadataResult(
            providerId = id,
            providerName = displayName,
            mediaType = mediaType,
            title = title,
            subtitle = string("subtitle"),
            creators = array("authors")?.strings()?.joinToString(", "),
            releaseYear = yearFrom(string("publishedDate")),
            description = string("description"),
            coverUrl = thumbnail,
            identifierType = isbn?.let { "ISBN" },
            identifier = isbn,
            genres = array("categories")?.strings()?.joinToString(", "),
            extra = buildMap {
                array("authors")?.strings()?.joinToString(", ")?.let { put("Authors", it) }
                string("publisher")?.let { put("Publisher", it) }
                string("publishedDate")?.let { put("Published", it) }
                long("pageCount")?.takeIf { it > 0 }?.let { put("Pages", it.toString()) }
                string("language")?.let { put("Language", it.uppercase()) }
            },
        )
    }
}
