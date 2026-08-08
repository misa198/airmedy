package me.misa198.airmedy.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.composables.icons.lucide.R as LucideR
import me.misa198.airmedy.R
import me.misa198.airmedy.ui.components.ActionList
import me.misa198.airmedy.ui.components.ActionListContainerStyle
import me.misa198.airmedy.ui.components.ActionListItem

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
                    LucideR.drawable.lucide_ic_palette,
                    onClick = onAppearanceSelected,
                ),
                ActionListItem(
                    R.string.settings_sync,
                    LucideR.drawable.lucide_ic_refresh_cw,
                    onClick = onSyncSelected,
                ),
                ActionListItem(R.string.settings_playback, LucideR.drawable.lucide_ic_play),
                ActionListItem(R.string.settings_integration, LucideR.drawable.lucide_ic_plug),
                ActionListItem(
                    R.string.settings_about,
                    LucideR.drawable.lucide_ic_info,
                    onClick = onAboutSelected,
                ),
            ),
            containerStyle = ActionListContainerStyle.Card,
        )
    }
}
