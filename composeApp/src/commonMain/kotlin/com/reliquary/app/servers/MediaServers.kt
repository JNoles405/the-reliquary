package com.reliquary.app.servers

import com.reliquary.app.data.ReliquaryRepository
import com.reliquary.app.data.newId
import com.reliquary.app.data.nowMillis
import com.reliquary.app.domain.CollectionItem
import com.reliquary.app.domain.MediaType
import com.reliquary.app.domain.PROGRESS_KEY
import com.reliquary.app.domain.PROGRESS_TOTAL_KEY
import com.reliquary.app.domain.Status
import com.reliquary.app.metadata.ReliquaryJson
import com.reliquary.app.metadata.array
import com.reliquary.app.metadata.long
import com.reliquary.app.metadata.obj
import com.reliquary.app.metadata.string
import com.reliquary.app.metadata.strings
import com.reliquary.app.util.AppInfo
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

@Serializable
enum class ServerType(val label: String) { JELLYFIN("Jellyfin"), PLEX("Plex") }

/** A saved connection to a media server. Persisted (token included) in settings. */
@Serializable
data class ServerConfig(
    val type: ServerType,
    val name: String,
    val url: String,
    val token: String,
    val userId: String = "",
    val machineId: String = "",
)

/** A browsable library/section on the server. [mediaType] is null when unsupported. */
data class ServerLibrary(val id: String, val title: String, val mediaType: MediaType?)

/** A normalized item from either server, ready to import. */
data class ServerItem(
    val serverItemId: String,
    val title: String,
    val year: Long?,
    val overview: String?,
    val genres: String?,
    val mediaType: MediaType,
    val posterUrl: String?,
    val backdropUrl: String?,
    val watched: Boolean,
    val progressMinutes: Long?,
    val totalMinutes: Long?,
    val playUrl: String?,
)

/**
 * Connects to Plex and Jellyfin servers and imports their libraries into the
 * collection (with poster/backdrop art, watched state and resume progress).
 * Tokens are stored locally in settings, never committed.
 */
