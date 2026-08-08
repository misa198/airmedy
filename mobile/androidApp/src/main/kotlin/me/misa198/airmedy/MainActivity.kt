package me.misa198.airmedy

import android.os.Bundle
import android.os.Build
import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.tooling.preview.Preview
import android.view.WindowInsetsController
import me.misa198.airmedy.settings.ThemeMode
import me.misa198.airmedy.settings.ThemePreferences
import me.misa198.airmedy.pairing.AndroidPairingClock
import me.misa198.airmedy.pairing.AndroidPairingIdGenerator
import me.misa198.airmedy.pairing.HiveMqPairingTransport
import me.misa198.airmedy.pairing.HiveMqSyncSession
import me.misa198.airmedy.pairing.MobilePairingUseCase
import me.misa198.airmedy.pairing.PairingPreferences
import me.misa198.airmedy.pairing.AndroidTrustedDesktopDiscovery
import me.misa198.airmedy.sync.AndroidSyncRuntime
import me.misa198.airmedy.sync.LibrarySyncService

import me.misa198.airmedy.ui.screens.LibraryTracksViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels {
        MainViewModel.Factory(ThemePreferences(applicationContext))
    }
    private val syncViewModel: SyncViewModel by viewModels {
        val preferences = PairingPreferences(applicationContext)
        SyncViewModel.Factory(
            MobilePairingUseCase(
                identityProvider = preferences,
                bindingStore = preferences,
                transport = HiveMqPairingTransport(),
                clock = AndroidPairingClock,
                ids = AndroidPairingIdGenerator,
            ),
            mqttSession = HiveMqSyncSession(),
            discovery = AndroidTrustedDesktopDiscovery(applicationContext),
            onSyncRequest = { payload, endpoint, session -> AndroidSyncRuntime.start(applicationContext, payload, endpoint, session) },
            onBeforeUnpair = {
                LibrarySyncService.cancel(applicationContext)
                AndroidSyncRuntime.clearAll()
            },
        )
    }
    private val tracksViewModel: LibraryTracksViewModel by viewModels {
        LibraryTracksViewModel.Factory(AndroidSyncRuntime.syncStore())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        AndroidSyncRuntime.initialize(applicationContext)
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), NotificationPermissionRequest)
        }

        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val syncUiState by syncViewModel.uiState.collectAsStateWithLifecycle()
            val tracksUiState by tracksViewModel.uiState.collectAsStateWithLifecycle()
            LaunchedEffect(viewModel) {
                viewModel.effects.collect { effect ->
                    when (effect) {
                        is AppEffect.OpenExternalUrl -> {
                            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(effect.url)))
                        }
                    }
                }
            }
            val darkTheme = isDarkTheme(uiState.themeMode)
            SideEffect {
                val lightSystemBars = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                    WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                window.insetsController?.setSystemBarsAppearance(
                    if (darkTheme) 0 else lightSystemBars,
                    lightSystemBars,
                )
            }
            App(
                uiState = uiState,
                syncUiState = syncUiState,
                tracksUiState = tracksUiState,
                onIntent = viewModel::dispatch,
                onSortOptionSelected = tracksViewModel::setSortOption,
                onToggleSortOrder = tracksViewModel::toggleSortOrder,
                onPairingQrScanned = { raw ->
                    if (syncViewModel.acceptsQr(raw)) {
                        syncViewModel.pair(raw)
                        viewModel.dispatch(AppIntent.NavigateBack)
                        true
                    } else {
                        false
                    }
                },
                onUnpair = syncViewModel::unpair,
                onSyncScreenVisible = syncViewModel::onSyncScreenVisible,
                onSyncScreenHidden = syncViewModel::onSyncScreenHidden,
            )
        }
    }

    private companion object { const val NotificationPermissionRequest = 51 }

    private fun isDarkTheme(themeMode: ThemeMode): Boolean = when (themeMode) {
        ThemeMode.System -> {
            resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        }
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
