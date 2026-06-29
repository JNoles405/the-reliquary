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
import com.reliquary.app.metadata.strings
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlinx.serialization.json.JsonObject

/**
 * Imports a user's Discogs collection (folder 0 = "All") via the Discogs API
 * using their personal token. Each release becomes a Music item with cover art,
 * artist(s), year, format and genres, and the Discogs release id as identifier
 * so re-imports merge instead of duplicating.
 */
class DiscogsImporter(
    private val client: HttpClient,
    private val keys: ApiKeyStore,
    private val repository: ReliquaryRepository,
) {
    private val ua = "TheReliquary/0.1 (https://github.com/JNoles405/the-reliquary)"

    /** Import the whole collection. Returns count imported, or -1 on a config/auth error. */
    suspend fun importCollection(): Int {
        val token = keys.get(ApiKeys.DISCOGS) ?: return -1
        val user = keys.get(ApiKeys.DISCOGS_USER)?.trim()?.trim('@') ?: return -1
        if (user.isBlank()) return -1

        var page = 1
        var pages = 1
        var count = 0
        while (page <= pages && page <= 100) {
            val body = client.get(
                "https://api.discogs.com/users/$user/collection/folders/0/releases" +
                    "?per_page=100&page=$page&token=$token",
            ) { header(HttpHeaders.UserAgent, ua) }.bodyAsText()
            val root = ReliquaryJson.parseToJsonElement(body).obj() ?: break
            pages = root.get("pagination").obj()?.long("pages")?.toInt() ?: 1
            val releases = root.array("releases") ?: break
            if (releases.isEmpty()) break
            releases.forEach { el ->
                val rel = el as? JsonObject ?: return@forEach
                val info = rel.get("basic_information").obj() ?: return@forEach
                val title = info.string("title")?.takeIf { it.isNotBlank() } ?: return@forEach
                val artists = info.array("artists")
                    ?.mapNotNull { (it as? JsonObject)?.string("name")?.replace(Regex("\\s*\\(\\d+\\)$"), "") }
                    ?.joinToString(", ")
                val formats = info.array("formats")
                    ?.mapNotNull { (it as? JsonObject)?.string("name") }
                    ?.distinct()?.joinToString(", ")
                val genres = info.array("genres")?.strings()?.joinToString(", ")
                val releaseId = rel.long("id") ?: info.long("id")
                val now = nowMillis()
                repository.importOrUpdate(
                    CollectionItem(
                        id = newId(),
                        mediaType = MediaType.MUSIC.name,
                        title = title,
                        creators = artists,
                        releaseYear = info.long("year")?.takeIf { it > 0 },
                        coverUrl = info.string("cover_image") ?: info.string("thumb"),
                        genres = genres,
                        format = formats,
                        identifierType = releaseId?.let { "Discogs" },
                        identifier = releaseId?.toString(),
                        addedAt = now,
                        updatedAt = now,
                    ),
                )
                count++
            }
            page++
        }
        return count
    }
}
