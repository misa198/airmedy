package me.misa198.airmedy.update

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CheckForAppUpdateTest {
    @Test fun comparesNumericVersionsAndRejectsMalformedValues() {
        assertTrue(AppVersion.parse("1.10.0")!! > AppVersion.parse("1.9.0")!!)
        assertTrue(AppVersion.parse("2.0.0")!! > AppVersion.parse("1.99.99")!!)
        assertTrue(AppVersion.parse("1.0.1")!! > AppVersion.parse("1.0.0")!!)
        listOf("v1.0.0", "1.0", "1.0.0-beta", "-1.0.0", "99999999999.0.0").forEach {
            assertNull(AppVersion.parse(it))
        }
    }

    @Test fun checksOnlyNewUnseenAndroidVersions() = runTest {
        val releases = FakeReleases()
        val notices = FakeNotices()
        val checker = CheckForAppUpdate(releases, notices)
        assertNull(checker.check("1.10.0"))
        assertNull(checker.check("2.0.0"))
        assertEquals(0, releases.releaseRequests)
        val update = checker.check("1.9.0")!!
        assertEquals("1.10.0", update.version)
        assertTrue(checker.markShown(update))
        assertFalse(checker.markShown(update))
        assertNull(CheckForAppUpdate(releases, notices).check("1.9.0"))
        assertEquals(1, releases.releaseRequests)
        releases.tag = "mobile-v1.11.0"
        assertEquals("1.11.0", checker.check("1.9.0")?.version)
        releases.tag = "mobile-v1.10.0"
        assertNull(checker.check("1.9.0"))
    }

    @Test fun ignoresWrongTagsAndInvalidReleaseWithoutMarking() = runTest {
        val releases = FakeReleases()
        val notices = FakeNotices()
        val checker = CheckForAppUpdate(releases, notices)
        for (tag in listOf("v9.0.0", "mobile-v1.0", "mobile-v1.0.0-beta")) {
            releases.tag = tag
            assertNull(checker.check("1.0.0"))
        }
        releases.tag = "mobile-v1.10.0"
        releases.valid = false
        assertNull(checker.check("1.0.0"))
        assertTrue(notices.shown.isEmpty())
    }

    @Test fun propagatesFailureAndCancellationWithoutMarking() = runTest {
        val notices = FakeNotices()
        val source = object : AppReleaseSource {
            override suspend fun latestAndroidTag(): String = throw CancellationException("cancelled")
            override suspend fun androidRelease(tag: String): AppUpdate? = error("Unexpected fetch")
        }
        assertFailsWith<CancellationException> { CheckForAppUpdate(source, notices).check("1.0.0") }
        assertTrue(notices.shown.isEmpty())
    }
}

private class FakeReleases : AppReleaseSource {
    var tag = "mobile-v1.10.0"
    var releaseRequests = 0
    var valid = true
    override suspend fun latestAndroidTag() = tag
    override suspend fun androidRelease(tag: String): AppUpdate? {
        releaseRequests++
        return if (valid) AppUpdate(tag.removePrefix("mobile-v"), "Notes", "https://github.com/release") else null
    }
}

private class FakeNotices : UpdateNoticeStore {
    val shown = mutableSetOf<String>()
    override suspend fun wasShown(version: String) = version in shown
    override suspend fun markShown(version: String) = shown.add(version)
}
