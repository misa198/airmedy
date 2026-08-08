package me.misa198.airmedy.ui.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import me.misa198.airmedy.AppDestination
import me.misa198.airmedy.ui.navigation.placeholderRes
import me.misa198.airmedy.ui.theme.LocalAirmedyColors

@Composable
internal fun PlaceholderContent(destination: AppDestination, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(destination.placeholderRes),
        modifier = modifier,
        style = MaterialTheme.typography.bodyLarge,
        color = LocalAirmedyColors.current.textMuted,
    )
}
