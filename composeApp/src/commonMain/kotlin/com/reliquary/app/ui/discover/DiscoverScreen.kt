package com.reliquary.app.ui.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reliquary.app.data.nowMillis
import com.reliquary.app.di.AppContainer
import com.reliquary.app.metadata.MetadataResult
import com.reliquary.app.ui.Navigator
import com.reliquary.app.ui.components.CoverImage
import com.reliquary.app.ui.theme.ReliquaryMuted
import com.reliquary.app.ui.theme.ReliquarySurfaceVariant
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text

@Composable
fun DiscoverScreen(container: AppContainer, navigator: Navigator) {
    val discover = container.discoverService
    var loading by remember { mutableStateOf(true) }
    val movies = remember { mutableStateListOf<MetadataResult>() }
    val tv = remember { mutableStateListOf<MetadataResult>() }
    val anime = remember { mutableStateListOf<MetadataResult>() }
    val added = remember { mutableStateListOf<String>() }

    LaunchedEffect(Unit) {
        if (discover.hasTmdb) {
            movies.addAll(discover.trendingMovies())
            tv.addAll(discover.trendingTv())
        }
        if (discover.hasSimkl) anime.addAll(discover.trendingAnime())
        loading = false
    }

    fun keyOf(r: MetadataResult) = "${r.mediaType}-${r.identifier ?: r.title}"
    fun add(r: MetadataResult) {
        val now = nowMillis()
        container.repository.importOrUpdate(r.toCollectionItem().copy(wanted = true, addedAt = now, updatedAt = now))
        added.add(keyOf(r))
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text("Discover", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 24.sp)
        Text("Trending now — tap to add to your wishlist.", color = ReliquaryMuted, fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))

        if (!discover.hasTmdb && !discover.hasSimkl) {
            Spacer(Modifier.height(20.dp))
            Text(
                "Add a TMDB key (movies & TV) and/or a Simkl Client ID (anime) in " +
                    "Settings to see trending titles here.",
                color = ReliquaryMuted,
            )
            return@Column
        }
        if (loading) {
            Spacer(Modifier.height(20.dp))
            Text("Loading trending titles…", color = ReliquaryMuted)
        }
        Section("Trending Movies", movies, added, ::keyOf) { add(it) }
        Section("Trending TV", tv, added, ::keyOf) { add(it) }
        Section("Trending Anime", anime, added, ::keyOf) { add(it) }
    }
}

@Composable
private fun Section(
    title: String,
    items: List<MetadataResult>,
    added: List<String>,
    keyOf: (MetadataResult) -> String,
    onAdd: (MetadataResult) -> Unit,
) {
    if (items.isEmpty()) return
    Spacer(Modifier.height(16.dp))
    Text(title, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 18.sp)
    Spacer(Modifier.height(8.dp))
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items.forEach { result ->
            val isAdded = keyOf(result) in added
            Column(Modifier.width(130.dp).clickable(enabled = !isAdded) { onAdd(result) }) {
                Box {
                    CoverImage(
                        url = result.coverUrl,
                        contentDescription = result.title,
                        modifier = Modifier.width(130.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(8.dp)),
                    )
                    Box(
                        Modifier.align(Alignment.BottomEnd).padding(6.dp).clip(RoundedCornerShape(50))
                            .background(if (isAdded) ReliquarySurfaceVariant else MaterialTheme.colorScheme.primary)
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    ) {
                        Text(if (isAdded) "✓" else "+ Wishlist", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    result.title,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
