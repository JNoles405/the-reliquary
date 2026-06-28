package com.reliquary.app.tools

import com.reliquary.app.domain.CollectionItem
import com.reliquary.app.domain.MediaType

/** Builds a standalone, shareable HTML catalog of the collection. */
object CatalogExporter {

    fun buildHtml(items: List<CollectionItem>): String {
        val owned = items.filter { !it.deleted }
        val byType = owned.groupBy { it.mediaType }
        val sb = StringBuilder()
        sb.append("<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\">")
        sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
        sb.append("<title>The Reliquary — Catalog</title>")
        sb.append(
            "<style>" +
                "body{background:#0b0e0d;color:#f2f5f4;font-family:Segoe UI,Roboto,Arial,sans-serif;margin:0;padding:24px}" +
                "h1{color:#14b8a6;margin:0 0 4px}h2{border-bottom:1px solid #22302d;padding-bottom:6px;margin-top:32px}" +
                ".muted{color:#9db2ad}.grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(150px,1fr));gap:16px;margin-top:12px}" +
                ".card{background:#16201e;border-radius:10px;overflow:hidden}" +
                ".card img{width:100%;aspect-ratio:2/3;object-fit:cover;display:block;background:#22302d}" +
                ".card .body{padding:8px 10px}.title{font-weight:600;font-size:14px}.sub{color:#9db2ad;font-size:12px;margin-top:2px}" +
                ".noimg{width:100%;aspect-ratio:2/3;display:flex;align-items:center;justify-content:center;color:#9db2ad;background:#22302d}" +
                "</style></head><body>",
        )
        sb.append("<h1>The Reliquary</h1>")
        sb.append("<p class=\"muted\">${owned.size} items</p>")

        MediaType.entries.forEach { type ->
            val list = byType[type.name]?.sortedBy { (it.sortTitle ?: it.title).lowercase() }.orEmpty()
            if (list.isEmpty()) return@forEach
            sb.append("<h2>${esc(type.displayName)} <span class=\"muted\">(${list.size})</span></h2>")
            sb.append("<div class=\"grid\">")
            list.forEach { item -> sb.append(card(item)) }
            sb.append("</div>")
        }
        sb.append("</body></html>")
        return sb.toString()
    }

    private fun card(item: CollectionItem): String {
        val img = item.coverUrl?.let { "<img src=\"${esc(it)}\" alt=\"\">" }
            ?: "<div class=\"noimg\">No cover</div>"
        val sub = listOfNotNull(
            item.releaseYear?.toString(),
            item.creators,
            item.status,
            item.takeIf { it.wanted }?.let { "Wishlist" },
        ).joinToString(" · ")
        return "<div class=\"card\">$img<div class=\"body\"><div class=\"title\">${esc(item.title)}</div>" +
            (if (sub.isNotBlank()) "<div class=\"sub\">${esc(sub)}</div>" else "") + "</div></div>"
    }

    private fun esc(s: String): String = s
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
