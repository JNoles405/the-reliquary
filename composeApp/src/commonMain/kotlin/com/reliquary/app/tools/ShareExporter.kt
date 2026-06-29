package com.reliquary.app.tools

import com.reliquary.app.domain.CollectionItem
import com.reliquary.app.domain.MediaType

/**
 * Builds a polished, self-contained "share page" you can hand to anyone — by
 * default it highlights the wishlist (what you're hoping to get), with the rest
 * of the owned collection listed below. No app or network needed to view it.
 */
object ShareExporter {

    fun buildHtml(items: List<CollectionItem>, ownerName: String? = null): String {
        val live = items.filter { !it.deleted }
        val wishlist = live.filter { it.wanted }
            .sortedWith(compareByDescending<CollectionItem> { priorityOf(it) }.thenBy { it.title.lowercase() })
        val owned = live.filter { !it.wanted }

        val sb = StringBuilder()
        sb.append("<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\">")
        sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
        sb.append("<title>${esc(ownerName ?: "My")} Wishlist — The Reliquary</title>")
        sb.append(
            "<style>" +
                "body{background:#0b0e0d;color:#f2f5f4;font-family:Segoe UI,Roboto,Arial,sans-serif;margin:0;padding:0}" +
                "header{background:linear-gradient(135deg,#0f766e,#14b8a6);padding:36px 24px;color:#04201c}" +
                "header h1{margin:0;font-size:30px}header p{margin:6px 0 0;opacity:.85;font-weight:600}" +
                "main{padding:24px;max-width:1100px;margin:0 auto}" +
                "h2{border-bottom:1px solid #22302d;padding-bottom:6px;margin-top:34px}" +
                ".muted{color:#9db2ad}" +
                ".grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(150px,1fr));gap:16px;margin-top:14px}" +
                ".card{background:#16201e;border-radius:10px;overflow:hidden;position:relative}" +
                ".card img{width:100%;aspect-ratio:2/3;object-fit:cover;display:block;background:#22302d}" +
                ".card .body{padding:8px 10px}.title{font-weight:600;font-size:14px}.sub{color:#9db2ad;font-size:12px;margin-top:2px}" +
                ".noimg{width:100%;aspect-ratio:2/3;display:flex;align-items:center;justify-content:center;color:#9db2ad;background:#22302d}" +
                ".badge{position:absolute;top:8px;left:8px;background:#14b8a6;color:#04201c;font-size:10px;font-weight:700;padding:2px 7px;border-radius:20px;text-transform:uppercase;letter-spacing:.4px}" +
                ".chips{margin-top:6px}.chip{display:inline-block;background:#22302d;color:#cfe;border-radius:14px;font-size:11px;padding:2px 9px;margin:2px 4px 0 0}" +
                "footer{padding:24px;text-align:center;color:#5d706b;font-size:12px}" +
                "</style></head><body>",
        )
        sb.append("<header><h1>${esc(ownerName ?: "My Collection")}</h1>")
        sb.append("<p>${wishlist.size} on the wishlist · ${owned.size} in the collection</p></header><main>")

        if (wishlist.isNotEmpty()) {
            sb.append("<h2>Wishlist <span class=\"muted\">(${wishlist.size})</span></h2>")
            sb.append("<p class=\"muted\">Things I'm hoping to add — handy for gift ideas.</p>")
            sb.append("<div class=\"grid\">")
            wishlist.forEach { sb.append(card(it, badge = priorityLabel(it))) }
            sb.append("</div>")
        }

        if (owned.isNotEmpty()) {
            val byType = owned.groupBy { it.mediaType }
            sb.append("<h2>The Collection <span class=\"muted\">(${owned.size})</span></h2>")
            MediaType.entries.forEach { type ->
                val list = byType[type.name]?.sortedBy { (it.sortTitle ?: it.title).lowercase() }.orEmpty()
                if (list.isEmpty()) return@forEach
                sb.append("<h3 style=\"color:#9db2ad\">${esc(type.displayName)} (${list.size})</h3>")
                sb.append("<div class=\"grid\">")
                list.forEach { sb.append(card(it, badge = null)) }
                sb.append("</div>")
            }
        }

        sb.append("</main><footer>Made with The Reliquary</footer></body></html>")
        return sb.toString()
    }

    private fun card(item: CollectionItem, badge: String?): String {
        val img = item.coverUrl?.let { "<img src=\"${esc(it)}\" alt=\"\">" }
            ?: "<div class=\"noimg\">No cover</div>"
        val sub = listOfNotNull(item.releaseYear?.toString(), item.creators).joinToString(" · ")
        val badgeHtml = badge?.let { "<span class=\"badge\">${esc(it)}</span>" } ?: ""
        return "<div class=\"card\">$badgeHtml$img<div class=\"body\"><div class=\"title\">${esc(item.title)}</div>" +
            (if (sub.isNotBlank()) "<div class=\"sub\">${esc(sub)}</div>" else "") + "</div></div>"
    }

    private fun priorityName(item: CollectionItem): String? = item.extraJson
        ?.let { Regex("\"_wishPriority\"\\s*:\\s*\"([^\"]+)\"").find(it)?.groupValues?.get(1) }

    private fun priorityOf(item: CollectionItem): Int = when (priorityName(item)) {
        "High" -> 3; "Medium" -> 2; "Low" -> 1; else -> 0
    }

    private fun priorityLabel(item: CollectionItem): String? = priorityName(item)

    private fun esc(s: String): String = s
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
