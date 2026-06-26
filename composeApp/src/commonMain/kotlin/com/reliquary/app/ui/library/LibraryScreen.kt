package com.reliquary.app.ui.library

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reliquary.app.di.AppContainer
import com.reliquary.app.domain.CollectionItem
import com.reliquary.app.ui.ActiveTab
import com.reliquary.app.ui.Navigator
import com.reliquary.app.ui.Screen
import com.reliquary.app.ui.components.CoverImage
import com.reliquary.app.ui.components.PillButton
import com.reliquary.app.ui.theme.ReliquaryMuted

@Composable
fun LibraryScreen(container: AppContainer, active: ActiveTab, navigator: Navigator) {
    val items by remember(active) {
        when (active) {
            is ActiveTab.Builtin -> container.repository.itemsByType(active.type.name)
            is ActiveTab.Custom -> container.repository.itemsByCustomTab(active.tab.id)
        }
    }.collectAsState(emptyList())
    val featured = items.firstOrNull()
    val canImport = active is ActiveTab.Builtin

    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Hero(
                title = active.title,
                featured = featured,
                count = items.size,
                showImport = canImport && active.supportsBarcode,
                onView = { featured?.let { navigator.push(Screen.Detail(it.id)) } },
                onAdd = { navigator.push(Screen.EditItem(null, active.mediaTypeName, active.customTabId)) },
                onImport = {
                    if (active is ActiveTab.Builtin) {
                        navigator.push(Screen.SearchImport(active.type, active.customTabId))
                    }
                },
            )
        }
        if (items.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) { EmptyState(active.title) }
        } else {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = "All ${active.title}",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                )
            }
            items(items, key = { it.id }) { item ->
                ItemCard(item) { navigator.push(Screen.Detail(item.id)) }
            }
        }
    }
}

@Composable
private fun Hero(
    title: String,
    featured: CollectionItem?,
    count: Int,
    showImport: Boolean,
    onView: () -> Unit,
    onAdd: () -> Unit,
    onImport: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(340.dp)
            .clip(RoundedCornerShape(12.dp)),
    ) {
        if (featured?.coverImage != null) {
            CoverImage(featured.coverImage, featured.title, Modifier.fillMaxSize())
        } else {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.linearGradient(listOf(Color(0xFF0F3631), Color(0xFF0E1413))),
                ),
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0.35f to Color.Transparent,
                    1f to MaterialTheme.colorScheme.background,
                ),
            ),
        )
        Column(Modifier.align(Alignment.BottomStart).padding(24.dp)) {
            Text(
                text = featured?.title ?: title,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Black,
                fontSize = 40.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = featured?.creators
                    ?: if (count > 0) "$count ${if (count == 1) "title" else "titles"}" else "Nothing here yet",
                color = ReliquaryMuted,
                fontSize = 15.sp,
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (featured != null) {
                    PillButton(
                        label = "View",
                        icon = Icons.Filled.PlayArrow,
                        background = MaterialTheme.colorScheme.onBackground,
                        foreground = Color.Black,
                        onClick = onView,
                    )
                }
                PillButton(
                    label = "Add",
                    icon = Icons.Filled.Add,
                    background = Color(0xCC4D4D4D),
                    foreground = MaterialTheme.colorScheme.onBackground,
                    onClick = onAdd,
                )
                if (showImport) {
                    PillButton(
                        label = "Scan / Search",
                        icon = Icons.Filled.QrCodeScanner,
                        background = Color(0xCC4D4D4D),
                        foreground = MaterialTheme.colorScheme.onBackground,
                        onClick = onImport,
                    )
                }
            }
        }
    }
}

@Composable
private fun ItemCard(item: CollectionItem, onClick: () -> Unit) {
    Column(Modifier.clickable(onClick = onClick)) {
        CoverImage(
            url = item.coverImage,
            contentDescription = item.title,
            modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(8.dp)),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = item.title,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        item.releaseYear?.let {
            Text(text = it.toString(), color = ReliquaryMuted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun EmptyState(title: String) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "No ${title.lowercase()} yet",
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Use Add to enter one manually, or Scan / Search to import.",
            color = ReliquaryMuted,
            fontSize = 14.sp,
        )
    }
}
