package me.misa198.airmedy.ui.components

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.composables.icons.lucide.R as LucideR
import me.misa198.airmedy.ui.theme.LocalAirmedyColors

data class SelectionOption<T>(
    val value: T,
    @StringRes val labelRes: Int,
)

@Composable
fun <T> Selection(
    @StringRes labelRes: Int,
    options: List<SelectionOption<T>>,
    selectedValue: T,
    onValueSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAirmedyColors.current
    val menuVisibility = remember { MutableTransitionState(false) }
    val selectedOption = options.firstOrNull { it.value == selectedValue }
    val menuOffset = with(LocalDensity.current) { IntOffset(0, 56.dp.roundToPx()) }

    Box(modifier = modifier) {
        LabeledActionRow(
            labelRes = labelRes,
        ) {
            Box(
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .selectable(
                        selected = false,
                        onClick = { menuVisibility.targetState = true },
                        role = Role.Button,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    )
                    .padding(start = 8.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = selectedOption?.let { stringResource(it.labelRes) }.orEmpty(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.textMuted,
                    )
                    Icon(
                        painter = painterResource(LucideR.drawable.lucide_ic_chevrons_up_down),
                        contentDescription = null,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .size(18.dp),
                        tint = colors.textMuted,
                    )
                }
                if (menuVisibility.currentState || menuVisibility.targetState) {
                    SelectionPopup(
                        menuVisibility = menuVisibility,
                        menuOffset = menuOffset,
                        options = options,
                        selectedValue = selectedValue,
                        onValueSelected = onValueSelected,
                    )
                }
            }
        }
    }
}

@Composable
private fun <T> SelectionPopup(
    menuVisibility: MutableTransitionState<Boolean>,
    menuOffset: IntOffset,
    options: List<SelectionOption<T>>,
    selectedValue: T,
    onValueSelected: (T) -> Unit,
) {
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
            SelectionMenu(
                options = options,
                selectedValue = selectedValue,
                onOptionSelected = {
                    onValueSelected(it)
                    menuVisibility.targetState = false
                },
            )
        }
    }
}

@Composable
private fun <T> SelectionMenu(
    options: List<SelectionOption<T>>,
    selectedValue: T,
    onOptionSelected: (T) -> Unit,
) {
    val colors = LocalAirmedyColors.current
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp)

    androidx.compose.foundation.layout.Column(
        modifier = Modifier
            .width(252.dp)
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
            val selected = option.value == selectedValue
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                .heightIn(min = 44.dp)
                    .selectable(
                        selected = selected,
                        onClick = { onOptionSelected(option.value) },
                        role = Role.RadioButton,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    )
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (selected) {
                    Icon(
                        painter = painterResource(LucideR.drawable.lucide_ic_check),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = colors.textMain,
                    )
                } else {
                    Box(modifier = Modifier.size(18.dp))
                }
                Text(
                    text = stringResource(option.labelRes),
                    modifier = Modifier.padding(start = 12.dp),
                    style = MaterialTheme.typography.bodyLarge,
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
    }
}
