package me.misa198.airmedy

import android.os.Bundle
import android.os.Build
import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.Intent
import android.net.Uri
import android.media.AudioManager
import android.media.MediaRouter2
import android.database.ContentObserver
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import android.os.Handler
import android.os.Looper
import android.provider.Settings
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
import me.misa198.airmedy.player.AndroidPlaybackRuntime

import me.misa198.airmedy.ui.screens.LibraryTracksViewModel
import me.misa198.airmedy.ui.screens.LibraryArtistsViewModel

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
        LibraryTracksViewModel.Factory(AndroidSyncRuntime.syncStore(), AndroidPlaybackRuntime.controller())
    }
    private val artistsViewModel: LibraryArtistsViewModel by viewModels {
        LibraryArtistsViewModel.Factory(AndroidSyncRuntime.syncStore())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        AndroidSyncRuntime.initialize(applicationContext)
        AndroidPlaybackRuntime.initialize(applicationContext, AndroidSyncRuntime.syncStore())
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), NotificationPermissionRequest)
        }

        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val syncUiState by syncViewModel.uiState.collectAsStateWithLifecycle()
            val tracksUiState by tracksViewModel.uiState.collectAsStateWithLifecycle()
            val artistsUiState by artistsViewModel.uiState.collectAsStateWithLifecycle()
            val playbackController = AndroidPlaybackRuntime.controller()
            val playbackState by playbackController.state.collectAsStateWithLifecycle()
            var systemVolume by remember { mutableFloatStateOf(currentSystemMusicVolume()) }
            DisposableEffect(Unit) {
                val volumeObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
                    override fun onChange(selfChange: Boolean) {
                        systemVolume = currentSystemMusicVolume()
                    }
                }
                contentResolver.registerContentObserver(
                    Settings.System.CONTENT_URI,
                    true,
                    volumeObserver,
                )
                onDispose { contentResolver.unregisterContentObserver(volumeObserver) }
            }
            var isFullScreenPlayerVisible by remember { mutableStateOf(false) }
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
                val statusBarAppearance = if (darkTheme || isFullScreenPlayerVisible) {
                    0
                } else {
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                }
                val navigationBarAppearance = if (darkTheme) {
                    0
                } else {
                    WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                }
                window.insetsController?.setSystemBarsAppearance(
                    statusBarAppearance or navigationBarAppearance,
                    lightSystemBars,
                )
            }
            App(
                uiState = uiState,
                syncUiState = syncUiState,
                tracksUiState = tracksUiState,
                artistsUiState = artistsUiState,
                onIntent = viewModel::dispatch,
                onSortOptionSelected = tracksViewModel::setSortOption,
                onToggleSortOrder = tracksViewModel::toggleSortOrder,
                onTrackClick = tracksViewModel::playTrack,
                onArtistSortOptionSelected = artistsViewModel::setSortOption,
                onArtistToggleSortOrder = artistsViewModel::toggleSortOrder,
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
                playbackState = playbackState,
                onPlaybackPrevious = playbackController::previous,
                onPlaybackPlayPause = {
                    when (playbackState) {
                        is me.misa198.airmedy.player.PlaybackState.Playing -> playbackController.pause()
                        is me.misa198.airmedy.player.PlaybackState.Paused -> playbackController.resume()
                        else -> Unit
                    }
                },
                onPlaybackNext = playbackController::next,
                onPlaybackSeek = playbackController::seekTo,
                systemVolume = systemVolume,
                onSystemVolumeChange = { volume ->
                    systemVolume = volume.coerceIn(0f, 1f)
                    setSystemMusicVolume(systemVolume)
                },
                onMiniPlayerDismiss = playbackController::clearQueue,
                onOpenMediaOutputSwitcher = ::openMediaOutputSwitcher,
                onFullScreenPlayerVisibilityChanged = { isFullScreenPlayerVisible = it },
            )
        }
    }

    private companion object {
        const val NotificationPermissionRequest = 51
    }

    private fun currentSystemMusicVolume(): Float {
        val manager = getSystemService(AudioManager::class.java)
        return normalizeSystemMusicVolume(
            current = manager.getStreamVolume(AudioManager.STREAM_MUSIC),
            maximum = manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC),
        )
    }

    private fun setSystemMusicVolume(volume: Float) {
        val manager = getSystemService(AudioManager::class.java)
        val maximum = manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        manager.setStreamVolume(AudioManager.STREAM_MUSIC, (maximum * volume).toInt(), 0)
    }

    private fun openMediaOutputSwitcher() {
        if (canShowSystemMediaOutputSwitcher(Build.VERSION.SDK_INT)) {
            MediaRouter2.getInstance(this).showSystemOutputSwitcher()
        }
    }

    private fun isDarkTheme(themeMode: ThemeMode): Boolean = when (themeMode) {
        ThemeMode.System -> {
            resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        }
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
}

internal fun canShowSystemMediaOutputSwitcher(sdkInt: Int): Boolean =
    sdkInt >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE

internal fun normalizeSystemMusicVolume(current: Int, maximum: Int): Float =
    current.coerceIn(0, maximum.coerceAtLeast(1)).toFloat() / maximum.coerceAtLeast(1)

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
