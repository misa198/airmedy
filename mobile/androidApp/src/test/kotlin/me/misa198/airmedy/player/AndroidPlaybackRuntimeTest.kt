package me.misa198.airmedy.player

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class AndroidPlaybackRuntimeTest {
    @Test
    fun `resolves synced relative audio path under app files directory`() {
        val filesDir = File("/data/user/0/me.misa198.airmedy/files")

        assertEquals(
            File(filesDir, "library-sync/assets/audio-file").path,
            resolveSyncedAudioFile(filesDir, "library-sync/assets/audio-file")?.path,
        )
    }
}
