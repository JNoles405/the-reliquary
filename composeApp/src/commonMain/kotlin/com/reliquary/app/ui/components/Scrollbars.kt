package com.reliquary.app.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable

/**
 * Themed vertical scrollbar over a scroll container. Rendered on desktop (inside
 * a Box, aligned to the trailing edge); a no-op on Android, which scrolls natively.
 */
@Composable
expect fun BoxScope.VScrollbar(state: ScrollState)

@Composable
expect fun BoxScope.VScrollbar(state: LazyListState)

@Composable
expect fun BoxScope.VScrollbar(state: LazyGridState)
