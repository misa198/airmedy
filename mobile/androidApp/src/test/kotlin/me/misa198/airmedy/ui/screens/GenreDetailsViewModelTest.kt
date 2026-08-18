package me.misa198.airmedy.ui.screens

import me.misa198.airmedy.sync.LibraryAlbum
import me.misa198.airmedy.sync.LibraryGenre
import me.misa198.airmedy.sync.LibraryTrack
import org.junit.Assert.assertEquals
import org.junit.Test

class GenreDetailsViewModelTest {
    @Test
    fun detailsMatchManifestGenreObjectsAndSortAlbumsAndTracks() {
        val state = GenreDetailsUiState(
            allGenres = listOf(LibraryGenre("electronic", "Electronic")),
            allAlbums = listOf(
                LibraryAlbum("z", "Alpha", "Artist", sortTitle = "Zulu", sortArtist = "Zulu Artist"),
                LibraryAlbum("a", "Zulu", "Artist", sortTitle = "Alpha", sortArtist = "Alpha Artist"),
            ),
            tracks = listOf(
                track("z-two", "z", "{\"genres\":[{\"id\":\"electronic\",\"name\":\"Electronic\"}]}", disc = 1, number = 2, order = 2),
                track("a-raw", "a", "{\"raw_genre_names\":\"Electronic / Ambient\"}", disc = 1, number = 2, order = 2),
                track("z-raw", "z", "{\"genre\":\"Electronic\"}", disc = 1, number = 1, order = 1),
                track("a-one", "a", "{\"genres\":[{\"id\":\"electronic\",\"name\":\"Electronic\"}]}", disc = 1, number = 1, order = 1),
                track("other", "a", "{\"genres\":[\"Ambient\"]}", disc = 1, number = 3, order = 3),
            ),
        )

        val details = genreDetailsUiStateFor(state, "electronic")

        assertEquals("Electronic", details.genre?.name)
        assertEquals(listOf("a", "z"), details.albums.map { it.id })
        assertEquals(listOf("a-one", "z-two"), details.tracks.map { it.id })
    }

    @Test
    fun detailsUseCanonicalGenreIdsRatherThanDisplayNames() {
        val state = GenreDetailsUiState(
            allGenres = listOf(LibraryGenre("electronic", "Electronic")),
            allAlbums = listOf(LibraryAlbum("album", "Album")),
            tracks = listOf(
                track("match", "album", "{\"genres\":[{\"id\":\"electronic\",\"name\":\"Electronic\"}]}", order = 0),
                track("different", "album", "{\"genres\":[{\"id\":\"ambient\",\"name\":\"Electronic\"}]}", order = 1),
            ),
        )

        assertEquals(listOf("match"), genreDetailsUiStateFor(state, "electronic").tracks.map { it.id })
    }

    private fun track(
        id: String,
        albumId: String,
        metadataJson: String,
        disc: Int = 0,
        number: Int = 0,
        order: Int,
    ) = LibraryTrack(
        id = id,
        title = id,
        artists = "Artist",
        albumId = albumId,
        discNumber = disc,
        trackNumber = number,
        syncOrder = order,
        metadataJson = metadataJson,
    )
}
