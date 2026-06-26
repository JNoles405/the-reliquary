package com.reliquary.app.domain

/**
 * Per-item progress status (distinct from the favorite star). Stored in extras
 * under a hidden key so it needs no schema change. Labels are media-appropriate.
 */
object Status {
    fun optionsFor(mediaTypeName: String): List<String> = when (mediaTypeName) {
        MediaType.MOVIES.name -> listOf("Unwatched", "Watching", "Watched")
        MediaType.TV.name -> listOf("Plan to watch", "Watching", "Watched")
        MediaType.ANIME.name -> listOf("Plan to watch", "Watching", "Completed")
        MediaType.BOOKS.name -> listOf("Unread", "Reading", "Read")
        MediaType.MUSIC.name -> listOf("Unlistened", "Listened")
        MediaType.GAMES.name -> listOf("Unplayed", "Playing", "Completed")
        MediaType.COMICS.name -> listOf("Unread", "Read")
        else -> listOf("New", "In progress", "Done")
    }

    /** Statuses that count as "finished" — used by the Unfinished library filter. */
    val DONE: Set<String> = setOf("Watched", "Read", "Listened", "Completed", "Played", "Done")

    /** Statuses that mean "in progress" — used by the Continue shelf. */
    val IN_PROGRESS: Set<String> = setOf("Watching", "Reading", "Playing", "In progress")
}
