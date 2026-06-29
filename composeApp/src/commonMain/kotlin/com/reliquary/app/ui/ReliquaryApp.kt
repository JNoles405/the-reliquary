package com.reliquary.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reliquary.app.data.nowMillis
import com.reliquary.app.di.AppContainer
import com.reliquary.app.domain.MediaType
import com.reliquary.app.ui.detail.DetailScreen
import com.reliquary.app.ui.discover.DiscoverScreen
import com.reliquary.app.ui.edit.EditItemScreen
import com.reliquary.app.ui.imports.SearchImportScreen
import com.reliquary.app.ui.library.LibraryScreen
import com.reliquary.app.ui.loans.LoanScreen
import com.reliquary.app.ui.loans.LoansScreen
import com.reliquary.app.ui.people.PeopleScreen
import com.reliquary.app.ui.people.PersonScreen
import com.reliquary.app.ui.csv.CsvScreen
import com.reliquary.app.ui.search.SearchScreen
import com.reliquary.app.ui.settings.CustomTabsScreen
import com.reliquary.app.ui.settings.SettingsScreen
import com.reliquary.app.ui.stats.StatsScreen
import com.reliquary.app.ui.sync.SyncScreen
import com.reliquary.app.ui.series.SeriesItemsScreen
import com.reliquary.app.ui.series.SeriesScreen
import com.reliquary.app.ui.tags.TagItemsScreen
import com.reliquary.app.ui.tags.TagsScreen
import com.reliquary.app.ui.theme.ReliquaryMuted

@Composable
fun ReliquaryApp(container: AppContainer, onAccentChange: (String) -> Unit = {}) {
    val navigator = rememberNavigator()
    var activeTab by remember {
        val defaultType = container.repository.getSetting("ui.defaultTab")
            ?.let { name -> MediaType.entries.firstOrNull { it.name == name } } ?: MediaType.MOVIES
        mutableStateOf<ActiveTab>(ActiveTab.Builtin(defaultType))
    }
    val customTabs by remember { container.repository.customTabs() }.collectAsState(emptyList())
    val activeLoans by remember { container.repository.activeLoans() }.collectAsState(emptyList())
    val overdueCount = activeLoans.count { it.dueAt != null && it.dueAt < nowMillis() }

    Box(Modifier.fillMaxSize()) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopNav(
                active = activeTab,
                navigator = navigator,
                customTabNames = customTabs,
                onSelectBuiltin = {
                    activeTab = ActiveTab.Builtin(it)
                    navigator.resetTo(Screen.Library)
                },
                onSelectCustom = { tab ->
                    activeTab = ActiveTab.Custom(tab)
                    navigator.resetTo(Screen.Library)
                },
                onManageTabs = { navigator.push(Screen.CustomTabs) },
                onSettings = { navigator.push(Screen.Settings) },
                onSurprise = { container.repository.surprisePick()?.let { navigator.push(Screen.Detail(it.id)) } },
                overdueCount = overdueCount,
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val screen = navigator.current) {
                Screen.Library -> LibraryScreen(container, activeTab, navigator)
                is Screen.Detail -> DetailScreen(container, screen.itemId, navigator)
                is Screen.SearchImport ->
                    SearchImportScreen(container, screen.mediaType, screen.customTabId, navigator)
                is Screen.EditItem ->
                    EditItemScreen(container, screen.itemId, screen.mediaTypeName, screen.customTabId, navigator)
                is Screen.LoanItem -> LoanScreen(container, screen.itemId, navigator)
                Screen.Loans -> LoansScreen(container, navigator)
                Screen.CustomTabs -> CustomTabsScreen(container, navigator)
                Screen.Sync -> SyncScreen(container, navigator)
                Screen.Search -> SearchScreen(container, navigator)
                Screen.Csv -> CsvScreen(container, navigator)
                Screen.Stats -> StatsScreen(container, navigator)
                Screen.Discover -> DiscoverScreen(container, navigator)
                Screen.People -> PeopleScreen(container, navigator)
                is Screen.Person -> PersonScreen(container, screen.personId, navigator)
                Screen.Tags -> TagsScreen(container, navigator)
                is Screen.TagItems -> TagItemsScreen(container, screen.tag, navigator)
                Screen.Series -> SeriesScreen(container, navigator)
                is Screen.SeriesItems -> SeriesItemsScreen(container, screen.series, navigator)
                Screen.Settings -> SettingsScreen(container, navigator, onAccentChange)
                Screen.Servers -> com.reliquary.app.ui.servers.ServersScreen(container, navigator)
                Screen.Duplicates -> com.reliquary.app.ui.tools.DuplicatesScreen(container, navigator)
                Screen.Backups -> com.reliquary.app.ui.tools.BackupScreen(container, navigator)
                Screen.QuickAdd -> com.reliquary.app.ui.tools.QuickAddScreen(container, navigator)
            }
        }
    }
        CommandPaletteOverlay(
            container = container,
            navigator = navigator,
            onSelectBuiltin = {
                activeTab = ActiveTab.Builtin(it)
                navigator.resetTo(Screen.Library)
            },
        )
    }
}

