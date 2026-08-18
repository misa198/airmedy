package me.misa198.airmedy.ui.screens

import me.misa198.airmedy.sync.LibraryAlbum
import me.misa198.airmedy.sync.LibraryComposer
import me.misa198.airmedy.sync.LibraryTrack
import org.junit.Assert.assertEquals
import org.junit.Test

class ComposerDetailsViewModelTest {
    @Test
    fun detailsMatchManifestComposerObjectsAndSortAlbumsAndTracks() {
        val state = ComposerDetailsUiState(
            allComposers = listOf(LibraryComposer("philip glass", "Philip Glass")),
            allAlbums = listOf(
                LibraryAlbum("z", "Alpha", "Artist", sortTitle = "Zulu", sortArtist = "Zulu Artist"),
                LibraryAlbum("a", "Zulu", "Artist", sortTitle = "Alpha", sortArtist = "Alpha Artist"),
            ),
            tracks = listOf(
                track("z-two", "z", "{\"composers\":[{\"id\":\"philip glass\",\"name\":\"Philip Glass\"}]}", disc = 1, number = 2, order = 2),
                track("a-raw", "a", "{\"raw_composer_names\":\"Philip Glass / Steve Reich\"}", disc = 1, number = 2, order = 2),
                track("z-raw", "z", "{\"composer\":\"Philip Glass\"}", disc = 1, number = 1, order = 1),
                track("a-one", "a", "{\"composers\":[{\"id\":\"philip glass\",\"name\":\"Philip Glass\"}]}", disc = 1, number = 1, order = 1),
                track("other", "a", "{\"composers\":[\"Steve Reich\"]}", disc = 1, number = 3, order = 3),
            ),
        )

        val details = composerDetailsUiStateFor(state, "philip glass")

        assertEquals("Philip Glass", details.composer?.name)
        assertEquals(listOf("a", "z"), details.albums.map { it.id })
        assertEquals(listOf("a-one", "z-two"), details.tracks.map { it.id })
    }

    @Test
    fun detailsUseCanonicalComposerIdsRatherThanDisplayNames() {
        val state = ComposerDetailsUiState(
            allComposers = listOf(LibraryComposer("glass", "Philip Glass")),
            allAlbums = listOf(LibraryAlbum("album", "Album")),
            tracks = listOf(
                track("match", "album", "{\"composers\":[{\"id\":\"glass\",\"name\":\"Philip Glass\"}]}", order = 0),
                track("different", "album", "{\"composers\":[{\"id\":\"reich\",\"name\":\"Philip Glass\"}]}", order = 1),
            ),
        )

        assertEquals(listOf("match"), composerDetailsUiStateFor(state, "glass").tracks.map { it.id })
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
