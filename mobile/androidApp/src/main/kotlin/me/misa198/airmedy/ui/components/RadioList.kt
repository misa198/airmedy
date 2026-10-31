package me.misa198.airmedy.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.misa198.airmedy.ui.theme.LocalAirmedyColors

/** Controlled, single-choice rows; the caller supplies any card or section title. */
@Composable
fun <T> RadioList(
    options: List<SelectionOption<T>>,
    selectedValue: T,
    onValueSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAirmedyColors.current
    Column(modifier = modifier.selectableGroup()) {
        options.forEachIndexed { index, option ->
            val selected = selectedValue == option.value
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                    .selectable(
                        selected = selected, role = Role.RadioButton,
                        onClick = { onValueSelected(option.value) },
                    )
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = option.label.orEmpty().ifBlank { option.labelRes?.let { stringResource(it) }.orEmpty() },
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge, color = colors.textMain,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (selected) MaterialSymbol(
                    symbol = MaterialSymbols.Check, contentDescription = null,
                    tint = colors.primary, size = 24.dp,
                    modifier = Modifier.testTag("radio_list_selected_check"),
                )
            }
            if (index < options.lastIndex) ActionListDivider(
                style = ActionListDividerStyle.FullWidth,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }
}
