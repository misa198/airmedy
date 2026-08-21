package me.misa198.airmedy.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import me.misa198.airmedy.R
import me.misa198.airmedy.ui.theme.LocalAirmedyColors

private val BottomSheetExitEasing = CubicBezierEasing(0.8f, 0f, 0.6f, 1f)
private val BottomSheetDismissDragThreshold = 96.dp

/** Lightweight Apple-inspired sheet. It deliberately does not depend on Material sheet APIs. */
@Composable
fun AirmedyBottomSheet(
    title: @Composable () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    leadingAction: @Composable (onDismissRequest: () -> Unit) -> Unit = { onDismissRequest ->
        AirmedyGlassIconButton(
            hazeState = null,
            symbol = MaterialSymbols.Close,
            label = stringResource(R.string.bottom_sheet_dismiss),
            onClick = onDismissRequest,
        )
    },
    trailingAction: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val colors = LocalAirmedyColors.current
    val dismissLabel = stringResource(R.string.bottom_sheet_dismiss)
    val backdropInteraction = remember { MutableInteractionSource() }
    var entered by remember { mutableStateOf(false) }
    var dismissing by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val hiddenOffset = with(density) { 88.dp.toPx() }
    val dismissDragThreshold = with(density) { BottomSheetDismissDragThreshold.toPx() }
    val requestDismiss = { if (!dismissing) dismissing = true }
    var isDragging by remember { mutableStateOf(false) }
    var dragOffset by remember { mutableStateOf(0f) }
    LaunchedEffect(dismissing) {
        entered = !dismissing
        if (dismissing) {
            delay(240)
            onDismiss()
        }
    }
    val motionEasing = if (entered) FastOutSlowInEasing else BottomSheetExitEasing
    val sheetAlpha by animateFloatAsState(if (entered) 1f else 0f, tween(220, easing = motionEasing), label = "sheet-alpha")
    val sheetTranslation by animateFloatAsState(
        targetValue = if (entered) 0f else hiddenOffset,
        animationSpec = tween(220, easing = motionEasing),
        label = "sheet-translation",
    )
    val dragTranslation by animateFloatAsState(
        targetValue = if (isDragging || dismissing) dragOffset else 0f,
        animationSpec = if (isDragging) snap() else tween(220, easing = FastOutSlowInEasing),
        label = "sheet-drag-translation",
    )
    Dialog(
        onDismissRequest = requestDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        val dialogWindow = (LocalView.current.parent as DialogWindowProvider).window
        SideEffect { dialogWindow.setDimAmount(0f) }
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier.fillMaxSize()
                    .background(colors.playerBackdrop.copy(alpha = 0.62f * sheetAlpha * (1f - (dragTranslation / dismissDragThreshold).coerceIn(0f, 1f))))
                    .semantics { contentDescription = dismissLabel }
                    .clickable(
                        role = Role.Button,
                        interactionSource = backdropInteraction,
                        indication = null,
                        onClick = requestDismiss,
                    ),
            )
            Column(
                modifier = modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .pointerInput(Unit) {
                        awaitPointerEventScope { while (true) awaitPointerEvent() }
                    }
                    .graphicsLayer { alpha = sheetAlpha; translationY = sheetTranslation + dragTranslation }
                    .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                    .background(colors.glassOpaque)
                    .border(1.dp, colors.borderGlass, RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                    .navigationBarsPadding()
                    .imePadding(),
            ) {
                Box(Modifier.fillMaxWidth().padding(top = 10.dp), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier.width(36.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(colors.textMain.copy(alpha = colors.foregroundSubtle.alpha)),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .height(64.dp)
                        .padding(horizontal = 12.dp)
                        .testTag("bottom-sheet-header")
                        .pointerInput(dismissDragThreshold) {
                            detectVerticalDragGestures(
                                onDragStart = { isDragging = true },
                                onDragCancel = {
                                    isDragging = false
                                    dragOffset = 0f
                                },
                                onDragEnd = {
                                    isDragging = false
                                    if (dragOffset >= dismissDragThreshold) {
                                        requestDismiss()
                                    } else {
                                        dragOffset = 0f
                                    }
                                },
                                onVerticalDrag = { change, dragAmount ->
                                    change.consume()
                                    dragOffset = (dragOffset + dragAmount).coerceAtLeast(0f)
                                },
                            )
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.width(48.dp), contentAlignment = Alignment.Center) { leadingAction(requestDismiss) }
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { title() }
                    Box(Modifier.width(48.dp), contentAlignment = Alignment.Center) { trailingAction() }
                }
                content()
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}
