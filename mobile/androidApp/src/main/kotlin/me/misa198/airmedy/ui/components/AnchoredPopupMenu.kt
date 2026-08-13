package me.misa198.airmedy.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import me.misa198.airmedy.ui.theme.LocalAirmedyColors
import kotlin.math.roundToInt

private data class AnchoredPopupMenuRequest(
    val id: Any,
    val anchorRightPx: Float,
    val anchorBottomPx: Float,
    val offset: DpOffset,
    val width: Dp,
    val shape: Shape,
    val hazeState: HazeState?,
    val onDismissRequest: () -> Unit,
    val menu: @Composable () -> Unit,
)

private class AnchoredPopupMenuHostState {
    var request by mutableStateOf<AnchoredPopupMenuRequest?>(null)

    fun show(request: AnchoredPopupMenuRequest) {
        val current = this.request
        if (
            current == null ||
            current.id !== request.id ||
            current.anchorRightPx != request.anchorRightPx ||
            current.anchorBottomPx != request.anchorBottomPx ||
            current.offset != request.offset ||
            current.width != request.width ||
            current.shape != request.shape ||
            current.hazeState !== request.hazeState
        ) {
            this.request = request
        }
    }

    fun dismiss(id: Any) {
        if (request?.id === id) request = null
    }

    fun dismissAll() {
        request = null
    }
}

private val LocalAnchoredPopupMenuHost = staticCompositionLocalOf<AnchoredPopupMenuHostState?> { null }

/**
 * App-level host for [AnchoredPopupMenu]. It keeps popup content in the same
 * Compose tree as the Haze source, unlike Android's separate-window [Popup].
 */
@Composable
fun AnchoredPopupMenuHost(
    hazeState: HazeState?,
    dismissKey: Any? = Unit,
    content: @Composable () -> Unit,
) {
    val host = remember { AnchoredPopupMenuHostState() }
    LaunchedEffect(dismissKey) {
        host.dismissAll()
    }
    CompositionLocalProvider(LocalAnchoredPopupMenuHost provides host) {
        Box(modifier = Modifier.fillMaxSize()) {
            content()
            AnchoredPopupMenuOverlay(host.request, hazeState)
        }
    }
}

@Composable
private fun AnchoredPopupMenuOverlay(
    request: AnchoredPopupMenuRequest?,
    defaultHazeState: HazeState?,
) {
    val colors = LocalAirmedyColors.current
    var retainedRequest by remember { mutableStateOf<AnchoredPopupMenuRequest?>(null) }
    var hazeEnabled by remember { mutableStateOf(false) }
    val progress = remember { Animatable(0f) }

    LaunchedEffect(request?.id) {
        if (request == null) {
            progress.animateTo(0f, animationSpec = tween(durationMillis = 180))
            retainedRequest = null
        } else {
            retainedRequest = request
            hazeEnabled = true
            progress.snapTo(0f)
            progress.animateTo(1f, animationSpec = tween(durationMillis = 180))
        }
    }
    val activeRequest = retainedRequest ?: return

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                onClick = activeRequest.onDismissRequest,
                indication = null,
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
            ),
    ) {
        val density = androidx.compose.ui.platform.LocalDensity.current
        val menuWidthPx = with(density) { activeRequest.width.roundToPx() }
        val offsetX = with(density) { activeRequest.offset.x.roundToPx() }
        val offsetY = with(density) { activeRequest.offset.y.roundToPx() }
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset {
                    IntOffset(
                        (activeRequest.anchorRightPx - menuWidthPx + offsetX).roundToInt(),
                        (activeRequest.anchorBottomPx + offsetY).roundToInt(),
                    )
                }
                .width(activeRequest.width)
                .graphicsLayer {
                    alpha = progress.value
                    scaleX = 0.94f + (0.06f * progress.value)
                    scaleY = 0.94f + (0.06f * progress.value)
                    transformOrigin = TransformOrigin(1f, 0f)
                }
                .clip(activeRequest.shape)
                .liquidGlassBackground(
                    hazeState = if (hazeEnabled) activeRequest.hazeState ?: defaultHazeState else null,
                    colors = colors,
                )
                .border(1.dp, colors.borderGlass, activeRequest.shape),
        ) {
            activeRequest.menu()
        }
    }
}

/**
 * Renders [menu] in the app-level [AnchoredPopupMenuHost], top-end aligned to
 * [anchor]. The host is required for Haze; without one the anchor still works
 * but no menu is rendered, which keeps isolated previews safe.
 */
@Composable
fun AnchoredPopupMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset.Zero,
    width: Dp,
    hazeState: HazeState? = null,
    shape: Shape = RoundedCornerShape(28.dp),
    anchor: @Composable () -> Unit,
    menu: @Composable () -> Unit,
) {
    val host = LocalAnchoredPopupMenuHost.current
    val id = remember { Any() }
    var anchorRightPx by remember { mutableStateOf<Float?>(null) }
    var anchorBottomPx by remember { mutableStateOf<Float?>(null) }

    SideEffect {
        val right = anchorRightPx
        val bottom = anchorBottomPx
        if (expanded && host != null && right != null && bottom != null) {
            host.show(
                AnchoredPopupMenuRequest(
                    id = id,
                    anchorRightPx = right,
                    anchorBottomPx = bottom,
                    offset = offset,
                    width = width,
                    shape = shape,
                    hazeState = hazeState,
                    onDismissRequest = onDismissRequest,
                    menu = menu,
                ),
            )
        } else if (!expanded) {
            host?.dismiss(id)
        }
    }

    Box(
        modifier = modifier.onGloballyPositioned { coordinates ->
            coordinates.boundsInRoot().let { bounds ->
                anchorRightPx = bounds.right
                anchorBottomPx = bounds.bottom
            }
        },
    ) {
        anchor()
    }
}
