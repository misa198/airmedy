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
import android.media.session.MediaSession
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.WindowInsetsController
import android.view.KeyEvent
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
import me.misa198.airmedy.sync.AndroidPlaylistReconciliationTransport
import me.misa198.airmedy.sync.PlaylistReconciliationClock
import me.misa198.airmedy.sync.PlaylistReconciliationCoordinator
import me.misa198.airmedy.sync.PlaylistReconciliationPublisher
import me.misa198.airmedy.sync.PlaylistSyncProtocol
import me.misa198.airmedy.sync.PlaylistReconciliationOutcome
import me.misa198.airmedy.sync.PlaylistMutationStatus
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.misa198.airmedy.player.AndroidPlaybackRuntime
import me.misa198.airmedy.player.AndroidPlaybackSession
import me.misa198.airmedy.player.PlaybackService
import me.misa198.airmedy.player.PlaybackState
import kotlin.math.roundToInt

import me.misa198.airmedy.ui.screens.LibraryTracksViewModel
import me.misa198.airmedy.ui.screens.LibraryArtistsViewModel
import me.misa198.airmedy.ui.screens.LibraryAlbumsViewModel
import me.misa198.airmedy.ui.screens.LibraryAlbumsLayoutPreferences
import me.misa198.airmedy.ui.screens.LibraryGenresViewModel
import me.misa198.airmedy.ui.screens.LibraryComposersViewModel
import me.misa198.airmedy.ui.screens.LibraryPlaylistsViewModel
import me.misa198.airmedy.ui.screens.PlaylistDetailsViewModel
import me.misa198.airmedy.ui.screens.AlbumDetailsViewModel
import me.misa198.airmedy.ui.screens.ArtistDetailsViewModel
import me.misa198.airmedy.ui.screens.GenreDetailsViewModel
import me.misa198.airmedy.ui.screens.ComposerDetailsViewModel