@Composable
private fun TopNav(
    active: ActiveTab,
    navigator: Navigator,
    customTabNames: List<com.reliquary.app.domain.CustomTab>,
    onSelectBuiltin: (MediaType) -> Unit,
    onSelectCustom: (com.reliquary.app.domain.CustomTab) -> Unit,
    onManageTabs: () -> Unit,
    onSettings: () -> Unit,
    onSurprise: () -> Unit,
    overdueCount: Int,
) {
    val onLibrary = navigator.current == Screen.Library
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (navigator.canGoBack) {
            IconButton(onClick = { navigator.pop() }) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
            Spacer(Modifier.width(4.dp))
        }
        Text(
            text = "THE RELIQUARY",
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 19.sp,
            modifier = Modifier.clickable { navigator.resetTo(Screen.Library) },
        )
        Spacer(Modifier.width(20.dp))
        // Library categories (built-in media types + custom tabs) scroll here.
        Row(
            Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MediaType.entries.forEach { type ->
                val selected = onLibrary && active is ActiveTab.Builtin && active.type == type
                TabLabel(type.displayName, selected) { onSelectBuiltin(type) }
            }
            customTabNames.forEach { tab ->
                val selected = onLibrary && active is ActiveTab.Custom && active.tab.id == tab.id
                TabLabel(tab.name, selected) { onSelectCustom(tab) }
            }
            IconButton(onClick = onManageTabs) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = "Manage custom tabs",
                    tint = ReliquaryMuted,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        // Tools / views — grouped in a bordered cluster, distinct from the library tabs.
        val loansLabel = if (overdueCount > 0) "Loans ($overdueCount)" else "Loans"
        Row(
            Modifier
                .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(22.dp))
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TabLabel("Discover", navigator.current == Screen.Discover) { navigator.resetTo(Screen.Discover) }
            TabLabel("Tags", navigator.current == Screen.Tags) { navigator.resetTo(Screen.Tags) }
            TabLabel("Series", navigator.current == Screen.Series) { navigator.resetTo(Screen.Series) }
            TabLabel(loansLabel, navigator.current == Screen.Loans) { navigator.resetTo(Screen.Loans) }
            TabLabel("Stats", navigator.current == Screen.Stats) { navigator.resetTo(Screen.Stats) }
        }
        Spacer(Modifier.width(8.dp))
        IconButton(onClick = onSurprise) {
            Icon(
                Icons.Filled.Shuffle,
                contentDescription = "Surprise me",
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(22.dp),
            )
        }
        IconButton(onClick = { navigator.push(Screen.Search) }) {
            Icon(
                Icons.Filled.Search,
                contentDescription = "Search",
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(22.dp),
            )
        }
        IconButton(onClick = onSettings) {
            Icon(
                Icons.Filled.Settings,
                contentDescription = "Settings",
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(22.dp),
            )
        }
    }
        Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.surfaceVariant))
    }
}

@Composable
private fun TabLabel(text: String, selected: Boolean, onClick: () -> Unit) {
    // Constant font weight so a selected (bold-feeling) tab never changes width and
    // shifts its neighbors; selection is shown by a rounded tint + accent color, and
    // the rounded clip keeps the hover highlight inside the rounded outline.
    Box(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (selected) MaterialTheme.colorScheme.primary else ReliquaryMuted,
            fontWeight = FontWeight.Medium,
            fontSize = 15.sp,
            lineHeight = 15.sp,
        )
    }
}
