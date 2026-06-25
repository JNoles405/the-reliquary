package com.reliquary.app.metadata.providers

import com.reliquary.app.domain.MediaType
import com.reliquary.app.metadata.ApiKeyStore
import com.reliquary.app.metadata.ApiKeys
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
import kotlinx.serialization.json.JsonObject

/**
 * Movie metadata via TMDB. Key-gated: activates once a (free) TMDB API key is
 * saved in Settings. TMDB has no barcode index, so only title search applies.
 */
class TmdbProvider(
    private val client: HttpClient,
    private val keys: ApiKeyStore,
) : MetadataProvider {
    override val id = "tmdb"
    override val displayName = "TMDB"
    override val mediaType = MediaType.MOVIES
    override val requiresApiKey = true
    override val isEnabled get() = keys.has(ApiKeys.TMDB)

    override suspend fun search(query: String): List<MetadataResult> {
        val key = keys.get(ApiKeys.TMDB) ?: return emptyList()
        val url = "https://api.themoviedb.org/3/search/movie" +
            "?include_adult=false&api_key=$key&query=${query.encodeURLParameter()}"
        val results = ReliquaryJson.parseToJsonElement(client.get(url).bodyAsText())
            .obj()?.array("results") ?: return emptyList()
        return results.mapNotNull { it.obj()?.toResult() }
    }

    // TMDB does not index retail barcodes (UPC/EAN), so there is nothing to look up.
    override suspend fun lookupByBarcode(barcode: String): List<MetadataResult> = emptyList()

    private fun JsonObject.toResult(): MetadataResult? {
        val title = string("title") ?: return null
        val poster = string("poster_path")?.let { "https://image.tmdb.org/t/p/w500$it" }
        return MetadataResult(
            providerId = id,
            providerName = displayName,
            mediaType = mediaType,
            title = title,
            releaseYear = yearFrom(string("release_date")),
            description = string("overview"),
            coverUrl = poster,
            identifierType = "TMDB",
            identifier = string("id"),
        )
    }
}
