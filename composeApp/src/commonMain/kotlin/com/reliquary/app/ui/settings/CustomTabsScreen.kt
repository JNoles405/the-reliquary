package com.reliquary.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import com.reliquary.app.data.newId
import com.reliquary.app.data.nowMillis
import com.reliquary.app.di.AppContainer
import com.reliquary.app.domain.CustomTab
import com.reliquary.app.ui.Navigator
import com.reliquary.app.ui.components.PillButton
import com.reliquary.app.ui.components.VScrollColumn
import com.reliquary.app.ui.theme.ReliquaryMuted
import com.reliquary.app.ui.theme.ReliquarySurface

@Composable
fun CustomTabsScreen(container: AppContainer, navigator: Navigator) {
    val repo = container.repository
    val tabs by remember { repo.customTabs() }.collectAsState(emptyList())

    var newName by remember { mutableStateOf("") }
    var newBarcode by remember { mutableStateOf(false) }

    VScrollColumn(
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            "Custom Tabs",
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
        )
        Text(
            "Create your own categories beyond the built-in ones — Vinyl, Board Games, " +
                "LEGO sets, anything. They appear in the top bar alongside the defaults.",
            color = ReliquaryMuted,
            fontSize = 13.sp,
        )

        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(ReliquarySurface).padding(16.dp)) {
            Column {
                Text("New tab", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Tab name") },
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = newBarcode, onCheckedChange = { newBarcode = it })
                    Text("  Allow barcode entry", color = MaterialTheme.colorScheme.onBackground)
                }
                Spacer(Modifier.height(10.dp))
                PillButton(
                    label = "Add tab",
                    icon = Icons.Filled.Add,
                    background = if (newName.isBlank()) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary,
                    foreground = Color.White,
                ) {
                    if (newName.isNotBlank()) {
                        val now = nowMillis()
                        repo.upsertCustomTab(
                            CustomTab(
                                id = newId(),
                                name = newName.trim(),
                                supportsBarcode = newBarcode,
                                position = tabs.size.toLong(),
                                updatedAt = now,
                            ),
                        )
                        newName = ""
                        newBarcode = false
                    }
                }
            }
        }

        if (tabs.isNotEmpty()) {
            Text("Your tabs", color = ReliquaryMuted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.height((tabs.size * 64).dp.coerceAtMost(400.dp))) {
                items(tabs, key = { it.id }) { tab ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(ReliquarySurface).padding(start = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(tab.name, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
                        IconButton(onClick = { repo.deleteCustomTab(tab.id) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete ${tab.name}", tint = ReliquaryMuted)
                        }
                    }
                }
            }
        }
    }
}