class MainActivity : ComponentActivity() {
    private val reconciliationMutex = Mutex()
    private var systemMusicVolumeState by mutableFloatStateOf(0f)
    private val viewModel: MainViewModel by viewModels {
        MainViewModel.Factory(ThemePreferences(applicationContext))
    }
    private val syncViewModel: SyncViewModel by viewModels {
        val preferences = PairingPreferences(applicationContext)
        val syncSession = HiveMqSyncSession()
        SyncViewModel.Factory(
            MobilePairingUseCase(
                identityProvider = preferences,
                bindingStore = preferences,
                transport = HiveMqPairingTransport(),
                clock = AndroidPairingClock,
                ids = AndroidPairingIdGenerator,
            ),
            mqttSession = syncSession,
            discovery = AndroidTrustedDesktopDiscovery(applicationContext),
            onSyncRequest = { payload, endpoint, session -> AndroidSyncRuntime.start(applicationContext, payload, endpoint, session) },
            onPlaylistReconciliationRequest = { payload ->
                reconciliationMutex.withLock {
                AndroidSyncRuntime.awaitNoForegroundSync()
                preferences.current()?.let { desktop ->
                    AndroidSyncRuntime.initialize(applicationContext)
                    val coordinator = PlaylistReconciliationCoordinator(
                        identityProvider = preferences,
                        clock = PlaylistReconciliationClock { System.currentTimeMillis() },
                        store = AndroidSyncRuntime.syncStore(),
                        transport = AndroidPlaylistReconciliationTransport(preferences, applicationContext.filesDir, AndroidSyncRuntime.syncStore()),
                        publisher = PlaylistReconciliationPublisher { result ->
                            val mobileId = preferences.identity().id
                            syncSession.publish(PlaylistSyncProtocol.resultTopic(desktop.desktopId, mobileId), result)
                        },
                    )
                    when (val outcome = coordinator.handle(payload, desktop)) {
                        is PlaylistReconciliationOutcome.Completed -> {
                            val rejected = outcome.results.filter {
                                it.status == PlaylistMutationStatus.REJECTED || it.status == PlaylistMutationStatus.SCOPE_CONFLICT
                            }.map { it.mutationId }
                            AndroidSyncRuntime.syncStore().markLocalPlaylistMutationsFailed(rejected)
                        }
                        else -> Unit
                    }
                }
                }
            },
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
    private val albumsViewModel: LibraryAlbumsViewModel by viewModels {
        LibraryAlbumsViewModel.Factory(
            AndroidSyncRuntime.syncStore(),
            AndroidPlaybackRuntime.controller(),
            LibraryAlbumsLayoutPreferences(applicationContext),
        )
    }
    private val genresViewModel: LibraryGenresViewModel by viewModels {
        LibraryGenresViewModel.Factory(AndroidSyncRuntime.syncStore())
    }
    private val composersViewModel: LibraryComposersViewModel by viewModels {
        LibraryComposersViewModel.Factory(AndroidSyncRuntime.syncStore())
    }
    private val playlistsViewModel: LibraryPlaylistsViewModel by viewModels {
        LibraryPlaylistsViewModel.Factory(applicationContext, AndroidSyncRuntime.syncStore())
    }
    private val albumDetailsViewModel: AlbumDetailsViewModel by viewModels {
        AlbumDetailsViewModel.Factory(AndroidSyncRuntime.syncStore(), AndroidPlaybackRuntime.controller())
    }
    private val playlistDetailsViewModel: PlaylistDetailsViewModel by viewModels {
        PlaylistDetailsViewModel.Factory(AndroidSyncRuntime.syncStore(), AndroidPlaybackRuntime.controller())
    }
    private val artistDetailsViewModel: ArtistDetailsViewModel by viewModels {
        ArtistDetailsViewModel.Factory(AndroidSyncRuntime.syncStore(), AndroidPlaybackRuntime.controller())
    }
    private val genreDetailsViewModel: GenreDetailsViewModel by viewModels {
        GenreDetailsViewModel.Factory(AndroidSyncRuntime.syncStore(), AndroidPlaybackRuntime.controller())
    }
    private val composerDetailsViewModel: ComposerDetailsViewModel by viewModels {
        ComposerDetailsViewModel.Factory(AndroidSyncRuntime.syncStore(), AndroidPlaybackRuntime.controller())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        AndroidSyncRuntime.initialize(applicationContext)
        AndroidPlaybackRuntime.initialize(applicationContext, AndroidSyncRuntime.syncStore())
        // Start the service once per app process so it can rebuild the last
        // private playback session before Compose observes its StateFlows.
        startService(Intent(this, PlaybackService::class.java))
        systemMusicVolumeState = currentSystemMusicVolume()
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), NotificationPermissionRequest)
        }

        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val syncUiState by syncViewModel.uiState.collectAsStateWithLifecycle()
            val tracksUiState by tracksViewModel.uiState.collectAsStateWithLifecycle()
            val artistsUiState by artistsViewModel.uiState.collectAsStateWithLifecycle()
            val albumsUiState by albumsViewModel.uiState.collectAsStateWithLifecycle()
            val genresUiState by genresViewModel.uiState.collectAsStateWithLifecycle()
            val composersUiState by composersViewModel.uiState.collectAsStateWithLifecycle()
            val playlistsUiState by playlistsViewModel.uiState.collectAsStateWithLifecycle()
            val albumDetailsUiState by albumDetailsViewModel.uiState.collectAsStateWithLifecycle()
            val playlistDetailsUiState by playlistDetailsViewModel.uiState.collectAsStateWithLifecycle()
            val artistDetailsUiState by artistDetailsViewModel.uiState.collectAsStateWithLifecycle()
            val genreDetailsUiState by genreDetailsViewModel.uiState.collectAsStateWithLifecycle()
            val composerDetailsUiState by composerDetailsViewModel.uiState.collectAsStateWithLifecycle()
            val playbackController = AndroidPlaybackRuntime.controller()
            val playbackPreferences = remember { me.misa198.airmedy.player.PlaybackPreferences(applicationContext) }
            val normalizationPreferences = remember { me.misa198.airmedy.player.NormalizationPreferences(applicationContext) }
            val preferenceScope = rememberCoroutineScope()
            val crossfadeSettings by playbackPreferences.settings.collectAsStateWithLifecycle(
                initialValue = me.misa198.airmedy.player.CrossfadeSettings(0, 4, true),
            )
            val normalizationSettings by normalizationPreferences.settings.collectAsStateWithLifecycle(
                initialValue = me.misa198.airmedy.player.NormalizationSettings(),
            )
            // Avoid clearing a valid preference while Room is still loading the active manifest.
            val normalizationAvailable by AndroidSyncRuntime.syncStore().analysisAvailable.collectAsStateWithLifecycle(initialValue = true)
            LaunchedEffect(normalizationAvailable) {
                if (!normalizationAvailable) normalizationPreferences.disable()
            }
            val playbackState by playbackController.state.collectAsStateWithLifecycle()
            val playbackQueue by playbackController.queue.collectAsStateWithLifecycle()
            val artworkCrossfade by playbackController.artworkCrossfade.collectAsStateWithLifecycle()
            val lyricsTrackId = when (val state = playbackState) {
                is PlaybackState.Preparing -> state.item.trackId
                is PlaybackState.Playing -> state.item.trackId
                is PlaybackState.Paused -> state.item.trackId
                else -> null
            }
            val lyricsFlow = remember(lyricsTrackId) {
                lyricsTrackId?.let(AndroidSyncRuntime.syncStore()::lyrics) ?: flowOf(null)
            }
            val lyrics by lyricsFlow.collectAsStateWithLifecycle(initialValue = null)
            DisposableEffect(Unit) {
                val volumeObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
                    override fun onChange(selfChange: Boolean) {
                        systemMusicVolumeState = currentSystemMusicVolume()
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
            LaunchedEffect(playlistsViewModel) {
                playlistsViewModel.createdPlaylistIds.collect { playlistId ->
                    viewModel.dispatch(AppIntent.OpenPlaylistDetails(playlistId))
                }
            }
            val darkTheme = isDarkTheme(uiState.themeMode)
            SideEffect {
                updateSystemBarAppearance(darkTheme, isFullScreenPlayerVisible)
            }
            App(
                uiState = uiState,
                syncUiState = syncUiState,
                tracksUiState = tracksUiState,
                artistsUiState = artistsUiState,
                albumsUiState = albumsUiState,
                genresUiState = genresUiState,
                composersUiState = composersUiState,
                playlistsUiState = playlistsUiState,
                albumDetailsUiState = albumDetailsUiState,
                playlistDetailsUiState = playlistDetailsUiState,
                artistDetailsUiState = artistDetailsUiState,
                genreDetailsUiState = genreDetailsUiState,
                composerDetailsUiState = composerDetailsUiState,
                onIntent = viewModel::dispatch,
                onSortOptionSelected = tracksViewModel::setSortOption,
                onToggleSortOrder = tracksViewModel::toggleSortOrder,
                onTrackClick = tracksViewModel::playTrack,
                onTracksPlayAll = tracksViewModel::playAll,
                onTracksFilterQueryChange = tracksViewModel::setFilterQuery,
                onRecentTrackClick = tracksViewModel::playRecentTrack,
                onTrackPlayNext = playbackController::playNext,
                onTrackAddToQueue = { trackId -> playbackController.append(listOf(trackId)) },
                onAlbumPlayNext = playbackController::playNext,
                onAlbumAddToQueue = playbackController::append,
                onArtistSortOptionSelected = artistsViewModel::setSortOption,
                onArtistToggleSortOrder = artistsViewModel::toggleSortOrder,
                onArtistsFilterQueryChange = artistsViewModel::setFilterQuery,
                onAlbumSortOptionSelected = albumsViewModel::setSortOption,
                onAlbumToggleSortOrder = albumsViewModel::toggleSortOrder,
                onAlbumLayoutModeSelected = albumsViewModel::setLayoutMode,
                onAlbumPlay = albumDetailsViewModel::play,
                onAlbumsPlayAll = albumsViewModel::playAll,
                onAlbumsFilterQueryChange = albumsViewModel::setFilterQuery,
                onAlbumTrackPlay = albumDetailsViewModel::playTrack,
                onPlaylistPlay = playlistDetailsViewModel::play,
                onPlaylistTrackPlay = playlistDetailsViewModel::playTrack,
                onPlaylistTrackRemove = playlistDetailsViewModel::removeTrack,
                onPlaylistPlayNext = playbackController::playNext,
                onPlaylistAddToQueue = playbackController::append,
                onPlaylistUpdate = playlistsViewModel::updatePlaylist,
                onPlaylistDelete = playlistsViewModel::deletePlaylist,
                onCreatePlaylist = playlistsViewModel::createPlaylist,
                onCreatePlaylistWithTracks = { name, artwork, trackIds -> playlistsViewModel.createPlaylist(name, artwork, trackIds) },
                onPlaylistMembershipChange = playlistsViewModel::changePlaylistMembership,
                onArtistPlay = artistDetailsViewModel::play,
                onArtistPlayNext = playbackController::playNext,
                onArtistAddToQueue = playbackController::append,
                orderedTrackIdsForArtist = artistDetailsViewModel::orderedTrackIds,
                onGenrePlay = genreDetailsViewModel::play,
                onGenrePlayNext = playbackController::playNext,
                onGenreAddToQueue = playbackController::append,
                orderedTrackIdsForGenre = genreDetailsViewModel::orderedTrackIds,
                onComposerPlay = composerDetailsViewModel::play,
                onComposerPlayNext = playbackController::playNext,
                onComposerAddToQueue = playbackController::append,
                orderedTrackIdsForComposer = composerDetailsViewModel::orderedTrackIds,
                onGenreSortOptionSelected = genresViewModel::setSortOption,
                onGenreToggleSortOrder = genresViewModel::toggleSortOrder,
                onGenresFilterQueryChange = genresViewModel::setFilterQuery,
                onComposerSortOptionSelected = composersViewModel::setSortOption,
                onComposerToggleSortOrder = composersViewModel::toggleSortOrder,
                onComposersFilterQueryChange = composersViewModel::setFilterQuery,
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
                crossfadeSeconds = crossfadeSettings.seconds,
                lastEnabledCrossfadeSeconds = crossfadeSettings.lastEnabledSeconds,
                onCrossfadeSecondsChanged = playbackController::setCrossfadeSeconds,
                blendArtworkDuringCrossfade = crossfadeSettings.blendArtworkDuringCrossfade,
                onBlendArtworkDuringCrossfadeChanged = playbackController::setBlendArtworkDuringCrossfade,
                normalizationAvailable = normalizationAvailable,
                normalization = normalizationSettings,
                onNormalizationChanged = { settings ->
                    preferenceScope.launch { normalizationPreferences.update { settings } }
                },
                artworkCrossfade = artworkCrossfade,
                playbackState = playbackState,
                playbackQueue = playbackQueue,
                queueTracks = tracksUiState.tracks,
                lyrics = lyrics,
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
                onQueueTrackSelected = playbackController::selectQueueTrack,
                onQueueReordered = playbackController::reorderQueue,
                onQueueTrackRemoved = playbackController::removeFromQueue,
                onShuffleChange = playbackController::setShuffle,
                onRepeatModeChange = playbackController::setRepeatMode,
                onFavoriteToggle = { trackId, favorite ->
                    preferenceScope.launch { AndroidSyncRuntime.syncStore().setFavorite(trackId, favorite) }
                },
                onAlbumAddToFavorites = { trackIds ->
                    preferenceScope.launch {
                        trackIds.forEach { trackId -> AndroidSyncRuntime.syncStore().setFavorite(trackId, true) }
                    }
                },
                systemVolume = systemMusicVolumeState,
                onSystemVolumeChange = { volume ->
                    setSystemMusicVolume(volume.coerceIn(0f, 1f))
                    // Reflect the system's discrete stream step immediately,
                    // so a delayed settings observer cannot snap the slider
                    // backward after a drag ends.
                    systemMusicVolumeState = currentSystemMusicVolume()
                },
                onMiniPlayerDismiss = playbackController::clearQueue,
                onOpenMediaOutputSwitcher = ::openMediaOutputSwitcher,
                onFullScreenPlayerVisibilityChanged = { visible ->
                    isFullScreenPlayerVisible = visible
                    updateSystemBarAppearance(darkTheme, visible)
                },
            )
        }
    }

