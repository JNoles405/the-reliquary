package com.reliquary.app.metadata

import com.reliquary.app.domain.MediaType

/**
 * Routes lookups to the providers registered for a given media type and merges
 * their results. A failing provider (offline, rate-limited, bad key) is skipped
 * rather than failing the whole search.
 */
class MetadataService(private val providers: List<MetadataProvider>) {

    fun providersFor(type: MediaType): List<MetadataProvider> =
        providers.filter { it.mediaType == type && it.isEnabled }

    fun hasProviderFor(type: MediaType): Boolean = providersFor(type).isNotEmpty()

    suspend fun search(type: MediaType, query: String): List<MetadataResult> =
        providersFor(type).flatMap { provider ->
            runCatching { provider.search(query) }.getOrElse { emptyList() }
        }

    suspend fun lookupByBarcode(type: MediaType, barcode: String): List<MetadataResult> =
        providersFor(type).flatMap { provider ->
            runCatching { provider.lookupByBarcode(barcode) }.getOrElse { emptyList() }
        }
}
