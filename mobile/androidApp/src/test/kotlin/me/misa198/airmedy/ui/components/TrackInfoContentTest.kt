package me.misa198.airmedy.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import me.misa198.airmedy.R
import me.misa198.airmedy.sync.LibraryTrack
import me.misa198.airmedy.ui.theme.AirmedyColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackInfoContentTest {
    private val colors = AirmedyColors(
        background = Color(0xFF18181B),
        playerBackdrop = Color(0xFF0A0A0A),
        card = Color(0xFF27272A),
        glass = Color(0x66232326),
        glassOpaque = Color(0xFF232326),
        glassElevated = Color(0x6637373C),
        sliderInactive = Color(0x1AFFFFFF),
        buttonSecondary = Color(0xFF52525B),
        textFieldClear = Color(0xFF3F3F46),
        borderGlass = Color(0x1AFFFFFF),
        textMain = Color.White,
        textMuted = Color(0xFFA1A1AA),
        primary = Color(0xFFE11D48),
        onPrimary = Color.White,
        foregroundSubtle = Color(0x75FFFFFF),
        success = Color(0xFF4ADE80),
        navigationActive = Color(0x66000000),
        qualityHiRes = Color(0xFFF59E0B),
        qualityDsd = Color(0xFFE879F9),
        qualityDsdSurface = Color(0xFFD946EF),
    )

    @Test
    fun qualityMatchesTheDesktopRules() {
        assertEquals(TrackAudioQuality.Lossy, trackAudioQuality(track("""{"format":"mp3","bitrate":320}""")))
        assertEquals(TrackAudioQuality.Lossless, trackAudioQuality(track("""{"format":"flac","bit_depth":16,"sample_rate":44100}""")))
        assertEquals(TrackAudioQuality.HiRes, trackAudioQuality(track("""{"format":"flac","bit_depth":24,"sample_rate":96000}""")))
        assertEquals(TrackAudioQuality.Dsd, trackAudioQuality(track("""{"format":"dsf","bit_depth":1,"sample_rate":2822400}""")))
        assertEquals(TrackAudioQuality.Lossy, trackAudioQuality(track("""{"format":"m4a","codec":"aac"}""")))
        assertEquals(TrackAudioQuality.Lossless, trackAudioQuality(track("""{"format":"m4a","codec":"alac","bit_depth":16,"sample_rate":44100}""")))
        assertEquals(TrackAudioQuality.Unknown, trackAudioQuality(track("""{"format":"m4a"}""")))
    }

    @Test
    fun qualityBadgeColorsMatchTheDesktopPalette() {
        val lossy = requireNotNull(trackQualityBadgeStyle(TrackAudioQuality.Lossy, colors))
        assertEquals(colors.textMain.copy(alpha = 0.50f), lossy.foreground)
        assertEquals(colors.textMain.copy(alpha = 0.08f), lossy.background)
        assertEquals(colors.textMain.copy(alpha = 0.15f), lossy.border)

        val lossless = requireNotNull(trackQualityBadgeStyle(TrackAudioQuality.Lossless, colors))
        assertEquals(colors.primary, lossless.foreground)

        val hiRes = requireNotNull(trackQualityBadgeStyle(TrackAudioQuality.HiRes, colors))
        assertEquals(Color(0xFFF59E0B), hiRes.foreground)
        assertEquals(Color(0x1AF59E0B), hiRes.background)
        assertEquals(Color(0x4DF59E0B), hiRes.border)

        val dsd = requireNotNull(trackQualityBadgeStyle(TrackAudioQuality.Dsd, colors))
        assertEquals(Color(0xFFE879F9), dsd.foreground)
        assertEquals(Color(0x1AD946EF), dsd.background)
        assertEquals(Color(0x4DE879F9), dsd.border)
    }

    @Test
    fun detailsUseAvailableManifestMetadataAndExcludeAbsentValues() {
        val details = trackInfoValues(track("""{
            "raw_artist_names":"Artist",
            "raw_genre_names":"Electronic",
            "duration":245,
            "format":"flac",
            "bitrate":1000,
            "sample_rate":96000,
            "bit_depth":24,
            "copyright":"© 2026 Example Records",
            "file_size":1048576
        }"""))

        assertTrue(details.contains(TrackInfoValue(R.string.track_info_artist, "Artist")))
        assertTrue(details.contains(TrackInfoValue(R.string.track_info_duration, "4:05")))
        assertTrue(details.contains(TrackInfoValue(R.string.track_info_sample_rate, "96 kHz")))
        assertTrue(details.contains(TrackInfoValue(R.string.track_info_copyright, "© 2026 Example Records")))
        assertTrue(details.contains(TrackInfoValue(R.string.track_info_file_size, "1.0 MB")))
        assertFalse(details.any { it.labelRes == R.string.track_info_isrc })
    }

    @Test
    fun detailsExcludeZeroBpm() {
        val details = trackInfoValues(track("""{"bpm":0}"""))

        assertFalse(details.any { it.labelRes == R.string.track_info_bpm })
    }

    @Test
    fun formattersMatchDesktopDisplay() {
        assertEquals("1:05", formatTrackDuration(65))
        assertEquals("1.5 KB", formatFileSize(1536))
    }

    @Test
    fun contentHeightUsesTheRemainingDialogSpaceAndTopInset() {
        assertEquals(768.dp, trackInfoMaxContentHeight(800.dp, 24.dp))
        assertEquals(0.dp, trackInfoMaxContentHeight(20.dp, 24.dp))
    }

    @Test
    fun artworkUsesTheExpandedMaximumSize() {
        assertEquals(307.2.dp, trackInfoArtworkSize)
    }

    private fun track(metadata: String) = LibraryTrack(
        id = "track-1",
        title = "Track",
        artists = "Artist",
        metadataJson = metadata,
    )
}
