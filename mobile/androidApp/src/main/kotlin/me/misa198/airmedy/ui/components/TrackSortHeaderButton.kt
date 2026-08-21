package me.misa198.airmedy.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.annotation.StringRes
import dev.chrisbanes.haze.HazeState
import me.misa198.airmedy.R
import me.misa198.airmedy.ui.screens.SortOrder
import me.misa198.airmedy.ui.screens.AlbumLayoutMode
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
    layoutMode: AlbumLayoutMode? = null,
    onLayoutModeSelected: ((AlbumLayoutMode) -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }

    AnchoredPopupMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false },
        modifier = modifier,
        offset = DpOffset.Zero,
        width = 220.dp,
        hazeState = hazeState,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
        anchor = {
            AirmedyGlassIconButton(
                hazeState = hazeState,
                symbol = MaterialSymbols.FilterList,
                label = stringResource(if (layoutMode == null) R.string.sort_by else R.string.album_display_options),
                onClick = { expanded = true },
                surfaceColor = glassSurfaceColor,
            )
        },
        menu = {
            LibrarySortMenu(
                options = options,
                selectedOption = selectedOption,
                sortOrder = sortOrder,
                onSortOptionSelected = { option ->
                    onSortOptionSelected(option)
                    expanded = false
                },
                onToggleSortOrder = {
                    onToggleSortOrder()
                    expanded = false
                },
                layoutMode = layoutMode,
                onLayoutModeSelected = onLayoutModeSelected?.let { callback ->
                    { layout ->
                        callback(layout)
                        expanded = false
                    }
                },
            )
        },
    )
}

@Composable
private fun <T> LibrarySortMenu(
    options: List<LibrarySortOption<T>>,
    selectedOption: T,
    sortOrder: SortOrder,
    onSortOptionSelected: (T) -> Unit,
    onToggleSortOrder: () -> Unit,
    layoutMode: AlbumLayoutMode?,
    onLayoutModeSelected: ((AlbumLayoutMode) -> Unit)?,
) {
    val colors = LocalAirmedyColors.current
    Column(
        modifier = Modifier.width(220.dp),
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

        if (layoutMode != null && onLayoutModeSelected != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .heightIn(min = 1.dp)
                    .background(colors.borderGlass, RectangleShape),
            )
            Text(
                text = stringResource(R.string.album_display_layout),
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp),
                style = MaterialTheme.typography.labelMedium,
                color = colors.textMuted,
            )
            AlbumLayoutOption(
                mode = AlbumLayoutMode.List,
                selectedMode = layoutMode,
                onSelected = onLayoutModeSelected,
            )
            AlbumLayoutOption(
                mode = AlbumLayoutMode.Grid,
                selectedMode = layoutMode,
                onSelected = onLayoutModeSelected,
            )
        }
    }
}

@Composable
private fun AlbumLayoutOption(
    mode: AlbumLayoutMode,
    selectedMode: AlbumLayoutMode,
    onSelected: (AlbumLayoutMode) -> Unit,
) {
    val colors = LocalAirmedyColors.current
    val selected = mode == selectedMode
    val labelRes = if (mode == AlbumLayoutMode.List) R.string.album_display_list else R.string.album_display_grid
    val symbol = if (mode == AlbumLayoutMode.List) MaterialSymbols.ViewList else MaterialSymbols.GridView
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .selectable(
                selected = selected,
                onClick = { onSelected(mode) },
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
        MaterialSymbol(
            symbol = symbol,
            contentDescription = null,
            modifier = Modifier.padding(start = 12.dp),
            size = 18.dp,
            tint = colors.textMain,
        )
        Text(
            text = stringResource(labelRes),
            modifier = Modifier.padding(start = 12.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textMain,
        )
    }
}
