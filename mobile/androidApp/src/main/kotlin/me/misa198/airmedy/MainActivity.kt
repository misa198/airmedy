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
import androidx.lifecycle.lifecycleScope
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
import me.misa198.airmedy.lastfm.AndroidLastFmRuntime
import me.misa198.airmedy.lastfm.LastFmService
import me.misa198.airmedy.lastfm.isLastFmAuthCallback
import me.misa198.airmedy.lyrics.AndroidLyricsService
import me.misa198.airmedy.lyrics.LyricsPreferences
import kotlin.math.roundToInt
import me.misa198.airmedy.ui.screens.LibraryTracksViewModel
import me.misa198.airmedy.ui.screens.LibraryArtistsViewModel
import me.misa198.airmedy.ui.screens.LibraryAlbumsViewModel
import me.misa198.airmedy.ui.screens.LibraryAlbumsLayoutPreferences
import me.misa198.airmedy.ui.screens.LibraryGenresViewModel
import me.misa198.airmedy.ui.screens.LibraryComposersViewModel
import me.misa198.airmedy.ui.screens.LibraryPlaylistsViewModel
import me.misa198.airmedy.ui.screens.LibrarySearchViewModel
import me.misa198.airmedy.ui.screens.playbackRequestFor
import me.misa198.airmedy.ui.screens.PlaylistDetailsViewModel
import me.misa198.airmedy.ui.screens.AlbumDetailsViewModel
import me.misa198.airmedy.ui.screens.ArtistDetailsViewModel
import me.misa198.airmedy.ui.screens.GenreDetailsViewModel
import me.misa198.airmedy.ui.screens.ComposerDetailsViewModel
import me.misa198.airmedy.ui.screens.InsightViewModel

private data class ManualLyricsOverride(val trackId: String, val content: String)

