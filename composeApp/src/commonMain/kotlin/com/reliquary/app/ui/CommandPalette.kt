package com.reliquary.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reliquary.app.di.AppContainer
import com.reliquary.app.domain.MediaType
import com.reliquary.app.ui.theme.ReliquaryMuted

/** Global open-state for the command palette (toggled from a key shortcut). */
object CommandPalette {
    var open by mutableStateOf(false)
}

private class Command(val label: String, val hint: String, val run: () -> Unit)

/** A fuzzy "jump to anything" overlay: tabs, screens, tools, and titles. */
@Composable
fun CommandPaletteOverlay(
    container: AppContainer,
    navigator: Navigator,
    onSelectBuiltin: (MediaType) -> Unit,
) {
    if (!CommandPalette.open) return
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    fun close() { CommandPalette.open = false }

    val destinations = remember {
        buildList {
            add(Command("Home", "Screen") { navigator.resetTo(Screen.Home) })
            MediaType.entries.forEach { t -> add(Command(t.displayName, "Tab") { onSelectBuiltin(t) }) }
            add(Command("Discover", "Screen") { navigator.resetTo(Screen.Discover) })
            add(Command("Tags", "Screen") { navigator.resetTo(Screen.Tags) })
            add(Command("Series", "Screen") { navigator.resetTo(Screen.Series) })
            add(Command("Loans", "Screen") { navigator.resetTo(Screen.Loans) })
            add(Command("Stats", "Screen") { navigator.resetTo(Screen.Stats) })
            add(Command("People", "Screen") { navigator.push(Screen.People) })
            add(Command("Settings", "Screen") { navigator.push(Screen.Settings) })
            add(Command("Quick add", "Tool") { navigator.push(Screen.QuickAdd) })
            add(Command("Backups", "Tool") { navigator.push(Screen.Backups) })
            add(Command("Find duplicates", "Tool") { navigator.push(Screen.Duplicates) })
            add(Command("Media servers", "Tool") { navigator.push(Screen.Servers) })
            add(Command("Sync library", "Tool") { navigator.push(Screen.Sync) })
            add(Command("CSV import / export", "Tool") { navigator.push(Screen.Csv) })
        }
    }
    val allCommands = remember {
        destinations + container.repository.allItems().filter { !it.deleted }
            .map { item -> Command(item.title, "Item") { navigator.push(Screen.Detail(item.id)) } }
    }
    val filtered = remember(query) {
        if (query.isBlank()) destinations.take(12)
        else allCommands.filter { it.label.contains(query, ignoreCase = true) }
            .sortedByDescending { it.label.startsWith(query, ignoreCase = true) }
            .take(25)
    }
    fun runFirst() { filtered.firstOrNull()?.let { it.run(); close() } }

    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    Box(
        Modifier.fillMaxSize().background(Color(0xCC000000)).clickable { close() },
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier.padding(top = 90.dp).fillMaxWidth(0.6f)
                .clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface)
                .clickable(enabled = false) {}.padding(10.dp),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().focusRequester(focus).onPreviewKeyEvent { e ->
                    when {
                        e.type == KeyEventType.KeyDown && e.key == Key.Escape -> { close(); true }
                        e.type == KeyEventType.KeyDown && (e.key == Key.Enter || e.key == Key.NumPadEnter) -> { runFirst(); true }
                        else -> false
                    }
                },
                singleLine = true,
                placeholder = { Text("Jump to a tab, screen, tool, or title…") },
            )
            Spacer(Modifier.height(8.dp))
            Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                if (filtered.isEmpty()) {
                    Text("No matches.", color = ReliquaryMuted, fontSize = 13.sp, modifier = Modifier.padding(10.dp))
                }
                filtered.forEach { cmd ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                            .clickable { cmd.run(); close() }.padding(horizontal = 12.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(cmd.label, color = MaterialTheme.colorScheme.onBackground, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text(cmd.hint, color = ReliquaryMuted, fontSize = 12.sp)
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text("Enter to open · Esc to close", color = ReliquaryMuted, fontSize = 11.sp, modifier = Modifier.padding(start = 12.dp, bottom = 4.dp))
        }
    }
}
