package me.misa198.airmedy.ui.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp

private val FullScreenPlayerCompactArtworkSize = 80.dp
private val FullScreenPlayerCompactGap = 24.dp

/** Phone-only geometry. State, effects and playback actions belong to the player host. */
@Composable
internal fun PhonePlayerLayout(
    isPanelOpen: Boolean,
    isQueueReordering: Boolean,
    artworkScale: Float,
    artworkTransformOrigin: Float,
    horizontalSwipeState: FullScreenPlayerSwipeState,
    displayedHorizontalSwipeOffset: Float,
    canNavigatePrevious: Boolean,
    canNavigateNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onClosePanel: () -> Unit,
    artwork: @Composable (Modifier) -> Unit,
    metadata: @Composable (Boolean) -> Unit,
    panelContent: @Composable (androidx.compose.ui.unit.Dp) -> Unit,
    controls: @Composable (Modifier) -> Unit,
) {
    BoxWithConstraints(
        Modifier.fillMaxSize().safeDrawingPadding().padding(bottom = 16.dp)
            .semantics { testTag = "phone_player_layout" },
    ) {
        val density = LocalDensity.current
        var controlsHeightPx by remember { mutableIntStateOf(0) }
        val controlsHeight = if (controlsHeightPx > 0) with(density) { controlsHeightPx.toDp() } else 300.dp
        val restingTopBlockHeight = (maxHeight - controlsHeight - 32.dp).coerceAtLeast(0.dp)
        var metadataHeightPx by remember { mutableIntStateOf(0) }
        val metadataHeight = if (metadataHeightPx > 0) with(density) { metadataHeightPx.toDp() } else 64.dp
        val artworkAreaHeight = (restingTopBlockHeight - metadataHeight - 16.dp).coerceAtLeast(0.dp)
        // Leave metadata next to seek and center the cover in the space above it.
        val expandedArtworkSize = minOf(
            maxWidth - 40.dp,
            artworkAreaHeight,
        )
        val compactMetadataWidth = maxWidth - 20.dp
        val queuePanelWidth = maxWidth
        val artworkSize by animateDpAsState(
            targetValue = if (isPanelOpen) FullScreenPlayerCompactArtworkSize else expandedArtworkSize,
            animationSpec = tween(320, easing = FastOutSlowInEasing),
            label = "full-screen-artwork-size",
        )
        val artworkHorizontalOffset by animateDpAsState(
            targetValue = if (isPanelOpen) 0.dp else ((maxWidth - 40.dp) - expandedArtworkSize) / 2,
            animationSpec = tween(320, easing = FastOutSlowInEasing),
            label = "full-screen-artwork-horizontal-offset",
        )
        val artworkVerticalOffset by animateDpAsState(
            targetValue = if (!isPanelOpen) (artworkAreaHeight - expandedArtworkSize) / 2 else 0.dp,
            animationSpec = tween(320, easing = FastOutSlowInEasing),
            label = "full-screen-artwork-vertical-offset",
        )
        // Keep the top block's expanded footprint reserved while a panel is open.
        // During a queue reorder, it temporarily grows through the controls area
        // so the list can use the complete safe fullscreen height.
        val topBlockHeight by animateDpAsState(
            targetValue = if (isQueueReordering) maxHeight else restingTopBlockHeight,
            animationSpec = tween(QueueReorderTransitionDurationMs, easing = FastOutSlowInEasing),
            label = "full-screen-queue-reorder-height",
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            FullScreenPlayerDragHandle()
            Spacer(Modifier.height(20.dp))
            FullScreenPlayerSwipeTarget(
                testTag = FullScreenPlayerArtworkSwipeTestTag,
                swipeState = horizontalSwipeState,
                displayedOffset = displayedHorizontalSwipeOffset,
                onPrevious = onPrevious,
                onNext = onNext,
                canNavigatePrevious = canNavigatePrevious,
                canNavigateNext = canNavigateNext,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(topBlockHeight),
                ) {
                    artwork(
                        Modifier.size(artworkSize).offset(x = artworkHorizontalOffset, y = artworkVerticalOffset)
                            .semantics { testTag = FullScreenPlayerArtworkTestTag }
                            .then(if (isPanelOpen) Modifier.clickable(
                                onClick = onClosePanel, role = Role.Button,
                                interactionSource = remember { MutableInteractionSource() }, indication = null,
                            ) else Modifier)
                            .graphicsLayer {
                                scaleX = artworkScale
                                scaleY = artworkScale
                                transformOrigin = TransformOrigin(artworkTransformOrigin, artworkTransformOrigin)
                            },
                    )
                    androidx.compose.animation.AnimatedVisibility(
                        visible = !isPanelOpen,
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .semantics { testTag = "phone_player_expanded_metadata" }
                            .onSizeChanged { metadataHeightPx = it.height }
                            .padding(bottom = 16.dp),
                        enter = fadeIn(tween(160)) + slideInVertically(tween(160)) { 10 },
                        exit = fadeOut(tween(120)) + slideOutVertically(tween(120)) { -10 },
                    ) {
                        metadata(false)
                    }
                    androidx.compose.animation.AnimatedVisibility(
                        visible = isPanelOpen,
                        modifier = Modifier
                            // Extend only the compact metadata into the outer
                            // column's right inset, leaving it flush to screen edge.
                            .requiredWidth(compactMetadataWidth)
                            .padding(start = FullScreenPlayerCompactArtworkSize + FullScreenPlayerCompactGap)
                            .height(FullScreenPlayerCompactArtworkSize),
                        enter = fadeIn(tween(200, delayMillis = 120)) + slideInVertically(tween(200, delayMillis = 120)) { 12 },
                        exit = fadeOut(tween(120)) + slideOutVertically(tween(120)) { -10 },
                    ) {
                        metadata(true)
                    }
                    androidx.compose.animation.AnimatedVisibility(
                        visible = isPanelOpen,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = FullScreenPlayerCompactArtworkSize + 8.dp),
                        enter = fadeIn(tween(200, delayMillis = 120)) + slideInVertically(tween(200, delayMillis = 120)) { 12 },
                        exit = fadeOut(tween(120)) + slideOutVertically(tween(120)) { -10 },
                    ) {
                        panelContent(queuePanelWidth)
                    }
                }
            }
            controls(Modifier.wrapContentHeight(unbounded = true).onSizeChanged {
                if (!isQueueReordering) controlsHeightPx = it.height
            })
        }
    }
}
