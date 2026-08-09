package me.misa198.airmedy.ui.navigation

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import me.misa198.airmedy.AppDestination
import me.misa198.airmedy.ui.components.liquidGlassBackground
import me.misa198.airmedy.ui.theme.LocalAirmedyColors

internal val FloatingNavigationHeight = 72.dp
internal val CompactNavigationHeight = 56.dp
internal val FloatingNavigationBottomMargin = 4.dp
internal val FloatingNavigationContentGap = 16.dp

internal val OuterPillRadius = 36.dp
private val InnerPillRadius = 32.dp
private val PillGap = 4.dp

@Composable
internal fun FloatingNavigationBar(
    selectedDestination: AppDestination,
    hazeState: HazeState?,
    onDestinationSelected: (AppDestination) -> Unit,
    fullNavigationContentAlpha: Float = 1f,
    onCompactClick: () -> Unit = {},
    height: Dp = FloatingNavigationHeight,
    stableGlassWidth: Dp? = null,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAirmedyColors.current
    val density = LocalDensity.current
    val outerPillShape = RoundedCornerShape(OuterPillRadius)
    Box(
        modifier = modifier
            .widthIn(max = 420.dp)
            .fillMaxWidth()
            .height(height)
            .clip(outerPillShape)
            .border(1.dp, colors.borderGlass, outerPillShape)
    ) {
        Box(
            modifier = if (stableGlassWidth == null) {
                Modifier.fillMaxSize()
            } else {
                Modifier
                    .align(Alignment.CenterStart)
                    .requiredWidth(stableGlassWidth)
                    .requiredHeight(FloatingNavigationHeight)
            }
                .liquidGlassBackground(hazeState, colors),
        )
        Box(modifier = Modifier.fillMaxSize().padding(PillGap)) {
            if (fullNavigationContentAlpha < 1f) {
                CompactNavigationTarget(
                    selectedDestination = selectedDestination,
                    onClick = onCompactClick,
                    modifier = Modifier.graphicsLayer(alpha = 1f - fullNavigationContentAlpha),
                )
            }
            if (fullNavigationContentAlpha > 0f) {
                val contentTranslationY = with(density) { (1f - fullNavigationContentAlpha) * 8.dp.toPx() }
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            alpha = fullNavigationContentAlpha,
                            scaleX = 0.96f + (0.04f * fullNavigationContentAlpha),
                            scaleY = 0.96f + (0.04f * fullNavigationContentAlpha),
                            translationY = contentTranslationY,
                        ),
                ) {
            val itemWidth = maxWidth / AppDestination.entries.size
            val maxIndicatorOffset = maxWidth - itemWidth
            var isDragging by remember { mutableStateOf(false) }
            var dragOffset by remember { mutableStateOf(0.dp) }
            val targetOffset = if (isDragging) dragOffset else itemWidth * selectedDestination.ordinal
            val indicatorOffset by animateDpAsState(
                targetValue = targetOffset,
                animationSpec = if (isDragging) snap() else spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
                label = "navigation-selection-offset",
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(itemWidth, maxIndicatorOffset, selectedDestination) {
                        detectDragGestures(
                            onDragStart = {
                                isDragging = true
                                dragOffset = itemWidth * selectedDestination.ordinal
                            },
                            onDragCancel = { isDragging = false },
                            onDragEnd = {
                                val destinationIndex = (dragOffset / itemWidth).toInt()
                                    .coerceIn(0, AppDestination.entries.lastIndex)
                                onDestinationSelected(AppDestination.entries[destinationIndex])
                                isDragging = false
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragOffset = (dragOffset + dragAmount.x.toDp())
                                    .coerceIn(0.dp, maxIndicatorOffset)
                            },
                        )
                    },
            ) {
                Box(
                    modifier = Modifier
                        .offset(x = indicatorOffset)
                        .width(itemWidth)
                        .fillMaxSize()
                        .clip(RoundedCornerShape(InnerPillRadius))
                        .background(colors.navigationActive),
                )
                FloatingNavigationVisuals(
                    foreground = colors.textMain,
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationForegroundMask(
                            indicatorOffset = indicatorOffset,
                            itemWidth = itemWidth,
                            clipOp = ClipOp.Difference,
                        ),
                )
                FloatingNavigationVisuals(
                    foreground = colors.primary,
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationForegroundMask(
                            indicatorOffset = indicatorOffset,
                            itemWidth = itemWidth,
                            clipOp = ClipOp.Intersect,
                        ),
                )
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppDestination.entries.forEach { destination ->
                        FloatingNavigationTarget(
                            destination = destination,
                            selected = destination == selectedDestination,
                            onClick = { onDestinationSelected(destination) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                }
            }
            }
        }
    }
}

@Composable
private fun CompactNavigationTarget(
    selectedDestination: AppDestination,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAirmedyColors.current
    val destinationLabel = stringResource(selectedDestination.titleRes)
    IconButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(InnerPillRadius))
            .semantics { contentDescription = destinationLabel },
    ) {
        Icon(
            painter = painterResource(selectedDestination.iconRes),
            contentDescription = null,
            tint = colors.primary,
        )
    }
}

private fun Modifier.navigationForegroundMask(
    indicatorOffset: Dp,
    itemWidth: Dp,
    clipOp: ClipOp,
): Modifier = drawWithContent {
    val contentDrawScope = this
    val pillLeft = indicatorOffset.roundToPx().toFloat()
    val pillWidth = itemWidth.roundToPx().toFloat()
    val pillRadius = InnerPillRadius.roundToPx().toFloat()
    val pillPath = Path().apply {
        addRoundRect(
            RoundRect(
                left = pillLeft,
                top = 0f,
                right = pillLeft + pillWidth,
                bottom = size.height,
                radiusX = pillRadius,
                radiusY = pillRadius,
            ),
        )
    }
    clipPath(pillPath, clipOp = clipOp) { contentDrawScope.drawContent() }
}

@Composable
private fun FloatingNavigationVisuals(
    foreground: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.clearAndSetSemantics { },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppDestination.entries.forEach { destination ->
            FloatingNavigationVisual(
                destination = destination,
                foreground = foreground,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun FloatingNavigationVisual(
    destination: AppDestination,
    foreground: Color,
    modifier: Modifier = Modifier,
) {
    val destinationLabel = stringResource(destination.titleRes)
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(destination.iconRes),
            contentDescription = null,
            tint = foreground,
        )
        Text(
            text = destinationLabel,
            style = MaterialTheme.typography.labelSmall,
            color = foreground,
        )
    }
}

@Composable
private fun FloatingNavigationTarget(
    destination: AppDestination,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val destinationLabel = stringResource(destination.titleRes)
    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(InnerPillRadius))
            .semantics { contentDescription = destinationLabel }
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.Tab,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ),
    )
}
