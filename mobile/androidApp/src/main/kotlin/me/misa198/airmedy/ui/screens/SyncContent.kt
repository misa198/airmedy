package me.misa198.airmedy.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.R as LucideR
import me.misa198.airmedy.R
import me.misa198.airmedy.SyncDevice
import me.misa198.airmedy.SyncDeviceType
import me.misa198.airmedy.ui.components.Card
import me.misa198.airmedy.ui.components.HeroCard
import me.misa198.airmedy.ui.theme.LocalAirmedyColors

@Composable
internal fun SyncContent(
    syncDevice: SyncDevice?,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAirmedyColors.current
    Column(modifier = modifier) {
        if (syncDevice == null) {
            HeroCard(
                iconRes = LucideR.drawable.lucide_ic_plug,
                title = stringResource(R.string.sync_empty_title),
                description = stringResource(R.string.sync_empty_description),
            )
        } else {
            Card(contentPadding = PaddingValues(24.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = syncDevice.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.textMain,
                    )
                    Text(
                        text = stringResource(syncDevice.type.labelRes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textMuted,
                    )
                    Text(
                        text = stringResource(R.string.sync_status_connected),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.primary,
                    )
                    Text(
                        text = stringResource(R.string.sync_revoke),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(colors.glassElevated)
                            .border(1.dp, colors.borderGlass, RoundedCornerShape(24.dp))
                            .clickable(
                                onClick = {},
                                role = Role.Button,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            )
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.textMain,
                    )
                }
            }
        }
    }
}

private val SyncDeviceType.labelRes: Int
    get() = when (this) {
        SyncDeviceType.Desktop -> R.string.sync_device_type_desktop
    }
