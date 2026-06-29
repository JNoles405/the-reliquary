package com.reliquary.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.SyncAlt
import com.reliquary.app.di.AppContainer
import com.reliquary.app.domain.MediaType
import com.reliquary.app.metadata.ApiKeys
import com.reliquary.app.ui.home.HOME_HIDDEN_RAILS_SETTING
import com.reliquary.app.ui.home.HOME_RAILS
import com.reliquary.app.ui.Navigator
import com.reliquary.app.ui.Screen
import com.reliquary.app.ui.components.PillButton
import com.reliquary.app.ui.components.VScrollColumn
import androidx.compose.material3.Switch
import com.reliquary.app.sync.defaultSyncFilePath
import com.reliquary.app.sync.writeTextFile
import com.reliquary.app.tools.CatalogExporter
import com.reliquary.app.tools.LabelExporter
import com.reliquary.app.tools.ShareExporter
import com.reliquary.app.tools.ValueReportExporter
import com.reliquary.app.update.UpdateStatus
import com.reliquary.app.update.downloadAndInstallUpdate
import com.reliquary.app.update.isAutoUpdateSupported
import com.reliquary.app.util.AppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.reliquary.app.util.WINDOW_MODE_SETTING
import com.reliquary.app.util.isDesktopPlatform
import com.reliquary.app.util.openUrl
import com.reliquary.app.util.setFullscreen
import kotlinx.coroutines.launch
import com.reliquary.app.ui.theme.ACCENTS
import com.reliquary.app.ui.theme.toRgbHex
import com.reliquary.app.ui.theme.ReliquaryMuted
import com.reliquary.app.ui.theme.ReliquarySurface
import com.reliquary.app.ui.theme.ReliquarySurfaceVariant

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(container: AppContainer, navigator: Navigator, onAccentChange: (String) -> Unit = {}) {
    val keys = container.apiKeyStore
    val scope = rememberCoroutineScope()

    VScrollColumn(
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            "Settings",
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
        )
        Text(
            "Add free API keys to unlock automatic lookups for Movies, Games, and " +
                "Comics. Books and Music already work without keys. Keys are stored " +
                "locally on this device and are never uploaded or committed.",
            color = ReliquaryMuted,
            fontSize = 13.sp,
        )

        if (isDesktopPlatform()) {
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp)) {
                Column {
                    var fullscreen by remember {
                        mutableStateOf(container.repository.getSetting(WINDOW_MODE_SETTING) != "windowed")
                    }
                    Text("Window", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Switch(
                            checked = fullscreen,
                            onCheckedChange = {
                                fullscreen = it
                                container.repository.setSetting(WINDOW_MODE_SETTING, if (it) "maximized" else "windowed")
                                setFullscreen(it)
                            },
                        )
                        Text("  Open maximized", color = MaterialTheme.colorScheme.onBackground)
                    }
                    Text(
                        "On fills the screen above the taskbar; off opens in a window at the default size. Applies now.",
                        color = ReliquaryMuted,
                        fontSize = 12.sp,
                    )
                }
            }
        }

        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                var startHome by remember { mutableStateOf(container.repository.getSetting("ui.startOnHome") != "false") }
                Switch(
                    checked = startHome,
                    onCheckedChange = {
                        startHome = it
                        container.repository.setSetting("ui.startOnHome", if (it) "true" else "false")
                    },
                )
                Text("  Open to the Home dashboard on launch", color = MaterialTheme.colorScheme.onBackground)
            }
        }

        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp)) {
            Column {
                Text("Home rails", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Spacer(Modifier.height(4.dp))
                Text("Choose which rows show on the Home dashboard.", color = ReliquaryMuted, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                var hidden by remember {
                    mutableStateOf(
                        container.repository.getSetting(HOME_HIDDEN_RAILS_SETTING)?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet() ?: emptySet(),
                    )
                }
                HOME_RAILS.forEach { rail ->
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Switch(
                            checked = rail !in hidden,
                            onCheckedChange = { on ->
                                hidden = if (on) hidden - rail else hidden + rail
                                container.repository.setSetting(HOME_HIDDEN_RAILS_SETTING, hidden.joinToString(",").ifBlank { null })
                            },
                        )
                        Text("  $rail", color = MaterialTheme.colorScheme.onBackground, fontSize = 14.sp)
                    }
                }
            }
        }

        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp)) {
            Column {
                Text("Appearance", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Spacer(Modifier.height(6.dp))
                var light by remember { mutableStateOf(!com.reliquary.app.UiPrefs.dark) }
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Switch(
                        checked = light,
                        onCheckedChange = {
                            light = it
                            com.reliquary.app.UiPrefs.dark = !it
                            container.repository.setSetting(com.reliquary.app.THEME_SETTING, if (it) "light" else "dark")
                        },
                    )
                    Text("  Light theme", color = MaterialTheme.colorScheme.onBackground)
                }
                Spacer(Modifier.height(10.dp))
                Text("Text size", color = ReliquaryMuted, fontSize = 13.sp)
                Spacer(Modifier.height(6.dp))
                var scale by remember { mutableStateOf(com.reliquary.app.UiPrefs.textScale) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Small" to 0.9f, "Normal" to 1.0f, "Large" to 1.15f, "Huge" to 1.3f).forEach { (label, value) ->
                        val sel = kotlin.math.abs(scale - value) < 0.01f
                        Box(
                            Modifier.clip(RoundedCornerShape(20.dp))
                                .background(if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable {
                                    scale = value
                                    com.reliquary.app.UiPrefs.textScale = value
                                    container.repository.setSetting(com.reliquary.app.TEXT_SCALE_SETTING, value.toString())
                                }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                        ) {
                            Text(label, color = if (sel) Color.Black else MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
                        }
                    }
                }
            }
        }

        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp)) {
            Column {
                Text("Accent color", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ACCENTS.forEach { option ->
                        Box(
                            Modifier.size(34.dp).clip(CircleShape).background(option.color)
                                .clickable { onAccentChange(option.color.toRgbHex()) },
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                var hex by remember { mutableStateOf("") }
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = hex,
                        onValueChange = { hex = it.removePrefix("#").filter { c -> c.isLetterOrDigit() }.take(6).uppercase() },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        label = { Text("Custom hex (e.g. 14B8A6)") },
                    )
                    PillButton("Apply", null, MaterialTheme.colorScheme.primary, Color.Black) {
                        if (hex.matches(Regex("[0-9A-Fa-f]{6}"))) onAccentChange(hex.uppercase())
                    }
                }
            }
        }

        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp)) {
            Column {
                Text("Open to tab", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Spacer(Modifier.height(10.dp))
                var defaultTab by remember { mutableStateOf(container.repository.getSetting("ui.defaultTab") ?: MediaType.MOVIES.name) }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MediaType.entries.forEach { type ->
                        val selected = defaultTab == type.name
                        Box(
                            Modifier.clip(RoundedCornerShape(20.dp))
                                .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable {
                                    defaultTab = type.name
                                    container.repository.setSetting("ui.defaultTab", type.name)
                                }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                        ) {
                            Text(type.displayName, color = if (selected) Color.Black else MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
                        }
                    }
                }
            }
        }

        var catalogMsg by remember { mutableStateOf<String?>(null) }
        var reportMsg by remember { mutableStateOf<String?>(null) }
        var shareMsg by remember { mutableStateOf<String?>(null) }
        var labelMsg by remember { mutableStateOf<String?>(null) }
        Text("Tools & data", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ToolButton("Quick add") { navigator.push(Screen.QuickAdd) }
            ToolButton("Sync to/from file") { navigator.push(Screen.Sync) }
            ToolButton("Import / export CSV") { navigator.push(Screen.Csv) }
            ToolButton("Media servers") { navigator.push(Screen.Servers) }
            ToolButton("Backups") { navigator.push(Screen.Backups) }
            ToolButton("Trash") { navigator.push(Screen.Trash) }
            ToolButton("Year in Review") { navigator.push(Screen.YearInReview) }
            ToolButton("Find duplicates") { navigator.push(Screen.Duplicates) }
            ToolButton("Export HTML catalog") {
                scope.launch {
                    val path = defaultSyncFilePath().replace("reliquary-sync.json", "reliquary-catalog.html")
                    runCatching {
                        val html = withContext(Dispatchers.Default) { CatalogExporter.buildHtml(container.repository.allItems()) }
                        withContext(Dispatchers.Default) { writeTextFile(path, html) }
                    }.onSuccess {
                        catalogMsg = "Catalog saved to:\n$path"
                        if (isDesktopPlatform()) openUrl("file:///" + path.replace("\\", "/"))
                    }.onFailure { catalogMsg = "Export failed: ${it.message}" }
                }
            }
            ToolButton("Export value report") {
                scope.launch {
                    val path = defaultSyncFilePath().replace("reliquary-sync.json", "reliquary-value-report.html")
                    runCatching {
                        val html = withContext(Dispatchers.Default) { ValueReportExporter.buildHtml(container.repository.allItems()) }
                        withContext(Dispatchers.Default) { writeTextFile(path, html) }
                    }.onSuccess {
                        reportMsg = "Value report saved to:\n$path"
                        if (isDesktopPlatform()) openUrl("file:///" + path.replace("\\", "/"))
                    }.onFailure { reportMsg = "Export failed: ${it.message}" }
                }
            }
            ToolButton("Share page (wishlist)") {
                scope.launch {
                    val path = defaultSyncFilePath().replace("reliquary-sync.json", "reliquary-share.html")
                    runCatching {
                        val owner = container.repository.getSetting("ui.ownerName")
                        val html = withContext(Dispatchers.Default) { ShareExporter.buildHtml(container.repository.allItems(), owner) }
                        withContext(Dispatchers.Default) { writeTextFile(path, html) }
                    }.onSuccess {
                        shareMsg = "Share page saved to:\n$path"
                        if (isDesktopPlatform()) openUrl("file:///" + path.replace("\\", "/"))
                    }.onFailure { shareMsg = "Export failed: ${it.message}" }
                }
            }
            ToolButton("Printable labels") {
                scope.launch {
                    val path = defaultSyncFilePath().replace("reliquary-sync.json", "reliquary-labels.html")
                    runCatching {
                        val html = withContext(Dispatchers.Default) { LabelExporter.buildHtml(container.repository.allItems()) }
                        withContext(Dispatchers.Default) { writeTextFile(path, html) }
                    }.onSuccess {
                        labelMsg = "Labels saved to:\n$path"
                        if (isDesktopPlatform()) openUrl("file:///" + path.replace("\\", "/"))
                    }.onFailure { labelMsg = "Export failed: ${it.message}" }
                }
            }
        }
        catalogMsg?.let { Text(it, color = ReliquaryMuted, fontSize = 12.sp) }
        reportMsg?.let { Text(it, color = ReliquaryMuted, fontSize = 12.sp) }
        shareMsg?.let { Text(it, color = ReliquaryMuted, fontSize = 12.sp) }
        labelMsg?.let { Text(it, color = ReliquaryMuted, fontSize = 12.sp) }

        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp)) {
            Column {
                Text("Collection goal", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Spacer(Modifier.height(4.dp))
                Text("How many items you're aiming to finish. Progress shows in Stats.", color = ReliquaryMuted, fontSize = 12.sp)
                Spacer(Modifier.height(10.dp))
                var goal by remember { mutableStateOf(container.repository.getSetting("stats.completionGoal") ?: "") }
                OutlinedTextField(
                    value = goal,
                    onValueChange = { v ->
                        goal = v.filter { it.isDigit() }
                        container.repository.setSetting("stats.completionGoal", goal.ifBlank { null })
                    },
                    singleLine = true,
                    label = { Text("Finish goal (number)") },
                )
                Spacer(Modifier.height(14.dp))
                Text("Your name", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Spacer(Modifier.height(4.dp))
                Text("Shown as the heading on the exported share page.", color = ReliquaryMuted, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                var ownerName by remember { mutableStateOf(container.repository.getSetting("ui.ownerName") ?: "") }
                OutlinedTextField(
                    value = ownerName,
                    onValueChange = { v ->
                        ownerName = v
                        container.repository.setSetting("ui.ownerName", v.trim().ifBlank { null })
                    },
                    singleLine = true,
                    label = { Text("Name on share page") },
                )
            }
        }

        KeySection(
            title = "TMDB — Movies",
            help = "Create a free account at themoviedb.org, then Settings → API → API Key (v3 auth).",
            fields = listOf(KeyField("API Key", ApiKeys.TMDB)),
            keysActive = keys.has(ApiKeys.TMDB),
            onSave = { values -> values.forEach { (k, v) -> keys.set(k, v) } },
            initialValue = { keys.get(it) },
        )

        KeySection(
            title = "OMDb — Movies (alternative)",
            help = "Free key by email at omdbapi.com/apikey.aspx. An easy alternative or " +
                "supplement to TMDB for movie lookups.",
            fields = listOf(KeyField("API Key", ApiKeys.OMDB)),
            keysActive = keys.has(ApiKeys.OMDB),
            onSave = { values -> values.forEach { (k, v) -> keys.set(k, v) } },
            initialValue = { keys.get(it) },
        )

        KeySection(
            title = "IGDB — Games",
            help = "Register an app at dev.twitch.tv/console/apps to get a Client ID and Client Secret.",
            fields = listOf(
                KeyField("Twitch Client ID", ApiKeys.IGDB_CLIENT_ID),
                KeyField("Twitch Client Secret", ApiKeys.IGDB_CLIENT_SECRET, secret = true),
            ),
            keysActive = keys.has(ApiKeys.IGDB_CLIENT_ID) && keys.has(ApiKeys.IGDB_CLIENT_SECRET),
            onSave = { values -> values.forEach { (k, v) -> keys.set(k, v) } },
            initialValue = { keys.get(it) },
        )

        KeySection(
            title = "Simkl — TV Shows & Anime",
            help = "Create an app at simkl.com/settings/developer to get a Client ID. " +
                "Powers the TV Shows and Anime tabs.",
            fields = listOf(KeyField("Client ID", ApiKeys.SIMKL)),
            keysActive = keys.has(ApiKeys.SIMKL),
            onSave = { values -> values.forEach { (k, v) -> keys.set(k, v) } },
            initialValue = { keys.get(it) },
        )

        KeySection(
            title = "ComicVine — Comics",
            help = "Get a free API key at comicvine.gamespot.com/api.",
            fields = listOf(KeyField("API Key", ApiKeys.COMICVINE)),
            keysActive = keys.has(ApiKeys.COMICVINE),
            onSave = { values -> values.forEach { (k, v) -> keys.set(k, v) } },
            initialValue = { keys.get(it) },
        )

        KeySection(
            title = "Discogs — Music (optional)",
            help = "Generate a personal access token at discogs.com → Settings → Developers. " +
                "Add your username too if you want to import your whole collection (Tools → Import / export CSV).",
            fields = listOf(
                KeyField("Personal Token", ApiKeys.DISCOGS, secret = true),
                KeyField("Username", ApiKeys.DISCOGS_USER),
            ),
            keysActive = keys.has(ApiKeys.DISCOGS),
            onSave = { values -> values.forEach { (k, v) -> keys.set(k, v) } },
            initialValue = { keys.get(it) },
        )

        KeySection(
            title = "Steam — Games (optional)",
            help = "Get a free Web API key at steamcommunity.com/dev/apikey, then add your " +
                "64-bit SteamID or profile name. Import your owned games from Tools → Import / export CSV.",
            fields = listOf(
                KeyField("Web API Key", ApiKeys.STEAM, secret = true),
                KeyField("SteamID or profile name", ApiKeys.STEAM_ID),
            ),
            keysActive = keys.has(ApiKeys.STEAM) && keys.has(ApiKeys.STEAM_ID),
            onSave = { values -> values.forEach { (k, v) -> keys.set(k, v) } },
            initialValue = { keys.get(it) },
        )

        UpdatesSection(container, scope)

        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp)) {
            Column {
                Text("About", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Spacer(Modifier.height(6.dp))
                Text("The Reliquary v${AppInfo.VERSION}", color = ReliquaryMuted, fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                PillButton(
                    label = "View releases on GitHub",
                    icon = null,
                    background = MaterialTheme.colorScheme.primary,
                    foreground = Color.Black,
                ) { openUrl(AppInfo.RELEASES_URL) }
            }
        }
    }
}

@Composable
private fun UpdatesSection(container: AppContainer, scope: kotlinx.coroutines.CoroutineScope) {
    var checking by remember { mutableStateOf(false) }
    var installing by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var status by remember { mutableStateOf<UpdateStatus?>(null) }

    fun check() {
        if (checking || installing) return
        status = null
        checking = true
        scope.launch {
            val result = container.updateService.checkForUpdate()
            checking = false
            status = result
            // "Automatically updates": if a newer build exists and we can self-install,
            // download it and launch the installer (the app then exits to upgrade).
            if (result is UpdateStatus.Available && isAutoUpdateSupported() && result.downloadUrl != null) {
                installing = true
                progress = 0f
                val error = downloadAndInstallUpdate(result.downloadUrl) { progress = it }
                if (error != null) { // only returns on failure
                    installing = false
                    status = UpdateStatus.Failed(error)
                }
            }
        }
    }

    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp)) {
        Column {
            Text("Updates", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                if (isAutoUpdateSupported()) {
                    "Checks GitHub for a newer release, then downloads and installs it. " +
                        "The app will close while it updates."
                } else {
                    "Checks GitHub for a newer release and opens the download page."
                },
                color = ReliquaryMuted,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(10.dp))

            when {
                installing -> {
                    Text("Downloading update… ${(progress * 100).toInt()}%", color = MaterialTheme.colorScheme.onBackground, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                checking -> Text("Checking for updates…", color = ReliquaryMuted, fontSize = 13.sp)
                else -> {
                    PillButton(
                        label = "Check for updates",
                        icon = Icons.Filled.Refresh,
                        background = MaterialTheme.colorScheme.primary,
                        foreground = Color.Black,
                    ) { check() }

                    when (val s = status) {
                        is UpdateStatus.UpToDate -> {
                            Spacer(Modifier.height(10.dp))
                            Text("You're on the latest version (v${AppInfo.VERSION}).", color = ReliquaryMuted, fontSize = 13.sp)
                        }
                        is UpdateStatus.Available -> {
                            Spacer(Modifier.height(10.dp))
                            Text("Update available: v${s.version}.", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            if (!isAutoUpdateSupported() || s.downloadUrl == null) {
                                Spacer(Modifier.height(8.dp))
                                PillButton(
                                    label = "Open download page",
                                    icon = null,
                                    background = MaterialTheme.colorScheme.surfaceVariant,
                                    foreground = MaterialTheme.colorScheme.onBackground,
                                ) { openUrl(s.pageUrl) }
                            }
                        }
                        is UpdateStatus.Failed -> {
                            Spacer(Modifier.height(10.dp))
                            Text("Couldn't check: ${s.message}", color = ReliquaryMuted, fontSize = 13.sp)
                        }
                        null -> {}
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolButton(label: String, onClick: () -> Unit) {
    PillButton(
        label = label,
        icon = Icons.Filled.SyncAlt,
        background = MaterialTheme.colorScheme.surface,
        foreground = MaterialTheme.colorScheme.onBackground,
        onClick = onClick,
    )
}

private data class KeyField(val label: String, val settingKey: String, val secret: Boolean = false)

@Composable
private fun KeySection(
    title: String,
    help: String,
    fields: List<KeyField>,
    keysActive: Boolean,
    onSave: (Map<String, String>) -> Unit,
    initialValue: (String) -> String? = { null },
) {
    var active by remember { mutableStateOf(keysActive) }
    val values = remember {
        fields.associate { it.settingKey to mutableStateOf(initialValue(it.settingKey) ?: "") }
    }

    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp)) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Spacer(Modifier.height(0.dp))
                Text(
                    text = if (active) "   ● Active" else "   ○ Not set",
                    color = if (active) MaterialTheme.colorScheme.primary else ReliquaryMuted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(help, color = ReliquaryMuted, fontSize = 12.sp)
            Spacer(Modifier.height(10.dp))
            fields.forEach { field ->
                val state = values.getValue(field.settingKey)
                OutlinedTextField(
                    value = state.value,
                    onValueChange = { state.value = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(field.label) },
                )
                Spacer(Modifier.height(8.dp))
            }
            PillButton(
                label = "Save",
                icon = Icons.Filled.Check,
                background = MaterialTheme.colorScheme.primary,
                foreground = Color.White,
            ) {
                onSave(values.mapValues { it.value.value.trim() })
                active = values.values.all { it.value.isNotBlank() }
            }
        }
    }
}
