package me.misa198.airmedy.ui.components

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.R as LucideR
import me.misa198.airmedy.ui.theme.LocalAirmedyColors

enum class ActionListContainerStyle {
    Card,
    Plain,
}

enum class ActionListDividerStyle {
    InsetForLeadingIcon,
    FullWidth,
}

data class ActionListItem(
    @StringRes val labelRes: Int,
    @DrawableRes val leadingIconRes: Int? = null,
    val trailingContent: (@Composable RowScope.() -> Unit)? = null,
    val onClick: (() -> Unit)? = null,
)

/** A vertical list of optional actions with adaptable leading and trailing content. */
@Composable
fun ActionList(
    items: List<ActionListItem>,
    containerStyle: ActionListContainerStyle,
    dividerStyle: ActionListDividerStyle = ActionListDividerStyle.InsetForLeadingIcon,
    modifier: Modifier = Modifier,
) {
    when (containerStyle) {
        ActionListContainerStyle.Card -> Card(modifier = modifier) {
            ActionListItems(items, dividerStyle)
        }
        ActionListContainerStyle.Plain -> Column(modifier = modifier.fillMaxWidth()) {
            ActionListItems(items, dividerStyle)
        }
    }
}

@Composable
private fun ColumnScope.ActionListItems(
    items: List<ActionListItem>,
    dividerStyle: ActionListDividerStyle,
) {
    items.forEachIndexed { index, item ->
        ActionListRow(item = item)
        if (index < items.lastIndex) {
            ActionListDivider(dividerStyle)
        }
    }
}

@Composable
private fun ActionListRow(item: ActionListItem) {
    val colors = LocalAirmedyColors.current
    val label = stringResource(item.labelRes)
    val clickModifier = item.onClick?.let { onClick ->
        Modifier.clickable(
            onClick = onClick,
            role = Role.Button,
            interactionSource = remember { MutableInteractionSource() },
        )
    } ?: Modifier

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .semantics(mergeDescendants = true) { contentDescription = label }
            .then(clickModifier)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        item.leadingIconRes?.let { iconRes ->
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = colors.textMain,
            )
        }
        Text(
            text = label,
            modifier = Modifier
                .weight(1f)
                .padding(start = if (item.leadingIconRes != null) 16.dp else 0.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = colors.textMain,
        )
        when {
            item.trailingContent != null -> Row(content = item.trailingContent)
            item.onClick != null -> Icon(
                painter = painterResource(LucideR.drawable.lucide_ic_chevron_right),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = colors.textMuted,
            )
        }
    }
}

@Composable
private fun ActionListDivider(style: ActionListDividerStyle) {
    val colors = LocalAirmedyColors.current
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = if (style == ActionListDividerStyle.InsetForLeadingIcon) 52.dp else 0.dp,
                end = if (style == ActionListDividerStyle.InsetForLeadingIcon) 16.dp else 0.dp,
            )
            .height(1.dp)
            .background(colors.borderGlass),
    )
}
