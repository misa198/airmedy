package me.misa198.airmedy.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.misa198.airmedy.ui.theme.LocalAirmedyColors

/** Shared content rows for anchored overflow menus. Hosts decide which actions are available. */
internal sealed interface ContextActionMenuEntry {
    data object Divider : ContextActionMenuEntry
    data class Action(
        val label: String,
        val symbol: String,
        val enabled: Boolean = true,
        val destructive: Boolean = false,
        val onClick: () -> Unit,
    ) : ContextActionMenuEntry
}

@Composable
internal fun ContextActionMenu(entries: List<ContextActionMenuEntry>, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        entries.forEach { entry ->
            when (entry) {
                ContextActionMenuEntry.Divider -> ActionListDivider(ActionListDividerStyle.FullWidth)
                is ContextActionMenuEntry.Action -> ContextActionMenuAction(entry)
            }
        }
    }
}

@Composable
private fun ContextActionMenuAction(entry: ContextActionMenuEntry.Action) {
    val colors = LocalAirmedyColors.current
    val contentColor = if (entry.destructive) colors.primary else colors.textMain
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .semantics { contentDescription = entry.label }
            .clickable(enabled = entry.enabled, role = Role.Button, onClick = entry.onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MaterialSymbol(entry.symbol, null, size = 21.dp, tint = contentColor.copy(alpha = if (entry.enabled) 1f else 0.45f))
        Text(
            entry.label,
            modifier = Modifier.weight(1f).padding(start = 16.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = contentColor.copy(alpha = if (entry.enabled) 1f else 0.45f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
