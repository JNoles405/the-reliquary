package com.reliquary.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.reliquary.app.domain.MediaType

/** The screens the app can show. Kept deliberately small — a list back stack. */
sealed interface Screen {
    data object Library : Screen
    data class Detail(val itemId: String) : Screen
    data class SearchImport(val mediaType: MediaType, val customTabId: String?) : Screen
    data class EditItem(val itemId: String?, val mediaTypeName: String, val customTabId: String?) : Screen
    data class LoanItem(val itemId: String) : Screen
    data object Loans : Screen
    data object CustomTabs : Screen
    data object Sync : Screen
    data object Search : Screen
    data object Csv : Screen
    data object Stats : Screen
    data object Settings : Screen
}

class Navigator(initial: Screen) {
    var stack by mutableStateOf(listOf(initial))
        private set

    val current: Screen get() = stack.last()
    val canGoBack: Boolean get() = stack.size > 1

    fun push(screen: Screen) { stack = stack + screen }
    fun pop() { if (stack.size > 1) stack = stack.dropLast(1) }

    /** Clear the back stack to a single root screen (used when switching tabs). */
    fun resetTo(screen: Screen) { stack = listOf(screen) }
}

@Composable
fun rememberNavigator(): Navigator = remember { Navigator(Screen.Library) }
