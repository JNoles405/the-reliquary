package com.reliquary.app.ui.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reliquary.app.data.nowMillis
import com.reliquary.app.di.AppContainer
import com.reliquary.app.domain.CollectionItem
import com.reliquary.app.domain.MediaType
import com.reliquary.app.data.newId
import com.reliquary.app.ui.Navigator
import com.reliquary.app.ui.components.PillButton
import com.reliquary.app.ui.components.VScrollColumn
import com.reliquary.app.ui.theme.ReliquaryMuted

@Composable
fun QuickAddScreen(container: AppContainer, navigator: Navigator) {
    var text by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(MediaType.MOVIES) }
    var status by remember { mutableStateOf<String?>(null) }

    VScrollColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Quick Add", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 24.sp)
        Text(
            "Paste a list of titles — one per line — to add them all at once. You can open " +
                "each later and use Find metadata to fill in covers and details.",
            color = ReliquaryMuted,
            fontSize = 13.sp,
        )

        TypePicker(type) { type = it }

        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp),
            label = { Text("Titles (one per line)") },
        )

        PillButton("Add all", null, MaterialTheme.colorScheme.primary, Color.Black) {
            val titles = text.lines().map { it.trim() }.filter { it.isNotBlank() }
            if (titles.isEmpty()) { status = "Type at least one title."; return@PillButton }
            var added = 0
            titles.forEach { title ->
                val now = nowMillis()
                container.repository.importOrUpdate(
                    CollectionItem(id = newId(), mediaType = type.name, title = title, addedAt = now, updatedAt = now),
                )
                added++
            }
            text = ""
            status = "Added $added ${if (added == 1) "title" else "titles"} to ${type.displayName}."
        }

        status?.let {
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surface).padding(14.dp)) {
                Text(it, color = MaterialTheme.colorScheme.onBackground, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun TypePicker(selected: MediaType, onSelect: (MediaType) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Box(
            Modifier.clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable { expanded = true }.padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text("Add to: ${selected.displayName}", color = Color.White, fontSize = 13.sp)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            MediaType.entries.forEach { t ->
                DropdownMenuItem(text = { Text(t.displayName) }, onClick = { onSelect(t); expanded = false })
            }
        }
    }
}
