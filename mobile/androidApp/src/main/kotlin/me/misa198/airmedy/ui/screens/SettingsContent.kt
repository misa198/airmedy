package me.misa198.airmedy.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import me.misa198.airmedy.R
import me.misa198.airmedy.ui.components.ActionList
import me.misa198.airmedy.ui.components.ActionListContainerStyle
import me.misa198.airmedy.ui.components.ActionListItem
import me.misa198.airmedy.ui.components.MaterialSymbols

@Composable
internal fun SettingsContent(
    onAppearanceSelected: () -> Unit,
    onSyncSelected: () -> Unit,
    onAboutSelected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        ActionList(
            items = listOf(
                ActionListItem(
                    R.string.settings_appearance,
                    leadingSymbol = MaterialSymbols.Palette,
                    onClick = onAppearanceSelected,
                ),
                ActionListItem(
                    R.string.settings_sync,
                    leadingSymbol = MaterialSymbols.Refresh,
                    onClick = onSyncSelected,
                ),
                ActionListItem(R.string.settings_playback, leadingSymbol = MaterialSymbols.PlayArrow),
                ActionListItem(R.string.settings_integration, leadingSymbol = MaterialSymbols.Power),
                ActionListItem(
                    R.string.settings_about,
                    leadingSymbol = MaterialSymbols.Info,
                    onClick = onAboutSelected,
                ),
            ),
            containerStyle = ActionListContainerStyle.Card,
        )
    }
}
