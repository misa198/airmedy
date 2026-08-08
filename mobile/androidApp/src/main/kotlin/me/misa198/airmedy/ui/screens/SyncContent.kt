package me.misa198.airmedy.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.R as LucideR
import me.misa198.airmedy.R
import me.misa198.airmedy.SyncUiState
import me.misa198.airmedy.pairing.PairingFailure
import me.misa198.airmedy.ui.components.AirmedyDialog
import me.misa198.airmedy.ui.components.AirmedyPillButton
import me.misa198.airmedy.ui.components.AirmedyPillButtonVariant
import me.misa198.airmedy.ui.components.HeroCard
import me.misa198.airmedy.ui.theme.LocalAirmedyColors

@Composable
internal fun SyncContent(
    syncUiState: SyncUiState,
    onUnpair: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAirmedyColors.current
    var showRevokeConfirmation by remember { mutableStateOf(false) }
    Column(modifier = modifier) {
        when {
            syncUiState.desktop != null -> {
                HeroCard(
                    iconRes = LucideR.drawable.lucide_ic_computer,
                    title = syncUiState.desktop.displayName,
                    description = stringResource(R.string.sync_paired_device_description),
                    belowTitle = {
                        MqttConnectionBadge(
                            isConnected = syncUiState.isMqttConnected,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    },
                )
                AirmedyPillButton(
                    label = stringResource(R.string.sync_revoke),
                    onClick = { showRevokeConfirmation = true },
                    variant = AirmedyPillButtonVariant.Destructive,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
            syncUiState.isPairing -> HeroCard(
                iconRes = LucideR.drawable.lucide_ic_loader_circle,
                title = stringResource(R.string.sync_waiting_title),
                description = stringResource(R.string.sync_waiting_description),
            )
            else -> HeroCard(
                iconRes = LucideR.drawable.lucide_ic_plug,
                title = stringResource(R.string.sync_empty_title),
                description = stringResource(R.string.sync_empty_description),
            )
        }
        syncUiState.failure?.let { failure ->
            Text(
                text = stringResource(failure.messageRes()),
                modifier = Modifier.padding(top = 16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textMuted,
            )
        }
    }
    if (showRevokeConfirmation) {
        AirmedyDialog(
            title = stringResource(R.string.sync_revoke_confirm_title),
            description = stringResource(R.string.sync_revoke_confirm_description),
            dismissLabel = stringResource(R.string.cancel),
            onDismiss = { showRevokeConfirmation = false },
            confirmLabel = stringResource(R.string.sync_revoke),
            onConfirm = { showRevokeConfirmation = false; onUnpair() },
            confirmVariant = AirmedyPillButtonVariant.Destructive,
        )
    }
}

@Composable
private fun MqttConnectionBadge(isConnected: Boolean, modifier: Modifier = Modifier) {
    val colors = LocalAirmedyColors.current
    val label = stringResource(if (isConnected) R.string.sync_status_online else R.string.sync_status_offline)
    val statusColor = if (isConnected) colors.success else colors.primary
    Row(
        modifier = modifier
            .background(colors.buttonSecondary, CircleShape)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.foundation.layout.Spacer(
            modifier = Modifier
                .size(8.dp)
                .background(statusColor, CircleShape),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = statusColor,
        )
    }
}

private fun PairingFailure.messageRes(): Int = when (this) {
    PairingFailure.AlreadyPaired -> R.string.sync_error_already_paired
    is PairingFailure.InvalidQr -> R.string.sync_error_invalid_qr
    is PairingFailure.Transport -> R.string.sync_error_transport
    PairingFailure.TimedOut -> R.string.sync_error_timeout
    PairingFailure.Rejected -> R.string.sync_error_rejected
    PairingFailure.Expired -> R.string.sync_error_expired
    PairingFailure.InvalidResponse -> R.string.sync_error_invalid_response
}
