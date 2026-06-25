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
import com.reliquary.app.metadata.strings
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.encodeURLParameter
import kotlinx.serialization.json.JsonObject

/** Music metadata via Discogs. Key-gated by a personal access token. */
class DiscogsProvider(
    private val client: HttpClient,
    private val keys: ApiKeyStore,
) : MetadataProvider {
    override val id = "discogs"
    override val displayName = "Discogs"
    override val mediaType = MediaType.MUSIC
    override val requiresApiKey = true
    override val isEnabled get() = keys.has(ApiKeys.DISCOGS)

    override suspend fun search(query: String): List<MetadataResult> =
        request("q=${query.encodeURLParameter()}&type=release")

    override suspend fun lookupByBarcode(barcode: String): List<MetadataResult> =
        request("barcode=${barcode.encodeURLParameter()}&type=release")

    private suspend fun request(params: String): List<MetadataResult> {
        val token = keys.get(ApiKeys.DISCOGS) ?: return emptyList()
        val url = "https://api.discogs.com/database/search?$params&token=$token"
        val body = client.get(url) { header(HttpHeaders.UserAgent, USER_AGENT) }.bodyAsText()
        val results = ReliquaryJson.parseToJsonElement(body).obj()?.array("results") ?: return emptyList()
        return results.mapNotNull { it.obj()?.toResult() }
    }

    private fun JsonObject.toResult(): MetadataResult? {
        val title = string("title") ?: return null
        val cover = string("cover_image") ?: string("thumb")
        val format = array("format")?.strings()?.joinToString(", ")
        val catno = string("catno")
        return MetadataResult(
            providerId = id,
            providerName = displayName,
            mediaType = mediaType,
            title = title,
            creators = array("label")?.strings()?.firstOrNull(),
            releaseYear = string("year")?.let { Regex("\\d{4}").find(it)?.value?.toLongOrNull() },
            coverUrl = cover,
            identifierType = catno?.let { "Catalog #" },
            identifier = catno,
            format = format,
            genres = array("genre")?.strings()?.joinToString(", "),
        )
    }

    private companion object {
        const val USER_AGENT = "TheReliquary/0.1 +https://github.com/JNoles405/the-reliquary"
    }
}
