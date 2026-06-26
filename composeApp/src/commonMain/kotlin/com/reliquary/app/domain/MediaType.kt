package com.reliquary.app.domain

/**
 * The built-in media categories. The app also supports user-defined custom
 * tabs, which are stored separately and merged into the tab bar at runtime.
 */
enum class MediaType(val displayName: String, val supportsBarcode: Boolean) {
    MOVIES("Movies", supportsBarcode = true),
    TV("TV Shows", supportsBarcode = true),
    ANIME("Anime", supportsBarcode = true),
    BOOKS("Books", supportsBarcode = true),
    MUSIC("Music", supportsBarcode = true),
    GAMES("Games", supportsBarcode = true),
    COMICS("Comics", supportsBarcode = true),
}
