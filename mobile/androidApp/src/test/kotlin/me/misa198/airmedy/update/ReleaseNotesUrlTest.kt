package me.misa198.airmedy.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReleaseNotesUrlTest {
    @Test fun preservesGitHubUploadedImageAndExternalLinks() {
        val image = "https://github.com/user-attachments/assets/abc-123"
        assertEquals(image, releaseNotesUrl(image, "2.0.0", image = true))
        assertEquals("https://example.com/notes", releaseNotesUrl("https://example.com/notes", "2.0.0"))
    }

    @Test fun resolvesRepositoryImagesAndLinksAtTheReleaseTag() {
        assertEquals("https://raw.githubusercontent.com/misa198/airmedy/mobile-v2.0.0/docs/image.png",
            releaseNotesUrl("./docs/image.png", "2.0.0", image = true))
        assertEquals("https://github.com/misa198/airmedy/blob/mobile-v2.0.0/README.md",
            releaseNotesUrl("README.md", "2.0.0"))
    }

    @Test fun rejectsLocalFilesExecutableLinksAndMalformedUrls() {
        for (url in listOf("file:///private/image.png", "content://private/image", "javascript:alert(1)",
            "data:image/png;base64,test", "https://", "https://user:password@example.com/image", "bad url")) {
            assertNull(url, releaseNotesUrl(url, "2.0.0", image = true))
            assertNull(url, releaseNotesUrl(url, "2.0.0"))
        }
    }
}
