package me.misa198.airmedy.ui.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.lazy.LazyListState
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import me.misa198.airmedy.AppDestination
import me.misa198.airmedy.AppDestinationModels
import me.misa198.airmedy.AppIntent
import me.misa198.airmedy.AppStackPage
import me.misa198.airmedy.PlaybackModel
import me.misa198.airmedy.StackPageEntry
import me.misa198.airmedy.SyncUiState
import me.misa198.airmedy.settings.ThemeMode
import me.misa198.airmedy.ui.components.HomeContent
import me.misa198.airmedy.ui.components.StackPageLayout
import me.misa198.airmedy.ui.screens.AboutContent
import me.misa198.airmedy.ui.screens.AppearanceContent
import me.misa198.airmedy.ui.screens.LibraryContent
import me.misa198.airmedy.ui.screens.LibrarySearchContent
import me.misa198.airmedy.ui.screens.LibrarySearchUiState
import me.misa198.airmedy.ui.screens.InsightContent
import me.misa198.airmedy.ui.screens.InsightPeriod
import me.misa198.airmedy.ui.screens.InsightSourceFilter
import me.misa198.airmedy.ui.screens.InsightUiState
import me.misa198.airmedy.ui.screens.SettingsContent
import me.misa198.airmedy.ui.screens.IntegrationContent
import me.misa198.airmedy.ui.screens.PlaybackSettingsContent
import me.misa198.airmedy.ui.screens.VolumeNormalizationContent
import me.misa198.airmedy.ui.screens.SongTransitionContent
import me.misa198.airmedy.ui.screens.EqualizerContent
import me.misa198.airmedy.ui.screens.SyncContent
import me.misa198.airmedy.lastfm.LastFmStatus
import me.misa198.airmedy.ui.screens.SyncScannerContent
import me.misa198.airmedy.ui.theme.LocalAirmedyColors

import me.misa198.airmedy.ui.screens.LibraryTracksContent
import me.misa198.airmedy.ui.screens.LibraryTracksUiState
import me.misa198.airmedy.ui.screens.HomeUiState
import me.misa198.airmedy.ui.screens.TrackSortOption
import me.misa198.airmedy.player.PlaybackQueueSnapshot
import me.misa198.airmedy.ui.screens.LibraryArtistsContent
import me.misa198.airmedy.ui.screens.LibraryArtistsUiState
import me.misa198.airmedy.ui.screens.AlbumSortOption
import me.misa198.airmedy.ui.screens.LibraryAlbumsContent
import me.misa198.airmedy.ui.screens.LibraryAlbumsUiState
import me.misa198.airmedy.ui.screens.LibraryGenresContent
import me.misa198.airmedy.ui.screens.LibraryGenresUiState
import me.misa198.airmedy.ui.screens.LibraryComposersContent
import me.misa198.airmedy.ui.screens.LibraryComposersUiState
import me.misa198.airmedy.ui.screens.LibraryPlaylistsContent
import me.misa198.airmedy.ui.screens.LibraryPlaylistsUiState
import me.misa198.airmedy.ui.screens.PlaylistDetailsContent
import me.misa198.airmedy.ui.screens.PlaylistDetailsUiState
import me.misa198.airmedy.ui.screens.playlistDetailsUiStateFor
import me.misa198.airmedy.ui.screens.AlbumDetailsContent
import me.misa198.airmedy.ui.screens.AlbumDetailsUiState
import me.misa198.airmedy.ui.screens.albumDetailsUiStateFor
import me.misa198.airmedy.ui.screens.ArtistDetailsContent
import me.misa198.airmedy.ui.screens.ArtistDetailsUiState
import me.misa198.airmedy.ui.screens.artistDetailsUiStateFor
import me.misa198.airmedy.ui.screens.GenreDetailsContent
import me.misa198.airmedy.ui.screens.GenreDetailsUiState
import me.misa198.airmedy.ui.screens.genreDetailsUiStateFor
import me.misa198.airmedy.ui.screens.ComposerDetailsContent
import me.misa198.airmedy.ui.screens.ComposerDetailsUiState
import me.misa198.airmedy.ui.screens.composerDetailsUiStateFor
import me.misa198.airmedy.sync.LibraryGenre
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalLayoutDirection

internal typealias PageKey = StackPageEntry

internal enum class ContentScrollDirection {
    Up,
    Down,
}

internal data class ContentScrollDelta(
    val direction: ContentScrollDirection,
    val distancePx: Float,
)

