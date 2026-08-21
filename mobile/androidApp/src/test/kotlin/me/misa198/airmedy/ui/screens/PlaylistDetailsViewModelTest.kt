package me.misa198.airmedy.ui.screens

import me.misa198.airmedy.sync.LibraryPlaylist
import me.misa198.airmedy.sync.LibraryTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaylistDetailsViewModelTest {
    private val first = LibraryTrack("first", "First", "Artist", metadataJson = """{"duration":61}""")
    private val second = LibraryTrack("second", "Second", "Artist", metadataJson = """{"duration":3660}""")

    @Test
    fun resolvesTracksInPlaylistOrderAndSkipsUnavailableIds() {
        val state = PlaylistDetailsUiState(
            playlists = listOf(LibraryPlaylist("mix", "Mix", listOf("second", "missing", "first"), "{}")),
            allTracks = listOf(first, second),
        )

        val result = playlistDetailsUiStateFor(state, "mix")

        assertEquals(listOf("second", "first"), result.tracks.map { it.id })
        assertEquals(3721L, playlistTotalDurationSeconds(result.tracks))
    }

    @Test
    fun derivesFavoritesTracksFromSyncedTrackMetadata() {
        val favorite = LibraryTrack("favorite", "Favorite", "Artist", metadataJson = """{"is_favorite":true}""")
        val notFavorite = LibraryTrack("other", "Other", "Artist", metadataJson = """{"is_favorite":false}""")
        val result = playlistDetailsUiStateFor(
            PlaylistDetailsUiState(allTracks = listOf(favorite, notFavorite)),
            FavoritesPlaylistId,
        )

        assertEquals(FavoritesPlaylistId, result.playlist?.id)
        assertEquals(listOf("favorite"), result.tracks.map { it.id })
    }

    @Test
    fun returnsEmptyStateForUnknownPlaylist() {
        assertNull(playlistDetailsUiStateFor(PlaylistDetailsUiState(), "missing").playlist)
    }

    @Test
    fun formatsDurationLikeDesktop() {
        val day = { value: Long -> "$value d" }
        val hour = { value: Long -> "$value hr" }
        val minute = { value: Long -> "$value min" }
        val second = { value: Long -> "$value s" }

        assertEquals("1 hr 1 min", formatPlaylistTotalDuration(3660L, day, hour, minute, second))
        assertEquals("2 min", formatPlaylistTotalDuration(120L, day, hour, minute, second))
        assertEquals("59 s", formatPlaylistTotalDuration(59L, day, hour, minute, second))
        assertEquals("52 d 3 hr", formatPlaylistTotalDuration(52L * 86_400L + 3L * 3_600L + 30L * 60L, day, hour, minute, second))
    }
}
