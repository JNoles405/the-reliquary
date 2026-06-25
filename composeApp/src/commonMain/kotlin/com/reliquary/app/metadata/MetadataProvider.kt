package com.reliquary.app.metadata

import com.reliquary.app.domain.MediaType

/**
 * A source of metadata for one media type. Keyless providers work immediately;
 * key-gated ones report [isEnabled] = false until an API key is configured.
 */
interface MetadataProvider {
    val id: String
    val displayName: String
    val mediaType: MediaType
    val requiresApiKey: Boolean get() = false
    val isEnabled: Boolean get() = true

    suspend fun search(query: String): List<MetadataResult>
    suspend fun lookupByBarcode(barcode: String): List<MetadataResult>
}
