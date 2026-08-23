package me.misa198.airmedy.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import me.misa198.airmedy.R
import me.misa198.airmedy.ui.theme.LocalAirmedyColors

@Stable
internal class AirmedyBottomSheetStack<T>(root: T) {
    private val entries = mutableStateListOf(root)
    private var lastNavigationWasPush by mutableStateOf(true)

    val current: T? get() = entries.lastOrNull()
    val canPop: Boolean get() = entries.size > 1
    val pushedLast: Boolean get() = lastNavigationWasPush

    fun push(value: T) {
        lastNavigationWasPush = true
        entries += value
    }

    fun pop(): Boolean {
        if (entries.isEmpty()) return false
        lastNavigationWasPush = false
        entries.removeAt(entries.lastIndex)
        return entries.isNotEmpty()
    }
}

private data class BottomSheetStackPage<T>(val value: T, val canPop: Boolean)

@Composable
internal fun <T> rememberAirmedyBottomSheetStack(root: T): AirmedyBottomSheetStack<T> =
    remember(root) { AirmedyBottomSheetStack(root) }

@Composable
internal fun <T> AirmedyBottomSheetStack(
    stack: AirmedyBottomSheetStack<T>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: @Composable (T) -> Unit,
    content: @Composable (T) -> Unit,
) {
    val current = stack.current ?: return
    val page = BottomSheetStackPage(current, stack.canPop)
    val colors = LocalAirmedyColors.current
    val pop = {
        if (!stack.pop()) onDismiss()
    }
    AirmedyBottomSheet(
        title = {
            AnimatedContent(
                targetState = page,
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
                transitionSpec = { (fadeIn(tween(120)) togetherWith fadeOut(tween(90))).using(null) },
                label = "bottom-sheet-stack-title",
            ) { sheet ->
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    title(sheet.value)
                }
            }
        },
        onDismiss = pop,
        onDragDismiss = onDismiss,
        modifier = modifier,
        dismissImmediately = stack.canPop,
        leadingAction = { dismiss ->
            AnimatedContent(
                targetState = page,
                transitionSpec = { (fadeIn(tween(120)) togetherWith fadeOut(tween(90))).using(null) },
                label = "bottom-sheet-stack-action",
            ) { sheet ->
                if (sheet.canPop) {
                    AirmedyGlassIconButton(
                        hazeState = null,
                        symbol = MaterialSymbols.ChevronLeft,
                        label = stringResource(R.string.bottom_sheet_back),
                        onClick = dismiss,
                    )
                } else {
                    AirmedyGlassIconButton(
                        hazeState = null,
                        symbol = MaterialSymbols.Close,
                        label = stringResource(R.string.bottom_sheet_dismiss),
                        onClick = dismiss,
                    )
                }
            }
        },
    ) {
        AnimatedContent(
            targetState = page,
            transitionSpec = {
                if (stack.pushedLast) {
                    (slideInHorizontally { it } togetherWith
                        slideOutHorizontally { -it / 4 }).apply {
                        targetContentZIndex = 1f
                    }.using(null)
                } else {
                    (slideInHorizontally { -it / 4 } togetherWith
                        slideOutHorizontally { it }).apply {
                        targetContentZIndex = 0f
                    }.using(null)
                }
            },
            label = "bottom-sheet-stack-content",
        ) { sheet ->
            androidx.compose.foundation.layout.Box(
                Modifier.fillMaxWidth().background(colors.glassOpaque),
            ) {
                content(sheet.value)
            }
        }
    }
}
