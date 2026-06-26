package com.reliquary.app.metadata.providers

import com.reliquary.app.domain.MediaType
import com.reliquary.app.metadata.MetadataProvider
import com.reliquary.app.metadata.MetadataResult
import com.reliquary.app.metadata.ReliquaryJson
import com.reliquary.app.metadata.array
import com.reliquary.app.metadata.obj
import com.reliquary.app.metadata.string
import com.reliquary.app.metadata.yearFrom
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.encodeURLParameter
import kotlinx.serialization.json.JsonObject

/**
 * Keyless music metadata via MusicBrainz, with cover art from the Cover Art
 * Archive. MusicBrainz asks for a descriptive User-Agent on every request.
 */
class MusicBrainzProvider(private val client: HttpClient) : MetadataProvider {
    override val id = "musicbrainz"
    override val displayName = "MusicBrainz"
    override val mediaType = MediaType.MUSIC

    override suspend fun search(query: String): List<MetadataResult> =
        queryReleases(query.encodeURLParameter())

    override suspend fun lookupByBarcode(barcode: String): List<MetadataResult> =
        queryReleases("barcode:${barcode.encodeURLParameter()}")

    private suspend fun queryReleases(luceneQuery: String): List<MetadataResult> {
        val url = "https://musicbrainz.org/ws/2/release/?fmt=json&limit=20&query=$luceneQuery"
        val body = client.get(url) { header(HttpHeaders.UserAgent, USER_AGENT) }.bodyAsText()
        val releases = ReliquaryJson.parseToJsonElement(body).obj()?.array("releases") ?: return emptyList()
        return releases.mapNotNull { it.obj()?.toResult() }
    }

    private fun JsonObject.toResult(): MetadataResult? {
        val title = string("title") ?: return null
        val mbid = string("id")
        val artists = array("artist-credit")?.mapNotNull { it.obj()?.string("name") }
        val catalog = array("label-info")
            ?.mapNotNull { it.obj()?.string("catalog-number") }
            ?.firstOrNull()
        return MetadataResult(
            providerId = id,
            providerName = displayName,
            mediaType = mediaType,
            title = title,
            creators = artists?.joinToString(", "),
            releaseYear = yearFrom(string("date")),
            coverUrl = mbid?.let { "https://coverartarchive.org/release/$it/front-1200" },
            identifierType = string("barcode")?.let { "Barcode" },
            identifier = string("barcode") ?: catalog,
            format = string("packaging"),
            extra = buildMap {
                catalog?.let { put("Catalog #", it) }
                string("country")?.let { put("Country", it) }
            },
        )
    }

    private companion object {
        const val USER_AGENT = "TheReliquary/0.1 ( https://github.com/JNoles405/the-reliquary )"
    }
}
