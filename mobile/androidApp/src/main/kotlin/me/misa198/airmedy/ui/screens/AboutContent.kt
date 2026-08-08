package me.misa198.airmedy.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.misa198.airmedy.BuildConfig
import me.misa198.airmedy.R
import me.misa198.airmedy.ui.components.ActionList
import me.misa198.airmedy.ui.components.ActionListContainerStyle
import me.misa198.airmedy.ui.components.ActionListDividerStyle
import me.misa198.airmedy.ui.components.ActionListItem
import me.misa198.airmedy.ui.components.HeroCard
import me.misa198.airmedy.ui.theme.LocalAirmedyColors

private const val AirmedyGithubUrl = "https://github.com/misa198/airmedy"
private const val AirmedyLicenseUrl = "https://github.com/misa198/airmedy/blob/master/LICENSE"

@Composable
internal fun AboutContent(
    onOpenExternalUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAirmedyColors.current
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HeroCard(
            title = stringResource(R.string.app_name),
            description = stringResource(R.string.about_description),
        ) {
            Image(
                painter = painterResource(R.drawable.airmedy_about_app_icon),
                contentDescription = null,
                modifier = Modifier.size(64.dp),
            )
        }
        ActionList(
            items = listOf(
                ActionListItem(
                    labelRes = R.string.about_version,
                    trailingContent = {
                        Text(
                            text = BuildConfig.VERSION_NAME,
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.textMuted,
                        )
                    },
                ),
                ActionListItem(
                    labelRes = R.string.about_github,
                    onClick = { onOpenExternalUrl(AirmedyGithubUrl) },
                ),
                ActionListItem(
                    labelRes = R.string.about_license,
                    onClick = { onOpenExternalUrl(AirmedyLicenseUrl) },
                ),
            ),
            containerStyle = ActionListContainerStyle.Card,
            dividerStyle = ActionListDividerStyle.FullWidth,
        )
    }
}
