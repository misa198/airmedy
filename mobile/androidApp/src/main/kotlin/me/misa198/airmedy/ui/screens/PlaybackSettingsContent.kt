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
import me.misa198.airmedy.ui.components.ActionListDivider
import me.misa198.airmedy.ui.components.ActionListDividerStyle
import me.misa198.airmedy.ui.components.ActionListItem
import me.misa198.airmedy.ui.components.LabeledCard
import me.misa198.airmedy.ui.components.Selection
import me.misa198.airmedy.ui.components.SelectionOption

@Composable
internal fun PlaybackSettingsContent(
    onSongTransitionSelected: () -> Unit,
    onVolumeNormalizationSelected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        ActionList(
            items = listOf(
                ActionListItem(
                    labelRes = R.string.song_transition_title,
                    onClick = onSongTransitionSelected,
                ),
                ActionListItem(
                    labelRes = R.string.playback_volume_normalization,
                    onClick = onVolumeNormalizationSelected,
                ),
            ),
            containerStyle = ActionListContainerStyle.Card,
            dividerStyle = ActionListDividerStyle.FullWidth,
        )
    }
}

@Composable
internal fun VolumeNormalizationContent(
    normalizationAvailable: Boolean,
    normalization: me.misa198.airmedy.player.NormalizationSettings,
    onNormalizationChanged: (me.misa198.airmedy.player.NormalizationSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        me.misa198.airmedy.ui.components.Card {
            ActionList(
                items = listOf(
                    ActionListItem(
                        labelRes = R.string.playback_volume_normalization,
                        trailingContent = { Switch(checked = normalization.enabled && normalizationAvailable, enabled = normalizationAvailable, onCheckedChange = { onNormalizationChanged(normalization.copy(enabled = it)) }) },
                        onClick = { if (normalizationAvailable) onNormalizationChanged(normalization.copy(enabled = !normalization.enabled)) },
                    ),
                    ActionListItem(
                        labelRes = R.string.playback_normalization_prevent_clip,
                        trailingContent = { Switch(checked = normalization.preventClip, enabled = normalizationAvailable && normalization.enabled, onCheckedChange = { onNormalizationChanged(normalization.copy(preventClip = it)) }) },
                        onClick = { if (normalizationAvailable && normalization.enabled) onNormalizationChanged(normalization.copy(preventClip = !normalization.preventClip)) },
                    ),
                ),
                containerStyle = ActionListContainerStyle.Plain,
                dividerStyle = ActionListDividerStyle.FullWidth,
            )
            if (normalization.enabled && normalizationAvailable) {
                ActionListDivider(style = ActionListDividerStyle.FullWidth)
                Selection(
                    labelRes = R.string.playback_normalization_mode,
                    options = listOf(
                        SelectionOption(me.misa198.airmedy.player.NormalizationMode.Track, R.string.playback_normalization_mode_track),
                        SelectionOption(me.misa198.airmedy.player.NormalizationMode.Album, R.string.playback_normalization_mode_album),
                    ),
                    selectedValue = normalization.mode,
                    onValueSelected = { onNormalizationChanged(normalization.copy(mode = it)) },
                )
            }
        }
        AnimatedVisibility(
            visible = normalization.enabled && normalizationAvailable,
            enter = fadeIn(animationSpec = tween(durationMillis = 200)),
            exit = fadeOut(animationSpec = tween(durationMillis = 150)),
        ) {
            LabeledCard(label = stringResource(R.string.playback_normalization_target)) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(text = stringResource(R.string.playback_normalization_target_value, normalization.targetLufs.toInt()), style = MaterialTheme.typography.labelMedium, color = me.misa198.airmedy.ui.theme.LocalAirmedyColors.current.textMuted)
                    LufsTargetSlider(
                        targetLufs = normalization.targetLufs,
                        enabled = true,
                        onTargetChanged = { onNormalizationChanged(normalization.copy(targetLufs = it)) },
                    )
                }
            }
        }
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
