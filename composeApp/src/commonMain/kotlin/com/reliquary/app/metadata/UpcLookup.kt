package com.reliquary.app.metadata

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText

/**
 * Resolves a retail UPC/EAN barcode to a product name using UPCitemdb's keyless
 * trial endpoint. Retail barcodes (the ones on Blu-rays, game boxes, CDs) aren't
 * indexed by title databases like TMDB/IGDB/ComicVine, so this bridges a scanned
 * barcode to a title that those providers *can* search.
 */
class UpcLookup(private val client: HttpClient) {

    data class Product(
        val title: String,
        val imageUrl: String?,
        val description: String? = null,
        val brand: String? = null,
    )

    suspend fun lookup(barcode: String): Product? {
        val code = barcode.filter { it.isDigit() }
        if (code.length !in 8..14) return null
        val url = "https://api.upcitemdb.com/prod/trial/lookup?upc=$code"
        val root = runCatching {
            ReliquaryJson.parseToJsonElement(client.get(url).bodyAsText()).obj()
        }.getOrNull() ?: return null
        val item = root.array("items")?.firstOrNull()?.obj() ?: return null
        val title = item.string("title")?.takeIf { it.isNotBlank() } ?: return null
        val image = item.array("images")?.strings()?.firstOrNull { it.startsWith("http") }
        return Product(
            title = title,
            imageUrl = image,
            description = item.string("description")?.takeIf { it.isNotBlank() },
            brand = item.string("brand")?.takeIf { it.isNotBlank() },
        )
    }
}
