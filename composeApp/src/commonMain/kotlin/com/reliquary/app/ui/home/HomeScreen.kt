package com.reliquary.app.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reliquary.app.di.AppContainer
import com.reliquary.app.domain.CollectionItem
import com.reliquary.app.domain.Status
import com.reliquary.app.domain.wishPriorityRank
import com.reliquary.app.tools.RecentlyViewed
import com.reliquary.app.ui.Navigator
import com.reliquary.app.ui.Screen
import com.reliquary.app.ui.components.CoverImage
import com.reliquary.app.ui.components.VScrollColumn
import com.reliquary.app.ui.theme.ReliquaryMuted

/** All Home rails, in display order. Settings lets the user hide any of them. */
val HOME_RAILS = listOf(
    "Continue", "Recently finished", "Due back", "Recently viewed",
    "Recently added", "Favorites", "Top of your wishlist", "Rediscover",
)

const val HOME_HIDDEN_RAILS_SETTING = "home.hiddenRails"

private fun priorityOf(item: CollectionItem): String? =
    item.extraJson?.let { Regex("\"_wishPriority\"\\s*:\\s*\"([^\"]+)\"").find(it)?.groupValues?.get(1) }

private fun finishedAtOf(item: CollectionItem): Long? =
    item.extraJson?.let { Regex("\"_finishedAt\"\\s*:\\s*\"?(\\d+)\"?").find(it)?.groupValues?.get(1)?.toLongOrNull() }

@Composable
fun HomeScreen(container: AppContainer, navigator: Navigator) {
    val items = remember { container.repository.allItems().filter { !it.deleted } }
    val loans = remember { container.repository.activeLoansNow() }
    val byId = remember(items) { items.associateBy { it.id } }
    val owned = items.filter { !it.wanted }

    val continueItems = owned.filter { it.status in Status.IN_PROGRESS }
    val recentlyFinished = owned.mapNotNull { item -> finishedAtOf(item)?.let { item to it } }
        .sortedByDescending { it.second }.map { it.first }.take(16)
    val dueBack = loans.filter { it.dueAt != null }.sortedBy { it.dueAt }.mapNotNull { byId[it.itemId] }
    val recentlyViewed = RecentlyViewed.load(container.repository).mapNotNull { byId[it] }
    val recentlyAdded = owned.sortedByDescending { it.addedAt }.take(16)
    val favorites = items.filter { it.favorite }
    val wishlist = items.filter { it.wanted }.sortedBy { wishPriorityRank(priorityOf(it)) }
    val rediscover = remember(items) { owned.shuffled().take(16) }
    val hidden = remember {
        container.repository.getSetting(HOME_HIDDEN_RAILS_SETTING)?.split(",")?.map { it.trim() }?.toSet() ?: emptySet()
    }

    VScrollColumn(contentPadding = PaddingValues(top = 8.dp, bottom = 28.dp)) {
        Text(
            "Home",
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
            modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 4.dp),
        )

        Rail("Continue", continueItems, navigator, hidden)
        Rail("Recently finished", recentlyFinished, navigator, hidden)
        Rail("Due back", dueBack, navigator, hidden)
        Rail("Recently viewed", recentlyViewed, navigator, hidden)
        Rail("Recently added", recentlyAdded, navigator, hidden)
        Rail("Favorites", favorites, navigator, hidden)
        Rail("Top of your wishlist", wishlist, navigator, hidden)
        Rail("Rediscover", rediscover, navigator, hidden)

        if (items.isEmpty()) {
            Text(
                "Your collection is empty — add something from a category tab to get started.",
                color = ReliquaryMuted,
                fontSize = 14.sp,
                modifier = Modifier.padding(20.dp),
            )
        }
    }
}

@Composable
private fun Rail(title: String, items: List<CollectionItem>, navigator: Navigator, hidden: Set<String> = emptySet()) {
    if (items.isEmpty() || title in hidden) return
    Column(Modifier.fillMaxWidth()) {
        Text(
            title,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 8.dp),
        )
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items.forEach { item ->
                Column(Modifier.width(120.dp).clickable { navigator.push(Screen.Detail(item.id)) }) {
                    CoverImage(
                        url = item.coverImage,
                        contentDescription = item.title,
                        modifier = Modifier.width(120.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(8.dp)),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        item.title,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
