package me.misa198.airmedy.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.misa198.airmedy.R
import me.misa198.airmedy.ui.components.AirmedyBottomSheet
import me.misa198.airmedy.ui.components.AirmedyTextField
import me.misa198.airmedy.ui.components.AirmedyTextFieldSize
import me.misa198.airmedy.ui.components.ContextActionMenu
import me.misa198.airmedy.ui.components.ContextActionMenuEntry
import me.misa198.airmedy.ui.components.MaterialSymbol
import me.misa198.airmedy.ui.components.MaterialSymbols
import me.misa198.airmedy.ui.theme.LocalAirmedyColors

@Composable
internal fun EqualizerProfileMenuBottomSheet(
    isDefault: Boolean,
    onDismiss: () -> Unit,
    onCreate: () -> Unit,
    onReset: () -> Unit,
    onDelete: () -> Unit,
) {
    AirmedyBottomSheet(
        title = { Text(stringResource(R.string.equalizer_profile_options), style = MaterialTheme.typography.titleMedium) },
        onDismiss = onDismiss,
    ) {
        ContextActionMenu(buildList {
            add(ContextActionMenuEntry.Action(stringResource(R.string.equalizer_profile_create), MaterialSymbols.Add) { onCreate() })
            if (isDefault) {
                add(ContextActionMenuEntry.Action(stringResource(R.string.equalizer_profile_reset), MaterialSymbols.Refresh) { onReset() })
            } else {
                add(ContextActionMenuEntry.Action(stringResource(R.string.equalizer_profile_delete), MaterialSymbols.Delete, destructive = true) { onDelete() })
            }
        })
    }
}

@Composable
internal fun CreateEqualizerProfileBottomSheet(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    val saveLabel = stringResource(R.string.save)
    val valid = name.trim().isNotEmpty()
    AirmedyBottomSheet(
        title = { Text(stringResource(R.string.equalizer_profile_create), style = MaterialTheme.typography.titleMedium) },
        onDismiss = onDismiss,
        trailingAction = {
            EqualizerProfileSheetAction(
                symbol = MaterialSymbols.Check,
                label = saveLabel,
                enabled = valid,
                primary = true,
                onClick = { onCreate(name.trim()) },
            )
        },
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)) {
            AirmedyTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = stringResource(R.string.equalizer_profile_name),
                size = AirmedyTextFieldSize.Medium,
                onDone = { if (valid) onCreate(name.trim()) },
            )
        }
    }
}

@Composable
private fun EqualizerProfileSheetAction(
    symbol: String,
    label: String,
    enabled: Boolean,
    primary: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalAirmedyColors.current
    Box(
        modifier = Modifier.size(48.dp).clip(CircleShape)
            .background(if (primary && enabled) colors.primary else colors.glassElevated)
            .border(1.dp, if (primary && enabled) colors.primary else colors.borderGlass, CircleShape)
            .semantics { contentDescription = label }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        MaterialSymbol(symbol, null, size = 22.dp, tint = if (primary && enabled) colors.onPrimary else colors.textMuted)
    }
}
