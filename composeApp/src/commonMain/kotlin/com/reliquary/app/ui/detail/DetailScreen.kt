package com.reliquary.app.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import com.reliquary.app.data.nowMillis
import com.reliquary.app.domain.Status
import kotlinx.serialization.encodeToString
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
import com.reliquary.app.domain.EDITION_FIELDS
import com.reliquary.app.domain.VALUE_FIELDS
import com.reliquary.app.domain.WANTED_KEY
import com.reliquary.app.metadata.ReliquaryJson
import com.reliquary.app.ui.Navigator
import com.reliquary.app.ui.Screen
import com.reliquary.app.ui.components.CoverImage
import com.reliquary.app.ui.components.PillButton
import com.reliquary.app.util.formatDate
import com.reliquary.app.ui.theme.ReliquaryMuted
import com.reliquary.app.ui.theme.ReliquaryTeal
import com.reliquary.app.ui.theme.ReliquarySurfaceVariant
import kotlin.math.round
import kotlinx.serialization.decodeFromString

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

    // Extras split into provider info (cast, crew, runtime, …) and editable edition details.
    val allExtras = remember(current.extraJson) {
        current.extraJson
            ?.let { json -> runCatching { ReliquaryJson.decodeFromString<Map<String, String>>(json) }.getOrNull() }
            ?.toList().orEmpty()
    }
    val editionKeys = remember { EDITION_FIELDS.toSet() }
    val valueKeys = remember { VALUE_FIELDS.toSet() }
    val providerExtras = allExtras.filter {
        it.first !in editionKeys && it.first !in valueKeys && !it.first.startsWith("_")
    }
    val editionExtras = allExtras.filter { it.first in editionKeys }
    val valueExtras = allExtras.filter { it.first in valueKeys }
    val backdrop = allExtras.firstOrNull { it.first == "_backdrop" }?.second
    val currentStatus = allExtras.firstOrNull { it.first == Status.KEY }?.second
    val isWanted = allExtras.firstOrNull { it.first == WANTED_KEY }?.second == "true"

    fun updateExtras(mutate: (MutableMap<String, String>) -> Unit) {
        val map = current.extraJson
            ?.let { runCatching { ReliquaryJson.decodeFromString<Map<String, String>>(it) }.getOrNull() }
            ?.toMutableMap() ?: mutableMapOf()
        mutate(map)
        val json = if (map.isEmpty()) null else ReliquaryJson.encodeToString(map)
        container.repository.upsertItem(current.copy(extraJson = json, updatedAt = nowMillis()))
    }

    fun setStatus(value: String?) = updateExtras { if (value == null) it.remove(Status.KEY) else it[Status.KEY] = value }
    fun setWanted(value: Boolean) = updateExtras { if (value) it[WANTED_KEY] = "true" else it.remove(WANTED_KEY) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Box(Modifier.fillMaxWidth().height(420.dp)) {
            val heroImage = backdrop ?: current.coverImage
            if (heroImage != null) {
                CoverImage(heroImage, current.title, Modifier.fillMaxSize())
            } else {
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.linearGradient(listOf(Color(0xFF0F3631), Color(0xFF0E1413))),
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
                val badge = when {
                    isWanted -> "WISHLIST"
                    activeLoan != null -> "ON LOAN"
                    else -> null
                }
                if (badge != null) {
                    Box(
                        Modifier.clip(RoundedCornerShape(4.dp)).background(ReliquaryTeal)
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    ) {
                        Text(badge, color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
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

            current.rating?.let { r ->
                Spacer(Modifier.height(16.dp))
                Box(
                    Modifier.clip(RoundedCornerShape(4.dp)).background(ReliquaryTeal)
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                ) {
                    Text(
                        "★ ${round(r * 10) / 10.0} / 10",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("Status", color = ReliquaryMuted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Status.optionsFor(current.mediaType).forEach { option ->
                    StatusChip(option, selected = currentStatus == option) {
                        setStatus(if (currentStatus == option) null else option)
                    }
                }
                StatusChip(if (isWanted) "On wishlist ✓" else "Wishlist", selected = isWanted) {
                    setWanted(!isWanted)
                }
            }

            if (activeLoan != null) {
                Spacer(Modifier.height(16.dp))
                val due = activeLoan.dueAt?.let { " · due ${formatDate(it)}" } ?: ""
                Text(
                    "Loaned to ${borrowerName ?: "someone"}$due",
                    color = ReliquaryTeal,
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
            providerExtras.forEach { (label, value) -> MetaRow(label, value) }
            MetaRow("Format", current.format)
            MetaRow("Location", current.location)
            MetaRow(current.identifierType ?: "Identifier", current.identifier)
            MetaRow("Barcode", current.barcode)

            if (editionExtras.isNotEmpty()) {
                Spacer(Modifier.height(18.dp))
                Text(
                    "Edition Details",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                )
                Spacer(Modifier.height(6.dp))
                editionExtras.forEach { (label, value) -> MetaRow(label, value) }
            }

            if (valueExtras.isNotEmpty()) {
                Spacer(Modifier.height(18.dp))
                Text(
                    "Purchase & Value",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                )
                Spacer(Modifier.height(6.dp))
                valueExtras.forEach { (label, value) -> MetaRow(label, value) }
            }

            current.notes?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(16.dp))
                Text("Notes", color = ReliquaryMuted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(it, color = MaterialTheme.colorScheme.onBackground, fontSize = 14.sp)
            }

            if (loans.isNotEmpty()) {
                Spacer(Modifier.height(18.dp))
                Text("Loan History", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(Modifier.height(6.dp))
                loans.forEach { loan ->
                    val who = container.repository.getPerson(loan.personId)?.name ?: "Someone"
                    val span = if (loan.returnedAt != null) {
                        "${formatDate(loan.loanedAt)} → ${formatDate(loan.returnedAt)}"
                    } else {
                        "${formatDate(loan.loanedAt)} → out"
                    }
                    MetaRow(who, span)
                }
            }
        }
    }
}

@Composable
private fun StatusChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(20.dp))
            .background(if (selected) ReliquaryTeal else ReliquarySurfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            label,
            color = if (selected) Color.Black else Color.White,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
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