class MainActivity : ComponentActivity() {
    private val reconciliationMutex = Mutex()
    private lateinit var lastFm: LastFmService
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
                        transport = AndroidPlaylistReconciliationTransport(
                            preferences, applicationContext.filesDir, AndroidSyncRuntime.syncStore(),
                            listening = AndroidSyncRuntime.syncStore(),
                        ),
                        publisher = PlaylistReconciliationPublisher { result ->
                            val mobileId = preferences.identity().id
                            syncSession.publish(PlaylistSyncProtocol.resultTopic(desktop.desktopId, mobileId), result)
                        },
                    )
                    when (val outcome = coordinator.handle(payload, desktop)) {
                        is PlaylistReconciliationOutcome.Completed -> {
                            val rejected = outcome.results.filter { it.status == PlaylistMutationStatus.REJECTED }.map { it.mutationId }
                            AndroidSyncRuntime.syncStore().markLocalPlaylistMutationsFailed(rejected)
                            AndroidSyncRuntime.syncStore().discardLocalPlaylistsForMutations(
                                outcome.results.filter { it.status == PlaylistMutationStatus.SCOPE_CONFLICT }.map { it.mutationId },
                            )
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
    private val insightViewModel: InsightViewModel by viewModels {
        InsightViewModel.Factory(
            AndroidSyncRuntime.syncStore(),
            PairingPreferences(applicationContext),
            AndroidPlaybackRuntime.controller(),
        )
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
    private val searchViewModel: LibrarySearchViewModel by viewModels {
        LibrarySearchViewModel.Factory(AndroidSyncRuntime.syncStore())
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
        lastFm = AndroidLastFmRuntime.initialize(applicationContext, AndroidSyncRuntime.syncStore())
        handleLastFmIntent(intent)
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
            val lastFmStatus by lastFm.status.collectAsStateWithLifecycle()
            val syncUiState by syncViewModel.uiState.collectAsStateWithLifecycle()
            val activePage = uiState.currentPage
            val activeDestination = uiState.selectedDestination
            val allTracks by AndroidSyncRuntime.syncStore().tracks.collectAsStateWithLifecycle(initialValue = emptyList())
            val allPlaylists by AndroidSyncRuntime.syncStore().playlists.collectAsStateWithLifecycle(initialValue = emptyList())
            val homeUiState by if (activeDestination == AppDestination.Home && activePage == AppStackPage.Root) {
                tracksViewModel.homeUiState.collectAsStateWithLifecycle()
            } else {
                // AnimatedContent keeps the old page composed until its slide-out completes.
                // Preserve its last state without keeping every inactive flow subscribed.
                remember { mutableStateOf(tracksViewModel.homeUiState.value) }
            }
            val tracksUiState by if (
                activeDestination == AppDestination.Library &&
                (activePage == AppStackPage.Root || activePage == AppStackPage.LibraryTracks)
            ) {
                tracksViewModel.uiState.collectAsStateWithLifecycle()
            } else {
                remember { mutableStateOf(tracksViewModel.uiState.value) }
            }
            val insightUiState by if (activeDestination == AppDestination.Insight && activePage == AppStackPage.Root) insightViewModel.uiState.collectAsStateWithLifecycle() else remember { mutableStateOf(insightViewModel.uiState.value) }
            val artistsUiState by if (activePage == AppStackPage.LibraryArtists) artistsViewModel.uiState.collectAsStateWithLifecycle() else remember { mutableStateOf(artistsViewModel.uiState.value) }
            val albumsUiState by if (activePage == AppStackPage.LibraryAlbums) albumsViewModel.uiState.collectAsStateWithLifecycle() else remember { mutableStateOf(albumsViewModel.uiState.value) }
            val genresUiState by if (activePage == AppStackPage.LibraryGenres) genresViewModel.uiState.collectAsStateWithLifecycle() else remember { mutableStateOf(genresViewModel.uiState.value) }
            val composersUiState by if (activePage == AppStackPage.LibraryComposers) composersViewModel.uiState.collectAsStateWithLifecycle() else remember { mutableStateOf(composersViewModel.uiState.value) }
            val playlistsUiState by if (activePage == AppStackPage.LibraryPlaylists) playlistsViewModel.uiState.collectAsStateWithLifecycle() else remember { mutableStateOf(playlistsViewModel.uiState.value) }
            val searchUiState by if (activePage == AppStackPage.LibrarySearch) searchViewModel.uiState.collectAsStateWithLifecycle() else remember { mutableStateOf(searchViewModel.uiState.value) }
            val albumDetailsUiState by if (activePage == AppStackPage.AlbumDetails) albumDetailsViewModel.uiState.collectAsStateWithLifecycle() else remember { mutableStateOf(albumDetailsViewModel.uiState.value) }
            val playlistDetailsUiState by if (activePage == AppStackPage.PlaylistDetails) playlistDetailsViewModel.uiState.collectAsStateWithLifecycle() else remember { mutableStateOf(playlistDetailsViewModel.uiState.value) }
            val artistDetailsUiState by if (activePage == AppStackPage.ArtistDetails) artistDetailsViewModel.uiState.collectAsStateWithLifecycle() else remember { mutableStateOf(artistDetailsViewModel.uiState.value) }
            val genreDetailsUiState by if (activePage == AppStackPage.GenreDetails) genreDetailsViewModel.uiState.collectAsStateWithLifecycle() else remember { mutableStateOf(genreDetailsViewModel.uiState.value) }
            val composerDetailsUiState by if (activePage == AppStackPage.ComposerDetails) composerDetailsViewModel.uiState.collectAsStateWithLifecycle() else remember { mutableStateOf(composerDetailsViewModel.uiState.value) }
            val playbackController = AndroidPlaybackRuntime.controller()
            val playbackPreferences = remember { me.misa198.airmedy.player.PlaybackPreferences(applicationContext) }
            val normalizationPreferences = remember { me.misa198.airmedy.player.NormalizationPreferences(applicationContext) }
            val equalizerPreferences = remember { me.misa198.airmedy.player.EqualizerPreferences(applicationContext) }
            val lyricsPreferences = remember { LyricsPreferences(applicationContext) }
            val lyricsService = remember { AndroidLyricsService(AndroidSyncRuntime.syncStore()) }
            val preferenceScope = rememberCoroutineScope()
            val crossfadeSettings by playbackPreferences.settings.collectAsStateWithLifecycle(
                initialValue = me.misa198.airmedy.player.CrossfadeSettings(0, 4, true),
            )
            val normalizationSettings by normalizationPreferences.settings.collectAsStateWithLifecycle(
                initialValue = me.misa198.airmedy.player.NormalizationSettings(),
            )
            val equalizerSettings by equalizerPreferences.settings.collectAsStateWithLifecycle(
                initialValue = me.misa198.airmedy.player.EqualizerSettings(),
            )
            val lyricsSettings by lyricsPreferences.settings.collectAsStateWithLifecycle(initialValue = me.misa198.airmedy.lyrics.LyricsSettings())
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
            val desktopLyricsFlow = remember(lyricsTrackId) {
                lyricsTrackId?.let(AndroidSyncRuntime.syncStore()::desktopLyrics) ?: flowOf(null)
            }
            val providerLyricsFlow = remember(lyricsTrackId) {
                lyricsTrackId?.let(AndroidSyncRuntime.syncStore()::providerLyrics) ?: flowOf(null)
            }
            val desktopLyrics by desktopLyricsFlow.collectAsStateWithLifecycle(initialValue = null)
            val providerLyrics by providerLyricsFlow.collectAsStateWithLifecycle(initialValue = null)
            var manualLyricsOverride by remember { mutableStateOf<ManualLyricsOverride?>(null) }
            LaunchedEffect(lyricsTrackId) {
                if (manualLyricsOverride?.trackId != lyricsTrackId) manualLyricsOverride = null
            }
            val lyrics = manualLyricsOverride?.takeIf { it.trackId == lyricsTrackId }?.content
                ?: me.misa198.airmedy.lyrics.preferredLyrics(lyricsSettings.preferredSource, desktopLyrics, providerLyrics)
            var lyricsLoadingTrackId by remember { mutableStateOf<String?>(null) }
            LaunchedEffect(lyricsTrackId, desktopLyrics, providerLyrics, lyricsSettings, manualLyricsOverride) {
                if (lyricsTrackId == null || manualLyricsOverride?.trackId == lyricsTrackId || !providerLyrics.isNullOrBlank() ||
                    (lyricsSettings.preferredSource == me.misa198.airmedy.lyrics.LyricsSource.Desktop && !desktopLyrics.isNullOrBlank())
                ) return@LaunchedEffect
                lyricsLoadingTrackId = lyricsTrackId
                try {
                    lyricsService.fetch(lyricsTrackId, lyricsSettings)
                } finally {
                    if (lyricsLoadingTrackId == lyricsTrackId) lyricsLoadingTrackId = null
                }
            }
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
                        is AppEffect.ResetPoppedPage -> when (effect.page) {
                            AppStackPage.LibrarySearch -> searchViewModel.clear()
                            AppStackPage.LibraryTracks -> tracksViewModel.setFilterQuery("")
                            AppStackPage.LibraryArtists -> artistsViewModel.setFilterQuery("")
                            AppStackPage.LibraryAlbums -> albumsViewModel.setFilterQuery("")
                            AppStackPage.LibraryGenres -> genresViewModel.setFilterQuery("")
                            AppStackPage.LibraryComposers -> composersViewModel.setFilterQuery("")
                            else -> Unit
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
            val destinations = AppDestinationModels(
                home = HomeDestinationModel(homeUiState, tracksViewModel::playHomeTrack),
                insight = InsightDestinationModel(
                    state = insightUiState,
                    onLibraryPeriodSelected = insightViewModel::setLibraryPeriod,
                    onListeningPeriodSelected = insightViewModel::setListeningPeriod,
                    onSourceSelected = insightViewModel::setSourceFilter,
                    onTrackClick = insightViewModel::playTopTrack,
                ),
                library = LibraryDestinationModel(
                    tracks = LibraryTracksModel(
                        state = tracksUiState,
                        onSortOptionSelected = tracksViewModel::setSortOption,
                        onToggleSortOrder = tracksViewModel::toggleSortOrder,
                        onTrackClick = tracksViewModel::playTrack,
                        onPlayAll = tracksViewModel::playAll,
                        onFilterQueryChange = tracksViewModel::setFilterQuery,
                        onRecentTrackClick = tracksViewModel::playRecentTrack,
                    ),
                    artists = LibraryArtistsModel(
                        state = artistsUiState,
                        onSortOptionSelected = artistsViewModel::setSortOption,
                        onToggleSortOrder = artistsViewModel::toggleSortOrder,
                        onFilterQueryChange = artistsViewModel::setFilterQuery,
                        onPlay = artistDetailsViewModel::play,
                        onPlayNext = playbackController::playNext,
                        onAddToQueue = playbackController::append,
                        orderedTrackIds = artistDetailsViewModel::orderedTrackIds,
                    ),
                    albums = LibraryAlbumsModel(
                        state = albumsUiState,
                        onSortOptionSelected = albumsViewModel::setSortOption,
                        onToggleSortOrder = albumsViewModel::toggleSortOrder,
                        onLayoutModeSelected = albumsViewModel::setLayoutMode,
                        onPlay = albumDetailsViewModel::play,
                        onPlayAll = albumsViewModel::playAll,
                        onFilterQueryChange = albumsViewModel::setFilterQuery,
                        onTrackPlay = albumDetailsViewModel::playTrack,
                        onPlayNext = playbackController::playNext,
                        onAddToQueue = playbackController::append,
                        onAddToFavorites = { trackIds ->
                            preferenceScope.launch {
                                trackIds.forEach { trackId -> AndroidSyncRuntime.syncStore().setFavorite(trackId, true) }
                            }
                        },
                    ),
                    genres = LibraryGenresModel(
                        state = genresUiState,
                        onSortOptionSelected = genresViewModel::setSortOption,
                        onToggleSortOrder = genresViewModel::toggleSortOrder,
                        onFilterQueryChange = genresViewModel::setFilterQuery,
                        onPlay = genreDetailsViewModel::play,
                        onPlayNext = playbackController::playNext,
                        onAddToQueue = playbackController::append,
                        orderedTrackIds = genreDetailsViewModel::orderedTrackIds,
                    ),
                    composers = LibraryComposersModel(
                        state = composersUiState,
                        onSortOptionSelected = composersViewModel::setSortOption,
                        onToggleSortOrder = composersViewModel::toggleSortOrder,
                        onFilterQueryChange = composersViewModel::setFilterQuery,
                        onPlay = composerDetailsViewModel::play,
                        onPlayNext = playbackController::playNext,
                        onAddToQueue = playbackController::append,
                        orderedTrackIds = composerDetailsViewModel::orderedTrackIds,
                    ),
                    playlists = LibraryPlaylistsModel(
                        state = playlistsUiState,
                        availablePlaylists = allPlaylists,
                        onPlay = playlistDetailsViewModel::play,
                        onTrackPlay = playlistDetailsViewModel::playTrack,
                        onTrackRemove = playlistDetailsViewModel::removeTrack,
                        onTrackMove = playlistDetailsViewModel::moveTrack,
                        onPlayNext = playbackController::playNext,
                        onAddToQueue = playbackController::append,
                        onUpdate = playlistsViewModel::updatePlaylist,
                        onDelete = playlistsViewModel::deletePlaylist,
                        onCreate = playlistsViewModel::createPlaylist,
                        onCreateWithTracks = { name, artwork, trackIds -> playlistsViewModel.createPlaylist(name, artwork, trackIds) },
                        onMembershipChange = playlistsViewModel::changePlaylistMembership,
                    ),
                    search = LibrarySearchModel(
                        state = searchUiState,
                        onQueryChange = searchViewModel::setQuery,
                        onTrackClick = { trackId ->
                            playbackRequestFor(searchUiState.tracks, trackId)?.let(playbackController::play)
                        },
                    ),
                    details = LibraryDetailActions(
                        albums = albumDetailsUiState,
                        playlists = playlistDetailsUiState,
                        artists = artistDetailsUiState,
                        genres = genreDetailsUiState,
                        composers = composerDetailsUiState,
                    ),
                ),
                settings = SettingsDestinationModel(
                    syncState = syncUiState,
                    onPairingQrScanned = { raw ->
                        if (!syncViewModel.acceptsQr(raw)) false else {
                            syncViewModel.pair(raw)
                            viewModel.dispatch(AppIntent.NavigateBack)
                            true
                        }
                    },
                    onUnpair = syncViewModel::unpair,
                    onSyncScreenVisible = syncViewModel::onSyncScreenVisible,
                    onSyncScreenHidden = syncViewModel::onSyncScreenHidden,
                    lastFmStatus = lastFmStatus,
                    onLastFmConnect = {
                        lastFm.authorizationUrl()?.let { url -> startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                    },
                    onLastFmDisconnect = { preferenceScope.launch { lastFm.disconnect() } },
                    lyricsSettings = lyricsSettings,
                    onLyricsSourceChanged = { source -> preferenceScope.launch { lyricsPreferences.setPreferredSource(source) } },
                    onLrclibChanged = { enabled -> preferenceScope.launch { lyricsPreferences.setLrclib(enabled) } },
                    onKugouChanged = { enabled -> preferenceScope.launch { lyricsPreferences.setKugou(enabled) } },
                    crossfadeSeconds = crossfadeSettings.seconds,
                    lastEnabledCrossfadeSeconds = crossfadeSettings.lastEnabledSeconds,
                    onCrossfadeSecondsChanged = playbackController::setCrossfadeSeconds,
                    blendArtworkDuringCrossfade = crossfadeSettings.blendArtworkDuringCrossfade,
                    onBlendArtworkDuringCrossfadeChanged = playbackController::setBlendArtworkDuringCrossfade,
                    normalizationAvailable = normalizationAvailable,
                    normalization = normalizationSettings,
                    onNormalizationChanged = { settings -> preferenceScope.launch { normalizationPreferences.update { settings } } },
                    equalizer = equalizerSettings,
                    onEqualizerEnabledChanged = { enabled -> preferenceScope.launch { equalizerPreferences.setEnabled(enabled) } },
                    onEqualizerPresetSelected = { key -> preferenceScope.launch { equalizerPreferences.selectPreset(key) } },
                    onEqualizerBandChanged = { index, gain -> preferenceScope.launch { equalizerPreferences.setBand(equalizerSettings, index, gain) } },
                    onEqualizerProfileCreate = { name -> preferenceScope.launch { equalizerPreferences.createProfile(name) } },
                    onEqualizerProfileReset = { key -> preferenceScope.launch { equalizerPreferences.resetDefault(key) } },
                    onEqualizerProfileDelete = { key -> preferenceScope.launch { equalizerPreferences.deleteProfile(key) } },
                ),
            )
            val playback = PlaybackModel(
                state = playbackState,
                queue = playbackQueue,
                queueTracks = allTracks,
                lyrics = lyrics,
                lyricsLoading = lyricsLoadingTrackId == lyricsTrackId,
                onSearchLyrics = { track, title, artist -> lyricsService.search(track.id, title, artist, lyricsSettings) },
                onLyricsSelected = { trackId, lyric ->
                    AndroidSyncRuntime.syncStore().saveProviderLyrics(trackId, lyric.content, lyric.source)
                    if (trackId == lyricsTrackId) manualLyricsOverride = ManualLyricsOverride(trackId, lyric.content)
                },
                artworkCrossfade = artworkCrossfade,
                blendArtworkDuringCrossfade = crossfadeSettings.blendArtworkDuringCrossfade,
                systemVolume = systemMusicVolumeState,
                onPrevious = playbackController::previous,
                onPlayPause = {
                    when (playbackState) {
                        is PlaybackState.Playing -> playbackController.pause()
                        is PlaybackState.Paused -> playbackController.resume()
                        else -> Unit
                    }
                },
                onNext = playbackController::next,
                onSeek = playbackController::seekTo,
                onQueueTrackSelected = playbackController::selectQueueTrack,
                onQueueReordered = playbackController::reorderQueue,
                onQueueTrackRemoved = playbackController::removeFromQueue,
                onShuffleChange = playbackController::setShuffle,
                onRepeatModeChange = playbackController::setRepeatMode,
                onFavoriteToggle = { trackId, favorite ->
                    preferenceScope.launch {
                        AndroidSyncRuntime.syncStore().setFavorite(trackId, favorite)
                        lastFm.setLoved(trackId, favorite)
                    }
                },
                onTrackPlayNext = playbackController::playNext,
                onTrackAddToQueue = { trackId -> playbackController.append(listOf(trackId)) },
                onSystemVolumeChange = { volume ->
                    setSystemMusicVolume(volume.coerceIn(0f, 1f))
                    systemMusicVolumeState = currentSystemMusicVolume()
                },
                onMiniPlayerDismiss = playbackController::clearQueue,
                onOpenMediaOutputSwitcher = ::openMediaOutputSwitcher,
            )
            App(
                uiState = uiState,
                destinations = destinations,
                playback = playback,
                onIntent = viewModel::dispatch,
                onFullScreenPlayerVisibilityChanged = { visible ->
                    isFullScreenPlayerVisible = visible
                    updateSystemBarAppearance(darkTheme, visible)
                },
                onDismissSyncFailure = AndroidSyncRuntime::idle,
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleLastFmIntent(intent)
    }

    private fun handleLastFmIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        if (isLastFmAuthCallback(uri.scheme, uri.host, uri.path, uri.getQueryParameter("token"))) {
            intent.data = null
            lifecycleScope.launch { lastFm.completeAuthorization(uri) }
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
