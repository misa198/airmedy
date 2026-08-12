package me.misa198.airmedy.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import me.misa198.airmedy.R
import me.misa198.airmedy.ui.components.ActionList
import me.misa198.airmedy.ui.components.ActionListContainerStyle
import me.misa198.airmedy.ui.components.ActionListItem
import me.misa198.airmedy.ui.components.LabeledCard

@Composable
internal fun PlaybackSettingsContent(
    onSongTransitionSelected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        ActionList(
            items = listOf(
                ActionListItem(
                    labelRes = R.string.song_transition_title,
                    onClick = onSongTransitionSelected,
                ),
            ),
            containerStyle = ActionListContainerStyle.Card,
        )
    }
}

@Composable
internal fun SongTransitionContent(
    crossfadeSeconds: Int,
    lastEnabledCrossfadeSeconds: Int,
    onCrossfadeSecondsChanged: (Int) -> Unit,
    blendArtworkDuringCrossfade: Boolean,
    onBlendArtworkDuringCrossfadeChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val enabled = crossfadeSeconds > 0
    val displayedSeconds = if (enabled) crossfadeSeconds else lastEnabledCrossfadeSeconds
    val crossfadeLabel = stringResource(R.string.playback_crossfade)
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        me.misa198.airmedy.ui.components.Card {
            ActionList(
                items = listOf(
                    ActionListItem(
                        labelRes = R.string.playback_crossfade,
                        trailingContent = {
                            Switch(
                                checked = enabled,
                                onCheckedChange = { checked ->
                                    onCrossfadeSecondsChanged(if (checked) lastEnabledCrossfadeSeconds else 0)
                                },
                            )
                        },
                        onClick = {
                            onCrossfadeSecondsChanged(if (enabled) 0 else lastEnabledCrossfadeSeconds)
                        },
                    ),
                ),
                containerStyle = ActionListContainerStyle.Plain,
            )
        }
        AnimatedVisibility(
            visible = enabled,
            enter = fadeIn(animationSpec = tween(durationMillis = 200)),
            exit = fadeOut(animationSpec = tween(durationMillis = 150)),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                LabeledCard(label = stringResource(R.string.playback_crossfade_duration)) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.playback_crossfade_duration_value, displayedSeconds),
                            style = MaterialTheme.typography.labelMedium,
                            color = me.misa198.airmedy.ui.theme.LocalAirmedyColors.current.textMuted,
                        )
                        CrossfadeDurationSlider(
                            seconds = displayedSeconds,
                            onSecondsChanged = onCrossfadeSecondsChanged,
                            enabled = enabled,
                            modifier = Modifier.semantics { contentDescription = crossfadeLabel },
                        )
                    }
                }
                me.misa198.airmedy.ui.components.Card {
                    ActionList(
                        items = listOf(
                            ActionListItem(
                                labelRes = R.string.playback_blend_artwork_during_crossfade,
                                trailingContent = {
                                    Switch(
                                        checked = blendArtworkDuringCrossfade,
                                        onCheckedChange = onBlendArtworkDuringCrossfadeChanged,
                                    )
                                },
                                onClick = {
                                    onBlendArtworkDuringCrossfadeChanged(!blendArtworkDuringCrossfade)
                                },
                            ),
                        ),
                        containerStyle = ActionListContainerStyle.Plain,
                    )
                }
            }
        }
    }
}
