package com.reliquary.app.domain

/** Hidden extras keys for reading/watching progress (e.g. page 120 of 320). */
const val PROGRESS_KEY = "_prog"
const val PROGRESS_TOTAL_KEY = "_progTotal"

/** Per-media label for the progress unit. */
fun progressUnit(mediaTypeName: String): String = when (mediaTypeName) {
    MediaType.BOOKS.name, MediaType.COMICS.name -> "Page"
    MediaType.TV.name, MediaType.ANIME.name -> "Episode"
    else -> "Progress"
}
