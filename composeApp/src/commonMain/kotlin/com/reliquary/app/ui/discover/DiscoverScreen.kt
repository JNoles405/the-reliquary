package com.reliquary.app.ui.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.reliquary.app.ui.components.VScrollColumn
import com.reliquary.app.ui.theme.ReliquaryMuted
import com.reliquary.app.ui.theme.ReliquarySurface
import com.reliquary.app.ui.theme.ReliquarySurfaceVariant

@Composable
fun DiscoverScreen(container: AppContainer, navigator: Navigator) {
    val discover = container.discoverService
    var loading by remember { mutableStateOf(true) }
    val movies = remember { mutableStateListOf<MetadataResult>() }
    val tv = remember { mutableStateListOf<MetadataResult>() }
    val anime = remember { mutableStateListOf<MetadataResult>() }
    val books = remember { mutableStateListOf<MetadataResult>() }
    val added = remember { mutableStateListOf<String>() }
    var preview by remember { mutableStateOf<MetadataResult?>(null) }

    fun keyOf(r: MetadataResult) = "${r.mediaType}-${r.identifier ?: r.title}"

    LaunchedEffect(Unit) {
        if (discover.hasTmdb) {
            movies.addAll(discover.trendingMovies())
            tv.addAll(discover.trendingTv())
        }
        if (discover.hasSimkl) anime.addAll(discover.trendingAnime())
        books.addAll(discover.trendingBooks())
        // Reflect what's already in the collection so the wishlist state survives navigation.
        (movies + tv + anime + books).forEach { r ->
            if (container.repository.findDuplicate(r.toCollectionItem()) != null) added.add(keyOf(r))
        }
        loading = false
    }

    fun add(r: MetadataResult) {
        val now = nowMillis()
        container.repository.importOrUpdate(r.toCollectionItem().copy(wanted = true, addedAt = now, updatedAt = now))
        if (keyOf(r) !in added) added.add(keyOf(r))
    }

    Box(Modifier.fillMaxSize()) {
        VScrollColumn(contentPadding = PaddingValues(20.dp)) {
            Text("Discover", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 24.sp)
            Text("Trending now — tap a title for details, then add it to your wishlist.", color = ReliquaryMuted, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))

            if (loading) {
                Spacer(Modifier.height(20.dp))
                Text("Loading trending titles…", color = ReliquaryMuted)
            }
            Section("Trending Movies", movies, added, ::keyOf) { preview = it }
            Section("Trending TV", tv, added, ::keyOf) { preview = it }
            Section("Trending Anime", anime, added, ::keyOf) { preview = it }
            Section("Popular Books", books, added, ::keyOf) { preview = it }

            if (!loading && movies.isEmpty() && tv.isEmpty() && anime.isEmpty() && books.isEmpty()) {
                Spacer(Modifier.height(20.dp))
                Text(
                    "Add a TMDB key (movies & TV) and/or a Simkl Client ID (anime) in " +
                        "Settings to see more trending titles here.",
                    color = ReliquaryMuted,
                )
            }
        }

        preview?.let { result ->
            PreviewOverlay(
                container = container,
                base = result,
                isAdded = keyOf(result) in added,
                onAdd = { add(result) },
                onClose = { preview = null },
            )
        }
    }
}

