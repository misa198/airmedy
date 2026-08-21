package me.misa198.airmedy.player

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test
import me.misa198.airmedy.sync.LibraryTrack

class AndroidPlaybackRuntimeTest {
    @Test
    fun `resolves synced relative audio path under app files directory`() {
        val filesDir = File("/data/user/0/me.misa198.airmedy/files")

        assertEquals(
            File(filesDir, "library-sync/assets/audio-file").path,
            resolveSyncedAudioFile(filesDir, "library-sync/assets/audio-file")?.path,
        )
    }

    @Test
    fun `validates restored queue from one snapshot and existing audio files`() {
        val filesDir = File.createTempFile("airmedy-playback", "").apply {
            delete()
            mkdirs()
        }
        try {
            File(filesDir, "library-sync/assets/kept").apply {
                parentFile?.mkdirs()
                writeText("")
            }

            val available = availableSyncedTrackIds(
                filesDir = filesDir,
                tracks = listOf(
                    LibraryTrack(id = "kept", title = "Kept", artists = "Artist", audioPath = "library-sync/assets/kept"),
                    LibraryTrack(id = "missing-file", title = "Missing", artists = "Artist", audioPath = "library-sync/assets/missing"),
                    LibraryTrack(id = "not-requested", title = "Other", artists = "Artist", audioPath = "library-sync/assets/kept"),
                ),
                requestedTrackIds = setOf("kept", "missing-file", "removed-from-library"),
            )

            assertEquals(setOf("kept"), available)
        } finally {
            filesDir.deleteRecursively()
        }
    }
}
