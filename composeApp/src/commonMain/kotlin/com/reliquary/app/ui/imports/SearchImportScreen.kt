package com.reliquary.app.ui.imports

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reliquary.app.di.AppContainer
import com.reliquary.app.domain.MediaType
import com.reliquary.app.metadata.MetadataResult
import com.reliquary.app.ui.Navigator
import com.reliquary.app.ui.Screen
import com.reliquary.app.ui.components.CoverImage
import com.reliquary.app.ui.components.PillButton
import com.reliquary.app.ui.theme.ReliquaryMuted
import com.reliquary.app.ui.theme.ReliquaryRed
import com.reliquary.app.ui.theme.ReliquarySurface
import com.reliquary.app.ui.theme.ReliquarySurfaceVariant
import kotlinx.coroutines.launch

@Composable
fun SearchImportScreen(
    container: AppContainer,
    mediaType: MediaType,
    customTabId: String?,
    navigator: Navigator,
) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<MetadataResult>>(emptyList()) }
    var message by remember { mutableStateOf<String?>(null) }
    val hasProviders = container.metadataService.hasProviderFor(mediaType)

    fun run(byBarcode: Boolean) {
        val q = query.trim()
        if (q.isEmpty()) return
        loading = true
        message = null
        results = emptyList()
        scope.launch {
            val found = if (byBarcode) {
                container.metadataService.lookupByBarcode(mediaType, q)
            } else {
                container.metadataService.search(mediaType, q)
            }
            results = found
            loading = false
            if (found.isEmpty()) message = "No results found."
        }
    }

    fun import(result: MetadataResult) {
        val item = result.toCollectionItem(
            barcode = result.identifier?.takeIf { result.identifierType != null },
            customTabId = customTabId,
        )
        container.repository.upsertItem(item)
        navigator.pop()
        navigator.push(Screen.Detail(item.id))
    }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text(
            text = "Import ${mediaType.displayName}",
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
        )
        Spacer(Modifier.height(12.dp))

        if (!hasProviders) {
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                    .background(ReliquarySurface).padding(14.dp),
            ) {
                Column {
                    Text(
                        "No active lookup provider for ${mediaType.displayName} yet.",
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Add a free API key in Settings to enable automatic lookups, or add this item manually.",
                        color = ReliquaryMuted,
                        fontSize = 13.sp,
                    )
                    Spacer(Modifier.height(10.dp))
                    PillButton(
                        label = "Add manually",
                        icon = null,
                        background = ReliquaryRed,
                        foreground = MaterialTheme.colorScheme.onBackground,
                    ) { navigator.push(Screen.EditItem(null, mediaType.name, customTabId)) }
                }
            }
            Spacer(Modifier.height(14.dp))
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Title, ISBN, or barcode") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PillButton(
                label = "Search",
                icon = Icons.Filled.Search,
                background = ReliquaryRed,
                foreground = MaterialTheme.colorScheme.onBackground,
                onClick = { run(byBarcode = false) },
            )
            PillButton(
                label = "Look up barcode",
                icon = null,
                background = ReliquarySurfaceVariant,
                foreground = MaterialTheme.colorScheme.onBackground,
                onClick = { run(byBarcode = true) },
            )
        }
        Spacer(Modifier.height(16.dp))

        when {
            loading -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = ReliquaryRed)
            }
            message != null -> Text(message!!, color = ReliquaryMuted)
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(results) { result -> ResultRow(result) { import(result) } }
        }
    }
}

@Composable
private fun ResultRow(result: MetadataResult, onImport: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .background(ReliquarySurface).clickable(onClick = onImport).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverImage(
            url = result.coverUrl,
            contentDescription = result.title,
            modifier = Modifier.size(width = 54.dp, height = 80.dp).clip(RoundedCornerShape(4.dp)),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                result.title,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val sub = listOfNotNull(result.creators, result.releaseYear?.toString()).joinToString(" · ")
            if (sub.isNotBlank()) {
                Text(sub, color = ReliquaryMuted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(result.providerName, color = ReliquaryRed, fontSize = 11.sp)
        }
    }
}
