package me.misa198.airmedy.lyrics

import org.junit.Assert.assertEquals
import org.junit.Test

class LyricsHtmlDecodeTest {
    @Test
    fun decodesEntitiesWithoutCollapsingLrcLines() {
        assertEquals(
            "[00:01.00]First\n[00:02.00]Second & Third",
            decodeLyricsHtml("[00:01.00]First&#10;[00:02.00]Second &amp; Third"),
        )
    }
}
