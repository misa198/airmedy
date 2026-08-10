package me.misa198.airmedy.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.annotation.StringRes
import dev.chrisbanes.haze.HazeState
import me.misa198.airmedy.R
import me.misa198.airmedy.ui.screens.SortOrder
import me.misa198.airmedy.ui.theme.LocalAirmedyColors

data class LibrarySortOption<T>(
    val value: T,
    @StringRes val labelRes: Int,
)

@Composable
fun <T> LibrarySortHeaderButton(
    hazeState: HazeState?,
    options: List<LibrarySortOption<T>>,
    selectedOption: T,
    sortOrder: SortOrder,
    onSortOptionSelected: (T) -> Unit,
    onToggleSortOrder: () -> Unit,
    modifier: Modifier = Modifier,
    glassSurfaceColor: Color? = null,
) {
    val menuVisibility = remember { MutableTransitionState(false) }
    val menuOffset = with(LocalDensity.current) { IntOffset(0, 52.dp.roundToPx()) }

    Box(modifier = modifier) {
        AirmedyGlassIconButton(
            hazeState = hazeState,
            symbol = MaterialSymbols.SwapVert,
            label = stringResource(R.string.sort_by),
            onClick = { menuVisibility.targetState = true },
            surfaceColor = glassSurfaceColor,
        )

        if (menuVisibility.currentState || menuVisibility.targetState) {
            Popup(
                alignment = Alignment.TopEnd,
                offset = menuOffset,
                onDismissRequest = { menuVisibility.targetState = false },
                properties = PopupProperties(focusable = true),
            ) {
                AnimatedVisibility(
                    visibleState = menuVisibility,
                    enter = expandVertically(
                        expandFrom = Alignment.Top,
                        animationSpec = tween(durationMillis = 220),
                    ) + scaleIn(
                        initialScale = 0.94f,
                        animationSpec = tween(durationMillis = 220),
                    ) + fadeIn(animationSpec = tween(durationMillis = 220)),
                    exit = shrinkVertically(
                        shrinkTowards = Alignment.Top,
                        animationSpec = tween(durationMillis = 220),
                    ) + scaleOut(
                        targetScale = 0.96f,
                        animationSpec = tween(durationMillis = 220),
                    ) + fadeOut(animationSpec = tween(durationMillis = 220)),
                ) {
                    LibrarySortMenu(
                        options = options,
                        selectedOption = selectedOption,
                        sortOrder = sortOrder,
                        onSortOptionSelected = { option ->
                            onSortOptionSelected(option)
                            menuVisibility.targetState = false
                        },
                        onToggleSortOrder = {
                            onToggleSortOrder()
                            menuVisibility.targetState = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun <T> LibrarySortMenu(
    options: List<LibrarySortOption<T>>,
    selectedOption: T,
    sortOrder: SortOrder,
    onSortOptionSelected: (T) -> Unit,
    onToggleSortOrder: () -> Unit,
) {
    val colors = LocalAirmedyColors.current
    val shape = RoundedCornerShape(24.dp)

    Column(
        modifier = Modifier
            .width(220.dp)
            .shadow(
                elevation = 8.dp,
                shape = shape,
                clip = false,
                ambientColor = Color.Black.copy(alpha = 0.16f),
                spotColor = Color.Black.copy(alpha = 0.20f),
            )
            .clip(shape)
            .background(colors.card)
            .border(1.dp, colors.borderGlass, shape),
    ) {
        options.forEachIndexed { index, option ->
            val selected = option.value == selectedOption
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
                    .selectable(
                        selected = selected,
                        onClick = { onSortOptionSelected(option.value) },
                        role = Role.RadioButton,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    )
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (selected) {
                    MaterialSymbol(
                        symbol = MaterialSymbols.Check,
                        contentDescription = null,
                        size = 18.dp,
                        tint = colors.primary,
                    )
                } else {
                    Box(modifier = Modifier.size(18.dp))
                }
                Text(
                    text = stringResource(option.labelRes),
                    modifier = Modifier.padding(start = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textMain,
                )
            }

            if (index < options.lastIndex) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 46.dp, end = 16.dp)
                        .heightIn(min = 1.dp)
                        .background(colors.borderGlass, RectangleShape),
                )
            }
        }

        // Divider before Order toggle
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .heightIn(min = 1.dp)
                .background(colors.borderGlass, RectangleShape),
        )

        // ASC / DESC toggle row
        val orderTextRes = if (sortOrder == SortOrder.Ascending) R.string.sort_order_asc else R.string.sort_order_desc
        val orderSymbol = if (sortOrder == SortOrder.Ascending) {
            MaterialSymbols.ArrowUpward
        } else {
            MaterialSymbols.ArrowDownward
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .selectable(
                    selected = false,
                    onClick = onToggleSortOrder,
                    role = Role.Button,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                )
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MaterialSymbol(
                symbol = orderSymbol,
                contentDescription = null,
                size = 18.dp,
                tint = colors.primary,
            )
            Text(
                text = stringResource(orderTextRes),
                modifier = Modifier.padding(start = 12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textMain,
            )
        }
    }
}
