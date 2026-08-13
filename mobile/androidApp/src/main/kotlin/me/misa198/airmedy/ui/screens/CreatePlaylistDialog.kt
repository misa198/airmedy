package me.misa198.airmedy.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import me.misa198.airmedy.R
import me.misa198.airmedy.ui.components.AirmedyPillButton
import me.misa198.airmedy.ui.components.AirmedyPillButtonVariant
import me.misa198.airmedy.ui.components.AirmedyTextField
import me.misa198.airmedy.ui.components.AirmedyTextFieldSize
import me.misa198.airmedy.ui.theme.LocalAirmedyColors

@Composable
internal fun CreatePlaylistDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    val colors = LocalAirmedyColors.current
    var name by remember { mutableStateOf("") }
    val valid = name.trim().isNotEmpty()
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(36.dp)).background(colors.card),
        ) {
            Column(
                modifier = Modifier.padding(start = 20.dp, top = 24.dp, end = 20.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(stringResource(R.string.playlist_create_title), style = MaterialTheme.typography.titleLarge, color = colors.textMain)
                AirmedyTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth().testTag("playlist-name-input"),
                    placeholder = stringResource(R.string.playlist_name),
                    size = AirmedyTextFieldSize.Small,
                    onDone = { if (valid) onCreate(name.trim()) },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().background(colors.glassElevated).padding(horizontal = 12.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AirmedyPillButton(stringResource(R.string.cancel), onDismiss, AirmedyPillButtonVariant.Secondary, Modifier.weight(1f))
                AirmedyPillButton(stringResource(R.string.create), { onCreate(name.trim()) }, AirmedyPillButtonVariant.Primary, Modifier.weight(1f).testTag("playlist-create-button"), enabled = valid)
            }
        }
    }
}
