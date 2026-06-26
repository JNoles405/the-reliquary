package com.reliquary.app.metadata

import com.reliquary.app.domain.MediaType
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlinx.serialization.json.JsonArray

/**
 * Trending titles for the Discover tab. Movies and TV come from TMDB (key),
 * anime from Simkl (client_id). Results are normalized so a tap can add them to
 * the wishlist.
 */
class DiscoverService(private val client: HttpClient, private val keys: ApiKeyStore) {

    val hasTmdb: Boolean get() = keys.has(ApiKeys.TMDB)
    val hasSimkl: Boolean get() = keys.has(ApiKeys.SIMKL)

    suspend fun trendingMovies(): List<MetadataResult> = tmdbTrending("movie", MediaType.MOVIES, "title", "release_date")
    suspend fun trendingTv(): List<MetadataResult> = tmdbTrending("tv", MediaType.TV, "name", "first_air_date")

    private suspend fun tmdbTrending(path: String, type: MediaType, titleKey: String, dateKey: String): List<MetadataResult> {
        val key = keys.get(ApiKeys.TMDB) ?: return emptyList()
        val url = "https://api.themoviedb.org/3/trending/$path/week?api_key=$key"
        val results = runCatching {
            ReliquaryJson.parseToJsonElement(client.get(url).bodyAsText()).obj()?.array("results")
        }.getOrNull() ?: return emptyList()
        return results.mapNotNull { el ->
            val o = el.obj() ?: return@mapNotNull null
            val title = o.string(titleKey) ?: return@mapNotNull null
            MetadataResult(
                providerId = "tmdb",
                providerName = "TMDB",
                mediaType = type,
                title = title,
                releaseYear = yearFrom(o.string(dateKey)),
                description = o.string("overview"),
                coverUrl = o.string("poster_path")?.let { "https://image.tmdb.org/t/p/w780$it" },
                identifierType = "TMDB",
                identifier = o.string("id"),
            )
        }
    }

    suspend fun trendingAnime(): List<MetadataResult> {
        val key = keys.get(ApiKeys.SIMKL) ?: return emptyList()
        val url = "https://api.simkl.com/anime/trending?client_id=$key"
        val arr = runCatching {
            ReliquaryJson.parseToJsonElement(
                client.get(url) { header(HttpHeaders.UserAgent, "TheReliquary/0.1") }.bodyAsText(),
            ) as? JsonArray
        }.getOrNull() ?: return emptyList()
        return arr.mapNotNull { el ->
            val outer = el.obj() ?: return@mapNotNull null
            val o = outer["anime"].obj() ?: outer["show"].obj() ?: outer
            val title = o.string("title") ?: return@mapNotNull null
            val ids = o["ids"].obj()
            MetadataResult(
                providerId = "simkl-anime",
                providerName = "Simkl",
                mediaType = MediaType.ANIME,
                title = title,
                releaseYear = o.long("year"),
                coverUrl = o.string("poster")?.let { "https://simkl.in/posters/${it}_m.jpg" },
                identifierType = "Simkl",
                identifier = (ids?.long("simkl")?.toString() ?: ids?.string("simkl")),
            )
        }
    }
}
