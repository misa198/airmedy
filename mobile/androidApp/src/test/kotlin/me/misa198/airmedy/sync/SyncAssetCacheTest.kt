package me.misa198.airmedy.sync

import java.io.File
import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SyncAssetCacheTest {
    @Test
    fun reusesAnExistingVerifiedAssetPathAcrossPlans() {
        val filesDir = createTempDirectory("airmedy-sync-cache").toFile()
        val relativePath = "library-sync/assets/hash"
        File(filesDir, relativePath).apply { parentFile?.mkdirs(); writeText("audio") }

        val cachedPath = cachedAssetPath(filesDir, SyncAssetEntity("old-plan", "audio:track", "audio", "hash", 5, relativePath))

        assertEquals(relativePath, cachedPath)
        filesDir.deleteRecursively()
    }

    @Test
    fun doesNotReuseAMissingOrUnsafeAssetPath() {
        val filesDir = createTempDirectory("airmedy-sync-cache").toFile()

        assertNull(cachedAssetPath(filesDir, SyncAssetEntity("old-plan", "audio:track", "audio", "hash", 5, "../outside")))

        filesDir.deleteRecursively()
    }
}
