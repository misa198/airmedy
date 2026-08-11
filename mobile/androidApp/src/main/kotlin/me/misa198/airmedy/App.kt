package me.misa198.airmedy

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import dev.chrisbanes.haze.HazeInputScale
import kotlinx.coroutines.launch
import me.misa198.airmedy.ui.components.AirmedyGlassIconButton
import me.misa198.airmedy.ui.components.MaterialSymbols
import me.misa198.airmedy.ui.components.StackPageHeader
import me.misa198.airmedy.ui.components.LibrarySortHeaderButton
import me.misa198.airmedy.ui.components.LibrarySortOption
import me.misa198.airmedy.ui.navigation.AppDestinationContent
import me.misa198.airmedy.ui.navigation.CompactNavigationHeight
import me.misa198.airmedy.ui.navigation.ContentScrollDirection
import me.misa198.airmedy.ui.navigation.FloatingNavigationBottomMargin
import me.misa198.airmedy.ui.navigation.FloatingNavigationContentGap
import me.misa198.airmedy.ui.navigation.FloatingNavigationHeight
import me.misa198.airmedy.ui.navigation.FullScreenPlayer
import me.misa198.airmedy.ui.navigation.MiniPlayerHeight
import me.misa198.airmedy.ui.navigation.MiniPlayerNavigationGap
import me.misa198.airmedy.ui.navigation.NavigationChrome
import me.misa198.airmedy.ui.navigation.NavigationChromeScrollState
import me.misa198.airmedy.ui.navigation.reduceNavigationChromeScroll
import me.misa198.airmedy.ui.navigation.showsMiniPlayer
import me.misa198.airmedy.ui.navigation.titleRes
import me.misa198.airmedy.ui.screens.LibraryTracksUiState
import me.misa198.airmedy.ui.screens.LibraryArtistsUiState
import me.misa198.airmedy.ui.screens.ArtistSortOption
import me.misa198.airmedy.ui.screens.AlbumSortOption
import me.misa198.airmedy.ui.screens.LibraryAlbumsUiState
import me.misa198.airmedy.ui.screens.LibraryGenresUiState
import me.misa198.airmedy.ui.screens.GenreSortOption
import me.misa198.airmedy.ui.screens.LibraryComposersUiState
import me.misa198.airmedy.ui.screens.ComposerSortOption
import me.misa198.airmedy.ui.screens.TrackSortOption
import me.misa198.airmedy.ui.screens.AlbumDetailsUiState
import me.misa198.airmedy.ui.screens.ArtistDetailsUiState
import me.misa198.airmedy.ui.screens.GenreDetailsUiState
import me.misa198.airmedy.ui.screens.ComposerDetailsUiState
import me.misa198.airmedy.ui.theme.AirmedyTheme
import me.misa198.airmedy.player.PlaybackState
import me.misa198.airmedy.player.PlaybackQueueSnapshot
import me.misa198.airmedy.player.RepeatMode
import me.misa198.airmedy.sync.LibraryTrack

internal fun shouldShowHeaderBlur(
    isContentScrolled: Boolean,
    destinationChanged: Boolean,
    previousHeaderWasBlurred: Boolean,
): Boolean = isContentScrolled || (destinationChanged && previousHeaderWasBlurred)

