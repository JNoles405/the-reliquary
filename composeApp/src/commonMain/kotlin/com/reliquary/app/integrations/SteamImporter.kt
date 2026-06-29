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
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.JsonObject

/**
 * Imports a user's owned Steam games via the public Steam Web API
 * (IPlayerService/GetOwnedGames). Needs a free Steam Web API key and the
 * account's 64-bit SteamID (a vanity name is resolved automatically). Each game
 * becomes a Games item with cover art from Steam's CDN, the Steam appid as its
 * identifier (so re-imports merge), and total playtime recorded in notes.
 */
class SteamImporter(
    private val client: HttpClient,
    private val keys: ApiKeyStore,
    private val repository: ReliquaryRepository,
) {
    /** Resolve a vanity name to a 64-bit SteamID, or pass a numeric id through. */
    suspend fun resolveSteamId(input: String): String? {
        val raw = input.trim()
            .removePrefix("https://steamcommunity.com/id/")
            .removePrefix("https://steamcommunity.com/profiles/")
            .trim('/')
        if (raw.isBlank()) return null
        if (raw.all { it.isDigit() } && raw.length >= 17) return raw
        val key = keys.get(ApiKeys.STEAM) ?: return null
        val o = ReliquaryJson.parseToJsonElement(
            client.get(
                "https://api.steampowered.com/ISteamUser/ResolveVanityURL/v1/?key=$key&vanityurl=$raw",
            ).bodyAsText(),
        ).obj()?.get("response").obj()
        return if (o?.long("success")?.toInt() == 1) o.string("steamid") else null
    }

    /** Import all owned games for the configured key + SteamID. Returns count, or -1 on error. */
    suspend fun importOwnedGames(): Int {
        val key = keys.get(ApiKeys.STEAM) ?: return -1
        val rawId = keys.get(ApiKeys.STEAM_ID) ?: return -1
        val steamId = resolveSteamId(rawId) ?: return -1
        val body = client.get(
            "https://api.steampowered.com/IPlayerService/GetOwnedGames/v1/" +
                "?key=$key&steamid=$steamId&include_appinfo=true&include_played_free_games=true&format=json",
        ).bodyAsText()
        val games = ReliquaryJson.parseToJsonElement(body).obj()
            ?.get("response").obj()?.array("games") ?: return 0
        var count = 0
        games.forEach { el ->
            val g = el as? JsonObject ?: return@forEach
            val appId = g.long("appid") ?: return@forEach
            val name = g.string("name")?.takeIf { it.isNotBlank() } ?: return@forEach
            val minutes = g.long("playtime_forever") ?: 0
            val now = nowMillis()
            repository.importOrUpdate(
                CollectionItem(
                    id = newId(),
                    mediaType = MediaType.GAMES.name,
                    title = name,
                    coverUrl = "https://cdn.cloudflare.steamstatic.com/steam/apps/$appId/library_600x900.jpg",
                    identifierType = "Steam",
                    identifier = appId.toString(),
                    notes = if (minutes > 0) "Steam playtime: ${minutes / 60}h ${minutes % 60}m" else null,
                    status = if (minutes > 0) "Playing" else "Unplayed",
                    addedAt = now,
                    updatedAt = now,
                ),
            )
            count++
        }
        return count
    }
}
