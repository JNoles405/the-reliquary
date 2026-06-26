package com.reliquary.app.domain

/** Hidden extras key holding an item's comma-separated tags. */
const val TAGS_KEY = "_tags"

fun parseTags(raw: String?): List<String> =
    raw?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }?.distinct().orEmpty()
