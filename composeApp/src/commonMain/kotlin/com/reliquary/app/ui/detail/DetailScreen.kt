package com.reliquary.app.ui.detail

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reliquary.app.di.AppContainer
import com.reliquary.app.domain.CollectionItem
import com.reliquary.app.ui.Navigator
import com.reliquary.app.ui.Screen
import com.reliquary.app.ui.components.CoverImage
import com.reliquary.app.ui.components.PillButton
import com.reliquary.app.util.formatDate
import com.reliquary.app.ui.theme.ReliquaryMuted
import com.reliquary.app.ui.theme.ReliquaryRed
import com.reliquary.app.ui.theme.ReliquarySurfaceVariant

@Composable
fun DetailScreen(container: AppContainer, itemId: String, navigator: Navigator) {
    val item by remember(itemId) { container.repository.itemFlow(itemId) }.collectAsState(null)
    val loans by remember(itemId) { container.repository.loansForItem(itemId) }.collectAsState(emptyList())
    val current = item

    if (current == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Loading…", color = ReliquaryMuted)
        }
        return
    }

    val activeLoan = loans.firstOrNull { it.isActive }
    val borrowerName = activeLoan?.let { container.repository.getPerson(it.personId)?.name }

    // Cache the remote cover to local storage the first time this item is viewed.
    LaunchedEffect(current.id) {
        container.coverCache.ensureCached(current, container.repository)
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Box(Modifier.fillMaxWidth().height(420.dp)) {
            if (current.coverImage != null) {
                CoverImage(current.coverImage, current.title, Modifier.fillMaxSize())
            } else {
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.linearGradient(listOf(Color(0xFF3A1C1C), Color(0xFF141414))),
                    ),
                )
            }
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0.3f to Color.Transparent,
                        1f to MaterialTheme.colorScheme.background,
                    ),
                ),
            )
            Column(Modifier.align(Alignment.BottomStart).padding(24.dp)) {
                if (activeLoan != null) {
                    Box(
                        Modifier.clip(RoundedCornerShape(4.dp)).background(ReliquaryRed)
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    ) {
                        Text("ON LOAN", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(8.dp))
                }
                Text(
                    current.title,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Black,
                    fontSize = 38.sp,
                )
                current.subtitle?.let {
                    Text(it, color = ReliquaryMuted, fontSize = 16.sp)
                }
                Spacer(Modifier.height(4.dp))
                val line = listOfNotNull(current.creators, current.releaseYear?.toString(), current.format)
                    .joinToString("  ·  ")
                if (line.isNotBlank()) Text(line, color = ReliquaryMuted, fontSize = 14.sp)
            }
        }

        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PillButton(
                    label = if (activeLoan == null) "Loan out" else "Manage loan",
                    icon = Icons.Filled.People,
                    background = MaterialTheme.colorScheme.onBackground,
                    foreground = Color.Black,
                ) { navigator.push(Screen.LoanItem(current.id)) }
                PillButton(
                    label = "Edit",
                    icon = Icons.Filled.Edit,
                    background = ReliquarySurfaceVariant,
                    foreground = MaterialTheme.colorScheme.onBackground,
                ) {
                    navigator.push(Screen.EditItem(current.id, current.mediaType, current.customTabId))
                }
                PillButton(
                    label = "Delete",
                    icon = Icons.Filled.Delete,
                    background = ReliquarySurfaceVariant,
                    foreground = MaterialTheme.colorScheme.onBackground,
                ) {
                    container.repository.deleteItem(current.id)
                    navigator.pop()
                }
            }

            if (activeLoan != null) {
                Spacer(Modifier.height(16.dp))
                val due = activeLoan.dueAt?.let { " · due ${formatDate(it)}" } ?: ""
                Text(
                    "Loaned to ${borrowerName ?: "someone"}$due",
                    color = ReliquaryRed,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            current.description?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(20.dp))
                Text(it, color = MaterialTheme.colorScheme.onBackground, fontSize = 15.sp, lineHeight = 22.sp)
            }

            Spacer(Modifier.height(20.dp))
            MetaRow("Genres", current.genres)
            MetaRow("Format", current.format)
            MetaRow("Location", current.location)
            MetaRow(current.identifierType ?: "Identifier", current.identifier)
            MetaRow("Barcode", current.barcode)
            current.notes?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(16.dp))
                Text("Notes", color = ReliquaryMuted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(it, color = MaterialTheme.colorScheme.onBackground, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun MetaRow(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, color = ReliquaryMuted, fontSize = 14.sp, modifier = Modifier.width(120.dp))
        Text(value, color = MaterialTheme.colorScheme.onBackground, fontSize = 14.sp, modifier = Modifier.weight(1f))
    }
}
