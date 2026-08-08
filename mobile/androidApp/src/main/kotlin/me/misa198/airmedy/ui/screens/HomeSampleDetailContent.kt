package me.misa198.airmedy.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.misa198.airmedy.R
import me.misa198.airmedy.ui.theme.LocalAirmedyColors

@Composable
internal fun HomeSampleDetailContent(modifier: Modifier = Modifier) {
    val colors = LocalAirmedyColors.current
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.home_sample_page_heading),
            style = MaterialTheme.typography.headlineMedium,
            color = colors.textMain,
        )
        Text(
            text = stringResource(R.string.home_sample_page_body),
            style = MaterialTheme.typography.bodyLarge,
            color = colors.textMuted,
        )
    }
}
