package me.misa198.airmedy.update

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UpdateNoticePreferencesTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test fun persistsVersionsAcrossStoreRecreationAndClaimsEachOnlyOnce() = runTest {
        val file = java.io.File(temporaryFolder.root, "notices.preferences_pb")
        val firstScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val first = UpdateNoticePreferences(PreferenceDataStoreFactory.create(scope = firstScope) { file })
            assertFalse(first.wasShown("2.0.0"))
            val results = List(5) { async { first.markShown("2.0.0") } }.awaitAll()
            assertEquals(1, results.count { it })
        } finally {
            firstScope.coroutineContext.job.cancelAndJoin()
        }
        val secondScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val reopened = UpdateNoticePreferences(PreferenceDataStoreFactory.create(scope = secondScope) { file })
            assertTrue(reopened.wasShown("2.0.0"))
            assertFalse(reopened.markShown("2.0.0"))
            assertTrue(reopened.markShown("2.1.0"))
            assertTrue(reopened.wasShown("2.0.0"))
        } finally {
            secondScope.coroutineContext.job.cancelAndJoin()
        }
    }
}
