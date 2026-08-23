package me.misa198.airmedy.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.serialization.json.contentOrNull
import me.misa198.airmedy.R
import me.misa198.airmedy.lyrics.LyricsSearchResult
import me.misa198.airmedy.sync.LibraryTrack
import me.misa198.airmedy.sync.metadataObject
import me.misa198.airmedy.ui.theme.LocalAirmedyColors

internal class FindLyricsSearchState(track: LibraryTrack) {
    var title by mutableStateOf(track.title)
    var artist by mutableStateOf(track.artists.substringBefore(',').trim())
    var results by mutableStateOf(emptyList<LyricsSearchResult>())
    var searching by mutableStateOf(false)
    var hasSearched by mutableStateOf(false)
}

@Composable
internal fun FindLyricsContent(
    track: LibraryTrack,
    state: FindLyricsSearchState,
    onSearch: suspend (LibraryTrack, String, String) -> List<LyricsSearchResult>,
    onPreview: (LyricsSearchResult) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val colors = LocalAirmedyColors.current
    val density = LocalDensity.current
    val topSafeInset = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
    val duration = track.metadataObject()?.get("duration")
        ?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull?.toLongOrNull() }
        ?.let(::formatTrackDuration).orEmpty()

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().height(trackInfoMaxContentHeight(maxHeight, topSafeInset)).padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            FindLyricsField(stringResource(R.string.find_lyrics_track_title), MaterialSymbols.MusicNote, state.title, { state.title = it }, enabled = !state.searching, onDone = {
                if (state.title.isNotBlank() && !state.searching) scope.launch {
                    state.hasSearched = true; state.searching = true
                    state.results = emptyList()
                    state.results = onSearch(track, state.title, state.artist)
                    state.searching = false
                }
            })
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FindLyricsField(stringResource(R.string.find_lyrics_artist), MaterialSymbols.Person, state.artist, { state.artist = it }, Modifier.weight(1f), enabled = !state.searching)
                FindLyricsField(stringResource(R.string.find_lyrics_duration), MaterialSymbols.Schedule, duration, {}, Modifier.weight(0.42f), readOnly = true)
            }
            AirmedyPillButton(
                label = stringResource(if (state.searching) R.string.find_lyrics_searching else R.string.find_lyrics_search),
                onClick = {
                    if (state.title.isNotBlank() && !state.searching) scope.launch {
                        state.hasSearched = true; state.searching = true
                        state.results = emptyList()
                        state.results = onSearch(track, state.title, state.artist)
                        state.searching = false
                    }
                },
                variant = AirmedyPillButtonVariant.Primary,
                enabled = state.title.isNotBlank() && !state.searching,
                modifier = Modifier.padding(top = 12.dp),
            )
            if (state.hasSearched && state.results.isEmpty() && !state.searching) {
                Text(stringResource(R.string.find_lyrics_no_results), Modifier.align(Alignment.CenterHorizontally).padding(top = 36.dp), color = colors.textMuted)
            } else {
                LazyColumn(Modifier.weight(1f).padding(top = 24.dp)) {
                    itemsIndexed(state.results) { index, result ->
                        if (index == 0) Box(Modifier.fillMaxWidth().height(1.dp).background(colors.borderGlass))
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 56.dp)
                                .clickable { onPreview(result) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(result.trackName, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = colors.textMain, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(result.artistName, style = MaterialTheme.typography.bodySmall, color = colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 12.dp)) {
                                Text(result.provider.uppercase(), style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
                                formatTrackDuration(result.duration.toLong()).takeIf(String::isNotBlank)?.let { resultDuration ->
                                    Text(resultDuration, style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
                                }
                            }
                        }
                        if (index < state.results.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(colors.borderGlass))
                    }
                }
            }
        }
    }
}

@Composable
internal fun FindLyricsPreviewContent(
    track: LibraryTrack,
    lyric: LyricsSearchResult,
    onSelected: suspend (String, LyricsSearchResult) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val colors = LocalAirmedyColors.current
    val density = LocalDensity.current
    val topSafeInset = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().height(trackInfoMaxContentHeight(maxHeight, topSafeInset)).padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            LazyColumn(Modifier.weight(1f)) {
                item { Text(lyric.content, style = MaterialTheme.typography.bodyMedium, color = colors.textMain) }
            }
            AirmedyPillButton(stringResource(R.string.find_lyrics_select), { scope.launch { onSelected(track.id, lyric); onDismiss() } }, AirmedyPillButtonVariant.Primary, Modifier.padding(vertical = 12.dp))
        }
    }
}

@Composable
private fun FindLyricsField(
    placeholder: String,
    leadingSymbol: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    enabled: Boolean = true,
    onDone: () -> Unit = {},
) {
    AirmedyTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = placeholder,
        leadingSymbol = leadingSymbol,
        modifier = modifier,
        showClearButton = !readOnly,
        readOnly = readOnly,
        enabled = enabled,
        onDone = onDone,
    )
}
