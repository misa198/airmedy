package me.misa198.airmedy.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import me.misa198.airmedy.SyncUiState
import me.misa198.airmedy.pairing.PairedDesktop
import me.misa198.airmedy.settings.ThemeMode
import me.misa198.airmedy.ui.theme.AirmedyTheme
import me.misa198.airmedy.sync.AndroidSyncState
import me.misa198.airmedy.sync.LibraryAnalysisProgress
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SyncContentTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun offlinePairedDesktopShowsReadyToConnectGuidanceAndRevokeActionInDeviceCard() {
        composeTestRule.setContent {
            AirmedyTheme(themeMode = ThemeMode.Dark) {
                SyncContent(
                    syncUiState = SyncUiState(
                        desktop = PairedDesktop(
                            desktopId = "01234567-89ab-cdef-0123-456789abcdef",
                            displayName = "Studio Mac",
                            publicKey = ByteArray(32),
                            host = "192.168.1.2",
                            port = 1883,
                        ),
                    ),
                    onUnpair = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Studio Mac").assertIsDisplayed()
        composeTestRule.onNodeWithText("Ready to connect. Start Broadcast in Airmedy on your desktop; this screen connects automatically.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Revoke").assertIsDisplayed()
        composeTestRule.onNodeWithText("Offline").assertIsDisplayed()

        composeTestRule.onNodeWithText("Revoke").performClick()
        composeTestRule.onNodeWithText("Disconnect this desktop?").assertIsDisplayed()
    }

    @Test
    fun pairedDesktopShowsOnlineBadgeWhenMqttSessionIsConnected() {
        composeTestRule.setContent {
            AirmedyTheme(themeMode = ThemeMode.Dark) {
                SyncContent(
                    syncUiState = SyncUiState(
                        desktop = PairedDesktop("01234567-89ab-cdef-0123-456789abcdef", "Studio Mac", ByteArray(32), "192.168.1.2", 1883),
                        isMqttConnected = true,
                    ),
                    onUnpair = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Online").assertIsDisplayed()
        composeTestRule.onNodeWithText("This phone can connect to one desktop at a time. Revoke it before pairing a new desktop.").assertIsDisplayed()
    }

    @Test
    fun syncRunningDisplaysProgressCardAndPercentage() {
        composeTestRule.setContent {
            AirmedyTheme(themeMode = ThemeMode.Dark) {
                SyncContent(
                    syncUiState = SyncUiState(
                        librarySync = AndroidSyncState.Running(
                            planId = "plan-1",
                            completed = 45,
                            total = 100,
                        ),
                    ),
                    onUnpair = {},
                )
            }
        }

        composeTestRule.onNodeWithText("45%").assertIsDisplayed()
    }

    @Test
    fun syncShowsActiveLibraryAnalysisPercentage() {
        composeTestRule.setContent {
            AirmedyTheme(themeMode = ThemeMode.Dark) {
                SyncContent(syncUiState = SyncUiState(libraryAnalysis = LibraryAnalysisProgress(2, 3), libraryAnalysisEnabled = true), onUnpair = {})
            }
        }

        composeTestRule.onNodeWithText("Library analysis").assertIsDisplayed()
        composeTestRule.onNodeWithText("66%").assertIsDisplayed()
    }

    @Test
    fun syncHidesLibraryAnalysisWhenDisabled() {
        composeTestRule.setContent {
            AirmedyTheme(themeMode = ThemeMode.Dark) {
                SyncContent(syncUiState = SyncUiState(libraryAnalysis = LibraryAnalysisProgress(2, 3)), onUnpair = {})
            }
        }

        composeTestRule.onNodeWithText("Library analysis").assertDoesNotExist()
    }

    @Test
    fun syncRunningDisablesRevokeAction() {
        composeTestRule.setContent {
            AirmedyTheme(themeMode = ThemeMode.Dark) {
                SyncContent(
                    syncUiState = SyncUiState(
                        desktop = PairedDesktop("01234567-89ab-cdef-0123-456789abcdef", "Studio Mac", ByteArray(32)),
                        librarySync = AndroidSyncState.Running(planId = "plan-1"),
                    ),
                    onUnpair = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Revoke").assertIsNotEnabled()
    }

    @Test
    fun syncCompletedDisplaysCompleteStatusAndFullPercentage() {
        composeTestRule.setContent {
            AirmedyTheme(themeMode = ThemeMode.Dark) {
                SyncContent(
                    syncUiState = SyncUiState(
                        librarySync = AndroidSyncState.Completed("plan-1"),
                        lastSyncedAtMillis = 1_772_100_000_000,
                    ),
                    onUnpair = {},
                )
            }
        }

        composeTestRule.onNodeWithText("100%").assertIsDisplayed()
        composeTestRule.onNodeWithText("Library sync complete").assertIsDisplayed()
        composeTestRule.onNodeWithText("Last synced:", substring = true).assertIsDisplayed()
    }

    @Test
    fun mobileSyncHelpOpensTheFaq() {
        var openedUrl: String? = null
        composeTestRule.setContent {
            AirmedyTheme(themeMode = ThemeMode.Dark) {
                SyncContent(syncUiState = SyncUiState(), onUnpair = {}, onOpenExternalUrl = { openedUrl = it })
            }
        }

        composeTestRule.onNodeWithText("Learn how to connect").performClick()

        assertEquals("https://airmedy.pages.dev/faq/mobile-sync", openedUrl)
    }
}
