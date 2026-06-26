package com.reliquary.app.domain

/**
 * User-editable "edition" details (disc-specific info that free metadata APIs
 * don't provide). Stored in an item's extras under these keys, kept separate
 * from provider-supplied extras so the detail screen can group them and the
 * edit form can expose them without disturbing cast/crew/etc.
 */
val EDITION_FIELDS: List<String> = listOf(
    "Edition",
    "Packaging",
    "Discs",
    "Region",
    "Audio",
    "Subtitles",
    "Screen Ratio",
)
