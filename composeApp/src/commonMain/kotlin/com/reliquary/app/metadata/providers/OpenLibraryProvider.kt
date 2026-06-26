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

/** Keyless book metadata via Open Library (search.json + the books data API). */
class OpenLibraryProvider(private val client: HttpClient) : MetadataProvider {
    override val id = "openlibrary"
    override val displayName = "Open Library"
    override val mediaType = MediaType.BOOKS

    override suspend fun search(query: String): List<MetadataResult> {
        val url = "https://openlibrary.org/search.json?limit=20" +
            "&fields=title,author_name,first_publish_year,cover_i,isbn,subject" +
            "&q=${query.encodeURLParameter()}"
        val docs = ReliquaryJson.parseToJsonElement(client.get(url).bodyAsText())
            .obj()?.array("docs") ?: return emptyList()
        return docs.mapNotNull { it.obj()?.toSearchResult() }
    }

    override suspend fun lookupByBarcode(barcode: String): List<MetadataResult> {
        val isbn = barcode.filter { it.isDigit() || it == 'X' || it == 'x' }
        if (isbn.isBlank()) return emptyList()
        val url = "https://openlibrary.org/api/books?bibkeys=ISBN:$isbn&jscmd=data&format=json"
        val root = ReliquaryJson.parseToJsonElement(client.get(url).bodyAsText()).obj() ?: return emptyList()
        val entry = (root["ISBN:$isbn"] ?: root.values.firstOrNull())?.obj() ?: return emptyList()
        val title = entry.string("title") ?: return emptyList()
        val authors = entry.array("authors")?.mapNotNull { it.obj()?.string("name") }
        val cover = entry["cover"]?.obj()?.let { it.string("large") ?: it.string("medium") ?: it.string("small") }
        val subjects = entry.array("subjects")?.mapNotNull { it.obj()?.string("name") }?.take(5)
        return listOf(
            MetadataResult(
                providerId = id,
                providerName = displayName,
                mediaType = mediaType,
                title = title,
                creators = authors?.joinToString(", "),
                releaseYear = yearFrom(entry.string("publish_date")),
                coverUrl = cover,
                identifierType = "ISBN",
                identifier = isbn,
                genres = subjects?.joinToString(", "),
            ),
        )
    }

    override suspend fun details(result: MetadataResult): MetadataResult? {
        val isbn = result.identifier?.filter { it.isDigit() || it == 'X' || it == 'x' }
        if (isbn.isNullOrBlank()) return null
        val url = "https://openlibrary.org/api/books?bibkeys=ISBN:$isbn&jscmd=data&format=json"
        val root = ReliquaryJson.parseToJsonElement(client.get(url).bodyAsText()).obj() ?: return null
        val entry = (root["ISBN:$isbn"] ?: root.values.firstOrNull())?.obj() ?: return null
        val extras = buildMap {
            entry.array("authors")?.mapNotNull { it.obj()?.string("name") }?.joinToString(", ")
                ?.let { put("Authors", it) }
            entry.array("publishers")?.mapNotNull { it.obj()?.string("name") }?.joinToString(", ")
                ?.let { put("Publisher", it) }
            entry.string("publish_date")?.let { put("Published", it) }
            entry.long("number_of_pages")?.takeIf { it > 0 }?.let { put("Pages", it.toString()) }
            entry.array("subjects")?.mapNotNull { it.obj()?.string("name") }?.take(6)?.joinToString(", ")
                ?.let { put("Subjects", it) }
        }
        val cover = entry["cover"]?.obj()?.let { it.string("large") ?: it.string("medium") }
        return result.copy(
            coverUrl = cover ?: result.coverUrl,
            extra = result.extra + extras,
        )
    }

    private fun JsonObject.toSearchResult(): MetadataResult? {
        val title = string("title") ?: return null
        val coverId = long("cover_i")
        val isbn = array("isbn")?.strings()?.firstOrNull()
        return MetadataResult(
            providerId = id,
            providerName = displayName,
            mediaType = mediaType,
            title = title,
            creators = array("author_name")?.strings()?.joinToString(", "),
            releaseYear = long("first_publish_year"),
            coverUrl = coverId?.let { "https://covers.openlibrary.org/b/id/$it-L.jpg" },
            identifierType = isbn?.let { "ISBN" },
            identifier = isbn,
            genres = array("subject")?.strings()?.take(5)?.joinToString(", "),
        )
    }
}
