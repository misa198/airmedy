package me.misa198.airmedy.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.misa198.airmedy.ui.theme.LocalAirmedyColors

/** A prominent informational card with a decorative icon, title, and supporting description. */
@Composable
fun HeroCard(
    @DrawableRes iconRes: Int,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    HeroCard(
        title = title,
        description = description,
        modifier = modifier,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = LocalAirmedyColors.current.textMuted,
        )
    }
}

/** A prominent informational card with caller-provided decorative content, title, and description. */
@Composable
fun HeroCard(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
) {
    val colors = LocalAirmedyColors.current
    Card(
        modifier = modifier,
        contentPadding = PaddingValues(24.dp),
    ) {
        icon()
        Text(
            text = title,
            modifier = Modifier.padding(top = 12.dp),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = colors.textMain,
        )
        Text(
            text = description,
            modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textMuted,
        )
    }
}
