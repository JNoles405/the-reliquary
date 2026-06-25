package com.reliquary.app.ui.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reliquary.app.data.newId
import com.reliquary.app.data.nowMillis
import com.reliquary.app.di.AppContainer
import com.reliquary.app.domain.CollectionItem
import com.reliquary.app.domain.MediaType
import com.reliquary.app.ui.Navigator
import com.reliquary.app.ui.components.PillButton
import com.reliquary.app.ui.theme.ReliquaryMuted
import com.reliquary.app.ui.theme.ReliquaryRed

@Composable
fun EditItemScreen(
    container: AppContainer,
    itemId: String?,
    mediaType: MediaType,
    customTabId: String?,
    navigator: Navigator,
) {
    val existing = remember(itemId) { itemId?.let { container.repository.getItem(it) } }

    var title by remember { mutableStateOf(existing?.title ?: "") }
    var subtitle by remember { mutableStateOf(existing?.subtitle ?: "") }
    var creators by remember { mutableStateOf(existing?.creators ?: "") }
    var year by remember { mutableStateOf(existing?.releaseYear?.toString() ?: "") }
    var format by remember { mutableStateOf(existing?.format ?: "") }
    var genres by remember { mutableStateOf(existing?.genres ?: "") }
    var identifier by remember { mutableStateOf(existing?.identifier ?: "") }
    var location by remember { mutableStateOf(existing?.location ?: "") }
    var coverUrl by remember { mutableStateOf(existing?.coverUrl ?: "") }
    var description by remember { mutableStateOf(existing?.description ?: "") }
    var notes by remember { mutableStateOf(existing?.notes ?: "") }
    var favorite by remember { mutableStateOf(existing?.favorite ?: false) }

    fun save() {
        if (title.isBlank()) return
        val now = nowMillis()
        val item = CollectionItem(
            id = existing?.id ?: newId(),
            mediaType = existing?.mediaType ?: mediaType.name,
            customTabId = existing?.customTabId ?: customTabId,
            title = title.trim(),
            subtitle = subtitle.orNull(),
            creators = creators.orNull(),
            releaseYear = year.trim().toLongOrNull(),
            description = description.orNull(),
            coverPath = existing?.coverPath,
            coverUrl = coverUrl.orNull(),
            barcode = existing?.barcode,
            identifierType = existing?.identifierType,
            identifier = identifier.orNull(),
            genres = genres.orNull(),
            format = format.orNull(),
            location = location.orNull(),
            extraJson = existing?.extraJson,
            notes = notes.orNull(),
            favorite = favorite,
            addedAt = existing?.addedAt ?: now,
            updatedAt = now,
        )
        container.repository.upsertItem(item)
        navigator.pop()
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = if (existing == null) "Add ${mediaType.displayName.trimEnd('s')}" else "Edit",
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
        )
        Field("Title *", title) { title = it }
        Field("Subtitle", subtitle) { subtitle = it }
        Field("Creators (author / director / artist)", creators) { creators = it }
        Field("Year", year) { year = it }
        Field("Format", format) { format = it }
        Field("Genres", genres) { genres = it }
        Field("Identifier (ISBN / UPC / catalog #)", identifier) { identifier = it }
        Field("Location (shelf / box)", location) { location = it }
        Field("Cover image URL", coverUrl) { coverUrl = it }
        Field("Description", description, singleLine = false) { description = it }
        Field("Notes", notes, singleLine = false) { notes = it }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = favorite, onCheckedChange = { favorite = it })
            Spacer(Modifier.height(0.dp))
            Text("  Favorite", color = MaterialTheme.colorScheme.onBackground)
        }

        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PillButton(
                label = "Save",
                icon = Icons.Filled.Check,
                background = ReliquaryRed,
                foreground = MaterialTheme.colorScheme.onBackground,
                onClick = { save() },
            )
            PillButton(
                label = "Cancel",
                icon = null,
                background = MaterialTheme.colorScheme.surfaceVariant,
                foreground = MaterialTheme.colorScheme.onBackground,
                onClick = { navigator.pop() },
            )
        }
        if (title.isBlank()) {
            Text("Title is required.", color = ReliquaryMuted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun Field(label: String, value: String, singleLine: Boolean = true, onValue: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = singleLine,
    )
}

private fun String.orNull(): String? = trim().ifBlank { null }
