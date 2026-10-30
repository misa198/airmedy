package me.misa198.airmedy.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable

/** Lives in the host so seek confirmations still reconcile while a placeholder is showing. */
internal class PlayerSeekState(fraction: Float? = null, awaiting: Boolean = false) {
    val pendingFraction = mutableStateOf(fraction)
    val awaitingConfirmation = mutableStateOf(awaiting)

    companion object {
        val Saver = listSaver<PlayerSeekState, Any>(
            save = { listOf(it.pendingFraction.value ?: -1f, it.awaitingConfirmation.value) },
            restore = { PlayerSeekState((it[0] as Float).takeIf { fraction -> fraction >= 0f }, it[1] as Boolean) },
        )
    }
}

@Composable
internal fun rememberPlayerSeekState(trackId: String): PlayerSeekState =
    rememberSaveable(trackId, saver = PlayerSeekState.Saver) { PlayerSeekState() }
