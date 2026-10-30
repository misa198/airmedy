package me.misa198.airmedy.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class AdaptivePlayerContentTest {
    @Test fun phonePaddingOnlyGrowsOnLargeBroadWindows() {
        listOf(390f to 844f, 475f to 751f, 599f to 700f, 600f to 1200f,
            600f to 1001f).forEach { (w, h) ->
            assertEquals(20f, phonePlayerHorizontalPadding(w, h), 0.001f)
        }
        listOf(600f to 1000f, 700f to 700f, 900f to 1200f).forEach { (w, h) ->
            assertEquals(w * 0.1f, phonePlayerHorizontalPadding(w, h), 0.001f)
        }
    }
    @Test fun invalidPhoneDimensionsKeepDefaultPadding() {
        listOf(0f to 800f, 700f to 0f, -1f to 500f, Float.NaN to 500f,
            700f to Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY to 800f).forEach { (w, h) ->
            assertEquals(20f, phonePlayerHorizontalPadding(w, h), 0.001f)
        }
    }
    @Test fun narrowPortraitWindowsFallBackToSquare() {
        listOf(280f to 800f, 320f to 800f, 359f to 800f).forEach { (w, h) ->
            assertEquals(PlayerLayoutMode.Square, resolvePlayerLayout(w, h))
            assertEquals(false, usesCompactPlayerControls(w, h))
        }
        assertEquals(PlayerLayoutMode.Phone, resolvePlayerLayout(360f, 800f))
        assertEquals(PlayerLayoutMode.Phone, resolvePlayerLayout(361f, 800f))
    }
    @Test fun selectsLayoutsFromAvailableSpace() {
        assertEquals(PlayerLayoutMode.Phone, resolvePlayerLayout(390f, 800f))
        assertEquals(PlayerLayoutMode.Square, resolvePlayerLayout(360f, 360f))
        assertEquals(PlayerLayoutMode.Phone, resolvePlayerLayout(700f, 700f))
        assertEquals(PlayerLayoutMode.Wide, resolvePlayerLayout(1000f, 700f))
        assertEquals(PlayerLayoutMode.Wide, resolvePlayerLayout(900f, 400f))
        assertEquals(PlayerLayoutMode.Square, resolvePlayerLayout(800f, 500f))
    }
    @Test fun locksSizeAndRatioBoundaries() {
        assertEquals(PlayerLayoutMode.Square, resolvePlayerLayout(599f, 600f))
        assertEquals(PlayerLayoutMode.Phone, resolvePlayerLayout(600f, 600f))
        assertEquals(PlayerLayoutMode.Square, resolvePlayerLayout(839f, 480f))
        assertEquals(PlayerLayoutMode.Wide, resolvePlayerLayout(840f, 480f))
        assertEquals(PlayerLayoutMode.Wide, resolvePlayerLayout(840f, 479f))
        assertEquals(PlayerLayoutMode.Square, resolvePlayerLayout(400f, 500f))
        assertEquals(PlayerLayoutMode.Phone, resolvePlayerLayout(399f, 500f))
        assertEquals(PlayerLayoutMode.Square, resolvePlayerLayout(500f, 400f))
        assertEquals(PlayerLayoutMode.Square, resolvePlayerLayout(501f, 400f))
    }
    @Test fun distinguishesFlipCoverFromFoldCoverAndInnerDisplays() {
        // Rounded logical dimensions from RTL captures; actual callers subtract safe insets.
        listOf(361f to 399f, 399f to 441f, 361f to 315f, 399f to 353f).forEach { (w, h) ->
            assertEquals(PlayerLayoutMode.Square, resolvePlayerLayout(w, h))
        }
        listOf(475f to 751f, 475f to 661f, 411f to 960f, 360f to 840f).forEach { (w, h) ->
            assertEquals(PlayerLayoutMode.Phone, resolvePlayerLayout(w, h))
        }
        listOf(750f to 832f, 704f to 933f).forEach { (w, h) ->
            assertEquals(PlayerLayoutMode.Phone, resolvePlayerLayout(w, h))
        }
        assertEquals(PlayerLayoutMode.Wide, resolvePlayerLayout(933f, 704f))
    }
    @Test fun largePortraitAndSquareWindowsUsePhoneUntilRotatedToLandscape() {
        listOf(840f to 840f, 900f to 1200f, 1000f to 1000f).forEach { (w, h) ->
            assertEquals(PlayerLayoutMode.Phone, resolvePlayerLayout(w, h))
        }
        assertEquals(PlayerLayoutMode.Wide, resolvePlayerLayout(1200f, 900f))
        assertEquals(PlayerLayoutMode.Wide, resolvePlayerLayout(841f, 840f))
        assertEquals(PlayerLayoutMode.Phone, resolvePlayerLayout(840f, 841f))
    }
    @Test fun landscapeNeverFallsBackToPhone() {
        listOf(500f to 400f, 700f to 400f, 839f to 400f, 900f to 399f, 800f to 500f).forEach { (w, h) ->
            assertEquals(PlayerLayoutMode.Square, resolvePlayerLayout(w, h))
        }
        listOf(840f to 400f, 900f to 400f, 1000f to 479f).forEach { (w, h) ->
            assertEquals(PlayerLayoutMode.Wide, resolvePlayerLayout(w, h))
        }
    }
    @Test fun invalidSizesFallBackToPhone() {
        listOf(0f to 800f, 400f to 0f, -1f to 500f, Float.NaN to 500f, 500f to Float.POSITIVE_INFINITY).forEach { (w, h) ->
            assertEquals(PlayerLayoutMode.Phone, resolvePlayerLayout(w, h))
        }
    }
    @Test fun compactsPhoneControlsAtThePreviousVolumeThreshold() {
        assertEquals(false, usesCompactPlayerControls(479f, 800f))
        listOf(480f to 800f, 481f to 800f, 475f to 751f, 475f to 661f,
            700f to 700f, 700f to 800f, 900f to 1200f).forEach { (w, h) ->
            assertEquals(true, usesCompactPlayerControls(w, h))
        }
        assertEquals(false, usesCompactPlayerControls(390f, 800f))
        assertEquals(false, usesCompactPlayerControls(400f, 500f))
        assertEquals(false, usesCompactPlayerControls(700f, 400f))
    }
    @Test fun compactWideControlsUseHalfTheSafeWidth() {
        assertEquals(false, usesCompactPlayerControls(958f, 800f))
        assertEquals(true, usesCompactPlayerControls(960f, 800f))
        assertEquals(true, usesCompactPlayerControls(962f, 800f))
        assertEquals(true, usesCompactPlayerControls(900f, 400f))
    }
    @Test fun invalidSizesKeepNormalControlSpacing() {
        listOf(0f to 800f, 400f to 0f, -1f to 500f, Float.NaN to 500f,
            500f to Float.POSITIVE_INFINITY).forEach { (w, h) ->
            assertEquals(false, usesCompactPlayerControls(w, h))
        }
    }
    @Test fun resolvesPostureWithoutAssumingASingleHinge() {
        val flat = fold(PlayerFoldOrientation.Vertical, false)
        val book = fold(PlayerFoldOrientation.Vertical, true)
        val tabletop = fold(PlayerFoldOrientation.Horizontal, true)
        assertEquals(PlayerPosture.Normal, resolvePlayerPosture(emptyList()))
        assertEquals(PlayerPosture.Normal, resolvePlayerPosture(listOf(flat)))
        assertEquals(PlayerPosture.Book, resolvePlayerPosture(listOf(flat, book)))
        assertEquals(PlayerPosture.Tabletop, resolvePlayerPosture(listOf(flat, book, tabletop)))
    }
    private fun fold(orientation: PlayerFoldOrientation, halfOpened: Boolean) =
        PlayerFoldingFeature(0, 400, 800, 400, orientation, halfOpened, halfOpened, false)
}
