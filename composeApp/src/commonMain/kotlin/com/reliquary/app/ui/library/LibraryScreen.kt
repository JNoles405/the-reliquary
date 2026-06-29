package com.reliquary.app.ui.library

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.rememberCoroutineScope
import com.reliquary.app.data.nowMillis
import com.reliquary.app.di.AppContainer
import com.reliquary.app.sync.defaultSyncFilePath
import com.reliquary.app.sync.writeTextFile
import com.reliquary.app.util.isDesktopPlatform
import com.reliquary.app.util.openUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.reliquary.app.domain.CollectionItem
import com.reliquary.app.domain.SERIES_KEY
import com.reliquary.app.domain.Status
import com.reliquary.app.domain.wishPriorityRank
import com.reliquary.app.ui.ActiveTab
import com.reliquary.app.ui.Navigator
import com.reliquary.app.ui.Screen
import com.reliquary.app.ui.components.CoverImage
import com.reliquary.app.ui.components.PillButton
import com.reliquary.app.ui.components.edgeToEdgeHorizontal
import com.reliquary.app.ui.components.VScrollbar
import com.reliquary.app.ui.theme.ReliquaryMuted
import com.reliquary.app.ui.theme.ReliquarySurfaceVariant

enum class SortOrder(val label: String) {
    TITLE("Title A–Z"),
    YEAR_NEW("Year (newest)"),
    RATING("Rating"),
    MY_RATING("My rating"),
    ADDED("Recently added"),
}

/** Read the user's 1–5 star rating from extras without a full JSON decode. */
private fun myRatingOf(item: CollectionItem): Int =
    item.extraJson?.let { Regex("\"_myRating\"\\s*:\\s*\"?(\\d)\"?").find(it)?.groupValues?.get(1)?.toIntOrNull() } ?: 0

/** Read the wishlist priority from extras without a full JSON decode. */
private fun priorityOf(item: CollectionItem): String? =
    item.extraJson?.let { Regex("\"_wishPriority\"\\s*:\\s*\"([^\"]+)\"").find(it)?.groupValues?.get(1) }

private fun SortOrder.comparator(): Comparator<CollectionItem> = when (this) {
    SortOrder.TITLE -> compareBy { (it.sortTitle ?: it.title).lowercase() }
    SortOrder.YEAR_NEW -> compareByDescending { it.releaseYear ?: Long.MIN_VALUE }
    SortOrder.RATING -> compareByDescending { it.rating ?: -1.0 }
    SortOrder.MY_RATING -> compareByDescending { myRatingOf(it) }
    SortOrder.ADDED -> compareByDescending { it.addedAt }
}