@Composable
internal fun App(
    uiState: AppUiState = AppUiState(),
    syncUiState: SyncUiState = SyncUiState(),
    tracksUiState: LibraryTracksUiState = LibraryTracksUiState(),
    artistsUiState: LibraryArtistsUiState = LibraryArtistsUiState(),
    albumsUiState: LibraryAlbumsUiState = LibraryAlbumsUiState(),
    genresUiState: LibraryGenresUiState = LibraryGenresUiState(),
    composersUiState: LibraryComposersUiState = LibraryComposersUiState(),
    albumDetailsUiState: AlbumDetailsUiState = AlbumDetailsUiState(),
    artistDetailsUiState: ArtistDetailsUiState = ArtistDetailsUiState(),
    genreDetailsUiState: GenreDetailsUiState = GenreDetailsUiState(),
    composerDetailsUiState: ComposerDetailsUiState = ComposerDetailsUiState(),
    onIntent: (AppIntent) -> Unit = {},
    onSortOptionSelected: (TrackSortOption) -> Unit = {},
    onToggleSortOrder: () -> Unit = {},
    onTrackClick: (String) -> Unit = {},
    onRecentTrackClick: (String) -> Unit = {},
    onArtistSortOptionSelected: (ArtistSortOption) -> Unit = {},
    onArtistToggleSortOrder: () -> Unit = {},
    onAlbumSortOptionSelected: (AlbumSortOption) -> Unit = {},
    onAlbumToggleSortOrder: () -> Unit = {},
    onAlbumPlay: (String, Boolean) -> Unit = { _, _ -> },
    onAlbumTrackPlay: (String, String) -> Unit = { _, _ -> },
    onArtistPlay: (String, Boolean) -> Unit = { _, _ -> },
    onGenrePlay: (String, Boolean) -> Unit = { _, _ -> },
    onComposerPlay: (String, Boolean) -> Unit = { _, _ -> },
    onAlbumHeroColorChanged: (Color) -> Unit = {},
    onGenreSortOptionSelected: (GenreSortOption) -> Unit = {},
    onGenreToggleSortOrder: () -> Unit = {},
    onComposerSortOptionSelected: (ComposerSortOption) -> Unit = {},
    onComposerToggleSortOrder: () -> Unit = {},
    onPairingQrScanned: (String) -> Boolean = { false },
    onUnpair: () -> Unit = {},
    onSyncScreenVisible: () -> Unit = {},
    onSyncScreenHidden: () -> Unit = {},
    playbackState: PlaybackState = PlaybackState.Idle,
    playbackQueue: PlaybackQueueSnapshot = PlaybackQueueSnapshot(),
    queueTracks: List<LibraryTrack> = emptyList(),
    lyrics: String? = null,
    onPlaybackPrevious: () -> Unit = {},
    onPlaybackPlayPause: () -> Unit = {},
    onPlaybackNext: () -> Unit = {},
    onPlaybackSeek: (Long) -> Unit = {},
    onQueueTrackSelected: (String) -> Unit = {},
    onQueueReordered: (List<String>) -> Unit = {},
    onShuffleChange: (Boolean) -> Unit = {},
    onRepeatModeChange: (RepeatMode) -> Unit = {},
    systemVolume: Float = 0f,
    onSystemVolumeChange: (Float) -> Unit = {},
    onMiniPlayerDismiss: () -> Unit = {},
    onOpenMediaOutputSwitcher: () -> Unit = {},
    onFullScreenPlayerVisibilityChanged: (Boolean) -> Unit = {},
) {
    AirmedyTheme(themeMode = uiState.themeMode) {
        val hazeState = if (uiState.reduceTransparency) null else rememberHazeState()
        val homeListState = rememberLazyListState()
        val libraryListState = rememberLazyListState()
        val tracksListState = remember(tracksUiState.sortOption, tracksUiState.sortOrder) {
            LazyListState()
        }
        val artistsListState = remember(artistsUiState.sortOption, artistsUiState.sortOrder) {
            LazyListState()
        }
        val albumsListState = remember(albumsUiState.sortOption, albumsUiState.sortOrder) {
            LazyListState()
        }
        val artistDetailsListState = remember(uiState.selectedArtistId) { LazyListState() }
        val genreDetailsListState = remember(uiState.selectedGenreId) { LazyListState() }
        val composerDetailsListState = remember(uiState.selectedComposerId) { LazyListState() }
        val genresListState = remember(genresUiState.sortOption, genresUiState.sortOrder) {
            LazyListState()
        }
        val composersListState = remember(composersUiState.sortOption, composersUiState.sortOrder) {
            LazyListState()
        }
        val coroutineScope = rememberCoroutineScope()
        var previousDestination by remember { mutableStateOf(uiState.selectedDestination) }
        var previousHeaderWasBlurred by remember { mutableStateOf(false) }
        val destinationChanged = previousDestination != uiState.selectedDestination
        val animateHeaderChanges = !destinationChanged
        val currentStackPage = uiState.stackFor(uiState.selectedDestination).currentStackPage(uiState.selectedDestination)
        val currentPage = currentStackPage.page
        var previousStackPage by remember { mutableStateOf(currentStackPage) }
        val isForwardHeaderTransition = currentStackPage.index >= previousStackPage.index
        val showsMiniPlayer = playbackState.showsMiniPlayer()
        var isFullScreenPlayerVisible by rememberSaveable { mutableStateOf(false) }
        var isFullScreenPlayerOpeningFromSwipe by remember { mutableStateOf(false) }
        var fullScreenPlayerDragProgress by remember { mutableFloatStateOf(0f) }
        var isFullScreenPlayerDragging by remember { mutableStateOf(false) }
        var isNavigationCompact by remember { mutableStateOf(false) }
        var albumHeroColor by remember { mutableStateOf<Color?>(null) }
        val albumHeaderFade by animateFloatAsState(
            targetValue = if (currentPage == AppStackPage.AlbumDetails || currentPage == AppStackPage.ArtistDetails || currentPage == AppStackPage.GenreDetails || currentPage == AppStackPage.ComposerDetails) 1f else 0f,
            animationSpec = tween(1500, easing = FastOutSlowInEasing),
            label = "album-header-glass-colour-fade",
        )
        val albumHeaderGlassSurface = albumHeroColor?.takeIf { albumHeaderFade > 0.01f }
            ?.copy(alpha = 0.18f * albumHeaderFade)
        var navigationScrollState by remember { mutableStateOf(NavigationChromeScrollState()) }
        val navigationScrollThresholdPx = with(LocalDensity.current) { 24.dp.toPx() }
        fun setFullScreenPlayerVisible(visible: Boolean) {
            if (isFullScreenPlayerVisible == visible) return
            isFullScreenPlayerVisible = visible
            // System-bar content must be updated in the same user interaction as
            // the panel. Deferring this callback to LaunchedEffect can leave it
            // stale until an unrelated playback-state recomposition occurs.
            onFullScreenPlayerVisibilityChanged(visible)
        }
        LaunchedEffect(showsMiniPlayer) {
            if (!showsMiniPlayer) {
                // Keep the chrome geometry while destinations and pages change.
                // Playback ending is the only transition that invalidates a
                // compact mini-player layout.
                isNavigationCompact = false
                navigationScrollState = NavigationChromeScrollState()
                setFullScreenPlayerVisible(false)
                isFullScreenPlayerOpeningFromSwipe = false
                isFullScreenPlayerDragging = false
                fullScreenPlayerDragProgress = 0f
            }
        }
        val navigationChromeHeight = when {
            showsMiniPlayer && isNavigationCompact -> CompactNavigationHeight
            showsMiniPlayer -> FloatingNavigationHeight + MiniPlayerHeight + MiniPlayerNavigationGap
            else -> FloatingNavigationHeight
        }
        val targetNavigationBottomPadding = navigationChromeHeight + FloatingNavigationBottomMargin + FloatingNavigationContentGap +
            WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val navigationBottomPadding by animateDpAsState(
            targetValue = targetNavigationBottomPadding,
            animationSpec = tween(420, easing = FastOutSlowInEasing),
            label = "navigation-bottom-padding",
        )
        val pageTitle = if (currentPage == AppStackPage.AlbumDetails || currentPage == AppStackPage.ArtistDetails || currentPage == AppStackPage.GenreDetails || currentPage == AppStackPage.ComposerDetails) "" else stringResource(currentPage.titleRes(uiState.selectedDestination))
        val showBack = currentPage != AppStackPage.Root
        val showSyncAddAction = currentPage == AppStackPage.SettingsSync && syncUiState.desktop == null && !syncUiState.isPairing
        val showLibrarySortAction = currentPage == AppStackPage.LibraryTracks ||
            currentPage == AppStackPage.LibraryArtists || currentPage == AppStackPage.LibraryAlbums ||
            currentPage == AppStackPage.LibraryGenres || currentPage == AppStackPage.LibraryComposers
        BackHandler(enabled = showBack) { onIntent(AppIntent.NavigateBack) }

        val isContentScrolled by remember(uiState.selectedDestination, currentPage, homeListState, libraryListState, tracksListState, artistsListState, albumsListState, artistDetailsListState, genreDetailsListState, composerDetailsListState, genresListState, composersListState) {
            derivedStateOf {
                when {
                    uiState.selectedDestination == AppDestination.Home && currentPage == AppStackPage.Root ->
                        homeListState.firstVisibleItemIndex > 0 || homeListState.firstVisibleItemScrollOffset > 0
                    uiState.selectedDestination == AppDestination.Library && currentPage == AppStackPage.Root ->
                        libraryListState.firstVisibleItemIndex > 0 || libraryListState.firstVisibleItemScrollOffset > 0
                    currentPage == AppStackPage.LibraryTracks ->
                        tracksListState.firstVisibleItemIndex > 0 || tracksListState.firstVisibleItemScrollOffset > 0
                    currentPage == AppStackPage.LibraryArtists ->
                        artistsListState.firstVisibleItemIndex > 0 || artistsListState.firstVisibleItemScrollOffset > 0
                    currentPage == AppStackPage.LibraryAlbums ->
                        albumsListState.firstVisibleItemIndex > 0 || albumsListState.firstVisibleItemScrollOffset > 0
                    currentPage == AppStackPage.ArtistDetails ->
                        artistDetailsListState.firstVisibleItemIndex > 0 || artistDetailsListState.firstVisibleItemScrollOffset > 0
                    currentPage == AppStackPage.GenreDetails ->
                        genreDetailsListState.firstVisibleItemIndex > 0 || genreDetailsListState.firstVisibleItemScrollOffset > 0
                    currentPage == AppStackPage.ComposerDetails ->
                        composerDetailsListState.firstVisibleItemIndex > 0 || composerDetailsListState.firstVisibleItemScrollOffset > 0
                    currentPage == AppStackPage.LibraryGenres ->
                        genresListState.firstVisibleItemIndex > 0 || genresListState.firstVisibleItemScrollOffset > 0
                    currentPage == AppStackPage.LibraryComposers ->
                        composersListState.firstVisibleItemIndex > 0 || composersListState.firstVisibleItemScrollOffset > 0
                    else -> false
                }
            }
        }
        val showHeaderBlur = shouldShowHeaderBlur(
            isContentScrolled = isContentScrolled,
            destinationChanged = destinationChanged,
            previousHeaderWasBlurred = previousHeaderWasBlurred,
        )
        SideEffect {
            previousDestination = uiState.selectedDestination
            previousHeaderWasBlurred = isContentScrolled
            previousStackPage = currentStackPage
        }
        Box(modifier = Modifier.fillMaxSize()) {
            AppDestinationContent(
                stackPage = currentStackPage,
                themeMode = uiState.themeMode,
                reduceTransparency = uiState.reduceTransparency,
                hazeState = hazeState,
                navigationBottomPadding = navigationBottomPadding,
                homeListState = homeListState,
                libraryListState = libraryListState,
                tracksListState = tracksListState,
                artistsListState = artistsListState,
                albumsListState = albumsListState,
                genresListState = genresListState,
                composersListState = composersListState,
                albumDetailsUiState = albumDetailsUiState,
                artistDetailsUiState = artistDetailsUiState,
                genreDetailsUiState = genreDetailsUiState,
                composerDetailsUiState = composerDetailsUiState,
                selectedAlbumId = uiState.selectedAlbumId,
                selectedArtistId = uiState.selectedArtistId,
                selectedGenreId = uiState.selectedGenreId,
                selectedComposerId = uiState.selectedComposerId,
                artistDetailsListState = artistDetailsListState,
                genreDetailsListState = genreDetailsListState,
                composerDetailsListState = composerDetailsListState,
                onIntent = onIntent,
                syncUiState = syncUiState,
                tracksUiState = tracksUiState,
                artistsUiState = artistsUiState,
                albumsUiState = albumsUiState,
                genresUiState = genresUiState,
                composersUiState = composersUiState,
                onSortOptionSelected = onSortOptionSelected,
                onToggleSortOrder = onToggleSortOrder,
                onTrackClick = onTrackClick,
                onRecentTrackClick = onRecentTrackClick,
                onAlbumSortOptionSelected = onAlbumSortOptionSelected,
                onAlbumToggleSortOrder = onAlbumToggleSortOrder,
                onAlbumPlay = onAlbumPlay,
                onAlbumTrackPlay = onAlbumTrackPlay,
                onArtistPlay = onArtistPlay,
                onGenrePlay = onGenrePlay,
                onComposerPlay = onComposerPlay,
                onAlbumHeroColorChanged = { color ->
                    albumHeroColor = color
                    onAlbumHeroColorChanged(color)
                },
                onPairingQrScanned = onPairingQrScanned,
                onUnpair = onUnpair,
                onSyncScreenVisible = onSyncScreenVisible,
                onSyncScreenHidden = onSyncScreenHidden,
                onContentScroll = { delta ->
                    if (!showsMiniPlayer) return@AppDestinationContent
                    navigationScrollState = reduceNavigationChromeScroll(
                        state = navigationScrollState.copy(compact = isNavigationCompact),
                        delta = delta,
                        thresholdPx = navigationScrollThresholdPx,
                    )
                    isNavigationCompact = navigationScrollState.compact
                },
            )
            albumHeroColor?.takeIf { albumHeaderFade > 0.01f }?.let { color ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .background(
                            Brush.verticalGradient(
                                0f to color.copy(alpha = 0.36f * albumHeaderFade),
                                1f to color.copy(alpha = 0f),
                            ),
                        ),
                )
            }
            StackPageHeader(
                title = pageTitle,
                hazeState = hazeState,
                isContentScrolled = showHeaderBlur,
                onBackClick = if (showBack) {
                    { onIntent(AppIntent.NavigateBack) }
                } else {
                    null
                },
                hasActions = showSyncAddAction || showLibrarySortAction,
                animateChanges = animateHeaderChanges,
                titleStackKey = "${uiState.selectedDestination.name}:${currentPage.name}",
                isForward = isForwardHeaderTransition,
                backGlassTintAlpha = if (currentPage == AppStackPage.AlbumDetails || currentPage == AppStackPage.ArtistDetails || currentPage == AppStackPage.GenreDetails || currentPage == AppStackPage.ComposerDetails) 0.08f else null,
                backHazeInputScale = if (currentPage == AppStackPage.AlbumDetails || currentPage == AppStackPage.ArtistDetails || currentPage == AppStackPage.GenreDetails || currentPage == AppStackPage.ComposerDetails) HazeInputScale.Fixed(1f) else HazeInputScale.Auto,
            ) {
                if (showSyncAddAction) {
                    AirmedyGlassIconButton(
                        hazeState = hazeState,
                        symbol = MaterialSymbols.Add,
                        label = stringResource(R.string.sync_add_device),
                        onClick = { onIntent(AppIntent.OpenPage(AppStackPage.SettingsSyncScanner)) },
                    )
                } else if (currentPage == AppStackPage.LibraryTracks) {
                    LibrarySortHeaderButton(
                        hazeState = hazeState,
                        options = listOf(
                            LibrarySortOption(TrackSortOption.Name, R.string.sort_name),
                            LibrarySortOption(TrackSortOption.Artist, R.string.sort_artist),
                            LibrarySortOption(TrackSortOption.PlayCount, R.string.sort_play_count),
                            LibrarySortOption(TrackSortOption.DateAdded, R.string.sort_date_added),
                        ),
                        selectedOption = tracksUiState.sortOption,
                        sortOrder = tracksUiState.sortOrder,
                        onSortOptionSelected = onSortOptionSelected,
                        onToggleSortOrder = onToggleSortOrder,
                    )
                } else if (currentPage == AppStackPage.LibraryArtists) {
                    LibrarySortHeaderButton(
                        hazeState = hazeState,
                        options = listOf(
                            LibrarySortOption(ArtistSortOption.Name, R.string.sort_name),
                            LibrarySortOption(ArtistSortOption.DateAdded, R.string.sort_date_added),
                        ),
                        selectedOption = artistsUiState.sortOption,
                        sortOrder = artistsUiState.sortOrder,
                        onSortOptionSelected = onArtistSortOptionSelected,
                        onToggleSortOrder = onArtistToggleSortOrder,
                    )
                } else if (currentPage == AppStackPage.LibraryAlbums) {
                    LibrarySortHeaderButton(
                        hazeState = hazeState,
                        options = listOf(
                            LibrarySortOption(AlbumSortOption.Name, R.string.sort_name),
                            LibrarySortOption(AlbumSortOption.Artist, R.string.sort_artist),
                            LibrarySortOption(AlbumSortOption.DateAdded, R.string.sort_date_added),
                        ),
                        selectedOption = albumsUiState.sortOption,
                        sortOrder = albumsUiState.sortOrder,
                        onSortOptionSelected = onAlbumSortOptionSelected,
                        onToggleSortOrder = onAlbumToggleSortOrder,
                        glassSurfaceColor = albumHeaderGlassSurface,
                    )
                } else if (currentPage == AppStackPage.LibraryGenres) {
                    LibrarySortHeaderButton(
                        hazeState = hazeState,
                        options = listOf(
                            LibrarySortOption(GenreSortOption.Name, R.string.sort_name),
                            LibrarySortOption(GenreSortOption.DateAdded, R.string.sort_date_added),
                        ),
                        selectedOption = genresUiState.sortOption,
                        sortOrder = genresUiState.sortOrder,
                        onSortOptionSelected = onGenreSortOptionSelected,
                        onToggleSortOrder = onGenreToggleSortOrder,
                    )
                } else if (currentPage == AppStackPage.LibraryComposers) {
                    LibrarySortHeaderButton(
                        hazeState = hazeState,
                        options = listOf(
                            LibrarySortOption(ComposerSortOption.Name, R.string.sort_name),
                            LibrarySortOption(ComposerSortOption.DateAdded, R.string.sort_date_added),
                        ),
                        selectedOption = composersUiState.sortOption,
                        sortOrder = composersUiState.sortOrder,
                        onSortOptionSelected = onComposerSortOptionSelected,
                        onToggleSortOrder = onComposerToggleSortOrder,
                    )
                }
            }
            NavigationChrome(
                selectedDestination = uiState.selectedDestination,
                playbackState = playbackState,
                hazeState = hazeState,
                compact = showsMiniPlayer && isNavigationCompact,
                onExpandClick = {
                    isNavigationCompact = false
                    navigationScrollState = NavigationChromeScrollState()
                },
                onDestinationSelected = { destination ->
                    if (destination == uiState.selectedDestination) {
                        if (destination == AppDestination.Home) {
                            coroutineScope.launch {
                                homeListState.animateScrollToItem(0)
                            }
                        } else if (destination == AppDestination.Library && currentPage == AppStackPage.Root) {
                            coroutineScope.launch {
                                libraryListState.animateScrollToItem(0)
                            }
                        }
                    }
                    onIntent(AppIntent.SelectDestination(destination))
                },
                onPreviousClick = onPlaybackPrevious,
                onPlayPauseClick = onPlaybackPlayPause,
                onNextClick = onPlaybackNext,
                onMiniPlayerDismiss = {
                    isNavigationCompact = false
                    navigationScrollState = NavigationChromeScrollState()
                    onMiniPlayerDismiss()
                },
                onOpenFullScreenPlayer = {
                    isFullScreenPlayerOpeningFromSwipe = false
                    setFullScreenPlayerVisible(true)
                },
                onFullScreenPlayerDrag = { progress ->
                    isFullScreenPlayerDragging = true
                    fullScreenPlayerDragProgress = progress
                },
                onFullScreenPlayerDragEnd = { shouldOpen ->
                    isFullScreenPlayerDragging = false
                    // A partial pull must not remain as the overlay's source of truth
                    // once the pointer is released. The overlay then animates to either
                    // its closed or fully-open resting state.
                    fullScreenPlayerDragProgress = 0f
                    isFullScreenPlayerOpeningFromSwipe = shouldOpen
                    setFullScreenPlayerVisible(shouldOpen)
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(start = 20.dp, end = 20.dp, bottom = FloatingNavigationBottomMargin),
            )
            FullScreenPlayer(
                visible = isFullScreenPlayerVisible,
                dragProgress = fullScreenPlayerDragProgress,
                isDragging = isFullScreenPlayerDragging,
                openingFromMiniPlayerSwipe = isFullScreenPlayerOpeningFromSwipe,
                playbackState = playbackState,
                queue = playbackQueue,
                queueTracks = queueTracks,
                lyrics = lyrics,
                volume = systemVolume,
                onSeek = onPlaybackSeek,
                onVolumeChange = onSystemVolumeChange,
                onPrevious = onPlaybackPrevious,
                onPlayPause = onPlaybackPlayPause,
                onNext = onPlaybackNext,
                onQueueTrackSelected = onQueueTrackSelected,
                onQueueReordered = onQueueReordered,
                onShuffleChange = onShuffleChange,
                onRepeatModeChange = onRepeatModeChange,
                onOpenMediaOutputSwitcher = onOpenMediaOutputSwitcher,
                onDismiss = {
                    setFullScreenPlayerVisible(false)
                    isFullScreenPlayerOpeningFromSwipe = false
                },
                hazeState = hazeState,
            )
        }
        BackHandler(enabled = isFullScreenPlayerVisible) {
            setFullScreenPlayerVisible(false)
            isFullScreenPlayerOpeningFromSwipe = false
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AppPreview() {
    App()
}
