package com.reliquary.app.ui.csv

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reliquary.app.di.AppContainer
import com.reliquary.app.domain.MediaType
import com.reliquary.app.csv.CSV_TARGET_FIELDS
import com.reliquary.app.csv.autoMapColumns
import com.reliquary.app.csv.parseCsv
import com.reliquary.app.sync.defaultSyncFilePath
import com.reliquary.app.sync.readTextFile
import com.reliquary.app.sync.writeTextFile
import com.reliquary.app.ui.Navigator
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import com.reliquary.app.ui.components.PillButton
import com.reliquary.app.ui.components.VScrollColumn
import com.reliquary.app.util.openUrl
import com.reliquary.app.ui.theme.ReliquaryMuted
import com.reliquary.app.ui.theme.ReliquarySurface
import com.reliquary.app.ui.theme.ReliquarySurfaceVariant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun CsvScreen(container: AppContainer, navigator: Navigator) {
    val scope = rememberCoroutineScope()
    val defaultPath = remember { defaultSyncFilePath().replace("reliquary-sync.json", "reliquary.csv") }
    var path by remember { mutableStateOf(defaultPath) }
    var importType by remember { mutableStateOf(MediaType.MOVIES) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    // Column-mapping state (for CSVs whose headers we don't auto-recognize).
    var headers by remember { mutableStateOf<List<String>>(emptyList()) }
    var rawText by remember { mutableStateOf<String?>(null) }
    var mapping by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }

    VScrollColumn(
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("CSV Import / Export", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 24.sp)
        Text(
            "Export your library to a spreadsheet-friendly CSV, or import one (e.g. a " +
                "Collectorz/CLZ export). Import is lenient about column names — Title, " +
                "Year, Genre, Director/Author, Barcode, etc. are recognized automatically.",
            color = ReliquaryMuted,
            fontSize = 13.sp,
        )

        OutlinedTextField(
            value = path,
            onValueChange = { path = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("CSV file path") },
        )

        Text("Import rows without a category column as:", color = ReliquaryMuted, fontSize = 13.sp)
        TypeSelector(importType) { importType = it }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PillButton(label = "Export CSV", icon = null, background = MaterialTheme.colorScheme.primary, foreground = Color.Black) {
                if (busy) return@PillButton
                busy = true; status = null
                scope.launch {
                    status = runCatching {
                        val csv = withContext(Dispatchers.Default) { container.csvService.exportCsv() }
                        withContext(Dispatchers.Default) { writeTextFile(path, csv) }
                        "Exported to:\n$path"
                    }.getOrElse { "Export failed: ${it.message}" }
                    busy = false
                }
            }
            PillButton(label = "Import CSV", icon = null, background = MaterialTheme.colorScheme.surfaceVariant, foreground = MaterialTheme.colorScheme.onSurface) {
                if (busy) return@PillButton
                busy = true; status = null
                scope.launch {
                    status = runCatching {
                        val text = withContext(Dispatchers.Default) { readTextFile(path) }
                            ?: return@runCatching "No file found at:\n$path"
                        val count = withContext(Dispatchers.Default) { container.csvService.importCsv(text, importType) }
                        "Imported $count ${if (count == 1) "item" else "items"} as ${importType.displayName}."
                    }.getOrElse { "Import failed: ${it.message}" }
                    busy = false
                }
            }
        }

        PillButton(label = "Map columns manually…", icon = null, background = MaterialTheme.colorScheme.surface, foreground = MaterialTheme.colorScheme.onBackground) {
            if (busy) return@PillButton
            scope.launch {
                val text = withContext(Dispatchers.Default) { readTextFile(path) }
                if (text == null) { status = "No file found at:\n$path"; return@launch }
                val rows = withContext(Dispatchers.Default) { parseCsv(text) }
                if (rows.isEmpty()) { status = "The file looks empty."; return@launch }
                rawText = text
                headers = rows.first().map { it.trim() }
                mapping = autoMapColumns(headers)
                status = null
            }
        }

        if (headers.isNotEmpty()) {
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Map columns", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("Pick which CSV column feeds each field. Auto-detected guesses are filled in.", color = ReliquaryMuted, fontSize = 12.sp)
                    CSV_TARGET_FIELDS.forEach { (field, _) ->
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Text(field, color = MaterialTheme.colorScheme.onBackground, fontSize = 13.sp, modifier = Modifier.width(130.dp))
                            ColumnPicker(headers, mapping[field]) { idx ->
                                mapping = if (idx == null) mapping - field else mapping + (field to idx)
                            }
                        }
                    }
                    PillButton(label = "Import with this mapping", icon = null, background = MaterialTheme.colorScheme.primary, foreground = Color.Black) {
                        if (busy) return@PillButton
                        val text = rawText ?: return@PillButton
                        if (mapping["Title"] == null) { status = "Map the Title column first."; return@PillButton }
                        busy = true
                        scope.launch {
                            status = runCatching {
                                val n = withContext(Dispatchers.Default) { container.csvService.importCsvMapped(text, importType, mapping) }
                                "Imported $n ${if (n == 1) "item" else "items"} using your mapping."
                            }.getOrElse { "Import failed: ${it.message}" }
                            headers = emptyList(); rawText = null
                            busy = false
                        }
                    }
                }
            }
        }

        status?.let {
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surface).padding(14.dp)) {
                Text(it, color = MaterialTheme.colorScheme.onBackground, fontSize = 13.sp)
            }
        }

        Spacer(Modifier.height(8.dp))
        LetterboxdSection(container)
        Spacer(Modifier.height(8.dp))
        SimklSection(container)
    }
}

