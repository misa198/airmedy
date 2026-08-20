package me.misa198.airmedy.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import me.misa198.airmedy.ui.theme.LocalAirmedyColors

data class SelectionOption<T>(
    val value: T,
    @StringRes val labelRes: Int? = null,
    val label: String? = null,
)

@Composable
fun <T> Selection(
    @StringRes labelRes: Int? = null,
    options: List<SelectionOption<T>>,
    selectedValue: T,
    onValueSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
) {
    val colors = LocalAirmedyColors.current
    var expanded by remember { mutableStateOf(false) }
    val selectedOption = options.firstOrNull { it.value == selectedValue }

    AnchoredPopupMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false },
        modifier = modifier,
        offset = DpOffset.Zero,
        width = 252.dp,
        hazeState = hazeState,
        shape = RoundedCornerShape(28.dp),
        anchor = {
            if (labelRes == null) {
                SelectionValue(selectedOption) { expanded = true }
            } else ActionList(
                items = listOf(ActionListItem(labelRes, trailingContent = {
                    SelectionValue(selectedOption) { expanded = true }
                })),
                containerStyle = ActionListContainerStyle.Plain,
            )
        },
        menu = {
            SelectionMenu(
                options = options,
                selectedValue = selectedValue,
                onOptionSelected = {
                    onValueSelected(it)
                    expanded = false
                },
            )
        },
    )
}

@Composable
private fun <T> SelectionValue(
    option: SelectionOption<T>?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = LocalAirmedyColors.current
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .selectable(
                selected = false,
                onClick = onClick,
                role = Role.Button,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            )
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = option?.label.orEmpty().ifBlank { option?.labelRes?.let { stringResource(it) }.orEmpty() },
                style = MaterialTheme.typography.bodyLarge,
                color = colors.textMuted,
            )
            MaterialSymbol(
                symbol = MaterialSymbols.UnfoldMore,
                contentDescription = null,
                size = 18.dp,
                tint = colors.textMuted,
                modifier = Modifier.padding(start = 8.dp),
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

    androidx.compose.foundation.layout.Column(
        modifier = Modifier.width(252.dp).heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
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
                    MaterialSymbol(
                        symbol = MaterialSymbols.Check,
                        contentDescription = null,
                        size = 18.dp,
                        tint = colors.textMain,
                    )
                } else {
                    Box(modifier = Modifier.size(18.dp))
                }
                Text(
                    text = option.label.orEmpty().ifBlank { option.labelRes?.let { stringResource(it) }.orEmpty() },
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
