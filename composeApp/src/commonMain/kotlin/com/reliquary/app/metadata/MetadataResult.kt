package com.reliquary.app.metadata

import com.reliquary.app.data.newId
import com.reliquary.app.data.nowMillis
import com.reliquary.app.domain.CollectionItem
import com.reliquary.app.domain.MediaType
import kotlinx.serialization.encodeToString

/**
 * A normalized lookup result from any provider. The UI lets the user pick one,
 * then [toCollectionItem] turns it into a row ready to persist.
 */
data class MetadataResult(
    val providerId: String,
    val providerName: String,
    val mediaType: MediaType,
    val title: String,
    val subtitle: String? = null,
    val creators: String? = null,
    val releaseYear: Long? = null,
    val description: String? = null,
    val coverUrl: String? = null,
    val identifierType: String? = null,
    val identifier: String? = null,
    val genres: String? = null,
    val format: String? = null,
    val rating: Double? = null,
    val extra: Map<String, String> = emptyMap(),
) {
    fun toCollectionItem(barcode: String? = null, customTabId: String? = null): CollectionItem {
        val now = nowMillis()
        return CollectionItem(
            id = newId(),
            mediaType = mediaType.name,
            customTabId = customTabId,
            title = title,
            subtitle = subtitle,
            creators = creators,
            releaseYear = releaseYear,
            description = description,
            coverUrl = coverUrl,
            barcode = barcode ?: identifier,
            identifierType = identifierType,
            identifier = identifier,
            genres = genres,
            format = format,
            rating = rating,
            extraJson = if (extra.isEmpty()) null else ReliquaryJson.encodeToString(extra),
            addedAt = now,
            updatedAt = now,
        )
    }
}