class MediaServerService(
    private val client: HttpClient,
    private val repository: ReliquaryRepository,
) {
    private val json = ReliquaryJson

    fun connections(): List<ServerConfig> = repository.getSetting(SETTING)
        ?.let { runCatching { json.decodeFromString<List<ServerConfig>>(it) }.getOrNull() }
        ?: emptyList()

    private fun persist(list: List<ServerConfig>) = repository.setSetting(SETTING, json.encodeToString(list))

    private fun upsert(cfg: ServerConfig) =
        persist(connections().filterNot { it.type == cfg.type && it.url == cfg.url } + cfg)

    fun removeConnection(cfg: ServerConfig) =
        persist(connections().filterNot { it.type == cfg.type && it.url == cfg.url })

    private fun base(url: String) = url.trim().trimEnd('/')

    // ---- Connect ------------------------------------------------------------

    /** Authenticate to Jellyfin with username/password; only the token is stored. */
    suspend fun connectJellyfin(url: String, user: String, pass: String): Result<ServerConfig> = runCatching {
        val b = base(url)
        val body = "{\"Username\":${json.encodeToString(user)},\"Pw\":${json.encodeToString(pass)}}"
        val resp = client.post("$b/Users/AuthenticateByName") {
            header(HttpHeaders.Authorization, AUTH_HEADER)
            contentType(ContentType.Application.Json)
            setBody(body)
        }.bodyAsText()
        val o = json.parseToJsonElement(resp).obj() ?: error("Unexpected response from server.")
        val token = o.string("AccessToken") ?: error("Login failed — check the username and password.")
        val userObj = o["User"].obj()
        val userId = userObj?.string("Id") ?: error("Could not read the Jellyfin user.")
        val name = userObj.string("Name")?.let { "Jellyfin ($it)" } ?: "Jellyfin"
        ServerConfig(ServerType.JELLYFIN, name, b, token, userId = userId).also { upsert(it) }
    }

    /** Validate a Plex server URL + X-Plex-Token and store it. */
    suspend fun connectPlex(url: String, token: String): Result<ServerConfig> = runCatching {
        val b = base(url)
        val resp = client.get("$b/identity") {
            header("X-Plex-Token", token)
            header(HttpHeaders.Accept, "application/json")
        }.bodyAsText()
        val mc = json.parseToJsonElement(resp).obj()?.get("MediaContainer").obj()
            ?: error("That doesn't look like a Plex server (check the URL and token).")
        val machineId = mc.string("machineIdentifier").orEmpty()
        ServerConfig(ServerType.PLEX, "Plex", b, token, machineId = machineId).also { upsert(it) }
    }

    // ---- Browse -------------------------------------------------------------

    suspend fun libraries(cfg: ServerConfig): List<ServerLibrary> = when (cfg.type) {
        ServerType.JELLYFIN -> jellyfinLibraries(cfg)
        ServerType.PLEX -> plexLibraries(cfg)
    }

    suspend fun items(cfg: ServerConfig, library: ServerLibrary): List<ServerItem> = when (cfg.type) {
        ServerType.JELLYFIN -> jellyfinItems(cfg, library)
        ServerType.PLEX -> plexItems(cfg, library)
    }

    // ---- Import -------------------------------------------------------------

    /** Import [items] into the collection, merging with any existing duplicates. */
    fun importItems(cfg: ServerConfig, items: List<ServerItem>): Int {
        items.forEach { si ->
            val now = nowMillis()
            val extra = buildMap {
                si.backdropUrl?.let { put("_backdrop", it) }
                si.playUrl?.let { put("_serverPlayUrl", it) }
                put("_server", cfg.name)
                if (si.progressMinutes != null && si.totalMinutes != null && si.totalMinutes > 0) {
                    put(PROGRESS_KEY, si.progressMinutes.toString())
                    put(PROGRESS_TOTAL_KEY, si.totalMinutes.toString())
                }
            }
            val options = Status.optionsFor(si.mediaType.name)
            val status = when {
                si.watched -> options.last()
                (si.progressMinutes ?: 0) > 0 -> options.firstOrNull { it in Status.IN_PROGRESS }
                else -> null
            }
            repository.importOrUpdate(
                CollectionItem(
                    id = newId(),
                    mediaType = si.mediaType.name,
                    title = si.title,
                    releaseYear = si.year,
                    description = si.overview,
                    coverUrl = si.posterUrl,
                    genres = si.genres,
                    identifierType = cfg.type.label,
                    identifier = si.serverItemId,
                    extraJson = if (extra.isEmpty()) null else json.encodeToString(extra),
                    status = status,
                    wanted = false,
                    addedAt = now,
                    updatedAt = now,
                ),
            )
        }
        return items.size
    }

    // ---- Jellyfin -----------------------------------------------------------

    private suspend fun jellyfinLibraries(cfg: ServerConfig): List<ServerLibrary> {
        val resp = client.get("${cfg.url}/Users/${cfg.userId}/Views?api_key=${cfg.token}").bodyAsText()
        return json.parseToJsonElement(resp).obj()?.array("Items").orEmpty().mapNotNull { el ->
            val o = el.obj() ?: return@mapNotNull null
            val id = o.string("Id") ?: return@mapNotNull null
            ServerLibrary(id, o.string("Name") ?: "Library", jellyfinMediaType(o.string("CollectionType")))
        }
    }

    private suspend fun jellyfinItems(cfg: ServerConfig, library: ServerLibrary): List<ServerItem> {
        val types = when (library.mediaType) {
            MediaType.MOVIES -> "Movie"
            MediaType.TV, MediaType.ANIME -> "Series"
            MediaType.MUSIC -> "MusicAlbum"
            else -> "Movie,Series"
        }
        val url = "${cfg.url}/Users/${cfg.userId}/Items?ParentId=${library.id}&Recursive=true" +
            "&IncludeItemTypes=$types&Fields=Overview,Genres,ProductionYear,RunTimeTicks&api_key=${cfg.token}"
        val resp = client.get(url).bodyAsText()
        return json.parseToJsonElement(resp).obj()?.array("Items").orEmpty().mapNotNull { el ->
            val o = el.obj() ?: return@mapNotNull null
            val id = o.string("Id") ?: return@mapNotNull null
            val type = jellyfinItemType(o.string("Type")) ?: library.mediaType ?: return@mapNotNull null
            val userData = o["UserData"].obj()
            val watched = userData?.string("Played") == "true"
            val posTicks = userData?.long("PlaybackPositionTicks") ?: 0L
            val runTicks = o.long("RunTimeTicks")
            val poster = o["ImageTags"].obj()?.string("Primary")
                ?.let { "${cfg.url}/Items/$id/Images/Primary?api_key=${cfg.token}" }
            val backdrop = if ((o.array("BackdropImageTags")?.size ?: 0) > 0) {
                "${cfg.url}/Items/$id/Images/Backdrop?api_key=${cfg.token}"
            } else null
            ServerItem(
                serverItemId = id,
                title = o.string("Name") ?: "Untitled",
                year = o.long("ProductionYear"),
                overview = o.string("Overview"),
                genres = o.array("Genres")?.strings()?.joinToString(", ")?.takeIf { it.isNotBlank() },
                mediaType = type,
                posterUrl = poster,
                backdropUrl = backdrop,
                watched = watched,
                progressMinutes = if (type == MediaType.MOVIES && posTicks > 0) posTicks / TICKS_PER_MIN else null,
                totalMinutes = if (type == MediaType.MOVIES) runTicks?.takeIf { it > 0 }?.div(TICKS_PER_MIN) else null,
                playUrl = "${cfg.url}/web/index.html#!/details?id=$id",
            )
        }
    }

    private fun jellyfinMediaType(collectionType: String?): MediaType? = when (collectionType) {
        "movies" -> MediaType.MOVIES
        "tvshows" -> MediaType.TV
        "music" -> MediaType.MUSIC
        else -> null
    }

    private fun jellyfinItemType(type: String?): MediaType? = when (type) {
        "Movie" -> MediaType.MOVIES
        "Series" -> MediaType.TV
        "MusicAlbum" -> MediaType.MUSIC
        else -> null
    }

    // ---- Plex ---------------------------------------------------------------

    private suspend fun plexLibraries(cfg: ServerConfig): List<ServerLibrary> {
        val resp = client.get("${cfg.url}/library/sections") {
            header("X-Plex-Token", cfg.token)
            header(HttpHeaders.Accept, "application/json")
        }.bodyAsText()
        val dirs = json.parseToJsonElement(resp).obj()?.get("MediaContainer").obj()?.array("Directory").orEmpty()
        return dirs.mapNotNull { el ->
            val o = el.obj() ?: return@mapNotNull null
            val key = o.string("key") ?: return@mapNotNull null
            ServerLibrary(key, o.string("title") ?: "Library", plexMediaType(o.string("type")))
        }
    }

    private suspend fun plexItems(cfg: ServerConfig, library: ServerLibrary): List<ServerItem> {
        val resp = client.get("${cfg.url}/library/sections/${library.id}/all") {
            header("X-Plex-Token", cfg.token)
            header(HttpHeaders.Accept, "application/json")
        }.bodyAsText()
        val md = json.parseToJsonElement(resp).obj()?.get("MediaContainer").obj()?.array("Metadata").orEmpty()
        return md.mapNotNull { el ->
            val o = el.obj() ?: return@mapNotNull null
            val ratingKey = o.string("ratingKey") ?: return@mapNotNull null
            val type = plexMediaType(o.string("type")) ?: library.mediaType ?: return@mapNotNull null
            val poster = o.string("thumb")?.let { "${cfg.url}$it?X-Plex-Token=${cfg.token}" }
            val backdrop = o.string("art")?.let { "${cfg.url}$it?X-Plex-Token=${cfg.token}" }
            val viewCount = o.long("viewCount") ?: 0L
            val offsetMs = o.long("viewOffset") ?: 0L
            val durationMs = o.long("duration")
            val genres = o.array("Genre")?.mapNotNull { it.obj()?.string("tag") }?.joinToString(", ")?.takeIf { it.isNotBlank() }
            val playUrl = if (cfg.machineId.isNotBlank()) {
                "${cfg.url}/web/index.html#!/server/${cfg.machineId}/details?key=%2Flibrary%2Fmetadata%2F$ratingKey"
            } else null
            ServerItem(
                serverItemId = ratingKey,
                title = o.string("title") ?: "Untitled",
                year = o.long("year"),
                overview = o.string("summary"),
                genres = genres,
                mediaType = type,
                posterUrl = poster,
                backdropUrl = backdrop,
                watched = viewCount > 0,
                progressMinutes = if (type == MediaType.MOVIES && offsetMs > 0) offsetMs / 60_000 else null,
                totalMinutes = if (type == MediaType.MOVIES) durationMs?.takeIf { it > 0 }?.div(60_000) else null,
                playUrl = playUrl,
            )
        }
    }

    private fun plexMediaType(type: String?): MediaType? = when (type) {
        "movie" -> MediaType.MOVIES
        "show" -> MediaType.TV
        "artist" -> MediaType.MUSIC
        else -> null
    }

    private companion object {
        const val SETTING = "servers.connections"
        const val TICKS_PER_MIN = 600_000_000L // Jellyfin ticks are 100ns units.
        val AUTH_HEADER = "MediaBrowser Client=\"The Reliquary\", Device=\"The Reliquary\", " +
            "DeviceId=\"the-reliquary\", Version=\"${AppInfo.VERSION}\""
    }
}
