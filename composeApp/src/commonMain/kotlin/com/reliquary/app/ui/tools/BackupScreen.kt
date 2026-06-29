package com.reliquary.app.ui.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reliquary.app.data.nowMillis
import com.reliquary.app.di.AppContainer
import com.reliquary.app.sync.BackupFile
import com.reliquary.app.sync.defaultSyncFilePath
import com.reliquary.app.sync.deleteFile
import com.reliquary.app.sync.listBackups
import com.reliquary.app.sync.readTextFile
import com.reliquary.app.sync.writeTextFile
import com.reliquary.app.ui.Navigator
import com.reliquary.app.ui.components.PillButton
import com.reliquary.app.ui.components.VScrollColumn
import com.reliquary.app.ui.theme.ReliquaryMuted
import com.reliquary.app.util.formatDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun BackupScreen(container: AppContainer, navigator: Navigator) {
    val scope = rememberCoroutineScope()
    var version by remember { mutableStateOf(0) }
    val backups = remember(version) { listBackups() }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var confirmRestore by remember { mutableStateOf<BackupFile?>(null) }

    fun createBackup() {
        if (busy) return
        busy = true; message = null
        scope.launch {
            val path = defaultSyncFilePath().replace("reliquary-sync.json", "reliquary-backup-${formatDate(nowMillis())}.json")
            runCatching { withContext(Dispatchers.Default) { writeTextFile(path, container.syncService.exportJson()) } }
                .onSuccess { message = "Backup saved: ${path.substringAfterLast('\\').substringAfterLast('/')}" }
                .onFailure { message = "Backup failed: ${it.message}" }
            busy = false; version++
        }
    }

    fun restore(file: BackupFile) {
        if (busy) return
        busy = true; message = null
        scope.launch {
            val result = runCatching {
                withContext(Dispatchers.Default) {
                    val text = readTextFile(file.path) ?: error("Could not read the file.")
                    container.syncService.importJson(text)
                }
            }
            message = result.fold(
                onSuccess = { "Restored ${it.total} records from ${file.name}." },
                onFailure = { "Restore failed: ${it.message}" },
            )
            busy = false; version++
        }
    }

    VScrollColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Backups", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 24.sp)
        Text(
            "Snapshots of your whole library (items, people, loans, custom tabs). Restoring " +
                "merges a snapshot in — newer records win, so nothing already-newer is lost.",
            color = ReliquaryMuted,
            fontSize = 13.sp,
        )

        PillButton("Create backup now", Icons.Filled.Save, MaterialTheme.colorScheme.primary, androidx.compose.ui.graphics.Color.Black) { createBackup() }

        message?.let { Text(it, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
        if (busy) Text("Working…", color = ReliquaryMuted, fontSize = 13.sp)

        if (backups.isEmpty()) {
            Text("No backups yet.", color = ReliquaryMuted, fontSize = 14.sp)
        } else {
            backups.forEach { file ->
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surface).padding(14.dp)) {
                    Column {
                        Text(file.name, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(
                            "${formatDate(file.modifiedAt)} · ${file.sizeBytes / 1024} KB",
                            color = ReliquaryMuted,
                            fontSize = 12.sp,
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            PillButton("Restore", null, MaterialTheme.colorScheme.primary, androidx.compose.ui.graphics.Color.Black) { confirmRestore = file }
                            PillButton("Delete", null, MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onBackground) {
                                deleteFile(file.path); version++
                            }
                        }
                    }
                }
            }
        }
    }

    confirmRestore?.let { file ->
        AlertDialog(
            onDismissRequest = { confirmRestore = null },
            title = { Text("Restore this backup?") },
            text = { Text("Merge \"${file.name}\" into your current library? Newer records are kept; this won't delete items added since the backup.") },
            confirmButton = { TextButton(onClick = { confirmRestore = null; restore(file) }) { Text("Restore") } },
            dismissButton = { TextButton(onClick = { confirmRestore = null }) { Text("Cancel") } },
        )
    }
}
