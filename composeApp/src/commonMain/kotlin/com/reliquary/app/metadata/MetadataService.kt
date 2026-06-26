package com.reliquary.app.metadata

import com.reliquary.app.domain.MediaType

/**
 * Routes lookups to the providers registered for a given media type and merges
 * their results. A failing provider (offline, rate-limited, bad key) is skipped
 * rather than failing the whole search.
 *
 * For barcodes that no provider resolves directly (retail UPCs on discs/boxes),
 * it falls back to resolving the UPC to a product name and searching by that.
 */
class MetadataService(
    private val providers: List<MetadataProvider>,
    private val upcLookup: UpcLookup,
) {

    fun providersFor(type: MediaType): List<MetadataProvider> =
        providers.filter { it.mediaType == type && it.isEnabled }

    fun hasProviderFor(type: MediaType): Boolean = providersFor(type).isNotEmpty()

    suspend fun search(type: MediaType, query: String): List<MetadataResult> =
        dedupe(
            providersFor(type).flatMap { provider ->
                runCatching { provider.search(query) }.getOrElse { emptyList() }
            },
        )

    suspend fun lookupByBarcode(type: MediaType, barcode: String): List<MetadataResult> {
        // 1) Providers that index this barcode directly (books by ISBN, music by barcode).
        val direct = dedupe(
            providersFor(type).flatMap { provider ->
                runCatching { provider.lookupByBarcode(barcode) }.getOrElse { emptyList() }
            },
        )
        if (direct.isNotEmpty()) return direct

        // 2) Resolve the retail UPC to a product name, then search by that title.
        val product = runCatching { upcLookup.lookup(barcode) }.getOrNull() ?: return emptyList()
        val title = cleanProductTitle(product.title)
        if (title.isNotBlank()) {
            val viaSearch = search(type, title)
            if (viaSearch.isNotEmpty()) return viaSearch
        }

        // 3) Last resort: a minimal result from the barcode product itself, so the
        //    user can import it (and edit) even with no provider/key for this type.
        val fallbackTitle = title.ifBlank { product.title }
        return listOf(
            MetadataResult(
                providerId = "upc",
                providerName = "Barcode lookup",
                mediaType = type,
                title = fallbackTitle,
                creators = product.brand,
                description = product.description,
                coverUrl = product.imageUrl,
                identifierType = "UPC",
                identifier = barcode,
            ),
        )
    }

    /**
     * Collapse the same title coming from multiple providers (e.g. a movie from
     * both TMDB and OMDb) into one entry, keeping the richest version. Order of
     * first appearance is preserved.
     */
    private fun dedupe(results: List<MetadataResult>): List<MetadataResult> {
        if (results.size < 2) return results
        val byKey = LinkedHashMap<String, MetadataResult>()
        for (result in results) {
            val key = dedupeKey(result)
            val existing = byKey[key]
            if (existing == null || richness(result) > richness(existing)) {
                byKey[key] = result
            }
        }
        return byKey.values.toList()
    }

    private fun dedupeKey(r: MetadataResult): String {
        val title = r.title.lowercase().filter { it.isLetterOrDigit() }
        return if (r.releaseYear != null) "$title|${r.releaseYear}" else title
    }

    private fun richness(r: MetadataResult): Int =
        listOf(r.description, r.coverUrl, r.genres, r.creators, r.releaseYear?.toString())
            .count { !it.isNullOrBlank() }

    /** Strip format/edition noise so a retail product title can match a title database. */
    private fun cleanProductTitle(raw: String): String {
        var t = raw
        t = t.replace(Regex("\\[[^\\]]*]"), " ")   // [Blu-ray]
        t = t.replace(Regex("\\([^)]*\\)"), " ")     // (2007), (Widescreen)
        val noise = listOf(
            "blu-ray", "bluray", "blu ray", "dvd", "4k", "ultra hd", "uhd", "digital",
            "combo", "steelbook", "widescreen", "full screen", "unrated", "import",
            "special edition", "collector's edition", "limited edition",
        )
        noise.forEach { word ->
            t = t.replace(Regex("(?i)\\b" + Regex.escape(word) + "\\b"), " ")
        }
        // Drop a trailing release year that retail titles often append (e.g. "... 2007").
        t = t.replace(Regex("\\s+(19|20)\\d{2}\\s*$"), " ")
        return t.replace(Regex("\\s+"), " ").trim().trim('-', ':', '|', '/', ',').trim()
    }
}