internal fun contentScrollDelta(consumedY: Float): ContentScrollDelta? = when {
    consumedY < 0f -> ContentScrollDelta(ContentScrollDirection.Up, -consumedY)
    consumedY > 0f -> ContentScrollDelta(ContentScrollDirection.Down, consumedY)
    else -> null
}

@Composable
internal fun AppDestinationContent(
    stackPage: StackPageEntry,
    themeMode: ThemeMode,
    reduceTransparency: Boolean,
    hazeState: HazeState?,
    navigationBottomPadding: Dp,
    homeListState: LazyListState,
    insightListState: LazyListState = androidx.compose.foundation.lazy.rememberLazyListState(),
    libraryListState: LazyListState = androidx.compose.foundation.lazy.rememberLazyListState(),
    tracksListState: LazyListState = androidx.compose.foundation.lazy.rememberLazyListState(),
    artistsListState: LazyListState = androidx.compose.foundation.lazy.rememberLazyListState(),
    albumsListState: LazyListState = androidx.compose.foundation.lazy.rememberLazyListState(),
    artistDetailsListState: LazyListState = androidx.compose.foundation.lazy.rememberLazyListState(),
    genreDetailsListState: LazyListState = androidx.compose.foundation.lazy.rememberLazyListState(),
    composerDetailsListState: LazyListState = androidx.compose.foundation.lazy.rememberLazyListState(),
    genresListState: LazyListState = androidx.compose.foundation.lazy.rememberLazyListState(),
    composersListState: LazyListState = androidx.compose.foundation.lazy.rememberLazyListState(),
    playlistsListState: LazyListState = androidx.compose.foundation.lazy.rememberLazyListState(),
    selectedAlbumId: String? = null,
    selectedPlaylistId: String? = null,
    selectedArtistId: String? = null,
    selectedGenreId: String? = null,
    selectedComposerId: String? = null,
    onIntent: (AppIntent) -> Unit,
    destinations: AppDestinationModels,
    playback: PlaybackModel,
    onTrackContextBottomSheet: (me.misa198.airmedy.ui.components.TrackContextBottomSheetRequest) -> Unit = {},
    onAlbumHeroColorChanged: (Color) -> Unit = {},
    onSettingsContentScrolled: (Boolean) -> Unit = {},
    onContentScroll: (ContentScrollDelta) -> Unit = {},
) {
    val focusManager = LocalFocusManager.current
    val homeUiState = destinations.home.state
    val insightUiState = destinations.insight.state
    val library = destinations.library
    val tracksUiState = library.tracks.state
    val artistsUiState = library.artists.state
    val albumsUiState = library.albums.state
    val genresUiState = library.genres.state
    val composersUiState = library.composers.state
    val playlistsUiState = library.playlists.state
    val searchUiState = library.search.state
    val albumDetailsUiState = library.details.albums
    val playlistDetailsUiState = library.details.playlists
    val artistDetailsUiState = library.details.artists
    val genreDetailsUiState = library.details.genres
    val composerDetailsUiState = library.details.composers
    val settings = destinations.settings
    val playbackQueue = playback.queue
    val onHomeTrackClick = destinations.home.onTrackClick
    val onInsightLibraryPeriodSelected = destinations.insight.onLibraryPeriodSelected
    val onInsightListeningPeriodSelected = destinations.insight.onListeningPeriodSelected
    val onInsightSourceSelected = destinations.insight.onSourceSelected
    val onInsightTrackClick = destinations.insight.onTrackClick
    val onSortOptionSelected = library.tracks.onSortOptionSelected
    val onToggleSortOrder = library.tracks.onToggleSortOrder
    val onTrackClick = library.tracks.onTrackClick
    val onTracksPlayAll = library.tracks.onPlayAll
    val onTracksFilterQueryChange = library.tracks.onFilterQueryChange
    val onRecentTrackClick = library.tracks.onRecentTrackClick
    val onSearchQueryChange = library.search.onQueryChange
    val onSearchTrackClick = library.search.onTrackClick
    val onTrackPlayNext = playback.onTrackPlayNext
    val onTrackAddToQueue = playback.onTrackAddToQueue
    val onTrackFavoriteToggle = playback.onFavoriteToggle
    val onAlbumPlayNext = library.albums.onPlayNext
    val onAlbumAddToQueue = library.albums.onAddToQueue
    val onAlbumAddToFavorites = library.albums.onAddToFavorites
    val onAlbumPlay = library.albums.onPlay
    val onAlbumsPlayAll = library.albums.onPlayAll
    val onAlbumsFilterQueryChange = library.albums.onFilterQueryChange
    val onAlbumTrackPlay = library.albums.onTrackPlay
    val onPlaylistPlay = library.playlists.onPlay
    val onPlaylistTrackPlay = library.playlists.onTrackPlay
    val onPlaylistTrackRemove = library.playlists.onTrackRemove
    val onPlaylistPlayNext = library.playlists.onPlayNext
    val onPlaylistAddToQueue = library.playlists.onAddToQueue
    val onPlaylistUpdate = library.playlists.onUpdate
    val onPlaylistDelete = library.playlists.onDelete
    val onArtistPlay = library.artists.onPlay
    val onArtistPlayNext = library.artists.onPlayNext
    val onArtistAddToQueue = library.artists.onAddToQueue
    val orderedTrackIdsForArtist = library.artists.orderedTrackIds
    val onArtistsFilterQueryChange = library.artists.onFilterQueryChange
    val onGenrePlay = library.genres.onPlay
    val onGenrePlayNext = library.genres.onPlayNext
    val onGenreAddToQueue = library.genres.onAddToQueue
    val orderedTrackIdsForGenre = library.genres.orderedTrackIds
    val onGenresFilterQueryChange = library.genres.onFilterQueryChange
    val onComposerPlay = library.composers.onPlay
    val onComposerPlayNext = library.composers.onPlayNext
    val onComposerAddToQueue = library.composers.onAddToQueue
    val orderedTrackIdsForComposer = library.composers.orderedTrackIds
    val onComposersFilterQueryChange = library.composers.onFilterQueryChange
    val onArtistTrackContextBottomSheet = onTrackContextBottomSheet
    val onGenreTrackContextBottomSheet = onTrackContextBottomSheet
    val onComposerTrackContextBottomSheet = onTrackContextBottomSheet
    val syncUiState = settings.syncState
    val onPairingQrScanned = settings.onPairingQrScanned
    val onUnpair = settings.onUnpair
    val onSyncScreenVisible = settings.onSyncScreenVisible
    val onSyncScreenHidden = settings.onSyncScreenHidden
    val lastFmStatus = settings.lastFmStatus
    val onLastFmConnect = settings.onLastFmConnect
    val onLastFmDisconnect = settings.onLastFmDisconnect
    val crossfadeSeconds = settings.crossfadeSeconds
    val lastEnabledCrossfadeSeconds = settings.lastEnabledCrossfadeSeconds
    val onCrossfadeSecondsChanged = settings.onCrossfadeSecondsChanged
    val blendArtworkDuringCrossfade = settings.blendArtworkDuringCrossfade
    val onBlendArtworkDuringCrossfadeChanged = settings.onBlendArtworkDuringCrossfadeChanged
    val normalizationAvailable = settings.normalizationAvailable
    val normalization = settings.normalization
    val onNormalizationChanged = settings.onNormalizationChanged
    val equalizer = settings.equalizer
    val onEqualizerEnabledChanged = settings.onEqualizerEnabledChanged
    val onEqualizerPresetSelected = settings.onEqualizerPresetSelected
    val onEqualizerBandChanged = settings.onEqualizerBandChanged
    val colors = LocalAirmedyColors.current
    val destination = stackPage.destination
    val page = stackPage.page
    val currentOnContentScroll = rememberUpdatedState(onContentScroll)
    val currentOnSettingsContentScrolled = rememberUpdatedState(onSettingsContentScrolled)
    val scrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (source == NestedScrollSource.UserInput) {
                    contentScrollDelta(consumed.y)?.let(currentOnContentScroll.value)
                }
                return Offset.Zero
            }
        }
    }
    Surface(
        color = colors.background,
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollConnection)
            .then(if (hazeState == null) Modifier else Modifier.hazeSource(hazeState)),
    ) {
        StackPageLayout(
            title = stringResource(page.titleRes(destination)),
            hazeState = hazeState,
            contentBottomPadding = navigationBottomPadding,
            isContentScrolled = false,
            onBackClick = if (page != AppStackPage.Root) {
                { onIntent(AppIntent.NavigateBack) }
            } else {
                null
            },
            showHeader = false,
        ) { modifier, contentPadding ->
            AnimatedContent(
                targetState = stackPage,
                modifier = modifier,
                transitionSpec = {
                    if (targetState.destination != initialState.destination) {
                        fadeIn(animationSpec = tween(durationMillis = 200)) togetherWith
                            fadeOut(animationSpec = tween(durationMillis = 200))
                    } else if (isForwardTransition(targetState, initialState)) {
                        (slideInHorizontally { it } togetherWith
                            slideOutHorizontally { -it / 4 }).apply {
                            targetContentZIndex = 1f
                        }
                    } else {
                        (slideInHorizontally { -it / 4 } togetherWith
                            slideOutHorizontally { it }).apply {
                            targetContentZIndex = 0f
                        }
                    }
                },
                label = "stack-page-content",
            ) { currentPage ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(colors.background)
                        .pointerInput(focusManager) {
                            detectTapGestures { focusManager.clearFocus() }
                        },
                ) {
                    when (currentPage.destination) {
                        AppDestination.Home -> HomeDestinationContent {
                            if (currentPage.page == AppStackPage.Root) {
                            HomeContent(
                                modifier = Modifier.fillMaxSize(),
                                listState = homeListState,
                                contentPadding = contentPadding,
                                isLoaded = homeUiState.isLoaded,
                                keepListeningTracks = homeUiState.keepListeningTracks,
                                mostPlayedTracks = homeUiState.mostPlayedTracks,
                                forgottenTracks = homeUiState.forgottenTracks,
                                onTrackClick = onHomeTrackClick,
                                playbackQueue = playbackQueue,
                                onTrackPlayNext = { track -> onTrackPlayNext(track.id) },
                                onTrackAddToQueue = { track -> onTrackAddToQueue(track.id) },
                                onTrackFavoriteToggle = { track, favorite -> onTrackFavoriteToggle(track.id, favorite) },
                                onTrackAlbumClick = { track -> onIntent(AppIntent.OpenAlbumDetails(track.albumId)) },
                                onTrackArtistClick = { artist -> onIntent(AppIntent.OpenArtistDetails(artist.id)) },
                                onTrackContextBottomSheet = onTrackContextBottomSheet,
                            )
                            }
                        }
                        AppDestination.Insight -> InsightDestinationContent {
                            InsightContent(
                                state = insightUiState,
                                listState = insightListState,
                                contentPadding = contentPadding,
                                onLibraryPeriodSelected = onInsightLibraryPeriodSelected,
                                onListeningPeriodSelected = onInsightListeningPeriodSelected,
                                onSourceSelected = onInsightSourceSelected,
                                onArtistClick = { onIntent(AppIntent.OpenArtistDetails(it)) },
                                onTrackClick = onInsightTrackClick,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        AppDestination.Settings -> SettingsDestinationContent {
                            key(currentPage.page) {
                            val settingsScrollState = rememberScrollState()
                            LaunchedEffect(currentPage, stackPage, settingsScrollState.value) {
                                if (currentPage == stackPage) {
                                    currentOnSettingsContentScrolled.value(settingsScrollState.value > 0)
                                }
                            }
                            val layoutDirection = LocalLayoutDirection.current
                            val settingsPageModifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(settingsScrollState)
                                .padding(
                                    start = contentPadding.calculateStartPadding(layoutDirection),
                                    top = contentPadding.calculateTopPadding(),
                                    end = contentPadding.calculateEndPadding(layoutDirection),
                                    bottom = contentPadding.calculateBottomPadding(),
                                )
                                .testTag("settings-page-scroll")
                            when (currentPage.page) {
                            AppStackPage.SettingsAppearance -> AppearanceContent(
                                modifier = settingsPageModifier,
                                themeMode = themeMode,
                                reduceTransparency = reduceTransparency,
                                onThemeModeSelected = { themeMode ->
                                    onIntent(AppIntent.SetThemeMode(themeMode))
                                },
                                onReduceTransparencyChanged = { enabled ->
                                    onIntent(AppIntent.SetReduceTransparency(enabled))
                                },
                                hazeState = hazeState,
                            )
                            AppStackPage.SettingsSync -> SyncContent(
                                syncUiState = syncUiState,
                                onUnpair = onUnpair,
                                onScreenVisible = onSyncScreenVisible,
                                onScreenHidden = onSyncScreenHidden,
                                modifier = settingsPageModifier,
                            )
                            AppStackPage.SettingsPlayback -> PlaybackSettingsContent(
                                onSongTransitionSelected = {
                                    onIntent(AppIntent.OpenPage(AppStackPage.SettingsSongTransition))
                                },
                                onVolumeNormalizationSelected = {
                                    onIntent(AppIntent.OpenPage(AppStackPage.SettingsVolumeNormalization))
                                },
                                onEqualizerSelected = {
                                    onIntent(AppIntent.OpenPage(AppStackPage.SettingsEqualizer))
                                },
                                modifier = settingsPageModifier,
                            )
                            AppStackPage.SettingsSongTransition -> SongTransitionContent(
                                crossfadeSeconds = crossfadeSeconds,
                                lastEnabledCrossfadeSeconds = lastEnabledCrossfadeSeconds,
                                onCrossfadeSecondsChanged = onCrossfadeSecondsChanged,
                                blendArtworkDuringCrossfade = blendArtworkDuringCrossfade,
                                onBlendArtworkDuringCrossfadeChanged = onBlendArtworkDuringCrossfadeChanged,
                                modifier = settingsPageModifier,
                            )
                            AppStackPage.SettingsVolumeNormalization -> VolumeNormalizationContent(
                                normalizationAvailable = normalizationAvailable,
                                normalization = normalization,
                                onNormalizationChanged = onNormalizationChanged,
                                modifier = settingsPageModifier,
                            )
                            AppStackPage.SettingsEqualizer -> EqualizerContent(
                                settings = equalizer,
                                onEnabledChanged = onEqualizerEnabledChanged,
                                onPresetSelected = onEqualizerPresetSelected,
                                onBandChanged = onEqualizerBandChanged,
                                modifier = settingsPageModifier,
                            )
                            AppStackPage.SettingsSyncScanner -> SyncScannerContent(
                                onQrScanned = onPairingQrScanned,
                                modifier = Modifier.padding(contentPadding),
                            )
                            AppStackPage.SettingsIntegration -> IntegrationContent(
                                status = lastFmStatus,
                                onConnect = onLastFmConnect,
                                onDisconnect = onLastFmDisconnect,
                                modifier = settingsPageModifier,
                            )
                            AppStackPage.SettingsAbout -> AboutContent(
                                modifier = settingsPageModifier,
                                onOpenExternalUrl = { url ->
                                    onIntent(AppIntent.OpenExternalUrl(url))
                                },
                            )
                            else -> SettingsContent(
                                modifier = settingsPageModifier,
                                onAppearanceSelected = {
                                    onIntent(AppIntent.OpenPage(AppStackPage.SettingsAppearance))
                                },
                                onPlaybackSelected = {
                                    onIntent(AppIntent.OpenPage(AppStackPage.SettingsPlayback))
                                },
                                onSyncSelected = {
                                    onIntent(AppIntent.OpenPage(AppStackPage.SettingsSync))
                                },
                                onIntegrationSelected = {
                                    onIntent(AppIntent.OpenPage(AppStackPage.SettingsIntegration))
                                },
                                onAboutSelected = {
                                    onIntent(AppIntent.OpenPage(AppStackPage.SettingsAbout))
                                },
                            )
                            }
                            }
                        }
                        AppDestination.Library -> LibraryDestinationContent {
                            when (currentPage.page) {
                            AppStackPage.LibrarySearch -> LibrarySearchContent(
                                uiState = searchUiState,
                                contentPadding = contentPadding,
                                onQueryChange = onSearchQueryChange,
                                onTrackClick = onSearchTrackClick,
                                onAlbumClick = { onIntent(AppIntent.OpenAlbumDetails(it)) },
                                onArtistClick = { onIntent(AppIntent.OpenArtistDetails(it)) },
                                onPlaylistClick = { onIntent(AppIntent.OpenPlaylistDetails(it)) },
                                onComposerClick = { onIntent(AppIntent.OpenComposerDetails(it)) },
                                playbackQueue = playbackQueue,
                                onTrackPlayNext = { onTrackPlayNext(it.id) },
                                onTrackAddToQueue = { onTrackAddToQueue(it.id) },
                                onTrackFavoriteToggle = { track, favorite -> onTrackFavoriteToggle(track.id, favorite) },
                                onTrackContextBottomSheet = onTrackContextBottomSheet,
                                onAlbumPlayNext = onAlbumPlayNext,
                                onAlbumAddToQueue = onAlbumAddToQueue,
                                onAlbumAddToFavorites = onAlbumAddToFavorites,
                            )
                            AppStackPage.LibraryArtists -> LibraryArtistsContent(
                                uiState = artistsUiState,
                                listState = artistsListState,
                                contentPadding = contentPadding,
                                modifier = Modifier.fillMaxSize(),
                                onArtistClick = { artist -> onIntent(AppIntent.OpenArtistDetails(artist.id)) },
                                onFilterQueryChange = onArtistsFilterQueryChange,
                                orderedTrackIdsForArtist = orderedTrackIdsForArtist,
                                onArtistPlayNext = onArtistPlayNext,
                                onArtistAddToQueue = onArtistAddToQueue,
                                onTrackContextBottomSheet = onArtistTrackContextBottomSheet,
                                hazeState = hazeState,
                                playbackQueue = playbackQueue,
                            )
                            AppStackPage.ArtistDetails -> ArtistDetailsContent(
                                uiState = selectedArtistId?.let { artistDetailsUiStateFor(artistDetailsUiState, it) } ?: ArtistDetailsUiState(),
                                listState = artistDetailsListState,
                                contentPadding = contentPadding,
                                modifier = Modifier.fillMaxSize(),
                                hazeState = hazeState,
                                onHeroColorChanged = onAlbumHeroColorChanged,
                                onPlay = { selectedArtistId?.let { onArtistPlay(it, false) } },
                                onShuffle = { selectedArtistId?.let { onArtistPlay(it, true) } },
                                onPlayNext = onArtistPlayNext,
                                onAddToQueue = onArtistAddToQueue,
                                onTrackContextBottomSheet = onArtistTrackContextBottomSheet,
                                onAlbumClick = { album -> onIntent(AppIntent.OpenAlbumDetails(album.id)) },
                                playbackQueue = playbackQueue,
                            )
                            AppStackPage.GenreDetails -> GenreDetailsContent(
                                uiState = selectedGenreId?.let { genreDetailsUiStateFor(genreDetailsUiState, it) } ?: GenreDetailsUiState(),
                                listState = genreDetailsListState,
                                contentPadding = contentPadding,
                                modifier = Modifier.fillMaxSize(),
                                hazeState = hazeState,
                                onHeroColorChanged = onAlbumHeroColorChanged,
                                onPlay = { selectedGenreId?.let { onGenrePlay(it, false) } },
                                onShuffle = { selectedGenreId?.let { onGenrePlay(it, true) } },
                                onPlayNext = onGenrePlayNext,
                                onAddToQueue = onGenreAddToQueue,
                                onTrackContextBottomSheet = onGenreTrackContextBottomSheet,
                                onAlbumClick = { album -> onIntent(AppIntent.OpenAlbumDetails(album.id)) },
                                playbackQueue = playbackQueue,
                            )
                            AppStackPage.ComposerDetails -> ComposerDetailsContent(
                                uiState = selectedComposerId?.let { composerDetailsUiStateFor(composerDetailsUiState, it) } ?: ComposerDetailsUiState(),
                                listState = composerDetailsListState,
                                contentPadding = contentPadding,
                                modifier = Modifier.fillMaxSize(),
                                hazeState = hazeState,
                                onHeroColorChanged = onAlbumHeroColorChanged,
                                onPlay = { selectedComposerId?.let { onComposerPlay(it, false) } },
                                onShuffle = { selectedComposerId?.let { onComposerPlay(it, true) } },
                                onPlayNext = onComposerPlayNext,
                                onAddToQueue = onComposerAddToQueue,
                                onTrackContextBottomSheet = onComposerTrackContextBottomSheet,
                                onAlbumClick = { album -> onIntent(AppIntent.OpenAlbumDetails(album.id)) },
                                playbackQueue = playbackQueue,
                            )
                            AppStackPage.LibraryAlbums -> LibraryAlbumsContent(
                                uiState = albumsUiState,
                                listState = albumsListState,
                                contentPadding = contentPadding,
                                modifier = Modifier.fillMaxSize(),
                                onAlbumClick = { album -> onIntent(AppIntent.OpenAlbumDetails(album.id)) },
                                hazeState = hazeState,
                                playbackQueue = playbackQueue,
                                onAlbumPlay = onAlbumPlay,
                                onPlayAll = onAlbumsPlayAll,
                                onAlbumPlayNext = onAlbumPlayNext,
                                onAlbumAddToQueue = onAlbumAddToQueue,
                                onAlbumAddToFavorites = onAlbumAddToFavorites,
                                onTrackContextBottomSheet = onTrackContextBottomSheet,
                                onFilterQueryChange = onAlbumsFilterQueryChange,
                            )
                            AppStackPage.AlbumDetails -> AlbumDetailsContent(
                                uiState = selectedAlbumId?.let { albumDetailsUiStateFor(albumDetailsUiState, it) } ?: AlbumDetailsUiState(),
                                contentPadding = contentPadding,
                                modifier = Modifier.fillMaxSize(),
                                hazeState = hazeState,
                                onHeroColorChanged = onAlbumHeroColorChanged,
                                onPlay = { selectedAlbumId?.let { onAlbumPlay(it, false) } },
                                onShuffle = { selectedAlbumId?.let { onAlbumPlay(it, true) } },
                                onTrackClick = { trackId -> selectedAlbumId?.let { onAlbumTrackPlay(it, trackId) } },
                                playbackQueue = playbackQueue,
                                onTrackPlayNext = onTrackPlayNext,
                                onTrackAddToQueue = onTrackAddToQueue,
                                onTrackFavoriteToggle = onTrackFavoriteToggle,
                                onTrackArtistClick = { artist -> onIntent(AppIntent.OpenArtistDetails(artist.id)) },
                                onAlbumPlayNext = onAlbumPlayNext,
                                onAlbumAddToQueue = onAlbumAddToQueue,
                                onAlbumAddToFavorites = onAlbumAddToFavorites,
                                onTrackContextBottomSheet = onTrackContextBottomSheet,
                            )
                            AppStackPage.LibraryGenres -> LibraryGenresContent(
                                uiState = genresUiState,
                                listState = genresListState,
                                contentPadding = contentPadding,
                                modifier = Modifier.fillMaxSize(),
                                onGenreClick = { genre -> onIntent(AppIntent.OpenGenreDetails(genre.id)) },
                                onFilterQueryChange = onGenresFilterQueryChange,
                                orderedTrackIdsForGenre = orderedTrackIdsForGenre,
                                onGenrePlayNext = onGenrePlayNext,
                                onGenreAddToQueue = onGenreAddToQueue,
                                onTrackContextBottomSheet = onGenreTrackContextBottomSheet,
                                hazeState = hazeState,
                                playbackQueue = playbackQueue,
                            )
                            AppStackPage.LibraryComposers -> LibraryComposersContent(
                                uiState = composersUiState,
                                listState = composersListState,
                                contentPadding = contentPadding,
                                modifier = Modifier.fillMaxSize(),
                                onComposerClick = { composer -> onIntent(AppIntent.OpenComposerDetails(composer.id)) },
                                onFilterQueryChange = onComposersFilterQueryChange,
                                orderedTrackIdsForComposer = orderedTrackIdsForComposer,
                                onComposerPlayNext = onComposerPlayNext,
                                onComposerAddToQueue = onComposerAddToQueue,
                                onTrackContextBottomSheet = onComposerTrackContextBottomSheet,
                                hazeState = hazeState,
                                playbackQueue = playbackQueue,
                            )
                            AppStackPage.LibraryPlaylists -> LibraryPlaylistsContent(
                                uiState = playlistsUiState,
                                listState = playlistsListState,
                                contentPadding = contentPadding,
                                modifier = Modifier.fillMaxSize(),
                                onPlaylistClick = { playlistId -> onIntent(AppIntent.OpenPlaylistDetails(playlistId)) },
                                onPlaylistPlayNext = onPlaylistPlayNext,
                                onPlaylistAddToQueue = onPlaylistAddToQueue,
                                onPlaylistUpdate = onPlaylistUpdate,
                                onPlaylistDelete = onPlaylistDelete,
                            )
                            AppStackPage.PlaylistDetails -> PlaylistDetailsContent(
                                uiState = selectedPlaylistId?.let { playlistDetailsUiStateFor(playlistDetailsUiState, it) } ?: PlaylistDetailsUiState(),
                                contentPadding = contentPadding,
                                modifier = Modifier.fillMaxSize(),
                                hazeState = hazeState,
                                onHeroColorChanged = onAlbumHeroColorChanged,
                                onPlay = { selectedPlaylistId?.let { onPlaylistPlay(it, false) } },
                                onShuffle = { selectedPlaylistId?.let { onPlaylistPlay(it, true) } },
                                onTrackClick = { trackId -> selectedPlaylistId?.let { onPlaylistTrackPlay(it, trackId) } },
                                playbackQueue = playbackQueue,
                                onTrackPlayNext = onTrackPlayNext,
                                onTrackAddToQueue = onTrackAddToQueue,
                                onTrackFavoriteToggle = onTrackFavoriteToggle,
                                onTrackArtistClick = { artist -> onIntent(AppIntent.OpenArtistDetails(artist.id)) },
                                onTrackRemoveFromPlaylist = { trackId -> selectedPlaylistId?.let { onPlaylistTrackRemove(it, trackId) } },
                                onTrackContextBottomSheet = onTrackContextBottomSheet,
                                onPlaylistPlayNext = onPlaylistPlayNext,
                                onPlaylistAddToQueue = onPlaylistAddToQueue,
                                onPlaylistUpdate = onPlaylistUpdate,
                                onPlaylistDelete = { playlistId ->
                                    onPlaylistDelete(playlistId)
                                    onIntent(AppIntent.NavigateBack)
                                },
                            )
                            AppStackPage.LibraryTracks -> LibraryTracksContent(
                                uiState = tracksUiState,
                                onSortOptionSelected = onSortOptionSelected,
                                onToggleSortOrder = onToggleSortOrder,
                                listState = tracksListState,
                                contentPadding = contentPadding,
                                modifier = Modifier.fillMaxSize(),
                                onTrackClick = { track -> onTrackClick(track.id) },
                                playbackQueue = playbackQueue,
                                onTrackPlayNext = { track -> onTrackPlayNext(track.id) },
                                onTrackAddToQueue = { track -> onTrackAddToQueue(track.id) },
                                onTrackFavoriteToggle = { track, favorite -> onTrackFavoriteToggle(track.id, favorite) },
                                onTrackAlbumClick = { track -> onIntent(AppIntent.OpenAlbumDetails(track.albumId)) },
                                onTrackArtistClick = { artist -> onIntent(AppIntent.OpenArtistDetails(artist.id)) },
                                hazeState = hazeState,
                                onPlayAll = onTracksPlayAll,
                                onFilterQueryChange = onTracksFilterQueryChange,
                                onTrackContextBottomSheet = onTrackContextBottomSheet,
                            )
                            else -> LibraryContent(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = contentPadding,
                                recentTracks = tracksUiState.recentTracks,
                                listState = libraryListState,
                                onTrackClick = onRecentTrackClick,
                                playbackQueue = playbackQueue,
                                onTrackPlayNext = { track -> onTrackPlayNext(track.id) },
                                onTrackAddToQueue = { track -> onTrackAddToQueue(track.id) },
                                onTrackFavoriteToggle = { track, favorite -> onTrackFavoriteToggle(track.id, favorite) },
                                onTrackAlbumClick = { track -> onIntent(AppIntent.OpenAlbumDetails(track.albumId)) },
                                onTrackArtistClick = { artist -> onIntent(AppIntent.OpenArtistDetails(artist.id)) },
                                onTrackContextBottomSheet = onTrackContextBottomSheet,
                                onSearchSelected = {
                                    onIntent(AppIntent.OpenPage(AppStackPage.LibrarySearch))
                                },
                                onTracksSelected = {
                                    onIntent(AppIntent.OpenPage(AppStackPage.LibraryTracks))
                                },
                                onArtistsSelected = {
                                    onIntent(AppIntent.OpenPage(AppStackPage.LibraryArtists))
                                },
                                onAlbumsSelected = {
                                    onIntent(AppIntent.OpenPage(AppStackPage.LibraryAlbums))
                                },
                                onGenresSelected = {
                                    onIntent(AppIntent.OpenPage(AppStackPage.LibraryGenres))
                                },
                                onComposersSelected = {
                                    onIntent(AppIntent.OpenPage(AppStackPage.LibraryComposers))
                                },
                                onPlaylistsSelected = {
                                    onIntent(AppIntent.OpenPage(AppStackPage.LibraryPlaylists))
                                },
                            )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun HomeDestinationContent(content: @Composable () -> Unit) = content()

@Composable
internal fun InsightDestinationContent(content: @Composable () -> Unit) = content()

@Composable
internal fun LibraryDestinationContent(content: @Composable () -> Unit) = content()

@Composable
internal fun SettingsDestinationContent(content: @Composable () -> Unit) = content()

internal fun isForwardTransition(target: PageKey, initial: PageKey): Boolean =
    target.index > initial.index
