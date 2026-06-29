package com.reliquary.app.metadata.providers

import com.reliquary.app.domain.MediaType
import com.reliquary.app.metadata.ApiKeyStore
import com.reliquary.app.metadata.ApiKeys
import com.reliquary.app.metadata.MetadataException
import com.reliquary.app.metadata.MetadataProvider
import com.reliquary.app.metadata.MetadataResult
import com.reliquary.app.metadata.ReliquaryJson
import com.reliquary.app.metadata.array
import com.reliquary.app.metadata.obj
import com.reliquary.app.metadata.string
import com.reliquary.app.metadata.yearFrom
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonObject

/**
 * Movie metadata via OMDb — an easy free alternative to TMDB (key by email at
 * omdbapi.com). OMDb's search returns sparse rows, so each hit is enriched with
 * a full lookup by IMDb id to get plot, genre, director, and poster. No barcode
 * index; the MetadataService resolves a UPC to a title first.
 */
class OmdbProvider(
    private val client: HttpClient,
    private val keys: ApiKeyStore,
) : MetadataProvider {
    override val id = "omdb"
    override val displayName = "OMDb"
    override val mediaType = MediaType.MOVIES
    override val requiresApiKey = true
    override val isEnabled get() = keys.has(ApiKeys.OMDB)

    override suspend fun search(query: String): List<MetadataResult> {
        val key = keys.get(ApiKeys.OMDB) ?: return emptyList()
        val url = "https://www.omdbapi.com/?apikey=$key&type=movie&s=${query.encodeURLParameter()}"
        val root = ReliquaryJson.parseToJsonElement(client.get(url).bodyAsText()).obj() ?: return emptyList()
        if (root.string("Response") == "False") {
            // Distinguish a real config problem from an ordinary empty result.
            val error = root.string("Error").orEmpty()
            if (error.contains("API key", ignoreCase = true) || error.contains("limit", ignoreCase = true)) {
                throw MetadataException("OMDb: $error")
            }
            return emptyList()
        }
        val hits = root.array("Search")?.mapNotNull { it.obj() } ?: return emptyList()
        return coroutineScope {
            hits.map { hit -> async { enrich(hit, key) } }.awaitAll().filterNotNull()
        }
    }

    // OMDb indexes titles/IMDb ids, not retail barcodes.
    override suspend fun lookupByBarcode(barcode: String): List<MetadataResult> = emptyList()

    override suspend fun details(result: MetadataResult): MetadataResult? {
        val key = keys.get(ApiKeys.OMDB) ?: return null
        val id = result.identifier ?: return null
        val url = "https://www.omdbapi.com/?apikey=$key&plot=full&i=$id"
        val o = ReliquaryJson.parseToJsonElement(client.get(url).bodyAsText()).obj() ?: return null
        if (o.string("Response") == "False") return null
        fun field(name: String) = o.string(name)?.takeIf { it.isNotBlank() && it != "N/A" }
        val extras = buildMap {
            field("Director")?.let { put("Director", it) }
            field("Writer")?.let { put("Writer", it) }
            field("Production")?.let { put("Studio", it) }
            field("Runtime")?.let { put("Runtime", it) }
            field("Rated")?.let { put("Rated", it) }
            field("Country")?.let { put("Country", it) }
            field("Language")?.let { put("Language", it) }
            field("Awards")?.let { put("Awards", it) }
            field("Actors")?.let { put("Cast", it) }
        }
        return result.copy(
            title = field("Title") ?: result.title,
            description = field("Plot") ?: result.description,
            releaseYear = yearFrom(field("Year")) ?: result.releaseYear,
            coverUrl = field("Poster") ?: result.coverUrl,
            genres = field("Genre") ?: result.genres,
            rating = field("imdbRating")?.toDoubleOrNull() ?: result.rating,
            extra = extras,
        )
    }

    private suspend fun enrich(hit: JsonObject, key: String): MetadataResult? {
        val imdbId = hit.string("imdbID")
        val full = imdbId?.let {
            runCatching {
                val url = "https://www.omdbapi.com/?apikey=$key&plot=short&i=$it"
                ReliquaryJson.parseToJsonElement(client.get(url).bodyAsText()).obj()
            }.getOrNull()
        }
        val o = full ?: hit
        val title = o.string("Title") ?: hit.string("Title") ?: return null
        fun field(name: String) = o.string(name)?.takeIf { it.isNotBlank() && it != "N/A" }
        return MetadataResult(
            providerId = id,
            providerName = displayName,
            mediaType = mediaType,
            title = title,
            creators = field("Director"),
            releaseYear = yearFrom(field("Year") ?: hit.string("Year")),
            description = field("Plot"),
            coverUrl = field("Poster") ?: hit.string("Poster")?.takeIf { it != "N/A" },
            genres = field("Genre"),
            format = field("Rated"),
            identifierType = "IMDb",
            identifier = imdbId,
        )
    }
}
