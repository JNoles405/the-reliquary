package com.reliquary.app.ui.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reliquary.app.di.AppContainer
import com.reliquary.app.domain.CollectionItem
import com.reliquary.app.ui.Navigator
import com.reliquary.app.ui.Screen
import com.reliquary.app.ui.components.CoverImage
import com.reliquary.app.ui.components.PillButton
import com.reliquary.app.ui.components.VScrollColumn
import com.reliquary.app.ui.theme.ReliquaryMuted

private fun normalize(s: String): String = s.lowercase().filter { it.isLetterOrDigit() }

@Composable
fun DuplicatesScreen(container: AppContainer, navigator: Navigator) {
    // Recompute when an item is removed.
    var version by remember { mutableStateOf(0) }
    val groups = remember(version) {
        container.repository.allItems().filter { !it.deleted }
            .groupBy { "${it.mediaType}|${normalize(it.title)}|${it.releaseYear ?: 0}" }
            .values.filter { it.size > 1 }
            .sortedByDescending { it.size }
    }

    VScrollColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Find Duplicates", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 24.sp)
        Text(
            "Items that look like duplicates (same type, title and year). Open one to check, " +
                "or remove the copies you don't want.",
            color = ReliquaryMuted,
            fontSize = 13.sp,
        )

        if (groups.isEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("No duplicates found. 🎉", color = ReliquaryMuted, fontSize = 14.sp)
            return@VScrollColumn
        }

        Text("${groups.size} group${if (groups.size == 1) "" else "s"} found", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)

        groups.forEach { group ->
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surface).padding(14.dp)) {
                Column {
                    val first = group.first()
                    Text(
                        first.title + (first.releaseYear?.let { " ($it)" } ?: ""),
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                    )
                    Spacer(Modifier.height(10.dp))
                    group.forEach { item ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            CoverImage(
                                url = item.coverImage,
                                contentDescription = item.title,
                                modifier = Modifier.width(40.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(4.dp)),
                            )
                            Column(Modifier.weight(1f).clickable { navigator.push(Screen.Detail(item.id)) }) {
                                Text(item.title, color = MaterialTheme.colorScheme.onBackground, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                val sub = listOfNotNull(
                                    item.format,
                                    item.identifier?.let { "id $it" },
                                    if (item.wanted) "wishlist" else null,
                                ).joinToString(" · ").ifBlank { "tap to open" }
                                Text(sub, color = ReliquaryMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            PillButton("Delete", null, MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onBackground) {
                                container.repository.deleteItem(item.id)
                                version++
                            }
                        }
                    }
                }
            }
        }
    }
}
