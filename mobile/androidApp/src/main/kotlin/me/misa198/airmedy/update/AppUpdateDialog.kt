package me.misa198.airmedy.update

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import me.misa198.airmedy.R
import me.misa198.airmedy.ui.components.AirmedyDialog

@Composable
internal fun AppUpdateDialog(update: AppUpdate, onDismiss: () -> Unit, onUpdate: (String) -> Unit) {
    AirmedyDialog(
        title = stringResource(R.string.app_update_title),
        description = stringResource(R.string.app_update_version, update.version),
        dismissLabel = stringResource(R.string.app_update_later),
        onDismiss = onDismiss,
        confirmLabel = stringResource(R.string.app_update_action),
        onConfirm = {
            onDismiss()
            onUpdate(update.releaseUrl)
        },
        content = {
            ReleaseNotesMarkdown(
                markdown = update.notes.ifBlank { stringResource(R.string.app_update_no_notes) },
                version = update.version,
                onOpenUrl = onUpdate,
            )
        },
    )
}
