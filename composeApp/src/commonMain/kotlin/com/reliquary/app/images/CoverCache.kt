package com.reliquary.app.images

import com.reliquary.app.data.ReliquaryRepository
import com.reliquary.app.data.nowMillis
import com.reliquary.app.domain.CollectionItem
import com.reliquary.app.sync.coversDir
import com.reliquary.app.sync.writeBytesFile
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.readRawBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Downloads remote cover art to a private local file the first time an item is
 * seen, then points the item's cover_path at it. After that the cover renders
 * from disk and survives going offline. Idempotent and best-effort: any failure
 * leaves the item unchanged (it still shows via cover_url).
 */
class CoverCache(private val client: HttpClient) {

    suspend fun ensureCached(item: CollectionItem, repository: ReliquaryRepository) {
        val url = item.coverUrl
        if (url.isNullOrBlank() || !item.coverPath.isNullOrBlank()) return
        runCatching {
            val bytes = withContext(Dispatchers.Default) { client.get(url).readRawBytes() }
            if (bytes.isEmpty()) return
            val path = "${coversDir()}/${item.id}.jpg"
            withContext(Dispatchers.Default) { writeBytesFile(path, bytes) }
            // Re-read in case the row changed meanwhile; only set the path.
            val current = repository.getItem(item.id) ?: return
            if (current.coverPath.isNullOrBlank()) {
                repository.upsertItem(current.copy(coverPath = path, updatedAt = nowMillis()))
            }
        }
    }
}
