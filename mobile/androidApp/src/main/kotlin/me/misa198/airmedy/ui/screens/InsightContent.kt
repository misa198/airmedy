package me.misa198.airmedy.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.misa198.airmedy.R
import me.misa198.airmedy.ui.components.DiscCard
import me.misa198.airmedy.ui.components.MaterialSymbols
import me.misa198.airmedy.ui.components.TrackRow
import me.misa198.airmedy.ui.components.formatFileSize
import me.misa198.airmedy.ui.theme.LocalAirmedyColors

@Composable
internal fun InsightContent(
    state: InsightUiState,
    listState: LazyListState,
    contentPadding: PaddingValues,
    onLibraryPeriodSelected: (InsightPeriod) -> Unit,
    onListeningPeriodSelected: (InsightPeriod) -> Unit,
    onSourceSelected: (InsightSourceFilter) -> Unit,
    onArtistClick: (String) -> Unit,
    onTrackClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expandedTracks by rememberSaveable { mutableStateOf(false) }
    val visibleTracks = if (expandedTracks) state.listening.topTracks else state.listening.topTracks.take(5)
    LazyColumn(
        modifier = modifier.testTag("insight-page"),
        state = listState,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SectionHeader(R.string.insight_library_section, Modifier.weight(1f))
                InsightPeriodSelector(state.libraryPeriod, onLibraryPeriodSelected, Modifier.testTag("insight-library-period"))
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard(
                    R.string.insight_library_size,
                    formatFileSize(state.library.bytes),
                    Modifier.fillMaxWidth().testTag("insight-library-size"),
                )
                MetricPair(
                    R.string.insight_total_tracks to number(state.library.tracks),
                    R.string.insight_total_albums to number(state.library.albums),
                )
                MetricPair(
                    R.string.insight_total_artists to number(state.library.artists),
                    R.string.insight_total_playlists to number(state.library.playlists),
                    secondModifier = Modifier.testTag("insight-playlists"),
                )
            }
        }
        item {
            ResponsivePair(
                first = {
                    ChartCard(R.string.insight_library_growth, MaterialSymbols.LegendToggle) {
                        if (state.library.tracks > 0) {
                            InsightLineChart(state.library.growth, stringResource(R.string.insight_library_growth_description))
                        } else EmptyInsightText(R.string.insight_no_library_data)
                    }
                },
                second = { QualityCard(state.library.quality) },
            )
        }
        item {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                SectionHeader(R.string.insight_listening_section, Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SourceControl(state, onSourceSelected, Modifier.testTag("insight-source-filter"))
                    InsightPeriodSelector(state.listeningPeriod, onListeningPeriodSelected, Modifier.testTag("insight-listening-period"))
                }
            }
        }
        item { ListeningHero(state.listening) }
        item {
            MetricPair(
                R.string.insight_streak to stringResource(R.string.insight_days_value, state.listening.streakDays),
                R.string.insight_average_session to formatDuration(state.listening.averageSessionSeconds),
            )
        }
        item {
            ResponsivePair(
                first = { GenreCard(state.listening.genres) },
                second = { OutcomeCard(state.listening) },
            )
        }
        item {
            ChartCard(
                R.string.insight_top_artists,
                MaterialSymbols.People,
                contentPadding = PaddingValues(vertical = 20.dp),
                headerPadding = PaddingValues(horizontal = 20.dp),
            ) {
                if (state.listening.topArtists.isEmpty()) EmptyInsightText(R.string.insight_no_top_artists, Modifier.padding(horizontal = 20.dp))
                else LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(state.listening.topArtists, key = { it.id }) { artist ->
                        DiscCard(
                            title = artist.name,
                            subtitle = formatDuration(artist.listenedSeconds),
                            artworkPath = artist.artworkPath,
                            artworkShape = CircleShape,
                            modifier = Modifier.size(112.dp, 168.dp),
                            fallbackSymbol = MaterialSymbols.People,
                            onClick = { onArtistClick(artist.id) },
                        )
                    }
                }
            }
        }
        item {
            ChartCard(R.string.insight_top_tracks, MaterialSymbols.MusicNote) {
                if (visibleTracks.isEmpty()) EmptyInsightText(R.string.insight_no_top_tracks)
                else {
                    visibleTracks.forEachIndexed { index, item ->
                        TrackRow(
                            title = item.track.title,
                            artist = item.track.artists,
                            artworkPath = item.track.artworkPath,
                            contentPadding = PaddingValues(vertical = 6.dp),
                            onClick = { onTrackClick(item.track.id) },
                            trailingContent = {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(formatDuration(item.listenedSeconds), style = MaterialTheme.typography.labelMedium, color = LocalAirmedyColors.current.textMuted)
                                    Text(stringResource(R.string.insight_plays_value, item.playCount), style = MaterialTheme.typography.labelSmall, color = LocalAirmedyColors.current.textMuted)
                                }
                            },
                        )
                        if (index < visibleTracks.lastIndex) {
                            HorizontalDivider(
                                color = LocalAirmedyColors.current.borderGlass,
                                thickness = 1.dp,
                                modifier = Modifier.testTag("insight-track-divider"),
                            )
                        }
                    }
                    if (state.listening.topTracks.size > 5) {
                        Text(
                            text = stringResource(if (expandedTracks) R.string.insight_show_less else R.string.insight_show_more),
                            color = LocalAirmedyColors.current.textMuted,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier
                                .align(Alignment.End)
                                .heightIn(min = 48.dp)
                                .clickable(role = Role.Button) { expandedTracks = !expandedTracks }
                                .padding(horizontal = 12.dp, vertical = 15.dp)
                                .testTag("insight-top-tracks-toggle"),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ListeningHero(state: ListeningInsightState) {
    ChartCard(R.string.insight_total_time, MaterialSymbols.MusicNote) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Column {
                Text(formatDuration(state.listenedSeconds), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = LocalAirmedyColors.current.textMain)
                state.changePercent?.let {
                    Text(stringResource(R.string.insight_change_value, it), style = MaterialTheme.typography.labelMedium, color = LocalAirmedyColors.current.textMuted)
                }
            }
            Text(stringResource(R.string.insight_plays_value, state.plays), style = MaterialTheme.typography.labelLarge, color = LocalAirmedyColors.current.textMuted)
        }
        if (state.listenedSeconds > 0) {
            InsightBarChart(state.activity, stringResource(R.string.insight_activity_description), Modifier.padding(top = 20.dp))
        } else EmptyInsightText(R.string.insight_no_listening_data)
    }
}

@Composable
private fun QualityCard(values: List<InsightQuality>) {
    ChartCard(R.string.insight_audio_quality, MaterialSymbols.GraphicEq) {
        if (values.isEmpty()) EmptyInsightText(R.string.insight_no_audio_quality)
        else {
            val colors = LocalAirmedyColors.current
            InsightDonut(values.map { it.count to qualityColor(it.quality) }, number(values.sumOf { it.count }), stringResource(R.string.insight_audio_quality_description))
            values.forEach { BreakdownRow(qualityLabel(it.quality), it.count, values.sumOf { value -> value.count }, qualityColor(it.quality)) }
        }
    }
}

@Composable
private fun GenreCard(values: List<InsightBreakdown>) {
    ChartCard(R.string.insight_genre_distribution, MaterialSymbols.Label) {
        if (values.isEmpty()) EmptyInsightText(R.string.insight_no_genres)
        else {
            val total = values.sumOf { it.listenedSeconds }
            InsightDonut(values.mapIndexed { index, value -> value.listenedSeconds to insightPalette(index) }, formatDuration(total), stringResource(R.string.insight_genre_description))
            values.forEachIndexed { index, value -> BreakdownRow(if (value.isOther) stringResource(R.string.insight_other) else value.name, value.listenedSeconds, total, insightPalette(index), true) }
        }
    }
}

@Composable
private fun OutcomeCard(state: ListeningInsightState) {
    val values = listOf(
        stringResource(R.string.insight_completed) to state.completed,
        stringResource(R.string.insight_skipped) to state.skipped,
        stringResource(R.string.insight_stopped) to state.stopped,
    ).sortedByDescending { it.second }
    val colors = LocalAirmedyColors.current
    val palette = listOf(colors.primary, colors.qualityHiRes, colors.textMuted)
    val total = values.sumOf { it.second }
    ChartCard(R.string.insight_playback_outcomes, MaterialSymbols.MusicNote) {
        if (total == 0) EmptyInsightText(R.string.insight_no_outcomes)
        else {
            InsightDonut(values.mapIndexed { index, value -> value.second to palette[index] }, stringResource(R.string.insight_attempts_value, total), stringResource(R.string.insight_outcomes_description))
            values.forEachIndexed { index, value -> BreakdownRow(value.first, value.second, total, palette[index]) }
        }
    }
}
