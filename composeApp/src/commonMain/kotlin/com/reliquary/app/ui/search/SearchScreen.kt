package com.reliquary.app.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reliquary.app.di.AppContainer
import com.reliquary.app.domain.CollectionItem
import com.reliquary.app.domain.MediaType
import com.reliquary.app.ui.Navigator
import com.reliquary.app.ui.Screen
import com.reliquary.app.ui.components.CoverImage
import com.reliquary.app.ui.theme.ReliquaryMuted
import com.reliquary.app.ui.theme.ReliquarySurface
import kotlinx.coroutines.flow.flowOf

@Composable
fun SearchScreen(container: AppContainer, navigator: Navigator) {
    var query by remember { mutableStateOf("") }
    val trimmed = query.trim()
    val results by remember(trimmed) {
        if (trimmed.length < 2) flowOf(emptyList()) else container.repository.searchItems(trimmed)
    }.collectAsState(emptyList())

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Search your whole collection") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        )
        Spacer(Modifier.height(14.dp))
        when {
            trimmed.length < 2 ->
                Text("Type at least two characters to search across every tab.", color = ReliquaryMuted)
            results.isEmpty() ->
                Text("No matches for \"$trimmed\".", color = ReliquaryMuted)
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(results, key = { it.id }) { item ->
                    ResultRow(item) { navigator.push(Screen.Detail(item.id)) }
                }
            }
        }
    }
}

@Composable
private fun ResultRow(item: CollectionItem, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(ReliquarySurface)
            .clickable(onClick = onClick).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverImage(
            url = item.coverImage,
            contentDescription = item.title,
            modifier = Modifier.size(width = 48.dp, height = 70.dp).clip(RoundedCornerShape(4.dp)),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.title,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val sub = listOfNotNull(item.creators, item.releaseYear?.toString()).joinToString(" · ")
            if (sub.isNotBlank()) {
                Text(sub, color = ReliquaryMuted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(categoryLabel(item), color = MaterialTheme.colorScheme.primary, fontSize = 11.sp)
        }
    }
}

private fun categoryLabel(item: CollectionItem): String =
    MediaType.entries.firstOrNull { it.name == item.mediaType }?.displayName ?: "Custom"
