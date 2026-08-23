package me.misa198.airmedy.ui.screens

import android.graphics.Bitmap
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import me.misa198.airmedy.lastfm.LastFmStatus
import me.misa198.airmedy.lyrics.LyricsSettings
import me.misa198.airmedy.lyrics.LyricsSource
import me.misa198.airmedy.settings.ThemeMode
import me.misa198.airmedy.ui.theme.AirmedyTheme
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class IntegrationContentTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun connectedAccountShowsUsernameAndDisconnects() {
        var disconnected = false
        composeTestRule.setContent {
            AirmedyTheme(themeMode = ThemeMode.Dark) {
                LastFmContent(
                    status = LastFmStatus(connected = true, username = "listener"),
                    onConnect = {},
                    onDisconnect = { disconnected = true },
                )
            }
        }

        composeTestRule.onNodeWithText("Connected as listener").assertIsDisplayed()
        composeTestRule.onNodeWithTag("lastfm-icon").assertIsDisplayed()
        composeTestRule.onNodeWithText("Disconnect").performClick()
        assertTrue(disconnected)
    }

    @Test
    fun connectedAccountUsesAvatarInsteadOfIcon() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val avatar = File(context.filesDir, "lastfm-test-avatar.png")
        avatar.outputStream().use {
            Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, it)
        }

        composeTestRule.setContent {
            AirmedyTheme(themeMode = ThemeMode.Dark) {
                LastFmContent(
                    status = LastFmStatus(connected = true, username = "listener", avatarPath = avatar.absolutePath),
                    onConnect = {},
                    onDisconnect = {},
                )
            }
        }

        composeTestRule.waitUntil {
            composeTestRule.onAllNodesWithTag("lastfm-avatar").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("lastfm-avatar").assertIsDisplayed()
        composeTestRule.onAllNodesWithTag("lastfm-icon").assertCountEquals(0)
        avatar.delete()
    }

    @Test
    fun lyricsSourceSelectionReportsAutoFetch() {
        var source = LyricsSource.Desktop
        composeTestRule.setContent {
            AirmedyTheme(themeMode = ThemeMode.Dark) {
                LyricsContent(
                    settings = LyricsSettings(),
                    onSourceChanged = { source = it },
                    onLrclibChanged = {},
                    onKugouChanged = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Desktop sync").performClick()
        composeTestRule.onNodeWithText("Auto fetch").performClick()
        assertEquals(LyricsSource.AutoFetch, source)
    }
}
