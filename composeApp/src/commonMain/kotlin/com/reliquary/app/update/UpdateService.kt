package com.reliquary.app.update

import com.reliquary.app.metadata.ReliquaryJson
import com.reliquary.app.metadata.array
import com.reliquary.app.metadata.obj
import com.reliquary.app.metadata.string
import com.reliquary.app.util.AppInfo
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders

/** Outcome of an update check. */
sealed interface UpdateStatus {
    data object UpToDate : UpdateStatus
    data class Available(
        val version: String,
        val downloadUrl: String?,
        val pageUrl: String,
        val notes: String?,
    ) : UpdateStatus
    data class Failed(val message: String) : UpdateStatus
}

/**
 * Checks the project's latest GitHub release against the running version. No
 * token needed — the releases API is public (and rate-limited per IP, which is
 * plenty for occasional checks).
 */
class UpdateService(private val client: HttpClient) {
    // https://github.com/owner/repo -> https://api.github.com/repos/owner/repo/releases/latest
    private val latestUrl =
        AppInfo.REPO_URL.replace("https://github.com/", "https://api.github.com/repos/") + "/releases/latest"

    suspend fun checkForUpdate(currentVersion: String = AppInfo.VERSION): UpdateStatus = try {
        val body = client.get(latestUrl) {
            header(HttpHeaders.Accept, "application/vnd.github+json")
            header(HttpHeaders.UserAgent, "TheReliquary/$currentVersion")
        }.bodyAsText()
        val obj = ReliquaryJson.parseToJsonElement(body).obj()
        val tag = obj?.string("tag_name")
        if (obj == null || tag == null) {
            UpdateStatus.Failed("No public release found — the repository may be private.")
        } else {
            val latest = tag.trimStart('v', 'V')
            val pageUrl = obj.string("html_url") ?: AppInfo.RELEASES_URL
            if (compareVersions(latest, currentVersion) <= 0) {
                UpdateStatus.UpToDate
            } else {
                val msiUrl = obj.array("assets")?.mapNotNull { it.obj() }
                    ?.firstOrNull { (it.string("name") ?: "").endsWith(".msi", ignoreCase = true) }
                    ?.string("browser_download_url")
                UpdateStatus.Available(latest, msiUrl, pageUrl, obj.string("body"))
            }
        }
    } catch (e: Throwable) {
        UpdateStatus.Failed(e.message ?: "Could not reach the update server.")
    }
}

/** Compare AA.BB.CC version strings. Negative if a<b, 0 if equal, positive if a>b. */
fun compareVersions(a: String, b: String): Int {
    val pa = a.split(".").map { it.toIntOrNull() ?: 0 }
    val pb = b.split(".").map { it.toIntOrNull() ?: 0 }
    for (i in 0 until maxOf(pa.size, pb.size)) {
        val diff = pa.getOrElse(i) { 0 } - pb.getOrElse(i) { 0 }
        if (diff != 0) return diff
    }
    return 0
}
