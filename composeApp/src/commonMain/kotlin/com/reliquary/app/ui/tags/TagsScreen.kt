package com.reliquary.app.ui.tags

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import com.reliquary.app.domain.parseTags
import com.reliquary.app.ui.Navigator
import com.reliquary.app.ui.Screen
import com.reliquary.app.ui.components.CoverImage
import com.reliquary.app.ui.components.VScrollbar
import com.reliquary.app.ui.theme.ReliquaryMuted
import com.reliquary.app.ui.theme.ReliquarySurface

private fun tagsOf(item: CollectionItem): List<String> = parseTags(item.tags)

@Composable
fun TagsScreen(container: AppContainer, navigator: Navigator) {
    val items = remember { container.repository.allItems().filter { !it.deleted } }
    val tagCounts = remember(items) {
        items.flatMap { tagsOf(it) }.groupingBy { it }.eachCount().toList().sortedByDescending { it.second }
    }
    val listState = rememberLazyListState()

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("Tags", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 24.sp)
        Spacer(Modifier.height(12.dp))
        if (tagCounts.isEmpty()) {
            Text("No tags yet. Add comma-separated tags to an item in its Edit screen.", color = ReliquaryMuted)
            return@Column
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(tagCounts, key = { it.first }) { (tag, count) ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(ReliquarySurface)
                            .clickable { navigator.push(Screen.TagItems(tag)) }.padding(14.dp),
                    ) {
                        Text(tag, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text(count.toString(), color = ReliquaryMuted)
                    }
                }
            }
            VScrollbar(listState)
        }
    }
}

@Composable
fun TagItemsScreen(container: AppContainer, tag: String, navigator: Navigator) {
    val matches = remember(tag) {
        container.repository.allItems().filter { !it.deleted && tag in tagsOf(it) }
            .sortedBy { (it.sortTitle ?: it.title).lowercase() }
    }
    val gridState = rememberLazyGridState()
    Column(Modifier.fillMaxSize().padding(top = 8.dp)) {
        Text(
            "Tag: $tag",
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyVerticalGrid(
                state = gridState,
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
                        Text(
                            item.title,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            VScrollbar(gridState)
        }
    }
}
