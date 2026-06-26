package com.reliquary.app.integrations

import com.reliquary.app.data.ReliquaryRepository
import com.reliquary.app.data.newId
import com.reliquary.app.data.nowMillis
import com.reliquary.app.domain.CollectionItem
import com.reliquary.app.domain.MediaType
import com.reliquary.app.metadata.ApiKeyStore
import com.reliquary.app.metadata.ApiKeys
import com.reliquary.app.metadata.ReliquaryJson
import com.reliquary.app.metadata.array
import com.reliquary.app.metadata.long
import com.reliquary.app.metadata.obj
import com.reliquary.app.metadata.string
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/**
 * Imports a Simkl account's library (movies / TV / anime) via the PIN device
 * flow: request a user code, the user enters it at simkl.com/pin, then we poll
 * for an access token and pull sync/all-items. Items carry status + IMDb/TMDB
 * ids so they merge with other imports.
 */
class SimklImporter(
    private val client: HttpClient,
    private val keys: ApiKeyStore,
    private val repository: ReliquaryRepository,
) {
    data class Pin(val userCode: String, val verificationUrl: String, val expiresIn: Int, val interval: Int)

    private val ua = "TheReliquary/0.1 (https://github.com/JNoles405/the-reliquary)"

    suspend fun requestPin(): Pin? {
        val id = keys.get(ApiKeys.SIMKL) ?: return null
        val o = ReliquaryJson.parseToJsonElement(
            client.get("https://api.simkl.com/oauth/pin?client_id=$id") { header(HttpHeaders.UserAgent, ua) }.bodyAsText(),
        ).obj() ?: return null
        val code = o.string("user_code") ?: return null
        return Pin(
            userCode = code,
            verificationUrl = o.string("verification_url") ?: "https://simkl.com/pin",
            expiresIn = o.long("expires_in")?.toInt() ?: 900,
            interval = (o.long("interval")?.toInt() ?: 5).coerceAtLeast(2),
        )
    }

    /** Returns the access token once authorized, or null while still pending. */
    suspend fun poll(userCode: String): String? {
        val id = keys.get(ApiKeys.SIMKL) ?: return null
        val o = ReliquaryJson.parseToJsonElement(
            client.get("https://api.simkl.com/oauth/pin/$userCode?client_id=$id") { header(HttpHeaders.UserAgent, ua) }.bodyAsText(),
        ).obj()
        return if (o?.string("result") == "OK") o.string("access_token") else null
    }

    suspend fun importAll(token: String): Int {
        val id = keys.get(ApiKeys.SIMKL) ?: return 0
        val body = client.get("https://api.simkl.com/sync/all-items/?extended=full") {
            header(HttpHeaders.Authorization, "Bearer $token")
            header("simkl-api-key", id)
            header(HttpHeaders.UserAgent, ua)
        }.bodyAsText()
        val root = ReliquaryJson.parseToJsonElement(body).obj() ?: return 0
        var count = 0
        count += importSection(root.array("movies"), MediaType.MOVIES)
        count += importSection(root.array("shows"), MediaType.TV)
        count += importSection(root.array("anime"), MediaType.ANIME)
        return count
    }

    private fun importSection(arr: JsonArray?, mediaType: MediaType): Int {
        if (arr == null) return 0
        var count = 0
        arr.forEach { element ->
            val entry = element.obj() ?: return@forEach
            val media = entry["movie"].obj() ?: entry["show"].obj() ?: entry["anime"].obj() ?: return@forEach
            val title = media.string("title") ?: return@forEach
            val ids = media["ids"].obj()
            val tmdb = ids?.long("tmdb")?.toString() ?: ids?.string("tmdb")
            val imdb = ids?.string("imdb")
            val simkl = ids?.long("simkl")?.toString()
            val (idType, idValue) = when {
                tmdb != null -> "TMDB" to tmdb
                imdb != null -> "IMDb" to imdb
                else -> "Simkl" to simkl
            }
            val now = nowMillis()
            repository.importOrUpdate(
                CollectionItem(
                    id = newId(),
                    mediaType = mediaType.name,
                    title = title,
                    releaseYear = media.long("year"),
                    coverUrl = media.string("poster")?.let { "https://simkl.in/posters/${it}_m.jpg" },
                    identifierType = idType,
                    identifier = idValue,
                    status = mapStatus(entry.string("status"), mediaType),
                    addedAt = now,
                    updatedAt = now,
                ),
            )
            count++
        }
        return count
    }

    private fun mapStatus(simklStatus: String?, mediaType: MediaType): String? = when (simklStatus) {
        "completed" -> if (mediaType == MediaType.ANIME) "Completed" else "Watched"
        "watching" -> "Watching"
        "plantowatch" -> "Plan to watch"
        else -> null
    }
}
