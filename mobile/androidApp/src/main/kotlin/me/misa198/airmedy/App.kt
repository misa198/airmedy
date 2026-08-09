package me.misa198.airmedy

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.rememberHazeState
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
import me.misa198.airmedy.ui.navigation.depth
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
import me.misa198.airmedy.ui.theme.AirmedyTheme
import me.misa198.airmedy.player.PlaybackState

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
    onIntent: (AppIntent) -> Unit = {},
    onSortOptionSelected: (TrackSortOption) -> Unit = {},
    onToggleSortOrder: () -> Unit = {},
    onTrackClick: (String) -> Unit = {},
    onArtistSortOptionSelected: (ArtistSortOption) -> Unit = {},
    onArtistToggleSortOrder: () -> Unit = {},
    onAlbumSortOptionSelected: (AlbumSortOption) -> Unit = {},
    onAlbumToggleSortOrder: () -> Unit = {},
    onGenreSortOptionSelected: (GenreSortOption) -> Unit = {},
    onGenreToggleSortOrder: () -> Unit = {},
    onComposerSortOptionSelected: (ComposerSortOption) -> Unit = {},
    onComposerToggleSortOrder: () -> Unit = {},
    onPairingQrScanned: (String) -> Boolean = { false },
    onUnpair: () -> Unit = {},
    onSyncScreenVisible: () -> Unit = {},
    onSyncScreenHidden: () -> Unit = {},
    playbackState: PlaybackState = PlaybackState.Idle,
    onPlaybackPrevious: () -> Unit = {},
    onPlaybackPlayPause: () -> Unit = {},
    onPlaybackNext: () -> Unit = {},
    onPlaybackSeek: (Long) -> Unit = {},
    systemVolume: Float = 0f,
    onSystemVolumeChange: (Float) -> Unit = {},
    onMiniPlayerDismiss: () -> Unit = {},
    onOpenMediaOutputSwitcher: () -> Unit = {},
    onFullScreenPlayerVisibilityChanged: (Boolean) -> Unit = {},
) {
    AirmedyTheme(themeMode = uiState.themeMode) {
        val hazeState = if (uiState.reduceTransparency) null else rememberHazeState()
        val homeListState = rememberLazyListState()
        val tracksListState = remember(tracksUiState.sortOption, tracksUiState.sortOrder) {
            LazyListState()
        }
        val artistsListState = remember(artistsUiState.sortOption, artistsUiState.sortOrder) {
            LazyListState()
        }
        val albumsListState = remember(albumsUiState.sortOption, albumsUiState.sortOrder) {
            LazyListState()
        }
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
        val currentPage = uiState.currentPage
        var previousPage by remember { mutableStateOf(currentPage) }
        val isForwardHeaderTransition = currentPage.depth >= previousPage.depth
        val showsMiniPlayer = playbackState.showsMiniPlayer()
        var isFullScreenPlayerVisible by rememberSaveable { mutableStateOf(false) }
        var isFullScreenPlayerOpeningFromSwipe by remember { mutableStateOf(false) }
        var fullScreenPlayerDragProgress by remember { mutableFloatStateOf(0f) }
        var isFullScreenPlayerDragging by remember { mutableStateOf(false) }
        var isNavigationCompact by remember { mutableStateOf(false) }
        var navigationScrollState by remember { mutableStateOf(NavigationChromeScrollState()) }
        val navigationScrollThresholdPx = with(LocalDensity.current) { 24.dp.toPx() }
        LaunchedEffect(isFullScreenPlayerVisible) {
            onFullScreenPlayerVisibilityChanged(isFullScreenPlayerVisible)
        }
        LaunchedEffect(showsMiniPlayer, uiState.selectedDestination, currentPage) {
            isNavigationCompact = false
            navigationScrollState = NavigationChromeScrollState()
        }
        LaunchedEffect(showsMiniPlayer) {
            if (!showsMiniPlayer) {
                isFullScreenPlayerVisible = false
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
        val pageTitle = stringResource(currentPage.titleRes(uiState.selectedDestination))
        val showBack = currentPage != AppStackPage.Root
        val showSyncAddAction = currentPage == AppStackPage.SettingsSync && syncUiState.desktop == null && !syncUiState.isPairing
        val showLibrarySortAction = currentPage == AppStackPage.LibraryTracks ||
            currentPage == AppStackPage.LibraryArtists || currentPage == AppStackPage.LibraryAlbums ||
            currentPage == AppStackPage.LibraryGenres || currentPage == AppStackPage.LibraryComposers
        BackHandler(enabled = showBack) { onIntent(AppIntent.NavigateBack) }

        val isContentScrolled by remember(uiState.selectedDestination, currentPage, homeListState, tracksListState, artistsListState, albumsListState, genresListState, composersListState) {
            derivedStateOf {
                when {
                    uiState.selectedDestination == AppDestination.Home && currentPage == AppStackPage.Root ->
                        homeListState.firstVisibleItemIndex > 0 || homeListState.firstVisibleItemScrollOffset > 0
                    currentPage == AppStackPage.LibraryTracks ->
                        tracksListState.firstVisibleItemIndex > 0 || tracksListState.firstVisibleItemScrollOffset > 0
                    currentPage == AppStackPage.LibraryArtists ->
                        artistsListState.firstVisibleItemIndex > 0 || artistsListState.firstVisibleItemScrollOffset > 0
                    currentPage == AppStackPage.LibraryAlbums ->
                        albumsListState.firstVisibleItemIndex > 0 || albumsListState.firstVisibleItemScrollOffset > 0
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
            previousPage = currentPage
        }
        Box(modifier = Modifier.fillMaxSize()) {
            AppDestinationContent(
                destination = uiState.selectedDestination,
                page = currentPage,
                themeMode = uiState.themeMode,
                reduceTransparency = uiState.reduceTransparency,
                hazeState = hazeState,
                navigationBottomPadding = navigationBottomPadding,
                homeListState = homeListState,
                tracksListState = tracksListState,
                artistsListState = artistsListState,
                albumsListState = albumsListState,
                genresListState = genresListState,
                composersListState = composersListState,
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
                onAlbumSortOptionSelected = onAlbumSortOptionSelected,
                onAlbumToggleSortOrder = onAlbumToggleSortOrder,
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
                    if (destination == uiState.selectedDestination && destination == AppDestination.Home) {
                        coroutineScope.launch {
                            homeListState.animateScrollToItem(0)
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
                    isFullScreenPlayerVisible = true
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
                    isFullScreenPlayerVisible = shouldOpen
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
                volume = systemVolume,
                onSeek = onPlaybackSeek,
                onVolumeChange = onSystemVolumeChange,
                onPrevious = onPlaybackPrevious,
                onPlayPause = onPlaybackPlayPause,
                onNext = onPlaybackNext,
                onOpenMediaOutputSwitcher = onOpenMediaOutputSwitcher,
                onDismiss = {
                    isFullScreenPlayerVisible = false
                    isFullScreenPlayerOpeningFromSwipe = false
                },
                hazeState = hazeState,
            )
        }
        BackHandler(enabled = isFullScreenPlayerVisible) {
            isFullScreenPlayerVisible = false
            isFullScreenPlayerOpeningFromSwipe = false
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AppPreview() {
    App()
}
