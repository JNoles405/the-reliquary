package com.reliquary.app.tools

import com.reliquary.app.domain.CollectionItem
import com.reliquary.app.domain.MediaType
import com.reliquary.app.domain.parseMoney
import com.reliquary.app.metadata.ReliquaryJson
import kotlinx.serialization.decodeFromString
import kotlin.math.round

/** Builds a printable insurance/value report (owned items, conditions, totals). */
object ValueReportExporter {

    fun buildHtml(items: List<CollectionItem>): String {
        val owned = items.filter { !it.deleted && !it.wanted }
        fun extras(item: CollectionItem): Map<String, String> = item.extraJson
            ?.let { runCatching { ReliquaryJson.decodeFromString<Map<String, String>>(it) }.getOrNull() } ?: emptyMap()
        fun valueOf(item: CollectionItem): Double {
            val e = extras(item)
            return parseMoney(e["Current Value"]) ?: parseMoney(e["Purchase Price"]) ?: 0.0
        }
        fun money(v: Double) = "$" + (round(v * 100) / 100.0)

        val grand = owned.sumOf { valueOf(it) }
        val sb = StringBuilder()
        sb.append("<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\">")
        sb.append("<title>The Reliquary — Value Report</title><style>")
        sb.append(
            "body{font-family:Segoe UI,Arial,sans-serif;color:#111;background:#fff;margin:24px}" +
                "h1{margin:0 0 2px}h2{margin-top:28px;border-bottom:2px solid #14b8a6;padding-bottom:4px}" +
                ".muted{color:#666}table{border-collapse:collapse;width:100%;margin-top:8px}" +
                "th,td{text-align:left;padding:6px 10px;border-bottom:1px solid #ddd;font-size:13px}" +
                "th{background:#f2f2f2}td.num,th.num{text-align:right}tfoot td{font-weight:bold}" +
                ".grand{font-size:20px;margin-top:6px}@media print{body{margin:0}}",
        )
        sb.append("</style></head><body>")
        sb.append("<h1>The Reliquary — Collection Value Report</h1>")
        sb.append("<p class=\"muted\">${owned.size} owned items</p>")
        sb.append("<p class=\"grand\">Total estimated value: <strong>${money(grand)}</strong></p>")

        MediaType.entries.forEach { type ->
            val list = owned.filter { it.mediaType == type.name }.sortedBy { (it.sortTitle ?: it.title).lowercase() }
            if (list.isEmpty()) return@forEach
            val subtotal = list.sumOf { valueOf(it) }
            sb.append("<h2>${esc(type.displayName)} <span class=\"muted\">(${list.size}) — ${money(subtotal)}</span></h2>")
            sb.append("<table><thead><tr><th>Title</th><th>Year</th><th>Format</th><th>Condition</th>")
            sb.append("<th class=\"num\">Purchase</th><th class=\"num\">Value</th></tr></thead><tbody>")
            list.forEach { item ->
                val e = extras(item)
                sb.append("<tr>")
                sb.append("<td>${esc(item.title)}</td>")
                sb.append("<td>${item.releaseYear?.toString().orEmpty()}</td>")
                sb.append("<td>${esc(item.format ?: e["Edition"] ?: "")}</td>")
                sb.append("<td>${esc(e["Condition"] ?: "")}</td>")
                sb.append("<td class=\"num\">${esc(e["Purchase Price"] ?: "")}</td>")
                sb.append("<td class=\"num\">${money(valueOf(item))}</td>")
                sb.append("</tr>")
            }
            sb.append("</tbody><tfoot><tr><td colspan=\"5\">Subtotal</td><td class=\"num\">${money(subtotal)}</td></tr></tfoot></table>")
        }
        sb.append("</body></html>")
        return sb.toString()
    }

    private fun esc(s: String): String = s
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
