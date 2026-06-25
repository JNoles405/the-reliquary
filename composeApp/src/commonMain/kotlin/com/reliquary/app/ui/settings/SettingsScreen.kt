package com.reliquary.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reliquary.app.di.AppContainer
import com.reliquary.app.ui.Navigator
import com.reliquary.app.ui.theme.ReliquaryMuted

@Composable
fun SettingsScreen(container: AppContainer, navigator: Navigator) {
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text(
            "Settings",
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "API keys for TMDB (movies), IGDB (games), ComicVine (comics), and " +
                "Discogs (music) will be configured here to unlock automatic lookups " +
                "for those categories. Coming in the next update.",
            color = ReliquaryMuted,
            fontSize = 14.sp,
        )
    }
}
