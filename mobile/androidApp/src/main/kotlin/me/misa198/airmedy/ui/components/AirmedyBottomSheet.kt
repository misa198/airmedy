package me.misa198.airmedy.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import me.misa198.airmedy.R
import me.misa198.airmedy.ui.theme.LocalAirmedyColors

/** Lightweight Apple-inspired sheet. It deliberately does not depend on Material sheet APIs. */
@Composable
fun AirmedyBottomSheet(
    title: @Composable () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    leadingAction: @Composable () -> Unit = {
        AirmedyGlassIconButton(
            hazeState = null,
            symbol = MaterialSymbols.Close,
            label = stringResource(R.string.bottom_sheet_dismiss),
            onClick = onDismiss,
        )
    },
    trailingAction: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val colors = LocalAirmedyColors.current
    val dismissLabel = stringResource(R.string.bottom_sheet_dismiss)
    val backdropInteraction = remember { MutableInteractionSource() }
    var entered by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val hiddenOffset = with(density) { 88.dp.toPx() }
    LaunchedEffect(Unit) { entered = true }
    val sheetAlpha by animateFloatAsState(if (entered) 1f else 0f, tween(180, easing = FastOutSlowInEasing), label = "sheet-alpha")
    val sheetTranslation by animateFloatAsState(
        targetValue = if (entered) 0f else hiddenOffset,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "sheet-translation",
    )
    BackHandler(onBack = onDismiss)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier.fillMaxSize()
                    .background(colors.playerBackdrop.copy(alpha = 0.62f * sheetAlpha))
                    .semantics { contentDescription = dismissLabel }
                    .clickable(
                        role = Role.Button,
                        interactionSource = backdropInteraction,
                        indication = null,
                        onClick = onDismiss,
                    ),
            )
            Column(
                modifier = modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .graphicsLayer { alpha = sheetAlpha; translationY = sheetTranslation }
                    .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                    .background(colors.glassOpaque)
                    .border(1.dp, colors.borderGlass, RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                    .navigationBarsPadding()
                    .imePadding(),
            ) {
                Box(Modifier.fillMaxWidth().padding(top = 10.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.width(36.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(colors.foregroundSubtle))
                }
                Row(
                    modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.width(48.dp), contentAlignment = Alignment.Center) { leadingAction() }
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { title() }
                    Box(Modifier.width(48.dp), contentAlignment = Alignment.Center) { trailingAction() }
                }
                content()
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}
