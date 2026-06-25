package com.reliquary.app.metadata.providers

import com.reliquary.app.domain.MediaType
import com.reliquary.app.metadata.ApiKeyStore
import com.reliquary.app.metadata.ApiKeys
import com.reliquary.app.metadata.MetadataProvider
import com.reliquary.app.metadata.MetadataResult
import com.reliquary.app.metadata.ReliquaryJson
import com.reliquary.app.metadata.array
import com.reliquary.app.metadata.obj
import com.reliquary.app.metadata.string
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.encodeURLParameter
import kotlinx.serialization.json.JsonObject

/**
 * Comic metadata via ComicVine. Key-gated by a ComicVine API key. ComicVine
 * requires a descriptive User-Agent and does not index retail barcodes.
 */
class ComicVineProvider(
    private val client: HttpClient,
    private val keys: ApiKeyStore,
) : MetadataProvider {
    override val id = "comicvine"
    override val displayName = "ComicVine"
    override val mediaType = MediaType.COMICS
    override val requiresApiKey = true
    override val isEnabled get() = keys.has(ApiKeys.COMICVINE)

    override suspend fun search(query: String): List<MetadataResult> {
        val key = keys.get(ApiKeys.COMICVINE) ?: return emptyList()
        val url = "https://comicvine.gamespot.com/api/search/" +
            "?api_key=$key&format=json&limit=20&resources=volume,issue" +
            "&query=${query.encodeURLParameter()}"
        val body = client.get(url) { header(HttpHeaders.UserAgent, USER_AGENT) }.bodyAsText()
        val results = ReliquaryJson.parseToJsonElement(body).obj()?.array("results") ?: return emptyList()
        return results.mapNotNull { it.obj()?.toResult() }
    }

    override suspend fun lookupByBarcode(barcode: String): List<MetadataResult> = emptyList()

    private fun JsonObject.toResult(): MetadataResult? {
        val name = string("name") ?: return null
        val image = this["image"]?.obj()?.let { it.string("medium_url") ?: it.string("original_url") }
        val publisher = this["publisher"]?.obj()?.string("name")
        val issueNumber = string("issue_number")
        val displayTitle = if (issueNumber != null) "$name #$issueNumber" else name
        return MetadataResult(
            providerId = id,
            providerName = displayName,
            mediaType = mediaType,
            title = displayTitle,
            creators = publisher,
            releaseYear = string("start_year")?.let { Regex("\\d{4}").find(it)?.value?.toLongOrNull() },
            description = string("deck"),
            coverUrl = image,
            identifierType = "ComicVine",
            identifier = string("id"),
        )
    }

    private companion object {
        const val USER_AGENT = "TheReliquary/0.1 (https://github.com/JNoles405/the-reliquary)"
    }
}
