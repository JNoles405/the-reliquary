package com.reliquary.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.material.icons.filled.SyncAlt
import com.reliquary.app.di.AppContainer
import com.reliquary.app.metadata.ApiKeys
import com.reliquary.app.ui.Navigator
import com.reliquary.app.ui.Screen
import com.reliquary.app.ui.components.PillButton
import com.reliquary.app.util.AppInfo
import com.reliquary.app.util.openUrl
import com.reliquary.app.ui.theme.ReliquaryMuted
import com.reliquary.app.ui.theme.ReliquaryTeal
import com.reliquary.app.ui.theme.ReliquarySurface

@Composable
fun SettingsScreen(container: AppContainer, navigator: Navigator) {
    val keys = container.apiKeyStore

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
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

        PillButton(
            label = "Sync library to/from a file",
            icon = Icons.Filled.SyncAlt,
            background = ReliquarySurface,
            foreground = MaterialTheme.colorScheme.onBackground,
        ) { navigator.push(Screen.Sync) }

        PillButton(
            label = "Import / export CSV",
            icon = Icons.Filled.SyncAlt,
            background = ReliquarySurface,
            foreground = MaterialTheme.colorScheme.onBackground,
        ) { navigator.push(Screen.Csv) }

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
            title = "ComicVine — Comics",
            help = "Get a free API key at comicvine.gamespot.com/api.",
            fields = listOf(KeyField("API Key", ApiKeys.COMICVINE)),
            keysActive = keys.has(ApiKeys.COMICVINE),
            onSave = { values -> values.forEach { (k, v) -> keys.set(k, v) } },
            initialValue = { keys.get(it) },
        )

        KeySection(
            title = "Discogs — Music (optional)",
            help = "Generate a personal access token at discogs.com → Settings → Developers.",
            fields = listOf(KeyField("Personal Token", ApiKeys.DISCOGS, secret = true)),
            keysActive = keys.has(ApiKeys.DISCOGS),
            onSave = { values -> values.forEach { (k, v) -> keys.set(k, v) } },
            initialValue = { keys.get(it) },
        )

        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(ReliquarySurface).padding(16.dp)) {
            Column {
                Text("About", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Spacer(Modifier.height(6.dp))
                Text("The Reliquary v${AppInfo.VERSION}", color = ReliquaryMuted, fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                PillButton(
                    label = "View releases on GitHub",
                    icon = null,
                    background = ReliquaryTeal,
                    foreground = Color.Black,
                ) { openUrl(AppInfo.RELEASES_URL) }
            }
        }
    }
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

    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(ReliquarySurface).padding(16.dp)) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Spacer(Modifier.height(0.dp))
                Text(
                    text = if (active) "   ● Active" else "   ○ Not set",
                    color = if (active) ReliquaryTeal else ReliquaryMuted,
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
                background = ReliquaryTeal,
                foreground = Color.White,
            ) {
                onSave(values.mapValues { it.value.value.trim() })
                active = values.values.all { it.value.isNotBlank() }
            }
        }
    }
}
