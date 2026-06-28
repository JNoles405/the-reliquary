package com.reliquary.app.metadata.providers

import com.reliquary.app.domain.MediaType
import com.reliquary.app.metadata.ApiKeyStore
import com.reliquary.app.metadata.ApiKeys
import com.reliquary.app.metadata.MetadataProvider
import com.reliquary.app.metadata.MetadataResult
import com.reliquary.app.metadata.ReliquaryJson
import com.reliquary.app.metadata.array
import com.reliquary.app.metadata.double
import com.reliquary.app.metadata.long
import com.reliquary.app.metadata.obj
import com.reliquary.app.metadata.string
import com.reliquary.app.metadata.strings
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.encodeURLParameter
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/**
 * TV / Anime metadata via Simkl (also covers movies). Key-only: activates once a
 * Simkl client_id is saved in Settings. Search returns title/year/poster/ids;
 * details() fetches overview, genres, network, runtime, and rating. Simkl has no
 * barcode index, so the MetadataService resolves a UPC to a title first.
 *
 * @param simklType one of "tv", "anime", "movies" (the API path segment).
 */
class SimklProvider(
    private val client: HttpClient,
    private val keys: ApiKeyStore,
    override val mediaType: MediaType,
    private val simklType: String,
) : MetadataProvider {
    override val id = "simkl-$simklType"
    override val displayName = "Simkl"
    override val requiresApiKey = true
    override val isEnabled get() = keys.has(ApiKeys.SIMKL)

    private val searchType = if (simklType == "movies") "movie" else simklType

    override suspend fun search(query: String): List<MetadataResult> {
        val key = keys.get(ApiKeys.SIMKL) ?: return emptyList()
        val url = "https://api.simkl.com/search/$searchType?client_id=$key&q=${query.encodeURLParameter()}"
        val body = client.get(url) { header(HttpHeaders.UserAgent, USER_AGENT) }.bodyAsText()
        val arr = ReliquaryJson.parseToJsonElement(body) as? JsonArray ?: return emptyList()
        return arr.mapNotNull { it.obj()?.toResult() }
    }

    override suspend fun lookupByBarcode(barcode: String): List<MetadataResult> = emptyList()

    override suspend fun details(result: MetadataResult): MetadataResult? {
        val key = keys.get(ApiKeys.SIMKL) ?: return null
        val id = result.identifier ?: return null
        val url = "https://api.simkl.com/$simklType/$id?extended=full&client_id=$key"
        val o = ReliquaryJson.parseToJsonElement(client.get(url) { header(HttpHeaders.UserAgent, USER_AGENT) }.bodyAsText())
            .obj() ?: return null
        val rating = o["ratings"]?.obj()?.get("simkl")?.obj()?.double("rating")
            ?: o["ratings"]?.obj()?.get("imdb")?.obj()?.double("rating")
        val tmdbId = result.extra["_tmdb"]
        val hasTmdb = tmdbId != null && keys.has(ApiKeys.TMDB)
        val cast = if (hasTmdb) fetchTmdbCast(tmdbId!!) else null
        val backdrop = if (hasTmdb) fetchTmdbBackdrop(tmdbId!!) else null
        val extras = buildMap {
            o.string("network")?.let { put("Network", it) }
            o.string("country")?.let { put("Country", it) }
            o.long("runtime")?.takeIf { it > 0 }?.let { put("Runtime", "$it min") }
            o.long("total_episodes")?.takeIf { it > 0 }?.let { put("Episodes", it.toString()) }
            o.string("status")?.let { put("Airing status", it) }
            cast?.let { put("Cast", it) }
            // Wide still for the detail hero banner.
            backdrop?.let { put("_backdrop", it) }
        }
        return result.copy(
            description = o.string("overview")?.takeIf { it.isNotBlank() } ?: result.description,
            genres = o.array("genres")?.strings()?.joinToString(", ") ?: result.genres,
            rating = rating ?: result.rating,
            extra = extras,
        )
    }

    private fun JsonObject.toResult(): MetadataResult? {
        val title = string("title") ?: return null
        val ids = this["ids"].obj()
        val simklId = ids?.long("simkl")?.toString() ?: ids?.string("simkl")
        val tmdbId = ids?.long("tmdb")?.toString() ?: ids?.string("tmdb")
        val poster = string("poster")?.let { "https://simkl.in/posters/${it}_m.jpg" }
        return MetadataResult(
            providerId = id,
            providerName = displayName,
            mediaType = mediaType,
            title = title,
            releaseYear = long("year"),
            coverUrl = poster,
            identifierType = "Simkl",
            identifier = simklId,
            // Hidden, used by details() to pull a cast list from TMDB if a key is set.
            extra = tmdbId?.let { mapOf("_tmdb" to it) } ?: emptyMap(),
        )
    }

    private suspend fun fetchTmdbBackdrop(tmdbId: String): String? {
        val tmdbKey = keys.get(ApiKeys.TMDB) ?: return null
        // Simkl TV/Anime map to TMDB TV ids; fall back to the movie endpoint if needed.
        for (path in listOf("tv", "movie")) {
            val url = "https://api.themoviedb.org/3/$path/$tmdbId?api_key=$tmdbKey"
            val o = runCatching {
                ReliquaryJson.parseToJsonElement(
                    client.get(url) { header(HttpHeaders.UserAgent, USER_AGENT) }.bodyAsText(),
                ).obj()
            }.getOrNull() ?: continue
            o.string("backdrop_path")?.let { return "https://image.tmdb.org/t/p/w1280$it" }
        }
        return null
    }

    private suspend fun fetchTmdbCast(tmdbId: String): String? {
        val tmdbKey = keys.get(ApiKeys.TMDB) ?: return null
        val url = "https://api.themoviedb.org/3/tv/$tmdbId/credits?api_key=$tmdbKey"
        val o = runCatching {
            ReliquaryJson.parseToJsonElement(client.get(url) { header(HttpHeaders.UserAgent, USER_AGENT) }.bodyAsText()).obj()
        }.getOrNull() ?: return null
        return o.array("cast")?.mapNotNull { it.obj() }?.take(12)?.joinToString(", ") { c ->
            val name = c.string("name").orEmpty()
            val role = c.string("character")
            if (!role.isNullOrBlank()) "$name ($role)" else name
        }?.takeIf { it.isNotBlank() }
    }

    private companion object {
        const val USER_AGENT = "TheReliquary/0.1 (https://github.com/JNoles405/the-reliquary)"
    }
}
