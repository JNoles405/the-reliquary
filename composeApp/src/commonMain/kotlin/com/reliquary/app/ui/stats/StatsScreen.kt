package com.reliquary.app.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reliquary.app.data.nowMillis
import com.reliquary.app.di.AppContainer
import com.reliquary.app.domain.MediaType
import com.reliquary.app.domain.Status
import com.reliquary.app.domain.parseMoney
import com.reliquary.app.metadata.ReliquaryJson
import com.reliquary.app.tools.ValueHistory
import com.reliquary.app.tools.ValuePoint
import com.reliquary.app.util.DAY_MILLIS
import com.reliquary.app.util.formatDate
import kotlin.math.round
import com.reliquary.app.ui.Navigator
import com.reliquary.app.ui.components.VScrollColumn
import com.reliquary.app.ui.theme.ReliquaryMuted
import com.reliquary.app.ui.theme.ReliquarySurface
import com.reliquary.app.ui.theme.ReliquarySurfaceVariant
import kotlinx.serialization.decodeFromString

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StatsScreen(container: AppContainer, navigator: Navigator) {
    val items = remember { container.repository.allItems().filter { !it.deleted } }
    val loans = remember { container.repository.activeLoansNow() }
    val now = remember { nowMillis() }

    val total = items.size
    val wanted = items.count { it.wanted }
    val owned = total - wanted
    val favorites = items.count { it.favorite }
    val overdue = loans.count { it.dueAt != null && it.dueAt < now }
    val finished = items.count { !it.wanted && it.status in Status.DONE }
    val finishedPct = if (owned > 0) finished * 100 / owned else 0
    val goal = remember { container.repository.getSetting("stats.completionGoal")?.toIntOrNull() ?: 0 }
    val thisYear = remember { formatDate(now).substring(0, 4) }
    val finishedThisYear = remember(items) {
        items.count { item ->
            !item.wanted && item.extraJson
                ?.let { runCatching { ReliquaryJson.decodeFromString<Map<String, String>>(it) }.getOrNull() }
                ?.get("_finishedAt")?.toLongOrNull()
                ?.let { formatDate(it).substring(0, 4) == thisYear } == true
        }
    }
    val collectionValue = remember(items) {
        items.filter { !it.wanted }.sumOf { item ->
            parseMoney(
                item.extraJson
                    ?.let { runCatching { ReliquaryJson.decodeFromString<Map<String, String>>(it) }.getOrNull() }
                    ?.get("Current Value"),
            ) ?: 0.0
        }
    }

    val typeCounts = MediaType.entries.map { it.displayName to items.count { i -> i.mediaType == it.name } }
        .toMutableList()
    val customCount = items.count { i -> MediaType.entries.none { it.name == i.mediaType } }
    if (customCount > 0) typeCounts.add("Custom" to customCount)
    val maxTypeCount = (typeCounts.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)

    val topGenres = remember(items) {
        items.flatMap { it.genres?.split(",").orEmpty() }.map { it.trim() }.filter { it.isNotBlank() }
            .groupingBy { it }.eachCount().toList().sortedByDescending { it.second }.take(8)
    }
    val maxGenre = (topGenres.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)

    val byDecade = remember(items) {
        items.filter { !it.wanted && it.releaseYear != null }
            .groupBy { (it.releaseYear!! / 10) * 10 }
            .map { (decade, list) -> "${decade}s" to list.size }
            .sortedBy { it.first }
    }
    val maxDecade = (byDecade.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)

    val ratingDist = remember(items) {
        (5 downTo 1).map { star ->
            "$star★" to items.count { item ->
                item.extraJson?.let { Regex("\"_myRating\"\\s*:\\s*\"?(\\d)\"?").find(it)?.groupValues?.get(1)?.toIntOrNull() } == star
            }
        }
    }
    val ratedAny = ratingDist.any { it.second > 0 }
    val maxRating = (ratingDist.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)

    val topPeople = remember(items) {
        items.flatMap { item ->
            val e = item.extraJson
                ?.let { runCatching { ReliquaryJson.decodeFromString<Map<String, String>>(it) }.getOrNull() } ?: emptyMap()
            val directors = e["Director"]?.split(",")?.map { it.trim() }.orEmpty()
            val cast = e["Cast"]?.split(",")?.map { it.substringBefore(" (").trim() }.orEmpty()
            val creators = item.creators?.split(",")?.map { it.trim() }.orEmpty()
            (directors + cast + creators).filter { it.isNotBlank() }
        }.groupingBy { it }.eachCount().toList()
            .filter { it.second >= 2 }
            .sortedByDescending { it.second }
            .take(10)
    }
    val maxPeople = (topPeople.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)

    val addedByMonth = remember(items) {
        items.filter { !it.wanted && it.addedAt > 0 }
            .groupingBy { formatDate(it.addedAt).substring(0, 7) }.eachCount()
            .toList().sortedBy { it.first }.takeLast(12)
    }
    val maxAdded = (addedByMonth.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)

    val completionByCat = remember(items) {
        MediaType.entries.mapNotNull { type ->
            val ownedCount = items.count { !it.wanted && it.mediaType == type.name }
            if (ownedCount == 0) return@mapNotNull null
            val doneCount = items.count { !it.wanted && it.mediaType == type.name && it.status in Status.DONE }
            type.displayName to (doneCount * 100 / ownedCount)
        }
    }

    // Record today's collection value (once/day) and load the series to chart it.
    var valueHistory by remember { mutableStateOf<List<ValuePoint>>(emptyList()) }
    LaunchedEffect(Unit) {
        valueHistory = if (collectionValue > 0) {
            ValueHistory.record(container.repository, collectionValue, now)
        } else {
            ValueHistory.load(container.repository)
        }
    }

    VScrollColumn(contentPadding = PaddingValues(20.dp)) {
        Text("Library Stats", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 24.sp)
        Spacer(Modifier.height(16.dp))

        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("Owned", owned.toString())
            StatCard("Wishlist", wanted.toString())
            StatCard("Finished", "$finished ($finishedPct%)")
            StatCard("Favorites", favorites.toString())
            StatCard("On loan", loans.size.toString())
            StatCard("Overdue", overdue.toString())
            if (finishedThisYear > 0) StatCard("Finished in $thisYear", finishedThisYear.toString())
            if (collectionValue > 0) {
                StatCard("Collection value", "$" + (round(collectionValue * 100) / 100.0))
            }
            if (goal > 0) StatCard("Goal", "$finished / $goal")
        }

        if (goal > 0) {
            Spacer(Modifier.height(16.dp))
            val pct = (finished.toFloat() / goal).coerceIn(0f, 1f)
            Text(
                "Completion goal — ${(pct * 100).toInt()}%" + if (finished >= goal) " · reached! 🎉" else "",
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
            )
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                Box(Modifier.fillMaxWidth(pct).height(12.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.primary))
            }
        }

        StatSection("By category", typeCounts, maxTypeCount)
        if (topGenres.isNotEmpty()) StatSection("Top genres", topGenres, maxGenre)
        if (byDecade.isNotEmpty()) StatSection("By decade", byDecade, maxDecade)
        if (ratedAny) StatSection("Your ratings", ratingDist, maxRating)
        if (topPeople.isNotEmpty()) StatSection("Most-collected people", topPeople, maxPeople)
        if (addedByMonth.size >= 2) StatSection("Added per month", addedByMonth, maxAdded)
        if (completionByCat.any { it.second > 0 }) StatSection("Finished by category (%)", completionByCat, 100)

        if (valueHistory.size >= 2) {
            val maxValue = valueHistory.maxOf { it.value }.coerceAtLeast(0.01)
            Spacer(Modifier.height(24.dp))
            Text("Collection value over time", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.height(10.dp))
            valueHistory.takeLast(14).forEach { point ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(formatDate(point.day * DAY_MILLIS), color = MaterialTheme.colorScheme.onBackground, fontSize = 13.sp, modifier = Modifier.width(96.dp))
                    Box(Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                        Box(
                            Modifier.fillMaxWidth((point.value / maxValue).toFloat().coerceIn(0f, 1f))
                                .height(10.dp).clip(RoundedCornerShape(5.dp)).background(MaterialTheme.colorScheme.primary),
                        )
                    }
                    Text("$" + (round(point.value * 100) / 100.0), color = ReliquaryMuted, fontSize = 13.sp, modifier = Modifier.width(72.dp).padding(start = 10.dp))
                }
            }
        }
    }
}

@Composable
private fun StatSection(title: String, rows: List<Pair<String, Int>>, max: Int) {
    Spacer(Modifier.height(24.dp))
    Text(title, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 18.sp)
    Spacer(Modifier.height(10.dp))
    rows.forEach { (label, count) -> BarRow(label, count, max) }
}

@Composable
private fun BarRow(label: String, count: Int, max: Int) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text(label, color = MaterialTheme.colorScheme.onBackground, fontSize = 14.sp, modifier = Modifier.width(96.dp))
        Box(Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
            Box(
                Modifier.fillMaxWidth((count.toFloat() / max).coerceIn(0f, 1f))
                    .height(10.dp).clip(RoundedCornerShape(5.dp)).background(MaterialTheme.colorScheme.primary),
            )
        }
        Text(count.toString(), color = ReliquaryMuted, fontSize = 14.sp, modifier = Modifier.width(40.dp).padding(start = 10.dp))
    }
}

@Composable
private fun StatCard(label: String, value: String) {
    Column(
        Modifier.width(150.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp),
    ) {
        Text(value, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black, fontSize = 26.sp)
        Spacer(Modifier.height(4.dp))
        Text(label, color = ReliquaryMuted, fontSize = 13.sp)
    }
}
