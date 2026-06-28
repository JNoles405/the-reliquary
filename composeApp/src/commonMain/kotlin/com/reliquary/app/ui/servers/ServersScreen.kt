package com.reliquary.app.ui.servers

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reliquary.app.di.AppContainer
import com.reliquary.app.servers.ServerConfig
import com.reliquary.app.servers.ServerLibrary
import com.reliquary.app.servers.ServerType
import com.reliquary.app.ui.Navigator
import com.reliquary.app.ui.components.PillButton
import com.reliquary.app.ui.components.VScrollColumn
import com.reliquary.app.ui.theme.ReliquaryMuted
import kotlinx.coroutines.launch

@Composable
fun ServersScreen(container: AppContainer, navigator: Navigator) {
    val svc = container.mediaServerService
    val scope = rememberCoroutineScope()
    var servers by remember { mutableStateOf(svc.connections()) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    // Browsing state for one server at a time.
    var browsing by remember { mutableStateOf<ServerConfig?>(null) }
    var libraries by remember { mutableStateOf<List<ServerLibrary>>(emptyList()) }

    fun refresh() { servers = svc.connections() }

    VScrollColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Media Servers", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 24.sp)
        Text(
            "Connect Plex or Jellyfin to import your library — posters, backdrops, watched " +
                "status and resume progress — and to open titles back on the server.",
            color = ReliquaryMuted,
            fontSize = 13.sp,
        )

        message?.let {
            Text(it, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }

        // Connected servers.
        servers.forEach { cfg ->
            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(cfg.name, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(cfg.url, color = ReliquaryMuted, fontSize = 12.sp)
                    }
                    Text(cfg.type.label, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PillButton("Browse & import", null, MaterialTheme.colorScheme.primary, Color.Black) {
                        if (busy) return@PillButton
                        busy = true; message = null; libraries = emptyList(); browsing = cfg
                        scope.launch {
                            libraries = runCatching { svc.libraries(cfg) }.getOrDefault(emptyList())
                            busy = false
                            if (libraries.isEmpty()) message = "No libraries found (check the connection)."
                        }
                    }
                    PillButton("Remove", null, MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onBackground) {
                        svc.removeConnection(cfg)
                        if (browsing == cfg) { browsing = null; libraries = emptyList() }
                        refresh()
                    }
                }

                // Library list for the server being browsed.
                if (browsing == cfg && libraries.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text("Libraries", color = ReliquaryMuted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    libraries.forEach { lib ->
                        val supported = lib.mediaType != null
                        Row(
                            Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable(enabled = supported && !busy) {
                                    busy = true; message = null
                                    scope.launch {
                                        val items = runCatching { svc.items(cfg, lib) }.getOrDefault(emptyList())
                                        val n = if (items.isNotEmpty()) svc.importItems(cfg, items) else 0
                                        busy = false
                                        message = if (n > 0) "Imported $n from \"${lib.title}\"." else "Nothing to import from \"${lib.title}\"."
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(lib.title, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text(
                                    lib.mediaType?.displayName ?: "Unsupported type — skipped",
                                    color = ReliquaryMuted,
                                    fontSize = 12.sp,
                                )
                            }
                            if (supported) Text("Import →", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }
        }

        if (busy) Text("Working…", color = ReliquaryMuted, fontSize = 13.sp)

        JellyfinConnect(enabled = !busy) { url, user, pass ->
            busy = true; message = null
            scope.launch {
                svc.connectJellyfin(url, user, pass)
                    .onSuccess { refresh(); message = "Connected to ${it.name}." }
                    .onFailure { message = it.message ?: "Could not connect." }
                busy = false
            }
        }

        PlexConnect(enabled = !busy) { url, token ->
            busy = true; message = null
            scope.launch {
                svc.connectPlex(url, token)
                    .onSuccess { refresh(); message = "Connected to Plex." }
                    .onFailure { message = it.message ?: "Could not connect." }
                busy = false
            }
        }
    }
}

@Composable
private fun JellyfinConnect(enabled: Boolean, onConnect: (url: String, user: String, pass: String) -> Unit) {
    var url by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    Card {
        Text("Add Jellyfin", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text("Sign in with your Jellyfin username and password. Only the access token is stored.", color = ReliquaryMuted, fontSize = 12.sp)
        Spacer(Modifier.height(10.dp))
        Field("Server URL (e.g. http://192.168.1.10:8096)", url) { url = it }
        Field("Username", user) { user = it }
        Field("Password", pass, isPassword = true) { pass = it }
        Spacer(Modifier.height(4.dp))
        PillButton("Connect Jellyfin", null, MaterialTheme.colorScheme.primary, Color.Black) {
            if (enabled && url.isNotBlank() && user.isNotBlank()) onConnect(url, user, pass)
        }
    }
}

@Composable
private fun PlexConnect(enabled: Boolean, onConnect: (url: String, token: String) -> Unit) {
    var url by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    Card {
        Text("Add Plex", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text(
            "Enter your server URL and an X-Plex-Token. Find a token via Plex support's " +
                "\"Finding an authentication token\" article.",
            color = ReliquaryMuted,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(10.dp))
        Field("Server URL (e.g. http://192.168.1.10:32400)", url) { url = it }
        Field("X-Plex-Token", token, isPassword = true) { token = it }
        Spacer(Modifier.height(4.dp))
        PillButton("Connect Plex", null, MaterialTheme.colorScheme.primary, Color.Black) {
            if (enabled && url.isNotBlank() && token.isNotBlank()) onConnect(url, token)
        }
    }
}

@Composable
private fun Card(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp)) {
        Column(content = content)
    }
}

@Composable
private fun Field(label: String, value: String, isPassword: Boolean = false, onValue: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text(label) },
        visualTransformation = if (isPassword) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
    )
    Spacer(Modifier.height(8.dp))
}
