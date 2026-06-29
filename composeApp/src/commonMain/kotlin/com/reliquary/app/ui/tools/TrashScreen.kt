package com.reliquary.app.ui.tools

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reliquary.app.di.AppContainer
import com.reliquary.app.ui.Navigator
import com.reliquary.app.ui.components.CoverImage
import com.reliquary.app.ui.components.PillButton
import com.reliquary.app.ui.components.VScrollColumn
import com.reliquary.app.ui.theme.ReliquaryMuted

@Composable
fun TrashScreen(container: AppContainer, navigator: Navigator) {
    var version by remember { mutableStateOf(0) }
    val items = remember(version) { container.repository.deletedItems() }

    VScrollColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Trash", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 24.sp)
        Text(
            "Deleted items live here until you restore them or remove them permanently.",
            color = ReliquaryMuted,
            fontSize = 13.sp,
        )
        if (items.isEmpty()) {
            Text("Trash is empty.", color = ReliquaryMuted, fontSize = 14.sp)
            return@VScrollColumn
        }
        items.forEach { item ->
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surface).padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CoverImage(item.coverImage, item.title, Modifier.width(40.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(4.dp)))
                    Column(Modifier.weight(1f)) {
                        Text(item.title, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(listOfNotNull(item.releaseYear?.toString(), item.format).joinToString(" · "), color = ReliquaryMuted, fontSize = 12.sp)
                    }
                    PillButton("Restore", null, MaterialTheme.colorScheme.primary, androidx.compose.ui.graphics.Color.Black) {
                        container.repository.restoreItem(item.id); version++
                    }
                    PillButton("Delete forever", null, MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onBackground) {
                        container.repository.purgeItem(item.id); version++
                    }
                }
            }
        }
    }
}
