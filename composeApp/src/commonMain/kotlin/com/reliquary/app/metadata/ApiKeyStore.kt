package com.reliquary.app.metadata

import com.reliquary.app.data.ReliquaryRepository

/** Setting keys for the key-gated providers. Stored locally, never committed. */
object ApiKeys {
    const val TMDB = "tmdb.apiKey"
    const val OMDB = "omdb.apiKey"
    const val IGDB_CLIENT_ID = "igdb.clientId"
    const val IGDB_CLIENT_SECRET = "igdb.clientSecret"
    const val COMICVINE = "comicvine.apiKey"
    const val DISCOGS = "discogs.token"
}

/** Thin reactive-enough wrapper over the settings table for API keys. */
class ApiKeyStore(private val repository: ReliquaryRepository) {
    fun get(key: String): String? = repository.getSetting(key)?.takeIf { it.isNotBlank() }
    fun set(key: String, value: String?) = repository.setSetting(key, value?.ifBlank { null })
    fun has(key: String): Boolean = !get(key).isNullOrBlank()
}