@Composable
fun LibraryScreen(container: AppContainer, active: ActiveTab, navigator: Navigator) {
    val items by remember(active) {
        when (active) {
            is ActiveTab.Builtin -> container.repository.itemsByType(active.type.name)
            is ActiveTab.Custom -> container.repository.itemsByCustomTab(active.tab.id)
        }
    }.collectAsState(emptyList())
    val activeLoans by remember { container.repository.activeLoans() }.collectAsState(emptyList())
    val onLoanIds = remember(activeLoans) { activeLoans.map { it.itemId }.toSet() }
    val scope = rememberCoroutineScope()

    var sort by remember(active) { mutableStateOf(SortOrder.TITLE) }
    var favoritesOnly by remember(active) { mutableStateOf(false) }
    var onLoanOnly by remember(active) { mutableStateOf(false) }
    var unfinishedOnly by remember(active) { mutableStateOf(false) }
    var wishlistOnly by remember(active) { mutableStateOf(false) }
    var statusFilter by remember(active) { mutableStateOf<String?>(null) }
    var genre by remember(active) { mutableStateOf<String?>(null) }

    val genres = remember(items) {
        items.flatMap { it.genres?.split(",").orEmpty() }
            .map { it.trim() }.filter { it.isNotBlank() }.distinct().sorted()
    }
    val statusOptions = remember(active, items) {
        when (active) {
            is ActiveTab.Builtin -> Status.optionsFor(active.type.name)
            is ActiveTab.Custom -> items.mapNotNull { it.status }.distinct()
        }
    }
    val displayed = remember(items, sort, favoritesOnly, onLoanOnly, unfinishedOnly, wishlistOnly, statusFilter, genre, onLoanIds) {
        val filtered = items.filter { item ->
            (if (wishlistOnly) item.wanted else !item.wanted) &&
                (!favoritesOnly || item.favorite) &&
                (!onLoanOnly || item.id in onLoanIds) &&
                (!unfinishedOnly || item.status !in Status.DONE) &&
                (statusFilter == null || item.status == statusFilter) &&
                (genre == null || item.genres?.contains(genre!!, ignoreCase = true) == true)
        }.sortedWith(sort.comparator())
        // In the wishlist view, surface higher-priority wants first (stable).
        if (wishlistOnly) filtered.sortedBy { wishPriorityRank(priorityOf(it)) } else filtered
    }
    val featured = displayed.firstOrNull() ?: items.firstOrNull()
    val canImport = active is ActiveTab.Builtin
    // A stable random sample to resurface older items; reshuffles only when the library changes.
    val rediscover = remember(items) { items.filter { !it.wanted }.shuffled().take(12) }

    var selectionMode by remember(active) { mutableStateOf(false) }
    val selected = remember(active) { mutableStateListOf<String>() }
    fun exitSelection() { selectionMode = false; selected.clear() }

    var bulkDialog by remember(active) { mutableStateOf<String?>(null) } // "tag" | "series" | "location"
    var bulkText by remember(active) { mutableStateOf("") }
    var statusDialog by remember(active) { mutableStateOf(false) }

    val viewsStore = remember { SmartViewsStore(container.repository) }
    var views by remember { mutableStateOf(viewsStore.list()) }
    var saveViewDialog by remember { mutableStateOf(false) }
    var manageViewsDialog by remember { mutableStateOf(false) }
    var viewName by remember { mutableStateOf("") }
    fun applyView(v: SmartView) {
        sort = SortOrder.entries.firstOrNull { it.name == v.sort } ?: SortOrder.TITLE
        favoritesOnly = v.favorites
        onLoanOnly = v.onLoan
        unfinishedOnly = v.unfinished
        wishlistOnly = v.wishlist
        genre = v.genre
    }

    var coverDp by remember { mutableStateOf(container.repository.getSetting("ui.coverSize")?.toIntOrNull() ?: 150) }
    fun cycleCover() {
        coverDp = when (coverDp) { in 0..120 -> 150; in 121..170 -> 190; else -> 110 }
        container.repository.setSetting("ui.coverSize", coverDp.toString())
    }
    val gridState = rememberLazyGridState()

    Box(Modifier.fillMaxSize()) {
    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Adaptive(coverDp.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Hero(
                title = active.title,
                featured = featured,
                count = displayed.size,
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
                Controls(
                    sort = sort, onSort = { sort = it },
                    favoritesOnly = favoritesOnly, onFavorites = { favoritesOnly = !favoritesOnly },
                    onLoanOnly = onLoanOnly, onLoan = { onLoanOnly = !onLoanOnly },
                    unfinishedOnly = unfinishedOnly, onUnfinished = { unfinishedOnly = !unfinishedOnly },
                    wishlistOnly = wishlistOnly, onWishlist = { wishlistOnly = !wishlistOnly },
                    statuses = statusOptions, statusFilter = statusFilter, onStatus = { statusFilter = it },
                    genres = genres, genre = genre, onGenre = { genre = it },
                    selectionMode = selectionMode,
                    onToggleSelect = { if (selectionMode) exitSelection() else selectionMode = true },
                    coverDp = coverDp,
                    onCycleCover = { cycleCover() },
                    onRandom = { displayed.randomOrNull()?.let { navigator.push(Screen.Detail(it.id)) } },
                    views = views,
                    onApplyView = { applyView(it) },
                    onSaveView = { viewName = ""; saveViewDialog = true },
                    onManageViews = { manageViewsDialog = true },
                )
            }
            if (selectionMode) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("${selected.size} selected", color = ReliquaryMuted, fontSize = 13.sp)
                        PillButton("Tag…", null, MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onBackground) {
                            if (selected.isNotEmpty()) { bulkText = ""; bulkDialog = "tag" }
                        }
                        PillButton("Add to series…", null, MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onBackground) {
                            if (selected.isNotEmpty()) { bulkText = ""; bulkDialog = "series" }
                        }
                        PillButton("Set status…", null, MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onBackground) {
                            if (selected.isNotEmpty()) statusDialog = true
                        }
                        PillButton("Set location…", null, MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onBackground) {
                            if (selected.isNotEmpty()) { bulkText = ""; bulkDialog = "location" }
                        }
                        PillButton("★ Favorite", null, MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onBackground) {
                            val now = nowMillis()
                            selected.toList().forEach { id ->
                                container.repository.getItem(id)?.let { container.repository.upsertItem(it.copy(favorite = true, updatedAt = now)) }
                            }
                            exitSelection()
                        }
                        PillButton("Export CSV", null, MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onBackground) {
                            val chosen = selected.toList().mapNotNull { container.repository.getItem(it) }
                            exitSelection()
                            if (chosen.isNotEmpty()) scope.launch {
                                val path = defaultSyncFilePath().replace("reliquary-sync.json", "reliquary-selected.csv")
                                val csv = withContext(Dispatchers.Default) { container.csvService.exportCsv(chosen) }
                                withContext(Dispatchers.Default) { writeTextFile(path, csv) }
                                if (isDesktopPlatform()) openUrl("file:///" + path.replace("\\", "/"))
                            }
                        }
                        PillButton("Mark finished", null, MaterialTheme.colorScheme.primary, Color.Black) {
                            selected.toList().forEach { id ->
                                container.repository.getItem(id)?.let {
                                    container.repository.updateStatus(id, Status.optionsFor(it.mediaType).last())
                                }
                            }
                            exitSelection()
                        }
                        PillButton("Delete", null, MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onBackground) {
                            selected.toList().forEach { container.repository.deleteItem(it) }
                            exitSelection()
                        }
                        PillButton("Done", null, MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onBackground) {
                            exitSelection()
                        }
                    }
                }
            }
            val showShelves = !favoritesOnly && !onLoanOnly && !unfinishedOnly && !wishlistOnly && genre == null && statusFilter == null
            if (showShelves) {
                val owned = items.filter { !it.wanted }
                val continueItems = owned.filter { it.status in Status.IN_PROGRESS }
                val recent = owned.sortedByDescending { it.addedAt }.take(12)
                val favorites = owned.filter { it.favorite }
                val loaned = owned.filter { it.id in onLoanIds }
                if (continueItems.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Shelf("Continue", continueItems) { navigator.push(Screen.Detail(it)) }
                    }
                }
                if (recent.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Shelf("Recently Added", recent) { navigator.push(Screen.Detail(it)) }
                    }
                }
                if (rediscover.size >= 4) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Shelf("Rediscover", rediscover) { navigator.push(Screen.Detail(it)) }
                    }
                }
                if (favorites.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Shelf("Favorites", favorites) { navigator.push(Screen.Detail(it)) }
                    }
                }
                if (loaned.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Shelf("On Loan", loaned) { navigator.push(Screen.Detail(it)) }
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = "All ${active.title}",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                )
            }
            if (displayed.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        "Nothing matches these filters.",
                        color = ReliquaryMuted,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            } else {
                items(displayed, key = { it.id }) { item ->
                    ItemCard(item, selected = selectionMode && item.id in selected) {
                        if (selectionMode) {
                            if (item.id in selected) selected.remove(item.id) else selected.add(item.id)
                        } else {
                            navigator.push(Screen.Detail(item.id))
                        }
                    }
                }
            }
        }
    }
        VScrollbar(gridState)
    }

    if (bulkDialog != null) {
        val kind = bulkDialog
        AlertDialog(
            onDismissRequest = { bulkDialog = null },
            title = {
                Text(
                    when (kind) {
                        "tag" -> "Tag ${selected.size} items"
                        "location" -> "Set location for ${selected.size} items"
                        else -> "Add ${selected.size} items to series"
                    },
                )
            },
            text = {
                OutlinedTextField(
                    value = bulkText,
                    onValueChange = { bulkText = it },
                    singleLine = true,
                    label = {
                        Text(
                            when (kind) {
                                "tag" -> "Tag"
                                "location" -> "Location (shelf / box)"
                                else -> "Series name"
                            },
                        )
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val t = bulkText.trim()
                    if (t.isNotBlank()) {
                        selected.toList().forEach { id ->
                            when (kind) {
                                "tag" -> container.repository.addTag(id, t)
                                "location" -> container.repository.getItem(id)?.let {
                                    container.repository.upsertItem(it.copy(location = t, updatedAt = nowMillis()))
                                }
                                else -> container.repository.setExtra(id, SERIES_KEY, t)
                            }
                        }
                    }
                    bulkDialog = null
                    exitSelection()
                }) { Text("Apply") }
            },
            dismissButton = { TextButton(onClick = { bulkDialog = null }) { Text("Cancel") } },
        )
    }

    if (statusDialog) {
        val typeName = selected.firstOrNull()?.let { container.repository.getItem(it)?.mediaType }
            ?: (active as? ActiveTab.Builtin)?.type?.name
        val options = typeName?.let { Status.optionsFor(it) }.orEmpty()
        AlertDialog(
            onDismissRequest = { statusDialog = false },
            title = { Text("Set status for ${selected.size} items") },
            text = {
                Column {
                    (options + "Clear status").forEach { opt ->
                        Text(
                            opt,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.fillMaxWidth().clickable {
                                val value = if (opt == "Clear status") null else opt
                                selected.toList().forEach { id -> container.repository.updateStatus(id, value) }
                                statusDialog = false
                                exitSelection()
                            }.padding(vertical = 12.dp),
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { statusDialog = false }) { Text("Cancel") } },
        )
    }

    if (saveViewDialog) {
        AlertDialog(
            onDismissRequest = { saveViewDialog = false },
            title = { Text("Save view") },
            text = {
                OutlinedTextField(
                    value = viewName,
                    onValueChange = { viewName = it },
                    singleLine = true,
                    label = { Text("View name") },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val n = viewName.trim()
                    if (n.isNotBlank()) {
                        viewsStore.save(
                            SmartView(n, sort.name, favoritesOnly, onLoanOnly, unfinishedOnly, wishlistOnly, genre),
                        )
                        views = viewsStore.list()
                    }
                    saveViewDialog = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { saveViewDialog = false }) { Text("Cancel") } },
        )
    }

    if (manageViewsDialog) {
        AlertDialog(
            onDismissRequest = { manageViewsDialog = false },
            title = { Text("Manage views") },
            text = {
                Column {
                    views.forEach { view ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(view.name, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
                            TextButton(onClick = {
                                viewsStore.delete(view.name)
                                views = viewsStore.list()
                                if (views.isEmpty()) manageViewsDialog = false
                            }) { Text("Delete") }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { manageViewsDialog = false }) { Text("Close") } },
        )
    }
}

@Composable
private fun Controls(
    sort: SortOrder,
    onSort: (SortOrder) -> Unit,
    favoritesOnly: Boolean,
    onFavorites: () -> Unit,
    onLoanOnly: Boolean,
    onLoan: () -> Unit,
    unfinishedOnly: Boolean,
    onUnfinished: () -> Unit,
    wishlistOnly: Boolean,
    onWishlist: () -> Unit,
    statuses: List<String>,
    statusFilter: String?,
    onStatus: (String?) -> Unit,
    genres: List<String>,
    genre: String?,
    onGenre: (String?) -> Unit,
    selectionMode: Boolean,
    onToggleSelect: () -> Unit,
    coverDp: Int,
    onCycleCover: () -> Unit,
    onRandom: () -> Unit,
    views: List<SmartView>,
    onApplyView: (SmartView) -> Unit,
    onSaveView: () -> Unit,
    onManageViews: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(if (selectionMode) "Cancel select" else "Select", selectionMode, onToggleSelect)
        FilterChip("🎲 Surprise", selected = false, onClick = onRandom)
        val coverLabel = when (coverDp) { in 0..120 -> "S"; in 121..170 -> "M"; else -> "L" }
        FilterChip("Covers: $coverLabel", selected = false, onClick = onCycleCover)
        MenuChip("Sort: ${sort.label}") { dismiss ->
            SortOrder.entries.forEach { option ->
                DropdownMenuItem(text = { Text(option.label) }, onClick = { onSort(option); dismiss() })
            }
        }
        FilterChip("Favorites", favoritesOnly, onFavorites)
        FilterChip("On loan", onLoanOnly, onLoan)
        FilterChip("Unfinished", unfinishedOnly, onUnfinished)
        FilterChip("Wishlist", wishlistOnly, onWishlist)
        if (statuses.isNotEmpty()) {
            MenuChip(statusFilter?.let { "Status: $it" } ?: "Status") { dismiss ->
                DropdownMenuItem(text = { Text("All statuses") }, onClick = { onStatus(null); dismiss() })
                statuses.forEach { s ->
                    DropdownMenuItem(text = { Text(s) }, onClick = { onStatus(s); dismiss() })
                }
            }
        }
        if (genres.isNotEmpty()) {
            MenuChip(genre?.let { "Genre: $it" } ?: "Genre") { dismiss ->
                DropdownMenuItem(text = { Text("All genres") }, onClick = { onGenre(null); dismiss() })
                genres.forEach { g ->
                    DropdownMenuItem(text = { Text(g) }, onClick = { onGenre(g); dismiss() })
                }
            }
        }
        MenuChip("Views") { dismiss ->
            DropdownMenuItem(text = { Text("Save current view…") }, onClick = { onSaveView(); dismiss() })
            if (views.isNotEmpty()) {
                DropdownMenuItem(text = { Text("Manage views…") }, onClick = { onManageViews(); dismiss() })
            }
            views.forEach { view ->
                DropdownMenuItem(text = { Text(view.name) }, onClick = { onApplyView(view); dismiss() })
            }
        }
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(20.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            label,
            color = if (selected) Color.Black else Color.White,
            fontSize = 13.sp,
            lineHeight = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
private fun MenuChip(label: String, menuContent: @Composable (dismiss: () -> Unit) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        FilterChip(label, selected = false) { expanded = true }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            menuContent { expanded = false }
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
        // The Box stays within the grid's 20.dp inset; only the image/gradient
        // layers bleed to the screen edges, so the title/buttons (at the Box's
        // left edge) line up with the thumbnail rows below.
        Modifier
            .fillMaxWidth()
            .height(360.dp),
    ) {
        if (featured?.coverImage != null) {
            CoverImage(featured.coverImage, featured.title, Modifier.edgeToEdgeHorizontal(20.dp).fillMaxSize())
        } else {
            Box(
                Modifier.edgeToEdgeHorizontal(20.dp).fillMaxSize().background(
                    Brush.linearGradient(
                        listOf(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.background),
                    ),
                ),
            )
        }
        Box(
            Modifier.edgeToEdgeHorizontal(20.dp).fillMaxSize().background(
                Brush.verticalGradient(
                    0.35f to Color.Transparent,
                    1f to MaterialTheme.colorScheme.background,
                ),
            ),
        )
        Column(Modifier.align(Alignment.BottomStart).padding(end = 24.dp, top = 24.dp, bottom = 24.dp)) {
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
private fun Shelf(title: String, items: List<CollectionItem>, onItemClick: (String) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
        )
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items.forEach { item -> ShelfCard(item) { onItemClick(item.id) } }
        }
    }
}

@Composable
private fun ShelfCard(item: CollectionItem, onClick: () -> Unit) {
    Column(Modifier.width(120.dp).clickable(onClick = onClick)) {
        CoverImage(
            url = item.coverImage,
            contentDescription = item.title,
            modifier = Modifier.width(120.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(8.dp)),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = item.title,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ItemCard(item: CollectionItem, selected: Boolean = false, onClick: () -> Unit) {
    Column(Modifier.clickable(onClick = onClick)) {
        Box {
            CoverImage(
                url = item.coverImage,
                contentDescription = item.title,
                modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(8.dp)),
            )
            if (selected) {
                Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)))
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = "Selected",
                    tint = Color.White,
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(26.dp),
                )
            }
        }
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
