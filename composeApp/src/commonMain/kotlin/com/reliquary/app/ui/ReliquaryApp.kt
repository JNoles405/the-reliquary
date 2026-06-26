package com.reliquary.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reliquary.app.data.nowMillis
import com.reliquary.app.di.AppContainer
import com.reliquary.app.domain.MediaType
import com.reliquary.app.ui.detail.DetailScreen
import com.reliquary.app.ui.edit.EditItemScreen
import com.reliquary.app.ui.imports.SearchImportScreen
import com.reliquary.app.ui.library.LibraryScreen
import com.reliquary.app.ui.loans.LoanScreen
import com.reliquary.app.ui.loans.LoansScreen
import com.reliquary.app.ui.csv.CsvScreen
import com.reliquary.app.ui.search.SearchScreen
import com.reliquary.app.ui.settings.CustomTabsScreen
import com.reliquary.app.ui.settings.SettingsScreen
import com.reliquary.app.ui.sync.SyncScreen
import com.reliquary.app.ui.theme.ReliquaryMuted
import com.reliquary.app.ui.theme.ReliquaryTeal

@Composable
fun ReliquaryApp(container: AppContainer) {
    val navigator = rememberNavigator()
    var activeTab by remember { mutableStateOf<ActiveTab>(ActiveTab.Builtin(MediaType.MOVIES)) }
    val customTabs by remember { container.repository.customTabs() }.collectAsState(emptyList())
    val activeLoans by remember { container.repository.activeLoans() }.collectAsState(emptyList())
    val overdueCount = activeLoans.count { it.dueAt != null && it.dueAt < nowMillis() }

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
                Screen.Settings -> SettingsScreen(container, navigator)
            }
        }
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
    overdueCount: Int,
) {
    val onLibrary = navigator.current == Screen.Library
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
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
            color = ReliquaryTeal,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 19.sp,
            modifier = Modifier.clickable { navigator.resetTo(Screen.Library) },
        )
        Spacer(Modifier.width(20.dp))
        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
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
            val loansLabel = if (overdueCount > 0) "Loans ($overdueCount)" else "Loans"
            TabLabel(loansLabel, navigator.current == Screen.Loans) { navigator.resetTo(Screen.Loans) }
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
}

@Composable
private fun TabLabel(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = text,
        color = if (selected) MaterialTheme.colorScheme.onBackground else ReliquaryMuted,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        fontSize = 15.sp,
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 9.dp, vertical = 4.dp),
    )
}
