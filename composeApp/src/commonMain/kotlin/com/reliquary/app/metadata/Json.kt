package com.reliquary.app.metadata

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

/** Lenient JSON used to defensively parse third-party metadata responses. */
val ReliquaryJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
}

// Defensive navigation helpers — third-party schemas have many optional fields,
// so we read by key and tolerate missing/null values rather than binding DTOs.

fun JsonElement?.obj(): JsonObject? = this as? JsonObject

fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

fun JsonObject.long(key: String): Long? = (this[key] as? JsonPrimitive)?.longOrNull

fun JsonObject.array(key: String): JsonArray? = this[key] as? JsonArray

fun JsonArray.strings(): List<String> = mapNotNull { (it as? JsonPrimitive)?.contentOrNull }

/** First 4-digit run in a free-form date string, as a year. */
fun yearFrom(date: String?): Long? =
    date?.let { Regex("\\d{4}").find(it)?.value?.toLongOrNull() }

/** UTC calendar year for a Unix timestamp in seconds (civil-from-days). */
fun yearFromEpochSeconds(seconds: Long): Long {
    var z = seconds / 86_400L + 719468
    val era = (if (z >= 0) z else z - 146096) / 146097
    val doe = z - era * 146097
    val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
    val y = yoe + era * 400
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val m = if (mp < 10) mp + 3 else mp - 9
    return if (m <= 2) y + 1 else y
}
