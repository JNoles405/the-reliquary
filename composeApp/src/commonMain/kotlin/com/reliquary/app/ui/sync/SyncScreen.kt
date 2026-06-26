package com.reliquary.app.ui.sync

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
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Upload
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
import com.reliquary.app.sync.LAN_SYNC_PORT
import com.reliquary.app.sync.defaultSyncFilePath
import com.reliquary.app.sync.localIpAddresses
import com.reliquary.app.sync.readTextFile
import com.reliquary.app.sync.writeTextFile
import com.reliquary.app.ui.Navigator
import com.reliquary.app.ui.components.PillButton
import com.reliquary.app.ui.theme.ReliquaryMuted
import com.reliquary.app.ui.theme.ReliquaryTeal
import com.reliquary.app.ui.theme.ReliquarySurface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SyncScreen(container: AppContainer, navigator: Navigator) {
    val scope = rememberCoroutineScope()
    var path by remember { mutableStateOf(defaultSyncFilePath()) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Sync", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 24.sp)
        Text(
            "Export your whole library to a single file, move it to your other device " +
                "(via cloud drive or USB), and import it there. Importing merges by most-" +
                "recent edit, so changes from both devices combine and deletions carry over. " +
                "API keys are never included.",
            color = ReliquaryMuted,
            fontSize = 13.sp,
        )

        OutlinedTextField(
            value = path,
            onValueChange = { path = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Sync file path") },
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PillButton(
                label = "Export",
                icon = Icons.Filled.Upload,
                background = ReliquaryTeal,
                foreground = Color.White,
            ) {
                if (busy) return@PillButton
                busy = true
                status = null
                scope.launch {
                    status = runCatching {
                        val json = withContext(Dispatchers.Default) { container.syncService.exportJson() }
                        withContext(Dispatchers.Default) { writeTextFile(path, json) }
                        "Exported library to:\n$path"
                    }.getOrElse { "Export failed: ${it.message}" }
                    busy = false
                }
            }
            PillButton(
                label = "Import & merge",
                icon = Icons.Filled.Download,
                background = MaterialTheme.colorScheme.surfaceVariant,
                foreground = MaterialTheme.colorScheme.onBackground,
            ) {
                if (busy) return@PillButton
                busy = true
                status = null
                scope.launch {
                    status = runCatching {
                        val text = withContext(Dispatchers.Default) { readTextFile(path) }
                            ?: return@runCatching "No file found at:\n$path"
                        val result = withContext(Dispatchers.Default) { container.syncService.importJson(text) }
                        "Merged ${result.total} records " +
                            "(${result.items} items, ${result.people} people, " +
                            "${result.loans} loans, ${result.customTabs} tabs)."
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
        LocalNetworkSection(container)
    }
}

@Composable
private fun LocalNetworkSection(container: AppContainer) {
    val scope = rememberCoroutineScope()
    val myIps = remember { localIpAddresses() }
    var hosting by remember { mutableStateOf(container.lanSync.isHosting) }
    var hostAddress by remember { mutableStateOf("") }
    var lanStatus by remember { mutableStateOf<String?>(null) }
    var lanBusy by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(ReliquarySurface).padding(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Local network sync", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text(
                "On the same Wi-Fi, host on one device and connect from the other. They " +
                    "merge both ways in one tap — no files to move.",
                color = ReliquaryMuted,
                fontSize = 12.sp,
            )

            // Host side
            PillButton(
                label = if (hosting) "Stop hosting" else "Host on this device",
                icon = null,
                background = if (hosting) MaterialTheme.colorScheme.surfaceVariant else ReliquaryTeal,
                foreground = if (hosting) MaterialTheme.colorScheme.onBackground else Color.White,
            ) {
                if (hosting) {
                    container.lanSync.stopHosting()
                    hosting = false
                } else {
                    runCatching { container.lanSync.startHosting() }
                    hosting = container.lanSync.isHosting
                }
            }
            if (hosting) {
                val where = if (myIps.isEmpty()) "this device" else myIps.joinToString(", ")
                Text("Listening on $where  (port $LAN_SYNC_PORT)", color = ReliquaryTeal, fontSize = 13.sp)
                Text("Enter that address on your other device to sync.", color = ReliquaryMuted, fontSize = 12.sp)
            }

            // Connect side
            OutlinedTextField(
                value = hostAddress,
                onValueChange = { hostAddress = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Host device IP address") },
            )
            PillButton(
                label = "Connect & sync",
                icon = null,
                background = if (hostAddress.isBlank()) MaterialTheme.colorScheme.surfaceVariant else ReliquaryTeal,
                foreground = Color.White,
            ) {
                if (lanBusy || hostAddress.isBlank()) return@PillButton
                lanBusy = true
                lanStatus = "Connecting…"
                scope.launch {
                    lanStatus = runCatching {
                        val result = container.lanSync.connectAndSync(hostAddress)
                        "Synced over the network — merged ${result.total} records."
                    }.getOrElse { "Network sync failed: ${it.message ?: "could not reach host"}" }
                    lanBusy = false
                }
            }
            lanStatus?.let { Text(it, color = MaterialTheme.colorScheme.onBackground, fontSize = 13.sp) }
        }
    }
}
