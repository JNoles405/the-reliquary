package com.reliquary.app.ui.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reliquary.app.data.newId
import com.reliquary.app.data.nowMillis
import com.reliquary.app.di.AppContainer
import com.reliquary.app.domain.CollectionItem
import com.reliquary.app.domain.EDITION_FIELDS
import com.reliquary.app.domain.MediaType
import com.reliquary.app.domain.SERIES_KEY
import com.reliquary.app.domain.SERIES_NUM_KEY
import com.reliquary.app.domain.VALUE_FIELDS
import com.reliquary.app.domain.parseTags
import com.reliquary.app.metadata.MetadataResult
import com.reliquary.app.metadata.ReliquaryJson
import com.reliquary.app.ui.Navigator
import com.reliquary.app.ui.components.CoverImage
import com.reliquary.app.ui.components.PillButton
import com.reliquary.app.ui.components.VScrollColumn
import com.reliquary.app.ui.theme.ReliquaryMuted
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

@Composable
fun EditItemScreen(
    container: AppContainer,
    itemId: String?,
    mediaTypeName: String,
    customTabId: String?,
    navigator: Navigator,
) {
    val existing = remember(itemId) { itemId?.let { container.repository.getItem(it) } }
    val typeLabel = MediaType.entries.firstOrNull { it.name == mediaTypeName }?.displayName?.trimEnd('s')
        ?: "Item"

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
    var rating by remember { mutableStateOf(existing?.rating) }

    val mediaType = remember {
        MediaType.entries.firstOrNull { it.name == (existing?.mediaType ?: mediaTypeName) } ?: MediaType.MOVIES
    }

    // Existing extras (provider cast/crew + any edition fields), preserved on save.
    val existingExtras = remember(itemId) {
        existing?.extraJson
            ?.let { json -> runCatching { ReliquaryJson.decodeFromString<Map<String, String>>(json) }.getOrNull() }
            .orEmpty()
    }
    // Provider extras (cast/crew/backdrop), seeded from the item and replaced when
    // the user applies a metadata match; merged into the saved extras.
    var providerExtras by remember(itemId) { mutableStateOf(existingExtras) }

    // "Find metadata" search state.
    val scope = rememberCoroutineScope()
    var searching by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<MetadataResult>>(emptyList()) }
    var showResults by remember { mutableStateOf(false) }
    fun runSearch() {
        if (title.isBlank()) return
        searching = true
        showResults = true
        results = emptyList()
        scope.launch {
            results = runCatching { container.metadataService.search(mediaType, title.trim()) }
                .getOrDefault(emptyList())
            searching = false
        }
    }
    fun applyMatch(r: MetadataResult) {
        showResults = false
        scope.launch {
            val full = runCatching { container.metadataService.detailsFor(r) }.getOrDefault(r)
            if (full.title.isNotBlank()) title = full.title
            full.subtitle?.let { subtitle = it }
            full.creators?.let { creators = it }
            full.releaseYear?.let { year = it.toString() }
            full.genres?.let { genres = it }
            full.format?.let { format = it }
            full.coverUrl?.let { coverUrl = it }
            full.description?.let { description = it }
            full.identifier?.let { identifier = it }
            full.rating?.let { rating = it }
            if (full.extra.isNotEmpty()) providerExtras = providerExtras + full.extra
        }
    }
    val editionStates = remember(itemId) {
        EDITION_FIELDS.associateWith { mutableStateOf(existingExtras[it] ?: "") }
    }
    val valueStates = remember(itemId) {
        VALUE_FIELDS.associateWith { mutableStateOf(existingExtras[it] ?: "") }
    }
    var wanted by remember(itemId) { mutableStateOf(existing?.wanted ?: false) }
    var tags by remember(itemId) { mutableStateOf(existing?.tags ?: "") }
    var series by remember(itemId) { mutableStateOf(existingExtras[SERIES_KEY] ?: "") }
    var seriesNum by remember(itemId) { mutableStateOf(existingExtras[SERIES_NUM_KEY] ?: "") }

    fun save() {
        if (title.isBlank()) return
        val now = nowMillis()
        // Merge edited edition fields into provider extras without losing cast/crew data.
        val extras = providerExtras.toMutableMap()
        (EDITION_FIELDS + VALUE_FIELDS).forEach { key ->
            val state = editionStates[key] ?: valueStates[key]
            val value = state?.value?.trim().orEmpty()
            if (value.isBlank()) extras.remove(key) else extras[key] = value
        }
        if (series.isBlank()) extras.remove(SERIES_KEY) else extras[SERIES_KEY] = series.trim()
        if (seriesNum.isBlank()) extras.remove(SERIES_NUM_KEY) else extras[SERIES_NUM_KEY] = seriesNum.trim()
        val mergedExtraJson = if (extras.isEmpty()) null else ReliquaryJson.encodeToString(extras)
        val normalizedTags = parseTags(tags).joinToString(", ").ifBlank { null }
        val item = CollectionItem(
            id = existing?.id ?: newId(),
            mediaType = existing?.mediaType ?: mediaTypeName,
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
            rating = rating,
            location = location.orNull(),
            extraJson = mergedExtraJson,
            notes = notes.orNull(),
            status = existing?.status,
            wanted = wanted,
            tags = normalizedTags,
            favorite = favorite,
            addedAt = existing?.addedAt ?: now,
            updatedAt = now,
        )
        container.repository.upsertItem(item)
        navigator.pop()
    }

    VScrollColumn(
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = if (existing == null) "Add $typeLabel" else "Edit",
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
        )
        Field("Title *", title) { title = it }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            PillButton(
                label = if (searching) "Searching…" else "Find metadata",
                icon = Icons.Filled.Search,
                background = MaterialTheme.colorScheme.surfaceVariant,
                foreground = MaterialTheme.colorScheme.onBackground,
                onClick = { runSearch() },
            )
            Text(
                "Search ${mediaType.displayName} by title to pull a cover, cast & details.",
                color = ReliquaryMuted,
                fontSize = 12.sp,
            )
        }
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
        Field("Tags (comma-separated)", tags) { tags = it }
        Field("Series", series) { series = it }
        Field("Series #", seriesNum) { seriesNum = it }

        Text(
            "Edition details",
            color = ReliquaryMuted,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
        )
        EDITION_FIELDS.forEach { key ->
            val state = editionStates.getValue(key)
            Field(key, state.value) { state.value = it }
        }

        Text(
            "Purchase & value",
            color = ReliquaryMuted,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
        )
        VALUE_FIELDS.forEach { key ->
            val state = valueStates.getValue(key)
            Field(key, state.value) { state.value = it }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = favorite, onCheckedChange = { favorite = it })
            Text("  Favorite", color = MaterialTheme.colorScheme.onBackground)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = wanted, onCheckedChange = { wanted = it })
            Text("  On wishlist (wanted, not owned)", color = MaterialTheme.colorScheme.onBackground)
        }

        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PillButton(
                label = "Save",
                icon = Icons.Filled.Check,
                background = MaterialTheme.colorScheme.primary,
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

    if (showResults) {
        AlertDialog(
            onDismissRequest = { showResults = false },
            title = { Text("Pick a match") },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    when {
                        searching -> Text("Searching…", color = ReliquaryMuted)
                        results.isEmpty() -> Text(
                            "No matches. Check the title, or that a provider key for " +
                                "${mediaType.displayName} is set in Settings.",
                            color = ReliquaryMuted,
                        )
                        else -> results.take(20).forEach { r ->
                            Row(
                                Modifier.fillMaxWidth().clickable { applyMatch(r) }.padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CoverImage(
                                    url = r.coverUrl,
                                    contentDescription = r.title,
                                    modifier = Modifier.width(44.dp).height(66.dp),
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        r.title,
                                        color = MaterialTheme.colorScheme.onBackground,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    val sub = listOfNotNull(
                                        r.releaseYear?.toString(),
                                        r.creators,
                                        r.providerName,
                                    ).joinToString(" · ")
                                    if (sub.isNotBlank()) Text(sub, color = ReliquaryMuted, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showResults = false }) { Text("Close") } },
        )
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
