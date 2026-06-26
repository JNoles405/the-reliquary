package com.reliquary.app.ui.series

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reliquary.app.di.AppContainer
import com.reliquary.app.domain.CollectionItem
import com.reliquary.app.domain.SERIES_KEY
import com.reliquary.app.domain.SERIES_NUM_KEY
import com.reliquary.app.metadata.ReliquaryJson
import com.reliquary.app.ui.Navigator
import com.reliquary.app.ui.Screen
import com.reliquary.app.ui.components.CoverImage
import com.reliquary.app.ui.theme.ReliquaryMuted
import com.reliquary.app.ui.theme.ReliquarySurface
import kotlinx.serialization.decodeFromString

private fun extrasOf(item: CollectionItem): Map<String, String> = item.extraJson
    ?.let { runCatching { ReliquaryJson.decodeFromString<Map<String, String>>(it) }.getOrNull() } ?: emptyMap()

private fun seriesOf(item: CollectionItem): String? = extrasOf(item)[SERIES_KEY]?.takeIf { it.isNotBlank() }

@Composable
fun SeriesScreen(container: AppContainer, navigator: Navigator) {
    val items = remember { container.repository.allItems().filter { !it.deleted } }
    val counts = remember(items) {
        items.mapNotNull { seriesOf(it) }.groupingBy { it }.eachCount().toList().sortedBy { it.first.lowercase() }
    }
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("Series", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 24.sp)
        Spacer(Modifier.height(12.dp))
        if (counts.isEmpty()) {
            Text("No series yet. Set a Series name on an item in its Edit screen.", color = ReliquaryMuted)
            return@Column
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(counts, key = { it.first }) { (series, count) ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(ReliquarySurface)
                        .clickable { navigator.push(Screen.SeriesItems(series)) }.padding(14.dp),
                ) {
                    Text(series, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text(count.toString(), color = ReliquaryMuted)
                }
            }
        }
    }
}

@Composable
fun SeriesItemsScreen(container: AppContainer, series: String, navigator: Navigator) {
    val matches = remember(series) {
        container.repository.allItems()
            .filter { !it.deleted && seriesOf(it) == series }
            .sortedWith(
                compareBy(
                    { extrasOf(it)[SERIES_NUM_KEY]?.toIntOrNull() ?: Int.MAX_VALUE },
                    { it.releaseYear ?: Long.MAX_VALUE },
                    { (it.sortTitle ?: it.title).lowercase() },
                ),
            )
    }
    val missing = remember(matches) {
        val nums = matches.mapNotNull { extrasOf(it)[SERIES_NUM_KEY]?.toIntOrNull() }.toSortedSet()
        if (nums.size >= 2) ((nums.first()..nums.last()).toSet() - nums).sorted() else emptyList()
    }
    Column(Modifier.fillMaxSize().padding(top = 8.dp)) {
        Text(
            series,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        if (missing.isNotEmpty()) {
            Text(
                "Missing #: ${missing.joinToString(", ")}",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 8.dp),
            )
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            gridItems(matches, key = { it.id }) { item ->
                Column(Modifier.clickable { navigator.push(Screen.Detail(item.id)) }) {
                    CoverImage(
                        url = item.coverImage,
                        contentDescription = item.title,
                        modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(8.dp)),
                    )
                    Spacer(Modifier.height(6.dp))
                    val num = extrasOf(item)[SERIES_NUM_KEY]?.let { "#$it · " } ?: ""
                    Text(
                        "$num${item.title}",
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
