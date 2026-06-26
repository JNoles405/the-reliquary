package com.reliquary.app.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable

@Composable actual fun BoxScope.VScrollbar(state: ScrollState) = Unit

@Composable actual fun BoxScope.VScrollbar(state: LazyListState) = Unit

@Composable actual fun BoxScope.VScrollbar(state: LazyGridState) = Unit
