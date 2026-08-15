package me.misa198.airmedy.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import me.misa198.airmedy.ui.theme.LocalAirmedyColors

/** Shared virtualized, divided list treatment for Library entity pages. */
@Composable
fun <T> LibraryVirtualList(
    items: List<T>,
    key: (T) -> Any,
    contentType: Any,
    listState: LazyListState,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    dividerTestTag: String? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    emptyContent: @Composable () -> Unit,
    itemContent: @Composable (T) -> Unit,
) {
    if (items.isEmpty()) {
        Box(modifier = modifier.fillMaxSize()) { emptyContent() }
        return
    }

    val colors = LocalAirmedyColors.current
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = listState,
        contentPadding = contentPadding,
    ) {
        if (leadingContent != null) {
            item(contentType = "library-list-leading-content") { leadingContent() }
        }
        itemsIndexed(
            items = items,
            key = { _, item -> key(item) },
            contentType = { _, _ -> contentType },
        ) { index, item ->
            itemContent(item)
            if (index < items.lastIndex) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .height(1.dp)
                        .background(colors.borderGlass)
                        .then(if (dividerTestTag == null) Modifier else Modifier.testTag(dividerTestTag)),
                )
            }
        }
    }
}