    private fun updateSystemBarAppearance(darkTheme: Boolean, fullScreenPlayerVisible: Boolean) {
        val lightSystemBars = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
            WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
        val statusBarAppearance = if (darkTheme || fullScreenPlayerVisible) {
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
        manager.setStreamVolume(AudioManager.STREAM_MUSIC, (maximum * volume).roundToInt(), 0)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_DOWN -> predictSystemMusicVolumeChange(-1)
            KeyEvent.KEYCODE_VOLUME_UP -> predictSystemMusicVolumeChange(1)
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun predictSystemMusicVolumeChange(direction: Int) {
        val manager = getSystemService(AudioManager::class.java)
        val maximum = manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        systemMusicVolumeState = adjustSystemMusicVolume(systemMusicVolumeState, maximum, direction)
    }

    private fun openMediaOutputSwitcher() {
        if (canShowSystemMediaOutputSwitcher(Build.VERSION.SDK_INT)) {
            showSystemOutputSwitcher(
                router = MediaRouter2.getInstance(this),
                sessionToken = AndroidPlaybackSession.tokenOrNull(),
            )
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

internal fun adjustSystemMusicVolume(current: Float, maximum: Int, direction: Int): Float =
    normalizeSystemMusicVolume(
        current = (current.coerceIn(0f, 1f) * maximum.coerceAtLeast(1)).roundToInt() + direction,
        maximum = maximum,
    )

/**
 * Android 16 QPR 1 added the session-bound overload. Reflection keeps the app
 * buildable with API 36 while using it on devices where the platform provides it.
 */
internal fun showSystemOutputSwitcher(router: MediaRouter2, sessionToken: MediaSession.Token?): Boolean {
    if (sessionToken != null) {
        val method = router.javaClass.methods.firstOrNull { candidate ->
            candidate.name == "showSystemOutputSwitcher" &&
                candidate.parameterTypes.contentEquals(arrayOf(MediaSession.Token::class.java))
        }
        if (method != null) {
            runCatching { method.invoke(router, sessionToken) as Boolean }.getOrNull()?.let { result ->
                return result
            }
        }
    }
    return router.showSystemOutputSwitcher()
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
