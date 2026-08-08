package me.misa198.airmedy.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.misa198.airmedy.R
import me.misa198.airmedy.settings.ThemeMode
import me.misa198.airmedy.ui.components.Card
import me.misa198.airmedy.ui.components.Selection
import me.misa198.airmedy.ui.components.SelectionOption

@Composable
internal fun AppearanceContent(
    themeMode: ThemeMode,
    onThemeModeSelected: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card {
            Selection(
                labelRes = R.string.appearance_theme_title,
                options = ThemeMode.entries.map { mode ->
                    SelectionOption(value = mode, labelRes = mode.labelRes)
                },
                selectedValue = themeMode,
                onValueSelected = onThemeModeSelected,
            )
        }
    }
}
