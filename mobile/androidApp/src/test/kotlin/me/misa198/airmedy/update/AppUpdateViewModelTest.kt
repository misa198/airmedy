package me.misa198.airmedy.update

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class AppUpdateViewModelTest {
    @Before fun setUp() { Dispatchers.setMain(StandardTestDispatcher()) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun checksOnceAndPersistsBeforeShowingEvenWhenDismissed() = runTest {
        val store = Notices()
        var requests = 0
        val checker = checker(store) { requests++; "mobile-v2.0.0" }
        val model = AppUpdateViewModel(checker, "1.0.0") { throw AssertionError(it) }
        model.checkOnce()
        model.checkOnce()
        advanceUntilIdle()
        assertTrue(store.shown.contains(model.update.value!!.version))
        model.dismiss()
        model.checkOnce()
        advanceUntilIdle()
        assertNull(model.update.value)
        assertEquals(1, requests)
        val reopened = AppUpdateViewModel(checker, "1.0.0") { throw AssertionError(it) }
        reopened.checkOnce()
        advanceUntilIdle()
        assertNull(reopened.update.value)
    }

    @Test fun errorsDoNotShowDialogAndNextLaunchCanRetry() = runTest {
        val store = Notices()
        var failures = 0
        val broken = AppUpdateViewModel(checker(store) { error("offline") }, "1.0.0") { failures++ }
        broken.checkOnce()
        advanceUntilIdle()
        assertNull(broken.update.value)
        assertTrue(store.shown.isEmpty())
        assertEquals(1, failures)
        val retry = AppUpdateViewModel(checker(store), "1.0.0") { throw AssertionError(it) }
        retry.checkOnce()
        advanceUntilIdle()
        assertNotNull(retry.update.value)
    }

    @Test fun persistenceFailureSkipsDialogAndCancellationIsNotLogged() = runTest {
        val store = Notices().apply { failSave = true }
        var failures = 0
        val model = AppUpdateViewModel(checker(store), "1.0.0") { failures++ }
        model.checkOnce()
        advanceUntilIdle()
        assertNull(model.update.value)
        assertTrue(store.shown.isEmpty())
        val cancelled = AppUpdateViewModel(checker(store) { throw CancellationException() }, "1.0.0") { failures++ }
        cancelled.checkOnce()
        advanceUntilIdle()
        assertEquals(1, failures)
    }

    private fun checker(store: Notices, tag: suspend () -> String = { "mobile-v2.0.0" }) =
        CheckForAppUpdate(object : AppReleaseSource {
            override suspend fun latestAndroidTag() = tag()
            override suspend fun androidRelease(tag: String) = AppUpdate("2.0.0", "Notes", "https://github.com/release")
        }, store)

    private class Notices : UpdateNoticeStore {
        val shown = mutableSetOf<String>()
        var failSave = false
        override suspend fun wasShown(version: String) = version in shown
        override suspend fun markShown(version: String): Boolean {
            if (failSave) error("disk full")
            return shown.add(version)
        }
    }
}
