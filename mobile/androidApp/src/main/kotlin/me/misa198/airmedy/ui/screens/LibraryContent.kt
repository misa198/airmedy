package me.misa198.airmedy.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.misa198.airmedy.R
import me.misa198.airmedy.sync.AndroidSyncRuntime
import me.misa198.airmedy.sync.LibraryTrack
import me.misa198.airmedy.ui.components.HeroCard
import me.misa198.airmedy.ui.theme.LocalAirmedyColors

@Composable
internal fun LibraryContent(modifier: Modifier = Modifier, contentPadding: PaddingValues = PaddingValues()) {
    val tracks by AndroidSyncRuntime.tracks().collectAsStateWithLifecycle(emptyList())
    if (tracks.isEmpty()) {
        HeroCard(
            title = stringResource(R.string.library_empty_title),
            description = stringResource(R.string.library_empty_description),
            modifier = modifier.padding(contentPadding),
        ) { }
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(tracks) { track -> LibraryTrackRow(track) }
    }
}

@Composable
private fun LibraryTrackRow(track: LibraryTrack) {
    val colors = LocalAirmedyColors.current
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
        Text(track.title, style = MaterialTheme.typography.bodyLarge, color = colors.textMain)
        Text(
            listOf(track.artists, track.album).filter(String::isNotBlank).joinToString(" · "),
            modifier = Modifier.padding(top = 2.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textMuted,
        )
    }
}
