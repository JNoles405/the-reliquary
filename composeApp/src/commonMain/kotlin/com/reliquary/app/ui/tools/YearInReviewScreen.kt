package com.reliquary.app.ui.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reliquary.app.data.nowMillis
import com.reliquary.app.di.AppContainer
import com.reliquary.app.domain.CollectionItem
import com.reliquary.app.metadata.ReliquaryJson
import com.reliquary.app.ui.Navigator
import com.reliquary.app.ui.components.VScrollColumn
import com.reliquary.app.ui.theme.ReliquaryMuted
import com.reliquary.app.util.formatDate
import kotlinx.serialization.decodeFromString

private fun extrasOf(item: CollectionItem): Map<String, String> = item.extraJson
    ?.let { runCatching { ReliquaryJson.decodeFromString<Map<String, String>>(it) }.getOrNull() } ?: emptyMap()

@Composable
fun YearInReviewScreen(container: AppContainer, navigator: Navigator) {
    val items = remember { container.repository.allItems().filter { !it.deleted } }
    val year = remember { formatDate(nowMillis()).substring(0, 4) }

    val finished = remember(items) {
        items.filter { item ->
            !item.wanted && extrasOf(item)["_finishedAt"]?.toLongOrNull()?.let { formatDate(it).substring(0, 4) == year } == true
        }
    }
    val added = remember(items) {
        items.filter { !it.wanted && it.addedAt > 0 && formatDate(it.addedAt).substring(0, 4) == year }
    }
    val topGenres = remember(finished) {
        finished.flatMap { it.genres?.split(",")?.map { g -> g.trim() } ?: emptyList() }
            .filter { it.isNotBlank() }.groupingBy { it }.eachCount().toList().sortedByDescending { it.second }.take(5)
    }
    val topPeople = remember(finished) {
        finished.flatMap { item ->
            val e = extrasOf(item)
            (e["Director"]?.split(",")?.map { it.trim() }.orEmpty()) +
                (e["Cast"]?.split(",")?.map { it.substringBefore(" (").trim() }.orEmpty())
        }.filter { it.isNotBlank() }.groupingBy { it }.eachCount().toList().sortedByDescending { it.second }.take(5)
    }

    VScrollColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("$year in Review", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Black, fontSize = 30.sp)

        if (finished.isEmpty() && added.isEmpty()) {
            Text(
                "Nothing recorded for $year yet. Mark items as finished and they'll show up here.",
                color = ReliquaryMuted,
                fontSize = 14.sp,
            )
            return@VScrollColumn
        }

        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Stat("Finished", finished.size.toString())
            Stat("Added", added.size.toString())
            topGenres.firstOrNull()?.let { Stat("Top genre", it.first) }
            topPeople.firstOrNull()?.let { Stat("Top person", it.first) }
        }

        if (topGenres.isNotEmpty()) ListBlock("Your top genres", topGenres)
        if (topPeople.isNotEmpty()) ListBlock("Your top people", topPeople)
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column(Modifier.width(160.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp)) {
        Text(value, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black, fontSize = 22.sp, maxLines = 1)
        Spacer(Modifier.height(4.dp))
        Text(label, color = ReliquaryMuted, fontSize = 13.sp)
    }
}

@Composable
private fun ListBlock(title: String, entries: List<Pair<String, Int>>) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp)) {
        Text(title, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(Modifier.height(8.dp))
        entries.forEachIndexed { i, (name, count) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                Text("${i + 1}.  $name", color = MaterialTheme.colorScheme.onBackground, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Text(count.toString(), color = ReliquaryMuted, fontSize = 14.sp)
            }
        }
    }
}
