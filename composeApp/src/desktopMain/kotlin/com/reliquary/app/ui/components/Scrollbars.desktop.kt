package com.reliquary.app.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.reliquary.app.ui.theme.ReliquaryMuted

@Composable
private fun scrollbarStyle(): ScrollbarStyle = ScrollbarStyle(
    minimalHeight = 24.dp,
    thickness = 8.dp,
    shape = RoundedCornerShape(4.dp),
    hoverDurationMillis = 300,
    unhoverColor = ReliquaryMuted.copy(alpha = 0.35f),
    hoverColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
)

@Composable
actual fun BoxScope.VScrollbar(state: ScrollState) {
    VerticalScrollbar(
        adapter = rememberScrollbarAdapter(state),
        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(2.dp),
        style = scrollbarStyle(),
    )
}

@Composable
actual fun BoxScope.VScrollbar(state: LazyListState) {
    VerticalScrollbar(
        adapter = rememberScrollbarAdapter(state),
        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(2.dp),
        style = scrollbarStyle(),
    )
}

@Composable
actual fun BoxScope.VScrollbar(state: LazyGridState) {
    VerticalScrollbar(
        adapter = rememberScrollbarAdapter(state),
        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(2.dp),
        style = scrollbarStyle(),
    )
}
