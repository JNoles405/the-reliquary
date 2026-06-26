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
import com.reliquary.app.sync.defaultSyncFilePath
import com.reliquary.app.sync.readTextFile
import com.reliquary.app.sync.writeTextFile
import com.reliquary.app.ui.Navigator
import com.reliquary.app.ui.components.PillButton
import com.reliquary.app.ui.theme.ReliquaryMuted
import com.reliquary.app.ui.theme.ReliquaryTeal
import com.reliquary.app.ui.theme.ReliquarySurface
import com.reliquary.app.ui.theme.ReliquarySurfaceVariant
import kotlinx.coroutines.Dispatchers
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

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
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
            PillButton(label = "Export CSV", icon = null, background = ReliquaryTeal, foreground = Color.Black) {
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
            PillButton(label = "Import CSV", icon = null, background = ReliquarySurfaceVariant, foreground = Color.White) {
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

        status?.let {
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(ReliquarySurface).padding(14.dp)) {
                Text(it, color = MaterialTheme.colorScheme.onBackground, fontSize = 13.sp)
            }
        }

        Spacer(Modifier.height(8.dp))
        LetterboxdSection(container)
    }
}

@Composable
private fun LetterboxdSection(container: AppContainer) {
    val scope = rememberCoroutineScope()
    var username by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(ReliquarySurface).padding(16.dp)) {
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
            PillButton(label = "Import watched", icon = null, background = ReliquaryTeal, foreground = Color.Black) {
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
private fun TypeSelector(selected: MediaType, onSelect: (MediaType) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Box(
            Modifier.clip(RoundedCornerShape(8.dp)).background(ReliquarySurfaceVariant)
                .clickable { expanded = true }.padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(selected.displayName, color = Color.White)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            MediaType.entries.forEach { type ->
                DropdownMenuItem(text = { Text(type.displayName) }, onClick = { onSelect(type); expanded = false })
            }
        }
    }
}
