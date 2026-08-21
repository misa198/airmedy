package me.misa198.airmedy.ui.screens

import me.misa198.airmedy.sync.LibraryAlbum
import me.misa198.airmedy.sync.LibraryArtist
import me.misa198.airmedy.sync.LibraryTrack
import org.junit.Assert.assertEquals
import org.junit.Test

class ArtistDetailsViewModelTest {
    @Test
    fun detailsSortAlbumsByCanonicalSortTitleAndTracksByDiscThenTrackNumber() {
        val state = ArtistDetailsUiState(
            allArtists = listOf(LibraryArtist("artist", "Artist")),
            allAlbums = listOf(
                LibraryAlbum("z", "Alpha", "Artist", sortTitle = "Zulu", sortArtist = "Zulu Artist"),
                LibraryAlbum("a", "Zulu", "Artist", sortTitle = "Alpha", sortArtist = "Alpha Artist"),
            ),
            tracks = listOf(
                track("z-two", "z", disc = 1, number = 2, order = 2),
                track("a-missing", "a", order = 0),
                track("z-one", "z", disc = 1, number = 1, order = 1),
                track("a-one", "a", disc = 1, number = 1, order = 1),
                LibraryTrack("other", "Other", "Other", albumId = "a", metadataJson = metadata("other")),
            ),
        )

        val details = artistDetailsUiStateFor(state, "artist")

        assertEquals(listOf("a", "z"), details.albums.map { it.id })
        assertEquals(listOf("a-one", "a-missing", "z-one", "z-two"), details.tracks.map { it.id })
    }

    @Test
    fun detailsUseCanonicalArtistIdsRatherThanDisplayNames() {
        val state = ArtistDetailsUiState(
            allArtists = listOf(LibraryArtist("artist", "Shared Name")),
            allAlbums = listOf(LibraryAlbum("album", "Album")),
            tracks = listOf(
                LibraryTrack("match", "Match", "Shared Name", albumId = "album", metadataJson = metadata("artist")),
                LibraryTrack("different", "Different", "Shared Name", albumId = "album", metadataJson = metadata("another")),
            ),
        )

        assertEquals(listOf("match"), artistDetailsUiStateFor(state, "artist").tracks.map { it.id })
    }

    private fun track(id: String, albumId: String, disc: Int = 0, number: Int = 0, order: Int): LibraryTrack =
        LibraryTrack(id, id, "Artist", albumId = albumId, discNumber = disc, trackNumber = number, syncOrder = order, metadataJson = metadata("artist"))

    private fun metadata(artistId: String): String = "{\"artists\":[{\"id\":\"$artistId\"}]}"
}
