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

    /** Streaming providers (flatrate) for a TMDB title in [region], via TMDB's JustWatch data. */
    suspend fun watchProviders(mediaType: MediaType, tmdbId: String, region: String = "US"): List<String> {
        val key = keys.get(ApiKeys.TMDB) ?: return emptyList()
        val path = if (mediaType == MediaType.TV || mediaType == MediaType.ANIME) "tv" else "movie"
        val url = "https://api.themoviedb.org/3/$path/$tmdbId/watch/providers?api_key=$key"
        val root = runCatching { ReliquaryJson.parseToJsonElement(client.get(url).bodyAsText()).obj() }.getOrNull()
            ?: return emptyList()
        val country = root["results"].obj()?.get(region).obj() ?: return emptyList()
        return country.array("flatrate")?.mapNotNull { it.obj()?.string("provider_name") }?.distinct().orEmpty()
    }

    /** TMDB recommendations for a title the user owns. */
    suspend fun recommendations(mediaType: MediaType, tmdbId: String): List<MetadataResult> {
        val key = keys.get(ApiKeys.TMDB) ?: return emptyList()
        val type = if (mediaType == MediaType.TV || mediaType == MediaType.ANIME) MediaType.TV else MediaType.MOVIES
        val path = if (type == MediaType.TV) "tv" else "movie"
        val titleKey = if (type == MediaType.TV) "name" else "title"
        val dateKey = if (type == MediaType.TV) "first_air_date" else "release_date"
        val url = "https://api.themoviedb.org/3/$path/$tmdbId/recommendations?api_key=$key"
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
                extra = o.string("backdrop_path")?.let { mapOf("_backdrop" to "https://image.tmdb.org/t/p/w1280$it") } ?: emptyMap(),
            )
        }
    }

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
            val backdrop = o.string("backdrop_path")?.let { "https://image.tmdb.org/t/p/w1280$it" }
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
                rating = o.double("vote_average")?.takeIf { it > 0 },
                extra = backdrop?.let { mapOf("_backdrop" to it) } ?: emptyMap(),
            )
        }
    }

    /** Popular books from Open Library — keyless, so Discover stays full even with no API keys. */
    suspend fun trendingBooks(): List<MetadataResult> {
        val url = "https://openlibrary.org/trending/weekly.json"
        val works = runCatching {
            ReliquaryJson.parseToJsonElement(
                client.get(url) { header(HttpHeaders.UserAgent, "TheReliquary/0.1") }.bodyAsText(),
            ).obj()?.array("works")
        }.getOrNull() ?: return emptyList()
        return works.mapNotNull { el ->
            val o = el.obj() ?: return@mapNotNull null
            val title = o.string("title") ?: return@mapNotNull null
            val coverId = o.long("cover_i")
            val authors = o.array("author_name")?.strings()?.joinToString(", ")?.takeIf { it.isNotBlank() }
            MetadataResult(
                providerId = "openlibrary",
                providerName = "Open Library",
                mediaType = MediaType.BOOKS,
                title = title,
                creators = authors,
                releaseYear = o.long("first_publish_year"),
                coverUrl = coverId?.let { "https://covers.openlibrary.org/b/id/$it-L.jpg" },
                identifierType = "OpenLibrary",
                identifier = o.string("key"),
            )
        }
    }

    /**
     * Fetch a richer view (cast, backdrop, genres) for a Discover card the user
     * opened. Only TMDB movies/TV expose credits; other sources return as-is.
     */
    suspend fun detailsFor(result: MetadataResult): MetadataResult = when {
        result.providerId == "tmdb" && result.mediaType == MediaType.MOVIES -> tmdbDetails("movie", result, "title", "release_date")
        result.providerId == "tmdb" && result.mediaType == MediaType.TV -> tmdbDetails("tv", result, "name", "first_air_date")
        else -> result
    }

    private suspend fun tmdbDetails(path: String, result: MetadataResult, titleKey: String, dateKey: String): MetadataResult {
        val key = keys.get(ApiKeys.TMDB) ?: return result
        val id = result.identifier ?: return result
        val url = "https://api.themoviedb.org/3/$path/$id?api_key=$key&append_to_response=credits"
        val root = runCatching {
            ReliquaryJson.parseToJsonElement(client.get(url).bodyAsText()).obj()
        }.getOrNull() ?: return result
        val credits = root["credits"].obj()
        val cast = credits?.array("cast")?.mapNotNull { it.obj() }?.take(12)?.joinToString(", ") { c ->
            val name = c.string("name").orEmpty()
            val role = c.string("character")
            if (!role.isNullOrBlank()) "$name ($role)" else name
        }?.takeIf { it.isNotBlank() }
        val crew = credits?.array("crew")?.mapNotNull { it.obj() }.orEmpty()
        val director = crew.filter { it.string("job") in setOf("Director") }
            .mapNotNull { it.string("name") }.distinct().joinToString(", ").takeIf { it.isNotBlank() }
        val genres = root.array("genres")?.mapNotNull { it.obj()?.string("name") }?.joinToString(", ")?.takeIf { it.isNotBlank() }
        val backdrop = root.string("backdrop_path")?.let { "https://image.tmdb.org/t/p/w1280$it" }
        val runtime = root.long("runtime")?.takeIf { it > 0 }
        val extra = result.extra.toMutableMap()
        cast?.let { extra["Cast"] = it }
        director?.let { extra["Director"] = it }
        runtime?.let { extra["Runtime"] = "$it min" }
        backdrop?.let { extra["_backdrop"] = it }
        return result.copy(
            title = root.string(titleKey) ?: result.title,
            description = root.string("overview")?.takeIf { it.isNotBlank() } ?: result.description,
            releaseYear = yearFrom(root.string(dateKey)) ?: result.releaseYear,
            genres = genres ?: result.genres,
            rating = root.double("vote_average")?.takeIf { it > 0 } ?: result.rating,
            extra = extra,
        )
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
