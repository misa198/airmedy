package me.misa198.airmedy.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.misa198.airmedy.ui.theme.LocalAirmedyColors

/** A standard 56dp row with a label on the left and caller-provided action content on the right. */
@Composable
fun LabeledActionRow(
    @StringRes labelRes: Int,
    modifier: Modifier = Modifier,
    action: @Composable RowScope.() -> Unit,
) {
    val colors = LocalAirmedyColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(labelRes),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = colors.textMain,
        )
        action()
    }
}
