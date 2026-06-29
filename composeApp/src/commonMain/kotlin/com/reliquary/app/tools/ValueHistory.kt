package com.reliquary.app.tools

import com.reliquary.app.data.ReliquaryRepository
import com.reliquary.app.metadata.ReliquaryJson
import com.reliquary.app.util.DAY_MILLIS
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

/** One day's recorded total collection value. */
@Serializable
data class ValuePoint(val day: Long, val value: Double)

/** Persists a daily snapshot of collection value so Stats can chart it over time. */
object ValueHistory {
    private const val KEY = "stats.valueHistory"

    fun load(repository: ReliquaryRepository): List<ValuePoint> = repository.getSetting(KEY)
        ?.let { runCatching { ReliquaryJson.decodeFromString<List<ValuePoint>>(it) }.getOrNull() }
        ?.sortedBy { it.day }
        ?: emptyList()

    /** Record today's value (one point per day), keep the last 60 days, return the series. */
    fun record(repository: ReliquaryRepository, value: Double, nowMs: Long): List<ValuePoint> {
        val today = nowMs / DAY_MILLIS
        val series = (load(repository).filter { it.day != today } + ValuePoint(today, value))
            .sortedBy { it.day }
            .takeLast(60)
        repository.setSetting(KEY, ReliquaryJson.encodeToString(series))
        return series
    }
}