@Composable
private fun Section(
    title: String,
    items: List<MetadataResult>,
    added: List<String>,
    keyOf: (MetadataResult) -> String,
    onOpen: (MetadataResult) -> Unit,
) {
    if (items.isEmpty()) return
    Spacer(Modifier.height(16.dp))
    Text(title, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 18.sp)
    Spacer(Modifier.height(8.dp))
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items.forEach { result ->
            val isAdded = keyOf(result) in added
            Column(Modifier.width(130.dp).clickable { onOpen(result) }) {
                Box {
                    CoverImage(
                        url = result.coverUrl,
                        contentDescription = result.title,
                        modifier = Modifier.width(130.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(8.dp)),
                    )
                    if (isAdded) {
                        Box(
                            Modifier.align(Alignment.BottomEnd).padding(6.dp).clip(RoundedCornerShape(50))
                                .background(MaterialTheme.colorScheme.primary)
                                .padding(horizontal = 9.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "✓ Wishlist",
                                color = Color.Black,
                                fontSize = 11.sp,
                                lineHeight = 18.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
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

@Composable
private fun PreviewOverlay(
    container: AppContainer,
    base: MetadataResult,
    isAdded: Boolean,
    onAdd: () -> Unit,
    onClose: () -> Unit,
) {
    // Fetch the richer view (cast, backdrop, genres) once the card is opened.
    var detail by remember(base) { mutableStateOf(base) }
    LaunchedEffect(base) { detail = container.discoverService.detailsFor(base) }

    val backdrop = detail.extra["_backdrop"]
    val cast = detail.extra["Cast"]
    val director = detail.extra["Director"]
    val runtime = detail.extra["Runtime"]

    // Scrim that closes on tap; the card itself swallows clicks.
    Box(
        Modifier.fillMaxSize().background(Color(0xCC000000)).clickable(onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.fillMaxWidth(0.7f).fillMaxSize(0.86f)
                .clip(RoundedCornerShape(16.dp)).background(ReliquarySurface)
                .clickable(enabled = false) {},
        ) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Box(Modifier.fillMaxWidth().aspectRatio(16f / 7f)) {
                    CoverImage(
                        url = backdrop ?: detail.coverUrl,
                        contentDescription = detail.title,
                        modifier = Modifier.fillMaxSize(),
                    )
                    // Close button.
                    Box(
                        Modifier.align(Alignment.TopEnd).padding(10.dp).clip(RoundedCornerShape(50))
                            .background(Color(0x99000000)).clickable(onClick = onClose)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("✕  Close", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Column(Modifier.padding(24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CoverImage(
                            url = detail.coverUrl,
                            contentDescription = detail.title,
                            modifier = Modifier.width(110.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(8.dp)),
                        )
                        Spacer(Modifier.width(18.dp))
                        Column(Modifier.weight(1f)) {
                            Text(detail.title, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 24.sp)
                            val meta = listOfNotNull(
                                detail.releaseYear?.toString(),
                                detail.mediaType.displayName,
                                runtime,
                                detail.genres,
                                detail.rating?.let { "★ ${(it * 10).toInt() / 10.0}" },
                            ).joinToString("  ·  ")
                            if (meta.isNotBlank()) {
                                Spacer(Modifier.height(6.dp))
                                Text(meta, color = ReliquaryMuted, fontSize = 13.sp)
                            }
                            director?.let {
                                Spacer(Modifier.height(4.dp))
                                Text("Directed by $it", color = ReliquaryMuted, fontSize = 13.sp)
                            }
                            Spacer(Modifier.height(14.dp))
                            WishlistButton(isAdded, onAdd)
                        }
                    }

                    detail.description?.takeIf { it.isNotBlank() }?.let {
                        Spacer(Modifier.height(20.dp))
                        Text("Overview", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(Modifier.height(6.dp))
                        Text(it, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f), fontSize = 14.sp, lineHeight = 21.sp)
                    }
                    cast?.let {
                        Spacer(Modifier.height(20.dp))
                        Text("Cast", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(Modifier.height(6.dp))
                        Text(it, color = ReliquaryMuted, fontSize = 14.sp, lineHeight = 21.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun WishlistButton(isAdded: Boolean, onAdd: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(50))
            .background(if (isAdded) ReliquarySurfaceVariant else MaterialTheme.colorScheme.primary)
            .clickable(enabled = !isAdded, onClick = onAdd)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (isAdded) "✓ In your wishlist" else "+ Add to Wishlist",
            color = if (isAdded) MaterialTheme.colorScheme.onBackground else Color.Black,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
        )
    }
}