@Composable
private fun SimklSection(container: AppContainer) {
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }
    var userCode by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val hasKey = container.apiKeyStore.has(com.reliquary.app.metadata.ApiKeys.SIMKL)

    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Import from Simkl", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text(
                "Connects your Simkl account and imports your movies, TV shows, and " +
                    "anime with their watch status.",
                color = ReliquaryMuted,
                fontSize = 12.sp,
            )
            if (!hasKey) {
                Text("Set your Simkl Client ID in Settings first.", color = ReliquaryMuted, fontSize = 13.sp)
            } else {
                PillButton(label = "Connect & import", icon = null, background = MaterialTheme.colorScheme.primary, foreground = Color.Black) {
                    if (busy) return@PillButton
                    busy = true; status = "Requesting a code…"; userCode = null
                    scope.launch {
                        val pin = runCatching { container.simklImporter.requestPin() }.getOrNull()
                        if (pin == null) {
                            status = "Couldn't start — check your Client ID."; busy = false; return@launch
                        }
                        userCode = pin.userCode
                        status = "Open simkl.com/pin, enter the code below, then wait here…"
                        var token: String? = null
                        var elapsed = 0
                        while (elapsed < pin.expiresIn && token == null) {
                            delay(pin.interval * 1000L)
                            elapsed += pin.interval
                            token = runCatching { container.simklImporter.poll(pin.userCode) }.getOrNull()
                        }
                        if (token == null) {
                            status = "Timed out waiting for authorization."; userCode = null; busy = false; return@launch
                        }
                        status = "Importing your library…"
                        val count = runCatching { container.simklImporter.importAll(token) }.getOrElse { -1 }
                        userCode = null
                        status = if (count < 0) "Import failed." else "Imported/updated $count items from Simkl."
                        busy = false
                    }
                }
                userCode?.let { code ->
                    Text("Code: $code", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black, fontSize = 22.sp)
                    PillButton(label = "Open simkl.com/pin", icon = null, background = MaterialTheme.colorScheme.surfaceVariant, foreground = MaterialTheme.colorScheme.onSurface) {
                        openUrl("https://simkl.com/pin")
                    }
                }
            }
            status?.let { Text(it, color = MaterialTheme.colorScheme.onBackground, fontSize = 13.sp) }
        }
    }
}

@Composable
private fun LetterboxdSection(container: AppContainer) {
    val scope = rememberCoroutineScope()
    var username by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Import from Letterboxd", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text(
                "Pulls your recently-watched films from a public Letterboxd account " +
                    "(via RSS) and marks them Watched. For full history, export your data " +
                    "from Letterboxd and use Import CSV above.",
                color = ReliquaryMuted,
                fontSize = 12.sp,
            )
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Letterboxd username") },
            )
            PillButton(label = "Import watched", icon = null, background = MaterialTheme.colorScheme.primary, foreground = Color.Black) {
                if (busy || username.isBlank()) return@PillButton
                busy = true; status = "Importing…"
                scope.launch {
                    status = runCatching {
                        val count = container.letterboxdImporter.importWatched(username)
                        if (count == 0) "No films found for \"$username\" (is the profile public?)."
                        else "Imported/updated $count films from Letterboxd."
                    }.getOrElse { "Import failed: ${it.message}" }
                    busy = false
                }
            }
            status?.let { Text(it, color = MaterialTheme.colorScheme.onBackground, fontSize = 13.sp) }
        }
    }
}

@Composable
private fun ColumnPicker(headers: List<String>, selectedIdx: Int?, onSelect: (Int?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val label = selectedIdx?.let { headers.getOrNull(it) } ?: "— none —"
    Box {
        Box(
            Modifier.clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable { expanded = true }.padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(label, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("— none —") }, onClick = { onSelect(null); expanded = false })
            headers.forEachIndexed { idx, header ->
                DropdownMenuItem(text = { Text(header.ifBlank { "(column ${idx + 1})" }) }, onClick = { onSelect(idx); expanded = false })
            }
        }
    }
}

@Composable
private fun TypeSelector(selected: MediaType, onSelect: (MediaType) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Box(
            Modifier.clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable { expanded = true }.padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(selected.displayName, color = MaterialTheme.colorScheme.onSurface)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            MediaType.entries.forEach { type ->
                DropdownMenuItem(text = { Text(type.displayName) }, onClick = { onSelect(type); expanded = false })
            }
        }
    }
}
