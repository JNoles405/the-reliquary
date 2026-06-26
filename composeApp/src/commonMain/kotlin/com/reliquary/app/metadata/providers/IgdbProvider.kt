package com.reliquary.app.metadata.providers

import com.reliquary.app.data.nowMillis
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
import com.reliquary.app.metadata.yearFromEpochSeconds
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/**
 * Game metadata via IGDB. Key-gated by Twitch client id + secret (IGDB auth
 * runs through Twitch OAuth). The app fetches and caches a bearer token, then
 * queries IGDB's apicalypse endpoint.
 */
class IgdbProvider(
    private val client: HttpClient,
    private val keys: ApiKeyStore,
) : MetadataProvider {
    override val id = "igdb"
    override val displayName = "IGDB"
    override val mediaType = MediaType.GAMES
    override val requiresApiKey = true
    override val isEnabled get() = keys.has(ApiKeys.IGDB_CLIENT_ID) && keys.has(ApiKeys.IGDB_CLIENT_SECRET)

    private var token: String? = null
    private var tokenExpiresAt: Long = 0

    private suspend fun ensureToken(): String? {
        val now = nowMillis()
        token?.let { if (now < tokenExpiresAt - 60_000) return it }
        val clientId = keys.get(ApiKeys.IGDB_CLIENT_ID) ?: return null
        val secret = keys.get(ApiKeys.IGDB_CLIENT_SECRET) ?: return null
        val url = "https://id.twitch.tv/oauth2/token" +
            "?client_id=$clientId&client_secret=$secret&grant_type=client_credentials"
        val obj = ReliquaryJson.parseToJsonElement(client.post(url).bodyAsText()).obj() ?: return null
        val access = obj.string("access_token") ?: return null
        token = access
        tokenExpiresAt = now + (obj.long("expires_in") ?: 3600L) * 1000L
        return access
    }

    override suspend fun search(query: String): List<MetadataResult> {
        val bearer = ensureToken() ?: return emptyList()
        val clientId = keys.get(ApiKeys.IGDB_CLIENT_ID) ?: return emptyList()
        val safe = query.replace("\"", "")
        val apicalypse = "search \"$safe\"; fields name,summary,first_release_date," +
            "cover.image_id,genres.name,platforms.name,game_modes.name,total_rating," +
            "involved_companies.company.name,involved_companies.developer,involved_companies.publisher; " +
            "limit 20;"
        val body = client.post("https://api.igdb.com/v4/games") {
            header("Client-ID", clientId)
            header(HttpHeaders.Authorization, "Bearer $bearer")
            setBody(apicalypse)
        }.bodyAsText()
        val games = ReliquaryJson.parseToJsonElement(body) as? JsonArray ?: return emptyList()
        return games.mapNotNull { it.obj()?.toResult() }
    }

    // IGDB indexes games, not retail barcodes.
    override suspend fun lookupByBarcode(barcode: String): List<MetadataResult> = emptyList()

    private fun JsonObject.toResult(): MetadataResult? {
        val name = string("name") ?: return null
        val imageId = this["cover"]?.obj()?.string("image_id")
        val cover = imageId?.let { "https://images.igdb.com/igdb/image/upload/t_cover_big/$it.jpg" }
        val genres = array("genres")?.mapNotNull { it.obj()?.string("name") }?.joinToString(", ")
        val platforms = array("platforms")?.mapNotNull { it.obj()?.string("name") }?.joinToString(", ")
        val companies = array("involved_companies")?.mapNotNull { it.obj() }.orEmpty()
        fun company(role: String) = companies
            .filter { it.string(role) == "true" }
            .mapNotNull { it["company"].obj()?.string("name") }
            .distinct().joinToString(", ").takeIf { it.isNotBlank() }
        val modes = array("game_modes")?.mapNotNull { it.obj()?.string("name") }?.joinToString(", ")
        val rating = double("total_rating")?.takeIf { it > 0 }?.let { it / 10.0 }
        return MetadataResult(
            providerId = id,
            providerName = displayName,
            mediaType = mediaType,
            title = name,
            releaseYear = long("first_release_date")?.let { yearFromEpochSeconds(it) },
            description = string("summary"),
            coverUrl = cover,
            genres = genres,
            format = platforms,
            rating = rating,
            identifierType = "IGDB",
            identifier = string("id"),
            extra = buildMap {
                company("developer")?.let { put("Developer", it) }
                company("publisher")?.let { put("Publisher", it) }
                platforms?.let { put("Platforms", it) }
                modes?.let { put("Modes", it) }
            },
        )
    }
}
