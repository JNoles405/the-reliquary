package com.reliquary.app.integrations

import com.reliquary.app.data.ReliquaryRepository
import com.reliquary.app.data.newId
import com.reliquary.app.data.nowMillis
import com.reliquary.app.domain.CollectionItem
import com.reliquary.app.domain.MediaType
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText

/**
 * Imports recently-watched films from a public Letterboxd account via its RSS
 * feed (letterboxd.com/{user}/rss). No API key needed. Each film is marked
 * Watched, carries the member rating (Letterboxd's 0–5 stars scaled to /10) and
 * the TMDB id, so it merges with TMDB imports. RSS only exposes recent activity;
 * the CSV export covers full history.
 */
class LetterboxdImporter(
    private val client: HttpClient,
    private val repository: ReliquaryRepository,
) {
    suspend fun importWatched(username: String): Int {
        val user = username.trim().trim('@').lowercase()
        if (user.isBlank()) return 0
        val xml = client.get("https://letterboxd.com/$user/rss/").bodyAsText()
        var count = 0
        Regex("<item>(.*?)</item>", setOf(RegexOption.DOT_MATCHES_ALL)).findAll(xml).forEach { match ->
            val block = match.groupValues[1]
            val title = tag(block, "letterboxd:filmTitle") ?: return@forEach
            val now = nowMillis()
            val tmdbId = tag(block, "tmdb:movieId")
            val stars = tag(block, "letterboxd:memberRating")?.toDoubleOrNull()
            repository.importOrUpdate(
                CollectionItem(
                    id = newId(),
                    mediaType = MediaType.MOVIES.name,
                    title = title,
                    releaseYear = tag(block, "letterboxd:filmYear")?.toLongOrNull(),
                    rating = stars?.let { it * 2 },
                    identifierType = tmdbId?.let { "TMDB" },
                    identifier = tmdbId,
                    status = "Watched",
                    addedAt = now,
                    updatedAt = now,
                ),
            )
            count++
        }
        return count
    }

    private fun tag(block: String, name: String): String? {
        val raw = Regex("<$name>(.*?)</$name>", setOf(RegexOption.DOT_MATCHES_ALL))
            .find(block)?.groupValues?.get(1) ?: return null
        return raw.removePrefix("<![CDATA[").removeSuffix("]]>").trim().takeIf { it.isNotBlank() }
    }
}
