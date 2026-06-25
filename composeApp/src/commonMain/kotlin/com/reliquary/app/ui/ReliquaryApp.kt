package com.reliquary.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reliquary.app.data.ReliquaryRepository
import com.reliquary.app.domain.MediaType
import com.reliquary.app.ui.theme.ReliquaryMuted
import com.reliquary.app.ui.theme.ReliquaryRed
import com.reliquary.app.ui.theme.ReliquarySurfaceVariant

/**
 * Top-level shell of The Reliquary. This is the visual skeleton — a Netflix-style
 * dark layout with a hero banner and horizontally scrolling shelves. Real data,
 * detail screens, scanning and loans are layered on in later increments.
 */
@Composable
fun ReliquaryApp(repository: ReliquaryRepository) {
    var selected by remember { mutableStateOf(MediaType.MOVIES) }
    val count by remember(selected) { repository.countByType(selected.name) }.collectAsState(0L)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { TopNav(selected) { selected = it } },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            HeroBanner(selected, count)
            Spacer(Modifier.height(24.dp))
            Shelf(title = "Recently Added")
            Spacer(Modifier.height(20.dp))
            Shelf(title = "On Loan")
        }
    }
}

@Composable
private fun TopNav(selected: MediaType, onSelect: (MediaType) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "THE RELIQUARY",
            color = ReliquaryRed,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 20.sp,
        )
        Spacer(Modifier.width(28.dp))
        MediaType.entries.forEach { type ->
            val active = type == selected
            Text(
                text = type.displayName,
                color = if (active) MaterialTheme.colorScheme.onBackground else ReliquaryMuted,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                fontSize = 15.sp,
                modifier = Modifier
                    .clickable { onSelect(type) }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun HeroBanner(selected: MediaType, count: Long) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(360.dp),
    ) {
        // Placeholder cinematic backdrop until real cover art is wired in.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF3A1C1C), Color(0xFF141414)),
                    ),
                ),
        )
        // Bottom scrim so text stays legible over artwork.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.4f to Color.Transparent,
                        1f to MaterialTheme.colorScheme.background,
                    ),
                ),
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = 28.dp, bottom = 28.dp, end = 28.dp),
        ) {
            Text(
                text = selected.displayName,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Black,
                fontSize = 48.sp,
            )
            Spacer(Modifier.height(8.dp))
            val subtitle = if (count > 0) {
                "$count ${if (count == 1L) "title" else "titles"} in your collection"
            } else {
                "Your ${selected.displayName.lowercase()} collection lives here."
            }
            Text(
                text = subtitle,
                color = ReliquaryMuted,
                fontSize = 16.sp,
            )
            Spacer(Modifier.height(18.dp))
            Row {
                HeroButton(
                    label = "Add Item",
                    icon = Icons.Filled.Add,
                    background = MaterialTheme.colorScheme.onBackground,
                    foreground = Color.Black,
                )
                Spacer(Modifier.width(12.dp))
                if (selected.supportsBarcode) {
                    HeroButton(
                        label = "Scan Barcode",
                        icon = Icons.Filled.QrCodeScanner,
                        background = Color(0xCC4D4D4D),
                        foreground = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    background: Color,
    foreground: Color,
) {
    Row(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(background)
            .clickable { /* wired up in a later increment */ }
            .padding(horizontal = 22.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = foreground, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, color = foreground, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
    }
}

@Composable
private fun Shelf(title: String) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            modifier = Modifier.padding(start = 28.dp, bottom = 12.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(emptyPlaceholders) { _ ->
                PlaceholderCard()
            }
        }
    }
}

private val emptyPlaceholders = List(6) { it }

@Composable
private fun PlaceholderCard() {
    Box(
        Modifier
            .width(130.dp)
            .height(190.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(ReliquarySurfaceVariant)
            .clickable { },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.Info,
            contentDescription = null,
            tint = ReliquaryMuted,
            modifier = Modifier.size(28.dp),
        )
    }
}
