package me.misa198.airmedy.ui.screens

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import me.misa198.airmedy.ui.components.rememberArtworkThumbnail
import me.misa198.airmedy.R
import me.misa198.airmedy.ui.components.AirmedyBottomSheet
import me.misa198.airmedy.ui.components.AirmedyTextField
import me.misa198.airmedy.ui.components.AirmedyTextFieldSize
import me.misa198.airmedy.ui.components.MaterialSymbol
import me.misa198.airmedy.ui.components.MaterialSymbols
import me.misa198.airmedy.ui.theme.LocalAirmedyColors

@Composable
internal fun CreatePlaylistBottomSheet(onDismiss: () -> Unit, onCreate: (String, Uri?) -> Unit) {
    PlaylistEditorBottomSheet(
        title = stringResource(R.string.playlist_create_title),
        actionLabel = stringResource(R.string.create),
        onDismiss = onDismiss,
        onSave = { name, artwork, _ -> onCreate(name, artwork) },
    )
}

@Composable
internal fun EditPlaylistBottomSheet(
    initialName: String,
    artworkPath: String?,
    showNameInput: Boolean = true,
    onDismiss: () -> Unit,
    onSave: (String, Uri?, Boolean) -> Unit,
) {
    PlaylistEditorBottomSheet(
        title = stringResource(R.string.playlist_edit_title),
        actionLabel = stringResource(R.string.save),
        initialName = initialName,
        artworkPath = artworkPath,
        canClearArtwork = artworkPath != null,
        showNameInput = showNameInput,
        onDismiss = onDismiss,
        onSave = { name, artwork, clear -> onSave(name, artwork, clear) },
    )
}

@Composable
private fun PlaylistEditorBottomSheet(
    title: String,
    actionLabel: String,
    onDismiss: () -> Unit,
    onSave: (String, Uri?, Boolean) -> Unit,
    initialName: String = "",
    artworkPath: String? = null,
    canClearArtwork: Boolean = false,
    showNameInput: Boolean = true,
) {
    val colors = LocalAirmedyColors.current
    val context = LocalContext.current
    var name by remember(initialName) { mutableStateOf(initialName) }
    var artworkUri by remember { mutableStateOf<Uri?>(null) }
    var clearArtwork by remember { mutableStateOf(false) }
    val imageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> artworkUri = uri; if (uri != null) clearArtwork = false }
    val artwork = remember(artworkUri) {
        artworkUri?.let { uri -> context.contentResolver.openInputStream(uri)?.use(BitmapFactory::decodeStream)?.asImageBitmap() }
    }
    val existingArtwork = rememberArtworkThumbnail(artworkPath, targetPx = 336)
    val valid = !showNameInput || name.trim().isNotEmpty()
    AirmedyBottomSheet(
        title = { Text(title, style = MaterialTheme.typography.titleMedium, color = colors.textMain) },
        onDismiss = onDismiss,
        leadingAction = {
            CreatePlaylistSheetIconButton(MaterialSymbols.Close, stringResource(R.string.cancel), onDismiss)
        },
        trailingAction = {
            CreatePlaylistSheetIconButton(
                MaterialSymbols.Check, actionLabel,
                { onSave(name.trim(), artworkUri, clearArtwork) }, enabled = valid,
                modifier = Modifier.testTag("playlist-create-button"),
                primary = true,
            )
        },
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier.size(168.dp).clip(RoundedCornerShape(18.dp)).background(colors.glassElevated)
                    .border(1.dp, colors.borderGlass, RoundedCornerShape(18.dp)).testTag("playlist-artwork-picker")
                    .clickable(onClick = { imageLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }),
                contentAlignment = Alignment.Center,
            ) {
                if (artwork != null) Image(artwork, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
                else if (!clearArtwork && existingArtwork != null) Image(existingArtwork, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CreatePlaylistSheetIconButton(
                        MaterialSymbols.Image,
                        stringResource(R.string.playlist_choose_artwork),
                        { imageLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        primary = true,
                    )
                    if ((canClearArtwork || artworkUri != null) && !clearArtwork) {
                        CreatePlaylistSheetIconButton(
                            MaterialSymbols.Close,
                            stringResource(R.string.playlist_clear_artwork),
                            { artworkUri = null; clearArtwork = canClearArtwork },
                        )
                    }
                }
            }
            if (showNameInput) {
                AirmedyTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth().padding(top = 24.dp).testTag("playlist-name-input"),
                    placeholder = stringResource(R.string.playlist_name),
                    size = AirmedyTextFieldSize.Medium,
                    onDone = { if (valid) onSave(name.trim(), artworkUri, clearArtwork) },
                )
            }
        }
    }
}

@Composable
private fun CreatePlaylistSheetIconButton(
    symbol: String,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    primary: Boolean = false,
) {
    val colors = LocalAirmedyColors.current
    Box(
        modifier = modifier.size(48.dp).clip(RoundedCornerShape(24.dp))
            .background(if (primary) if (enabled) colors.primary else colors.buttonSecondary else colors.glassElevated)
            .border(1.dp, if (primary && enabled) colors.primary else colors.borderGlass, RoundedCornerShape(24.dp))
            .semantics { contentDescription = label }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        MaterialSymbol(
            symbol,
            null,
            size = 22.dp,
            tint = if (primary && enabled) colors.onPrimary else if (enabled) colors.textMain else colors.textMuted,
        )
    }
}
