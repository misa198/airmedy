package me.misa198.airmedy.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import me.misa198.airmedy.R
import me.misa198.airmedy.lyrics.RomanizationUiState
import me.misa198.airmedy.ui.components.AirmedyIconButton
import me.misa198.airmedy.ui.components.AirmedyIconButtonVariant
import me.misa198.airmedy.ui.components.MaterialSymbols
import me.misa198.airmedy.ui.components.sliderFilledTrackColor
import me.misa198.airmedy.ui.theme.LocalAirmedyColors

@Composable
internal fun RomanizationToggle(state: RomanizationUiState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalAirmedyColors.current
    val background by animateColorAsState(
        if (state.enabled) sliderFilledTrackColor(colors, false) else fullScreenSecondaryControlBackground(colors),
        tween(250), label = "romanization-background",
    )
    val tint by animateColorAsState(
        if (state.enabled) colors.playerBackdrop.copy(alpha = 0.72f) else colors.onPrimary,
        tween(250), label = "romanization-icon",
    )
    val description = stringResource(when {
        state.loading -> R.string.player_romanization_loading
        state.error -> R.string.player_romanization_error
        state.mandarinDefault -> R.string.player_romanization_mandarin
        else -> R.string.player_romanization_hint
    })
    Box(modifier, contentAlignment = Alignment.Center) {
        AirmedyIconButton(
            symbol = MaterialSymbols.Translate,
            label = stringResource(if (state.enabled) R.string.player_romanization_hide else R.string.player_romanization_show),
            onClick = onClick,
            variant = AirmedyIconButtonVariant.Glass,
            tint = tint, glassColor = background, showGlassBorder = false,
            circleSize = 48.dp, iconSize = 20.dp,
            modifier = Modifier.testTag("romanization_toggle").semantics {
                selected = state.enabled
                stateDescription = description
            },
        )
        if (state.loading) CircularProgressIndicator(Modifier.size(36.dp), color = tint, strokeWidth = 2.dp)
    }
}
