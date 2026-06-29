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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.reliquary.app.data.newId
import com.reliquary.app.data.nowMillis
import com.reliquary.app.domain.Status
import kotlinx.serialization.encodeToString
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import com.reliquary.app.di.AppContainer
import com.reliquary.app.domain.CollectionItem
import com.reliquary.app.domain.COVER_LOCK_KEY
import com.reliquary.app.domain.EDITION_FIELDS
import com.reliquary.app.domain.PROGRESS_KEY
import com.reliquary.app.domain.PROGRESS_TOTAL_KEY
import com.reliquary.app.domain.SERIES_KEY
import com.reliquary.app.domain.VALUE_FIELDS
import com.reliquary.app.domain.MediaType
import com.reliquary.app.domain.MY_RATING_KEY
import com.reliquary.app.domain.WISH_PRIORITIES
import com.reliquary.app.domain.WISH_PRIORITY_KEY
import com.reliquary.app.domain.parseTags
import com.reliquary.app.domain.progressUnit
import com.reliquary.app.metadata.MetadataResult
import com.reliquary.app.metadata.ReliquaryJson
import com.reliquary.app.tools.ValuePoint
import com.reliquary.app.tools.Session
import com.reliquary.app.tools.SessionLog
import com.reliquary.app.util.DAY_MILLIS
import kotlinx.serialization.encodeToString
import com.reliquary.app.ui.Navigator
import com.reliquary.app.ui.Screen
import com.reliquary.app.ui.components.CoverImage
import com.reliquary.app.ui.components.PillButton
import com.reliquary.app.ui.components.VScrollColumn
import com.reliquary.app.util.formatDate
import com.reliquary.app.util.isDesktopPlatform
import com.reliquary.app.util.openUrl
import com.reliquary.app.util.pickAndStoreImage
import com.reliquary.app.ui.theme.ReliquaryMuted
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
        com.reliquary.app.tools.RecentlyViewed.record(container.repository, current.id)
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
        it.first !in editionKeys && it.first !in valueKeys && !it.first.startsWith("_") && !it.first.startsWith("cf:")
    }
    val editionExtras = allExtras.filter { it.first in editionKeys }
    val valueExtras = allExtras.filter { it.first in valueKeys }
    val customExtras = allExtras.filter { it.first.startsWith("cf:") }
    val backdrop = allExtras.firstOrNull { it.first == "_backdrop" }?.second
    val serverPlayUrl = allExtras.firstOrNull { it.first == "_serverPlayUrl" }?.second
    val serverName = allExtras.firstOrNull { it.first == "_server" }?.second
    val currentStatus = current.status
    val isWanted = current.wanted

    fun setStatus(value: String?) = container.repository.updateStatus(current.id, value)
    fun setWanted(value: Boolean) =
        container.repository.upsertItem(current.copy(wanted = value, updatedAt = nowMillis()))

    fun updateExtra(mutate: (MutableMap<String, String>) -> Unit) {
        val map = current.extraJson
            ?.let { runCatching { ReliquaryJson.decodeFromString<Map<String, String>>(it) }.getOrNull() }
            ?.toMutableMap() ?: mutableMapOf()
        mutate(map)
        val json = if (map.isEmpty()) null else ReliquaryJson.encodeToString(map)
        container.repository.upsertItem(current.copy(extraJson = json, updatedAt = nowMillis()))
    }

    // Streaming availability + recommendations (TMDB titles only).
    val tmdbId = current.identifier?.takeIf { current.identifierType == "TMDB" }
    val tmdbMediaType = runCatching { MediaType.valueOf(current.mediaType) }.getOrNull()
    var streaming by remember(current.id) { mutableStateOf<List<String>>(emptyList()) }
    val recommendations = remember(current.id) { mutableStateListOf<MetadataResult>() }
    var collectionName by remember(current.id) { mutableStateOf<String?>(null) }
    val collectionMissing = remember(current.id) { mutableStateListOf<MetadataResult>() }
    LaunchedEffect(current.id) {
        if (tmdbId != null && tmdbMediaType != null) {
            streaming = runCatching { container.discoverService.watchProviders(tmdbMediaType, tmdbId) }.getOrDefault(emptyList())
            recommendations.clear()
            recommendations.addAll(
                runCatching { container.discoverService.recommendations(tmdbMediaType, tmdbId) }.getOrDefault(emptyList()).take(16),
            )
            if (tmdbMediaType == MediaType.MOVIES) {
                runCatching { container.discoverService.collectionParts(tmdbId) }.getOrNull()?.let { (name, parts) ->
                    val owned = container.repository.allItems().filter { !it.deleted }
                    val ownedTmdb = owned.filter { it.identifierType == "TMDB" }.mapNotNull { it.identifier }.toSet()
                    val ownedTitles = owned.map { normalizeTitle(it.title) }.toSet()
                    val missing = parts.filter { it.identifier !in ownedTmdb && normalizeTitle(it.title) !in ownedTitles }
                    if (missing.isNotEmpty()) {
                        collectionName = name
                        collectionMissing.clear()
                        collectionMissing.addAll(missing)
                    }
                }
            }
        }
    }

    VScrollColumn {
        Box(Modifier.fillMaxWidth().height(420.dp)) {
            val heroImage = backdrop ?: current.coverImage
            if (heroImage != null) {
                CoverImage(heroImage, current.title, Modifier.fillMaxSize())
            } else {
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.linearGradient(
                            listOf(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.background),
                        ),
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
            // Quick favorite toggle (top-right of the hero).
            Icon(
                imageVector = if (current.favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = if (current.favorite) "Unfavorite" else "Favorite",
                tint = if (current.favorite) MaterialTheme.colorScheme.primary else Color.White,
                modifier = Modifier.align(Alignment.TopEnd).padding(20.dp).size(30.dp)
                    .clickable { container.repository.upsertItem(current.copy(favorite = !current.favorite, updatedAt = nowMillis())) },
            )
            Column(Modifier.align(Alignment.BottomStart).padding(24.dp)) {
                val badge = when {
                    isWanted -> "WISHLIST"
                    activeLoan != null -> "ON LOAN"
                    else -> null
                }
                if (badge != null) {
                    Box(
                        Modifier.clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.primary)
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
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PillButton(
                    label = if (activeLoan == null) "Loan out" else "Manage loan",
                    icon = Icons.Filled.People,
                    background = MaterialTheme.colorScheme.onBackground,
                    foreground = Color.Black,
                ) { navigator.push(Screen.LoanItem(current.id)) }
                PillButton(
                    label = "Edit",
                    icon = Icons.Filled.Edit,
                    background = MaterialTheme.colorScheme.surfaceVariant,
                    foreground = MaterialTheme.colorScheme.onBackground,
                ) {
                    navigator.push(Screen.EditItem(current.id, current.mediaType, current.customTabId))
                }
                PillButton(
                    label = "Trailer",
                    icon = Icons.Filled.PlayCircle,
                    background = MaterialTheme.colorScheme.surfaceVariant,
                    foreground = MaterialTheme.colorScheme.onBackground,
                ) {
                    val q = listOfNotNull(current.title, current.releaseYear?.toString(), "trailer")
                        .joinToString(" ").replace(" ", "+")
                    openUrl("https://www.youtube.com/results?search_query=$q")
                }
                if (serverPlayUrl != null) {
                    PillButton(
                        label = "Open in ${serverName ?: "server"}",
                        icon = Icons.Filled.PlayCircle,
                        background = MaterialTheme.colorScheme.primary,
                        foreground = Color.Black,
                    ) { openUrl(serverPlayUrl) }
                }
                webLinkFor(current)?.let { (label, url) ->
                    PillButton(
                        label = label,
                        icon = Icons.Filled.OpenInNew,
                        background = MaterialTheme.colorScheme.surfaceVariant,
                        foreground = MaterialTheme.colorScheme.onBackground,
                    ) { openUrl(url) }
                }
                PillButton(
                    label = "Duplicate",
                    icon = Icons.Filled.ContentCopy,
                    background = MaterialTheme.colorScheme.surfaceVariant,
                    foreground = MaterialTheme.colorScheme.onBackground,
                ) {
                    val now = nowMillis()
                    val copy = current.copy(
                        id = newId(),
                        title = current.title + " (copy)",
                        coverPath = null,
                        addedAt = now,
                        updatedAt = now,
                    )
                    container.repository.upsertItem(copy)
                    navigator.push(Screen.Detail(copy.id))
                }
                PillButton(
                    label = "Delete",
                    icon = Icons.Filled.Delete,
                    background = MaterialTheme.colorScheme.surfaceVariant,
                    foreground = MaterialTheme.colorScheme.onBackground,
                ) {
                    container.repository.deleteItem(current.id)
                    navigator.pop()
                }
            }

            current.rating?.let { r ->
                Spacer(Modifier.height(16.dp))
                Box(
                    Modifier.clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.primary)
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

            if (isWanted) {
                val priority = allExtras.firstOrNull { it.first == WISH_PRIORITY_KEY }?.second
                Spacer(Modifier.height(16.dp))
                Text("Wishlist priority", color = ReliquaryMuted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WISH_PRIORITIES.forEach { p ->
                        StatusChip(p, selected = priority == p) {
                            updateExtra { m -> if (priority == p) m.remove(WISH_PRIORITY_KEY) else m[WISH_PRIORITY_KEY] = p }
                        }
                    }
                }
            }

            val myRating = allExtras.firstOrNull { it.first == MY_RATING_KEY }?.second?.toIntOrNull() ?: 0
            Spacer(Modifier.height(16.dp))
            Text("Your rating", color = ReliquaryMuted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                (1..5).forEach { star ->
                    Icon(
                        imageVector = if (star <= myRating) Icons.Filled.Star else Icons.Filled.StarBorder,
                        contentDescription = "$star star${if (star == 1) "" else "s"}",
                        tint = if (star <= myRating) MaterialTheme.colorScheme.primary else ReliquaryMuted,
                        modifier = Modifier.size(30.dp).clickable {
                            // Tap the current rating again to clear it.
                            val next = if (star == myRating) null else star.toString()
                            updateExtra { m -> if (next == null) m.remove(MY_RATING_KEY) else m[MY_RATING_KEY] = next }
                        },
                    )
                    Spacer(Modifier.width(4.dp))
                }
                if (myRating > 0) {
                    Spacer(Modifier.width(8.dp))
                    Text("$myRating/5", color = ReliquaryMuted, fontSize = 13.sp)
                }
            }

            var progCur by remember(current.id) {
                mutableStateOf(allExtras.firstOrNull { it.first == PROGRESS_KEY }?.second ?: "")
            }
            var progTotal by remember(current.id) {
                mutableStateOf(allExtras.firstOrNull { it.first == PROGRESS_TOTAL_KEY }?.second ?: "")
            }
            Spacer(Modifier.height(16.dp))
            Text("Progress", color = ReliquaryMuted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = progCur,
                    onValueChange = { input ->
                        val v = input.filter { it.isDigit() }
                        progCur = v
                        updateExtra { m -> if (v.isBlank()) m.remove(PROGRESS_KEY) else m[PROGRESS_KEY] = v }
                    },
                    modifier = Modifier.width(120.dp),
                    singleLine = true,
                    label = { Text(progressUnit(current.mediaType)) },
                )
                Text("of", color = ReliquaryMuted)
                OutlinedTextField(
                    value = progTotal,
                    onValueChange = { input ->
                        val v = input.filter { it.isDigit() }
                        progTotal = v
                        updateExtra { m -> if (v.isBlank()) m.remove(PROGRESS_TOTAL_KEY) else m[PROGRESS_TOTAL_KEY] = v }
                    },
                    modifier = Modifier.width(120.dp),
                    singleLine = true,
                    label = { Text("Total") },
                )
            }
            val cur = progCur.toIntOrNull() ?: 0
            val tot = progTotal.toIntOrNull() ?: 0
            if (tot > 0) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { (cur.toFloat() / tot).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(4.dp))
                Text("$cur / $tot", color = ReliquaryMuted, fontSize = 12.sp)
            }

            // ---- Sessions log: a history of plays / watches / reads --------
            val sessions = remember(current.extraJson) {
                SessionLog.parse(allExtras.firstOrNull { it.first == SessionLog.KEY }?.second)
            }
            var sessionNote by remember(current.id) { mutableStateOf("") }
            fun saveSessions(list: List<Session>) = updateExtra { m ->
                val enc = SessionLog.encode(list)
                if (enc == null) m.remove(SessionLog.KEY) else m[SessionLog.KEY] = enc
            }
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Sessions", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                if (sessions.isNotEmpty()) {
                    Spacer(Modifier.width(8.dp))
                    Text("${sessions.size} logged", color = ReliquaryMuted, fontSize = 13.sp)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = sessionNote,
                    onValueChange = { sessionNote = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("What happened? (optional)") },
                )
                PillButton("Log session", null, MaterialTheme.colorScheme.primary, Color.Black) {
                    saveSessions(sessions + Session(at = nowMillis(), note = sessionNote.trim()))
                    sessionNote = ""
                }
            }
            sessions.take(20).forEach { s ->
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(formatDate(s.at), color = MaterialTheme.colorScheme.onBackground, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        if (s.note.isNotBlank()) Text(s.note, color = ReliquaryMuted, fontSize = 12.sp)
                    }
                    Text(
                        "Remove",
                        color = ReliquaryMuted,
                        fontSize = 12.sp,
                        modifier = Modifier.clickable { saveSessions(sessions - s) }.padding(start = 8.dp),
                    )
                }
            }

            val series = allExtras.firstOrNull { it.first == SERIES_KEY }?.second
            if (!series.isNullOrBlank()) {
                Spacer(Modifier.height(12.dp))
                Text("Series", color = ReliquaryMuted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Row { StatusChip(series, selected = false) { navigator.push(Screen.SeriesItems(series)) } }
            }

            val tags = parseTags(current.tags)
            if (tags.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text("Tags", color = ReliquaryMuted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    tags.forEach { tag ->
                        StatusChip(tag, selected = false) { navigator.push(Screen.TagItems(tag)) }
                    }
                }
            }

            if (activeLoan != null) {
                Spacer(Modifier.height(16.dp))
                val due = activeLoan.dueAt?.let { " · due ${formatDate(it)}" } ?: ""
                Text(
                    "Loaned to ${borrowerName ?: "someone"}$due",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            val photos = allExtras.firstOrNull { it.first == "_photos" }?.second
                ?.let { runCatching { ReliquaryJson.decodeFromString<List<String>>(it) }.getOrNull() } ?: emptyList()
            fun setPhotos(list: List<String>) = updateExtra { m ->
                if (list.isEmpty()) m.remove("_photos") else m["_photos"] = ReliquaryJson.encodeToString(list)
            }
            if (photos.isNotEmpty() || isDesktopPlatform()) {
                Spacer(Modifier.height(20.dp))
                Text("Your Photos", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    photos.forEach { path ->
                        Column(Modifier.width(110.dp)) {
                            Box {
                                CoverImage(path, "Photo", Modifier.width(110.dp).height(150.dp).clip(RoundedCornerShape(8.dp)))
                                Box(
                                    Modifier.align(Alignment.TopEnd).padding(4.dp).clip(RoundedCornerShape(50))
                                        .background(Color(0xCC000000)).clickable { setPhotos(photos - path) }
                                        .padding(horizontal = 7.dp, vertical = 2.dp),
                                ) {
                                    Text("✕", color = Color.White, fontSize = 12.sp)
                                }
                            }
                            Text(
                                "Set as cover",
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.clickable {
                                    container.repository.upsertItem(current.copy(coverUrl = path, coverPath = null, updatedAt = nowMillis()))
                                    // Pin it so a later metadata refresh won't replace the chosen photo.
                                    container.repository.setExtra(current.id, COVER_LOCK_KEY, "1")
                                }.padding(top = 4.dp),
                            )
                        }
                    }
                    if (isDesktopPlatform()) {
                        Box(
                            Modifier.width(110.dp).height(150.dp).clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { pickAndStoreImage()?.let { setPhotos(photos + it) } },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("+ Add photo", color = ReliquaryMuted, fontSize = 13.sp)
                        }
                    }
                }
            }

            current.description?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(20.dp))
                Text(it, color = MaterialTheme.colorScheme.onBackground, fontSize = 15.sp, lineHeight = 22.sp)
            }

            if (streaming.isNotEmpty()) {
                Spacer(Modifier.height(18.dp))
                Text("Streaming on", color = ReliquaryMuted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(streaming.joinToString(" · "), color = MaterialTheme.colorScheme.onBackground, fontSize = 14.sp)
            }

            if (collectionMissing.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                Text("Complete the set: ${collectionName ?: ""}", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    collectionMissing.forEach { part ->
                        Column(Modifier.width(110.dp).clickable {
                            val now = nowMillis()
                            container.repository.importOrUpdate(part.toCollectionItem().copy(wanted = true, addedAt = now, updatedAt = now))
                        }) {
                            CoverImage(part.coverUrl, part.title, Modifier.width(110.dp).height(165.dp).clip(RoundedCornerShape(8.dp)))
                            Spacer(Modifier.height(4.dp))
                            Text(part.title, color = MaterialTheme.colorScheme.onBackground, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        }
                    }
                }
                Text("Missing from your collection — tap to add to wishlist.", color = ReliquaryMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
            }

            if (recommendations.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                Text("Because you own this", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    recommendations.forEach { rec ->
                        Column(Modifier.width(110.dp).clickable {
                            val now = nowMillis()
                            container.repository.importOrUpdate(rec.toCollectionItem().copy(wanted = true, addedAt = now, updatedAt = now))
                        }) {
                            CoverImage(rec.coverUrl, rec.title, Modifier.width(110.dp).height(165.dp).clip(RoundedCornerShape(8.dp)))
                            Spacer(Modifier.height(4.dp))
                            Text(rec.title, color = MaterialTheme.colorScheme.onBackground, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        }
                    }
                }
                Text("Tap a title to add it to your wishlist.", color = ReliquaryMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
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

            val editions = allExtras.firstOrNull { it.first == "_editions" }?.second
                ?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
            if (editions.isNotEmpty()) {
                Spacer(Modifier.height(18.dp))
                Text("Editions owned", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(Modifier.height(6.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    editions.forEach { StatusChip(it, selected = false) {} }
                }
            }

            val valueHistory = allExtras.firstOrNull { it.first == "_valueHistory" }?.second
                ?.let { runCatching { ReliquaryJson.decodeFromString<List<ValuePoint>>(it) }.getOrNull() } ?: emptyList()
            if (valueHistory.size >= 2) {
                val maxV = valueHistory.maxOf { it.value }.coerceAtLeast(0.01)
                Spacer(Modifier.height(18.dp))
                Text("Value over time", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(Modifier.height(6.dp))
                valueHistory.takeLast(10).forEach { pt ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(formatDate(pt.day * DAY_MILLIS), color = ReliquaryMuted, fontSize = 13.sp, modifier = Modifier.width(96.dp))
                        Box(Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                            Box(Modifier.fillMaxWidth((pt.value / maxV).toFloat().coerceIn(0f, 1f)).height(10.dp).clip(RoundedCornerShape(5.dp)).background(MaterialTheme.colorScheme.primary))
                        }
                        Text("$" + (kotlin.math.round(pt.value * 100) / 100.0), color = ReliquaryMuted, fontSize = 13.sp, modifier = Modifier.width(72.dp).padding(start = 8.dp))
                    }
                }
            }

            if (customExtras.isNotEmpty()) {
                Spacer(Modifier.height(18.dp))
                Text(
                    "Custom Fields",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                )
                Spacer(Modifier.height(6.dp))
                customExtras.forEach { (label, value) -> MetaRow(label.removePrefix("cf:"), value) }
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
            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            label,
            color = if (selected) Color.Black else MaterialTheme.colorScheme.onSurface,
            fontSize = 13.sp,
            lineHeight = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

private fun normalizeTitle(s: String): String = s.lowercase().filter { it.isLetterOrDigit() }

/** A provider page link for an item, when we can build one from its identifier. */
private fun webLinkFor(item: CollectionItem): Pair<String, String>? {
    val id = item.identifier?.takeIf { it.isNotBlank() } ?: return null
    return when (item.identifierType) {
        "TMDB" -> {
            val seg = if (item.mediaType == MediaType.TV.name || item.mediaType == MediaType.ANIME.name) "tv" else "movie"
            "View on TMDB" to "https://www.themoviedb.org/$seg/$id"
        }
        "Simkl" -> {
            val seg = when (item.mediaType) {
                MediaType.TV.name -> "tv"
                MediaType.ANIME.name -> "anime"
                else -> "movies"
            }
            "View on Simkl" to "https://simkl.com/$seg/$id"
        }
        "OpenLibrary" -> "View on Open Library" to ("https://openlibrary.org" + if (id.startsWith("/")) id else "/$id")
        else -> null
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
