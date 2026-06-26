package com.reliquary.app.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reliquary.app.data.nowMillis
import com.reliquary.app.di.AppContainer
import com.reliquary.app.domain.MediaType
import com.reliquary.app.domain.Status
import com.reliquary.app.domain.WANTED_KEY
import com.reliquary.app.domain.parseMoney
import com.reliquary.app.metadata.ReliquaryJson
import kotlin.math.round
import com.reliquary.app.ui.Navigator
import com.reliquary.app.ui.theme.ReliquaryMuted
import com.reliquary.app.ui.theme.ReliquaryTeal
import com.reliquary.app.ui.theme.ReliquarySurface
import com.reliquary.app.ui.theme.ReliquarySurfaceVariant
import kotlinx.serialization.decodeFromString

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StatsScreen(container: AppContainer, navigator: Navigator) {
    val items = remember { container.repository.allItems().filter { !it.deleted } }
    val loans = remember { container.repository.activeLoansNow() }
    val now = remember { nowMillis() }

    val decoded = remember(items) {
        items.map { item ->
            item to (item.extraJson
                ?.let { runCatching { ReliquaryJson.decodeFromString<Map<String, String>>(it) }.getOrNull() }
                ?: emptyMap())
        }
    }
    val total = items.size
    val wanted = decoded.count { it.second[WANTED_KEY] == "true" }
    val owned = total - wanted
    val favorites = items.count { it.favorite }
    val overdue = loans.count { it.dueAt != null && it.dueAt < now }
    val finished = decoded.count { it.second[WANTED_KEY] != "true" && it.second[Status.KEY] in Status.DONE }
    val finishedPct = if (owned > 0) finished * 100 / owned else 0
    val collectionValue = decoded
        .filter { it.second[WANTED_KEY] != "true" }
        .sumOf { parseMoney(it.second["Current Value"]) ?: 0.0 }

    val typeCounts = MediaType.entries.map { it.displayName to items.count { i -> i.mediaType == it.name } }
        .toMutableList()
    val customCount = items.count { i -> MediaType.entries.none { it.name == i.mediaType } }
    if (customCount > 0) typeCounts.add("Custom" to customCount)
    val maxTypeCount = (typeCounts.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text("Library Stats", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 24.sp)
        Spacer(Modifier.height(16.dp))

        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("Owned", owned.toString())
            StatCard("Wishlist", wanted.toString())
            StatCard("Finished", "$finished ($finishedPct%)")
            StatCard("Favorites", favorites.toString())
            StatCard("On loan", loans.size.toString())
            StatCard("Overdue", overdue.toString())
            if (collectionValue > 0) {
                StatCard("Collection value", "$" + (round(collectionValue * 100) / 100.0))
            }
        }

        Spacer(Modifier.height(24.dp))
        Text("By category", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Spacer(Modifier.height(10.dp))
        typeCounts.forEach { (label, count) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(label, color = MaterialTheme.colorScheme.onBackground, fontSize = 14.sp, modifier = Modifier.width(90.dp))
                Box(Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(ReliquarySurfaceVariant)) {
                    Box(
                        Modifier.fillMaxWidth(count.toFloat() / maxTypeCount)
                            .height(10.dp).clip(RoundedCornerShape(5.dp)).background(ReliquaryTeal),
                    )
                }
                Text(count.toString(), color = ReliquaryMuted, fontSize = 14.sp, modifier = Modifier.width(40.dp).padding(start = 10.dp))
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String) {
    Column(
        Modifier.width(150.dp).clip(RoundedCornerShape(10.dp)).background(ReliquarySurface).padding(16.dp),
    ) {
        Text(value, color = ReliquaryTeal, fontWeight = FontWeight.Black, fontSize = 26.sp)
        Spacer(Modifier.height(4.dp))
        Text(label, color = ReliquaryMuted, fontSize = 13.sp)
    }
}
