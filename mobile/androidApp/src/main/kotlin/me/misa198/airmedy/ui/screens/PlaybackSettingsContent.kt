package me.misa198.airmedy.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
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
import me.misa198.airmedy.ui.components.AirmedyTrackSlider
import me.misa198.airmedy.ui.theme.LocalAirmedyColors
import me.misa198.airmedy.player.EqualizerFrequenciesHz
import me.misa198.airmedy.player.EqualizerSettings
import me.misa198.airmedy.player.normalizeEqGain

@Composable
internal fun PlaybackSettingsContent(
    showFullscreenQualityBadge: Boolean,
    onShowFullscreenQualityBadgeChanged: (Boolean) -> Unit,
    onSongTransitionSelected: () -> Unit,
    onVolumeNormalizationSelected: () -> Unit,
    onEqualizerSelected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                ActionListItem(
                    labelRes = R.string.equalizer_title,
                    onClick = onEqualizerSelected,
                ),
                ActionListItem(
                    labelRes = R.string.playback_show_quality_badge,
                    trailingContent = {
                        Switch(
                            checked = showFullscreenQualityBadge,
                            onCheckedChange = onShowFullscreenQualityBadgeChanged,
                        )
                    },
                    onClick = { onShowFullscreenQualityBadgeChanged(!showFullscreenQualityBadge) },
                ),
            ),
            containerStyle = ActionListContainerStyle.Card,
            dividerStyle = ActionListDividerStyle.FullWidth,
        )
    }
}

@Composable
internal fun EqualizerContent(
    settings: EqualizerSettings,
    onEnabledChanged: (Boolean) -> Unit,
    onPresetSelected: (String) -> Unit,
    onBandChanged: (Int, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val enabledLabel = stringResource(R.string.equalizer_enable)
    val colors = LocalAirmedyColors.current
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        me.misa198.airmedy.ui.components.Card {
            ActionList(
                items = listOf(
                    ActionListItem(
                        labelRes = R.string.equalizer_enable,
                        trailingContent = { Switch(checked = settings.enabled, onCheckedChange = onEnabledChanged) },
                        onClick = { onEnabledChanged(!settings.enabled) },
                    ),
                ),
                containerStyle = ActionListContainerStyle.Plain,
            )
            ActionListDivider(style = ActionListDividerStyle.FullWidth)
            Selection(
                labelRes = R.string.equalizer_preset,
                options = settings.profiles.map { SelectionOption(it.key, label = it.name) },
                selectedValue = settings.presetKey,
                onValueSelected = onPresetSelected,
            )
        }
        LabeledCard(label = stringResource(R.string.equalizer_bands)) {
            Column(
                modifier = Modifier.padding(start = 16.dp, top = 24.dp, end = 16.dp, bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                    settings.gainsDb.forEachIndexed { index, gain ->
                        val frequency = EqualizerFrequenciesHz[index]
                        val frequencyLabel = if (frequency < 1_000) {
                            stringResource(R.string.equalizer_frequency_hz, frequency)
                        } else {
                            stringResource(R.string.equalizer_frequency_khz, frequency / 1_000)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = frequencyLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.textMuted,
                            )
                            Text(
                                text = stringResource(R.string.equalizer_gain_value, gain),
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.textMuted,
                            )
                        }
                        AirmedyTrackSlider(
                            value = gain,
                            valueRange = -12f..12f,
                            onValueChange = { onBandChanged(index, normalizeEqGain(it)) },
                            modifier = Modifier.semantics { contentDescription = "$enabledLabel $frequencyLabel" },
                            trackHeight = 6.dp,
                            activeTrackColor = colors.primary,
                            inactiveTrackColor = colors.buttonSecondary,
                            trackAlignment = Alignment.TopCenter,
                        )
                    }
                }
        }
    }
}

@Composable
internal fun VolumeNormalizationContent(
    normalizationAvailable: Boolean,
    normalization: me.misa198.airmedy.player.NormalizationSettings,
    onNormalizationChanged: (me.misa198.airmedy.player.NormalizationSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
        verticalArrangement = Arrangement.spacedBy(12.dp),
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
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
