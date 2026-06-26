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

    override suspend fun details(result: MetadataResult): MetadataResult? {
        val key = keys.get(ApiKeys.TMDB) ?: return null
        val id = result.identifier ?: return null
        val url = "https://api.themoviedb.org/3/movie/$id" +
            "?api_key=$key&append_to_response=credits,release_dates"
        val root = ReliquaryJson.parseToJsonElement(client.get(url).bodyAsText()).obj() ?: return null

        val credits = root["credits"].obj()
        val crew = credits?.array("crew")?.mapNotNull { it.obj() }.orEmpty()
        fun job(vararg names: String): String? = crew
            .filter { it.string("job") in names }
            .mapNotNull { it.string("name") }
            .distinct().joinToString(", ").takeIf { it.isNotBlank() }
        val cast = credits?.array("cast")?.mapNotNull { it.obj() }?.take(12)?.joinToString(", ") { c ->
            val name = c.string("name").orEmpty()
            val role = c.string("character")
            if (!role.isNullOrBlank()) "$name ($role)" else name
        }?.takeIf { it.isNotBlank() }

        val certification = root["release_dates"].obj()?.array("results")
            ?.mapNotNull { it.obj() }
            ?.firstOrNull { it.string("iso_3166_1") == "US" }
            ?.array("release_dates")?.mapNotNull { it.obj()?.string("certification") }
            ?.firstOrNull { it.isNotBlank() }

        val studio = root.array("production_companies")?.mapNotNull { it.obj()?.string("name") }?.firstOrNull()
        val country = root.array("production_countries")?.mapNotNull { it.obj()?.string("name") }?.firstOrNull()
        val language = root.array("spoken_languages")
            ?.mapNotNull { it.obj()?.let { o -> o.string("english_name") ?: o.string("name") } }?.firstOrNull()
        val runtime = root.long("runtime")?.takeIf { it > 0 }
        val genres = root.array("genres")?.mapNotNull { it.obj()?.string("name") }?.joinToString(", ")

        val extras = buildMap {
            job("Director")?.let { put("Director", it) }
            job("Screenplay", "Writer", "Story")?.let { put("Writer", it) }
            job("Producer")?.let { put("Producer", it) }
            job("Director of Photography")?.let { put("Cinematography", it) }
            job("Original Music Composer", "Music")?.let { put("Music", it) }
            studio?.let { put("Studio", it) }
            runtime?.let { put("Runtime", "$it min") }
            certification?.let { put("Rated", it) }
            country?.let { put("Country", it) }
            language?.let { put("Language", it) }
            root.string("tagline")?.takeIf { it.isNotBlank() }?.let { put("Tagline", it) }
            cast?.let { put("Cast", it) }
        }

        return result.copy(
            title = root.string("title") ?: result.title,
            description = root.string("overview")?.takeIf { it.isNotBlank() } ?: result.description,
            releaseYear = yearFrom(root.string("release_date")) ?: result.releaseYear,
            coverUrl = root.string("poster_path")?.let { "https://image.tmdb.org/t/p/w500$it" } ?: result.coverUrl,
            genres = genres ?: result.genres,
            rating = root.double("vote_average")?.takeIf { it > 0 } ?: result.rating,
            extra = extras,
        )
    }

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
