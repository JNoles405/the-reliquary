package com.reliquary.app.tools

import com.reliquary.app.domain.CollectionItem
import com.reliquary.app.domain.MediaType

/**
 * Builds a print-ready HTML sheet of shelf labels — one small card per item,
 * laid out in a grid that fits a standard label/letter page. Open it and use
 * the browser's Print dialog (the @media print CSS hides everything but the grid).
 */
object LabelExporter {

    fun buildHtml(items: List<CollectionItem>): String {
        val owned = items.filter { !it.deleted && !it.wanted }
            .sortedWith(compareBy({ it.mediaType }, { (it.sortTitle ?: it.title).lowercase() }))
        val sb = StringBuilder()
        sb.append("<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\">")
        sb.append("<title>The Reliquary — Labels</title>")
        sb.append(
            "<style>" +
                "body{background:#f4f6f5;color:#15211e;font-family:Segoe UI,Roboto,Arial,sans-serif;margin:0;padding:18px}" +
                ".bar{margin-bottom:14px}.bar button{font-size:14px;padding:8px 16px;border:0;border-radius:8px;background:#14b8a6;color:#062a25;font-weight:600;cursor:pointer}" +
                ".sheet{display:grid;grid-template-columns:repeat(3,1fr);gap:8px}" +
                ".label{border:1px solid #c5d2ce;border-radius:6px;padding:8px 10px;break-inside:avoid;background:#fff;min-height:54px}" +
                ".t{font-weight:700;font-size:13px;line-height:1.2}" +
                ".s{color:#566;font-size:11px;margin-top:3px}" +
                ".cat{display:inline-block;font-size:9px;letter-spacing:.5px;text-transform:uppercase;color:#0f766e;font-weight:700;margin-bottom:3px}" +
                "@media print{.bar{display:none}body{background:#fff;padding:0}.sheet{gap:0}.label{border-color:#ccc}}" +
                "</style></head><body>",
        )
        sb.append("<div class=\"bar\"><button onclick=\"window.print()\">Print labels</button> ")
        sb.append("<span style=\"color:#566;font-size:13px\">${owned.size} labels</span></div>")
        sb.append("<div class=\"sheet\">")
        owned.forEach { item ->
            val cat = MediaType.entries.firstOrNull { it.name == item.mediaType }?.displayName ?: item.mediaType
            val sub = listOfNotNull(
                item.releaseYear?.toString(),
                item.creators,
                item.format,
                item.location?.let { "@ $it" },
            ).joinToString(" · ")
            sb.append("<div class=\"label\"><div class=\"cat\">${esc(cat)}</div>")
            sb.append("<div class=\"t\">${esc(item.title)}</div>")
            if (sub.isNotBlank()) sb.append("<div class=\"s\">${esc(sub)}</div>")
            sb.append("</div>")
        }
        sb.append("</div></body></html>")
        return sb.toString()
    }

    private fun esc(s: String): String = s
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
