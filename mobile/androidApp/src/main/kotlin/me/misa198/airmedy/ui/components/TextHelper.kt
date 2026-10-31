package me.misa198.airmedy.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.misa198.airmedy.ui.theme.LocalAirmedyColors

/** Muted, wrapping annotation aligned with section labels below a settings card. */
@Composable
fun TextHelper(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier.padding(horizontal = 18.dp),
        style = MaterialTheme.typography.bodySmall,
        color = LocalAirmedyColors.current.textMuted,
    )
}
