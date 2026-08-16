package me.misa198.airmedy.ui.components

import androidx.compose.foundation.background
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.collectIsDraggedAsState
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
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
    filterKey: String? = null,
    filterActive: Boolean = false,
    filterContent: (@Composable (showPlaceholderAndLeadingSymbol: Boolean) -> Unit)? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    emptyContent: @Composable () -> Unit,
    itemContent: @Composable (T) -> Unit,
) {
    var filterVisible by rememberSaveable(filterKey) { mutableStateOf(false) }
    var revealDistancePx by remember(filterKey) { mutableFloatStateOf(0f) }
    var hideDistancePx by remember(filterKey) { mutableFloatStateOf(0f) }
    val filterThresholdPx = with(LocalDensity.current) { 48.dp.toPx() }
    val isUserDragging by listState.interactionSource.collectIsDraggedAsState()

    LaunchedEffect(filterActive) {
        if (filterActive) {
            filterVisible = true
            hideDistancePx = 0f
        }
    }
    LaunchedEffect(isUserDragging, filterActive) {
        if (!isUserDragging) {
            if (!filterVisible && !filterActive) revealDistancePx = 0f
            if (filterVisible && !filterActive) hideDistancePx = 0f
        }
    }

    val filterScrollConnection = remember(listState, filterActive, filterContent, filterThresholdPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput || filterContent == null) return Offset.Zero
                if (!filterVisible && listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0 && available.y > 0f) {
                    revealDistancePx += available.y
                    if (revealDistancePx >= filterThresholdPx) {
                        filterVisible = true
                        hideDistancePx = 0f
                        revealDistancePx = 0f
                    }
                    return Offset(0f, available.y)
                } else if (!filterVisible && revealDistancePx > 0f && available.y < 0f) {
                    val consumedY = maxOf(available.y, -revealDistancePx)
                    revealDistancePx += consumedY
                    return Offset(0f, consumedY)
                } else if (filterVisible && !filterActive && available.y < 0f) {
                    hideDistancePx = (hideDistancePx - available.y).coerceAtMost(filterThresholdPx)
                    if (hideDistancePx >= filterThresholdPx) {
                        filterVisible = false
                    }
                    return Offset(0f, available.y)
                } else if (filterVisible && !filterActive && hideDistancePx > 0f && available.y > 0f) {
                    val consumedY = minOf(available.y, hideDistancePx)
                    hideDistancePx -= consumedY
                    return Offset(0f, consumedY)
                } else if (available.y < 0f) {
                    revealDistancePx = 0f
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput || filterContent == null) return Offset.Zero
                return Offset.Zero
            }
        }
    }

    val colors = LocalAirmedyColors.current
    if (filterContent != null) {
        // Keep the filter in a stable lazy item while its query changes the
        // result set. Replacing the entire list when there are no matches
        // disposes BasicTextField and drops its focus.
        Box(modifier = modifier.fillMaxSize().nestedScroll(filterScrollConnection)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = contentPadding,
            ) {
                item(key = "library-list-text-filter", contentType = "library-list-text-filter") {
                    LibraryTextFilterSlot(
                        visible = filterVisible,
                        dismissalProgressPx = hideDistancePx,
                        previewHeightPx = revealDistancePx,
                        isUserDragging = isUserDragging,
                        content = filterContent,
                    )
                }
                if (items.isEmpty()) {
                    item(key = "library-list-empty-state", contentType = "library-list-empty-state") {
                        Box(modifier = Modifier.fillMaxWidth().height(320.dp)) { emptyContent() }
                    }
                } else {
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
                            LibraryListDivider(dividerTestTag, colors)
                        }
                    }
                }
            }
        }
        return
    }

    if (items.isEmpty()) {
        Box(modifier = modifier.fillMaxSize()) { emptyContent() }
        return
    }

    Box(modifier = modifier.fillMaxSize().nestedScroll(filterScrollConnection)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
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
                    LibraryListDivider(dividerTestTag, colors)
                }
            }
        }
    }
}

@Composable
private fun LibraryListDivider(
    testTag: String?,
    colors: me.misa198.airmedy.ui.theme.AirmedyColors,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .height(1.dp)
            .background(colors.borderGlass)
            .then(if (testTag == null) Modifier else Modifier.testTag(testTag)),
    )
}

@Composable
private fun LibraryTextFilterSlot(
    visible: Boolean,
    dismissalProgressPx: Float = 0f,
    previewHeightPx: Float = 0f,
    isUserDragging: Boolean = false,
    content: @Composable (showPlaceholderAndLeadingSymbol: Boolean) -> Unit,
) {
    val density = LocalDensity.current
    val previewHeight = with(density) { previewHeightPx.coerceIn(0f, LibraryTextFilterHeight.toPx()).toDp() }
    val dismissalProgress = with(density) { dismissalProgressPx.coerceIn(0f, LibraryTextFilterHeight.toPx()).toDp() }
    val targetHeight = libraryTextFilterSlotTargetHeight(
        visible = visible,
        dismissalProgress = dismissalProgress,
        previewHeight = previewHeight,
    )
    val displayedHeight by animateDpAsState(
        targetValue = targetHeight,
        animationSpec = if (
            libraryTextFilterSlotUsesDragSnap(
                isUserDragging = isUserDragging,
                visible = visible,
                dismissalProgressPx = dismissalProgressPx,
                previewHeightPx = previewHeightPx,
            )
        ) snap() else tween(180),
        label = "library-text-filter-height",
    )
    val isFullyOpen = visible && dismissalProgressPx == 0f && displayedHeight == LibraryTextFilterHeight
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(displayedHeight)
            .clipToBounds(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            content(isFullyOpen)
        }
    }
}

private val LibraryTextFilterHeight = 62.dp

internal fun libraryTextFilterSlotTargetHeight(
    visible: Boolean,
    dismissalProgress: Dp,
    previewHeight: Dp,
): Dp = when {
    visible -> (LibraryTextFilterHeight - dismissalProgress).coerceAtLeast(0.dp)
    // A closed filter must occupy no space. Retaining a clipped tail of the
    // text field exposes its bottom edge as a stray divider.
    else -> previewHeight
}

internal fun libraryTextFilterSlotUsesDragSnap(
    isUserDragging: Boolean,
    visible: Boolean,
    dismissalProgressPx: Float,
    previewHeightPx: Float,
): Boolean = isUserDragging && (
    previewHeightPx > 0f || (visible && dismissalProgressPx > 0f)
)
