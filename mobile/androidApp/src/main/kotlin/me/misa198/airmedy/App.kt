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
import java.time.LocalTime
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import dev.chrisbanes.haze.HazeInputScale
import kotlinx.coroutines.launch
import me.misa198.airmedy.ui.components.AirmedyGlassIconButton
import me.misa198.airmedy.ui.components.AnchoredPopupMenuHost
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
import me.misa198.airmedy.ui.navigation.NavigationChromeScrollAccumulator
import me.misa198.airmedy.ui.navigation.showsMiniPlayer
import me.misa198.airmedy.ui.navigation.titleRes
import me.misa198.airmedy.ui.screens.LibraryTracksUiState
import me.misa198.airmedy.ui.screens.LibraryArtistsUiState
import me.misa198.airmedy.ui.screens.ArtistSortOption
import me.misa198.airmedy.ui.screens.AlbumSortOption
import me.misa198.airmedy.ui.screens.AlbumLayoutMode
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
import me.misa198.airmedy.ui.screens.LibraryPlaylistsUiState
import me.misa198.airmedy.ui.screens.PlaylistDetailsUiState
import me.misa198.airmedy.ui.screens.isFavorite
import me.misa198.airmedy.ui.screens.CreatePlaylistBottomSheet
import me.misa198.airmedy.ui.components.TrackContextBottomSheet
import me.misa198.airmedy.ui.components.TrackContextBottomSheetRequest
import me.misa198.airmedy.ui.theme.AirmedyTheme
import me.misa198.airmedy.player.PlaybackState
import me.misa198.airmedy.player.ArtworkCrossfadeTransition
import me.misa198.airmedy.player.PlaybackQueueSnapshot
import me.misa198.airmedy.player.RepeatMode
import me.misa198.airmedy.sync.LibraryTrack
import me.misa198.airmedy.lastfm.LastFmStatus

internal fun shouldShowHeaderBlur(
    isContentScrolled: Boolean,
    destinationChanged: Boolean,
    previousHeaderWasBlurred: Boolean,
): Boolean = isContentScrolled || (destinationChanged && previousHeaderWasBlurred)

internal fun homeGreetingTitleRes(hour: Int): Int = when (hour) {
    in 0..11 -> R.string.home_greeting_morning
    in 12..16 -> R.string.home_greeting_afternoon
    in 17..20 -> R.string.home_greeting_evening
    else -> R.string.home_greeting_night
}

@Composable
private fun LazyListState.resetAfterPagePop(generation: Int) {
    LaunchedEffect(generation) {
        if (generation > 0) scrollToItem(0)
    }
}

@Composable
internal fun App(
    uiState: AppUiState = AppUiState(),
    syncUiState: SyncUiState = SyncUiState(),
    tracksUiState: LibraryTracksUiState = LibraryTracksUiState(),
    artistsUiState: LibraryArtistsUiState = LibraryArtistsUiState(),
    albumsUiState: LibraryAlbumsUiState = LibraryAlbumsUiState(),
    genresUiState: LibraryGenresUiState = LibraryGenresUiState(),
    composersUiState: LibraryComposersUiState = LibraryComposersUiState(),
    playlistsUiState: LibraryPlaylistsUiState = LibraryPlaylistsUiState(),
    albumDetailsUiState: AlbumDetailsUiState = AlbumDetailsUiState(),
    playlistDetailsUiState: PlaylistDetailsUiState = PlaylistDetailsUiState(),
    artistDetailsUiState: ArtistDetailsUiState = ArtistDetailsUiState(),
    genreDetailsUiState: GenreDetailsUiState = GenreDetailsUiState(),
    composerDetailsUiState: ComposerDetailsUiState = ComposerDetailsUiState(),
    onIntent: (AppIntent) -> Unit = {},
    onSortOptionSelected: (TrackSortOption) -> Unit = {},
    onToggleSortOrder: () -> Unit = {},
    onTrackClick: (String) -> Unit = {},
    onTracksPlayAll: (Boolean) -> Unit = {},
    onTracksFilterQueryChange: (String) -> Unit = {},
    onRecentTrackClick: (String) -> Unit = {},
    onHomeTrackClick: (List<LibraryTrack>, String) -> Unit = { _, _ -> },
    onTrackPlayNext: (String) -> Unit = {},
    onTrackAddToQueue: (String) -> Unit = {},
    onAlbumPlayNext: (List<String>) -> Unit = {},
    onAlbumAddToQueue: (List<String>) -> Unit = {},
    onAlbumAddToFavorites: (List<String>) -> Unit = {},
    onArtistSortOptionSelected: (ArtistSortOption) -> Unit = {},
    onArtistToggleSortOrder: () -> Unit = {},
    onAlbumSortOptionSelected: (AlbumSortOption) -> Unit = {},
    onAlbumToggleSortOrder: () -> Unit = {},
    onAlbumLayoutModeSelected: (AlbumLayoutMode) -> Unit = {},
    onAlbumPlay: (String, Boolean) -> Unit = { _, _ -> },
    onAlbumsPlayAll: (Boolean) -> Unit = {},
    onAlbumsFilterQueryChange: (String) -> Unit = {},
    onAlbumTrackPlay: (String, String) -> Unit = { _, _ -> },
    onPlaylistPlay: (String, Boolean) -> Unit = { _, _ -> },
    onPlaylistTrackPlay: (String, String) -> Unit = { _, _ -> },
    onPlaylistTrackRemove: (String, String) -> Unit = { _, _ -> },
    onPlaylistPlayNext: (List<String>) -> Unit = {},
    onPlaylistAddToQueue: (List<String>) -> Unit = {},
    onPlaylistUpdate: (String, String, android.net.Uri?, Boolean) -> Unit = { _, _, _, _ -> },
    onPlaylistDelete: (String) -> Unit = {},
    onCreatePlaylist: (String, android.net.Uri?) -> Unit = { _, _ -> },
    onCreatePlaylistWithTracks: (String, android.net.Uri?, List<String>) -> Unit = { _, _, _ -> },
    onPlaylistMembershipChange: (String, List<String>, Boolean) -> Unit = { _, _, _ -> },
    onArtistPlay: (String, Boolean) -> Unit = { _, _ -> },
    onArtistPlayNext: (List<String>) -> Unit = {},
    onArtistAddToQueue: (List<String>) -> Unit = {},
    orderedTrackIdsForArtist: (String) -> List<String> = { emptyList() },
    onArtistTrackContextBottomSheet: (me.misa198.airmedy.ui.components.TrackContextBottomSheetRequest) -> Unit = {},
    onArtistsFilterQueryChange: (String) -> Unit = {},
    onGenrePlay: (String, Boolean) -> Unit = { _, _ -> },
    onGenrePlayNext: (List<String>) -> Unit = {},
    onGenreAddToQueue: (List<String>) -> Unit = {},
    orderedTrackIdsForGenre: (String) -> List<String> = { emptyList() },
    onGenresFilterQueryChange: (String) -> Unit = {},
    onComposerPlay: (String, Boolean) -> Unit = { _, _ -> },
    onComposerPlayNext: (List<String>) -> Unit = {},
    onComposerAddToQueue: (List<String>) -> Unit = {},
    orderedTrackIdsForComposer: (String) -> List<String> = { emptyList() },
    onComposerTrackContextBottomSheet: (me.misa198.airmedy.ui.components.TrackContextBottomSheetRequest) -> Unit = {},
    onComposersFilterQueryChange: (String) -> Unit = {},
    onAlbumHeroColorChanged: (Color) -> Unit = {},
    onGenreSortOptionSelected: (GenreSortOption) -> Unit = {},
    onGenreToggleSortOrder: () -> Unit = {},
    onComposerSortOptionSelected: (ComposerSortOption) -> Unit = {},
    onComposerToggleSortOrder: () -> Unit = {},
    onPairingQrScanned: (String) -> Boolean = { false },
    onUnpair: () -> Unit = {},
    onSyncScreenVisible: () -> Unit = {},
    onSyncScreenHidden: () -> Unit = {},
    lastFmStatus: LastFmStatus = LastFmStatus(),
    onLastFmConnect: () -> Unit = {},
    onLastFmDisconnect: () -> Unit = {},
    crossfadeSeconds: Int = 0,
    lastEnabledCrossfadeSeconds: Int = 4,
    onCrossfadeSecondsChanged: (Int) -> Unit = {},
    blendArtworkDuringCrossfade: Boolean = true,
    onBlendArtworkDuringCrossfadeChanged: (Boolean) -> Unit = {},
    normalizationAvailable: Boolean = false,
    normalization: me.misa198.airmedy.player.NormalizationSettings = me.misa198.airmedy.player.NormalizationSettings(),
    onNormalizationChanged: (me.misa198.airmedy.player.NormalizationSettings) -> Unit = {},
    artworkCrossfade: ArtworkCrossfadeTransition? = null,
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
    onQueueTrackRemoved: (String) -> Unit = {},
    onShuffleChange: (Boolean) -> Unit = {},
    onRepeatModeChange: (RepeatMode) -> Unit = {},
    systemVolume: Float = 0f,
    onSystemVolumeChange: (Float) -> Unit = {},
    onMiniPlayerDismiss: () -> Unit = {},
    onOpenMediaOutputSwitcher: () -> Unit = {},
    onFavoriteToggle: (String, Boolean) -> Unit = { _, _ -> },
    onFullScreenPlayerVisibilityChanged: (Boolean) -> Unit = {},
) {
    AirmedyTheme(themeMode = uiState.themeMode) {
        val hazeState = if (uiState.reduceTransparency) null else rememberHazeState()
        val currentStackPage = uiState.stackFor(uiState.selectedDestination).currentStackPage(uiState.selectedDestination)
        val homeListState = remember(uiState.pageStateGenerationFor(AppDestination.Home, AppStackPage.Root)) { LazyListState() }
        val libraryListState = remember(uiState.pageStateGenerationFor(AppDestination.Library, AppStackPage.Root)) { LazyListState() }
        val tracksListState = remember(uiState.pageStateGenerationFor(AppDestination.Library, AppStackPage.LibraryTracks), tracksUiState.sortOption, tracksUiState.sortOrder) {
            LazyListState()
        }
        val artistsListState = remember(uiState.pageStateGenerationFor(AppDestination.Library, AppStackPage.LibraryArtists), artistsUiState.sortOption, artistsUiState.sortOrder) {
            LazyListState()
        }
        val albumsListState = remember(uiState.pageStateGenerationFor(AppDestination.Library, AppStackPage.LibraryAlbums), albumsUiState.sortOption, albumsUiState.sortOrder, albumsUiState.layoutMode) {
            LazyListState()
        }
        val artistDetailsListState = remember(uiState.pageStateGenerationFor(AppDestination.Library, AppStackPage.ArtistDetails), uiState.selectedArtistId) { LazyListState() }
        val genreDetailsListState = remember(uiState.pageStateGenerationFor(AppDestination.Library, AppStackPage.GenreDetails), uiState.selectedGenreId) { LazyListState() }
        val composerDetailsListState = remember(uiState.pageStateGenerationFor(AppDestination.Library, AppStackPage.ComposerDetails), uiState.selectedComposerId) { LazyListState() }
        val genresListState = remember(uiState.pageStateGenerationFor(AppDestination.Library, AppStackPage.LibraryGenres), genresUiState.sortOption, genresUiState.sortOrder) {
            LazyListState()
        }
        val composersListState = remember(uiState.pageStateGenerationFor(AppDestination.Library, AppStackPage.LibraryComposers), composersUiState.sortOption, composersUiState.sortOrder) {
            LazyListState()
        }
        val playlistsListState = remember(uiState.pageStateGenerationFor(AppDestination.Library, AppStackPage.LibraryPlaylists)) { LazyListState() }
        homeListState.resetAfterPagePop(uiState.pageStateGenerationFor(AppDestination.Home, AppStackPage.Root))
        libraryListState.resetAfterPagePop(uiState.pageStateGenerationFor(AppDestination.Library, AppStackPage.Root))
        tracksListState.resetAfterPagePop(uiState.pageStateGenerationFor(AppDestination.Library, AppStackPage.LibraryTracks))
        artistsListState.resetAfterPagePop(uiState.pageStateGenerationFor(AppDestination.Library, AppStackPage.LibraryArtists))
        albumsListState.resetAfterPagePop(uiState.pageStateGenerationFor(AppDestination.Library, AppStackPage.LibraryAlbums))
        artistDetailsListState.resetAfterPagePop(uiState.pageStateGenerationFor(AppDestination.Library, AppStackPage.ArtistDetails))
        genreDetailsListState.resetAfterPagePop(uiState.pageStateGenerationFor(AppDestination.Library, AppStackPage.GenreDetails))
        composerDetailsListState.resetAfterPagePop(uiState.pageStateGenerationFor(AppDestination.Library, AppStackPage.ComposerDetails))
        genresListState.resetAfterPagePop(uiState.pageStateGenerationFor(AppDestination.Library, AppStackPage.LibraryGenres))
        composersListState.resetAfterPagePop(uiState.pageStateGenerationFor(AppDestination.Library, AppStackPage.LibraryComposers))
        playlistsListState.resetAfterPagePop(uiState.pageStateGenerationFor(AppDestination.Library, AppStackPage.LibraryPlaylists))
        val coroutineScope = rememberCoroutineScope()
        var previousDestination by remember { mutableStateOf(uiState.selectedDestination) }
        var previousHeaderWasBlurred by remember { mutableStateOf(false) }
        val destinationChanged = previousDestination != uiState.selectedDestination
        val animateHeaderChanges = !destinationChanged
        val currentPage = currentStackPage.page
        var previousStackPage by remember { mutableStateOf(currentStackPage) }
        val isForwardHeaderTransition = currentStackPage.index >= previousStackPage.index
        val showsMiniPlayer = playbackState.showsMiniPlayer()
        var isFullScreenPlayerVisible by rememberSaveable { mutableStateOf(false) }
        var isFullScreenPlayerOpeningFromSwipe by remember { mutableStateOf(false) }
        var fullScreenPlayerDragProgress by remember { mutableFloatStateOf(0f) }
        var isFullScreenPlayerDragging by remember { mutableStateOf(false) }
        var pendingFullScreenPlayerAction by remember { mutableStateOf<(() -> Unit)?>(null) }
        var trackContextSheet by remember { mutableStateOf<TrackContextBottomSheetRequest?>(null) }
        var isNavigationCompact by remember { mutableStateOf(false) }
        var albumHeroColor by remember { mutableStateOf<Color?>(null) }
        val albumHeaderFade by animateFloatAsState(
            targetValue = if (currentPage == AppStackPage.AlbumDetails || currentPage == AppStackPage.PlaylistDetails || currentPage == AppStackPage.ArtistDetails || currentPage == AppStackPage.GenreDetails || currentPage == AppStackPage.ComposerDetails) 1f else 0f,
            animationSpec = tween(1500, easing = FastOutSlowInEasing),
            label = "album-header-glass-colour-fade",
        )
        val albumHeaderGlassSurface = albumHeroColor?.takeIf { albumHeaderFade > 0.01f }
            ?.copy(alpha = 0.18f * albumHeaderFade)
        val navigationScrollAccumulator = remember { NavigationChromeScrollAccumulator() }
        val navigationScrollThresholdPx = with(LocalDensity.current) { 24.dp.toPx() }
        fun setFullScreenPlayerVisible(visible: Boolean) {
            if (isFullScreenPlayerVisible == visible) return
            isFullScreenPlayerVisible = visible
            // System-bar content must be updated in the same user interaction as
            // the panel. Deferring this callback to LaunchedEffect can leave it
            // stale until an unrelated playback-state recomposition occurs.
            onFullScreenPlayerVisibilityChanged(visible)
        }
        fun closeFullScreenPlayerThen(action: () -> Unit) {
            if (!isFullScreenPlayerVisible) {
                action()
                return
            }
            pendingFullScreenPlayerAction = action
            setFullScreenPlayerVisible(false)
            isFullScreenPlayerOpeningFromSwipe = false
            isFullScreenPlayerDragging = false
            fullScreenPlayerDragProgress = 0f
        }
        LaunchedEffect(showsMiniPlayer) {
            if (!showsMiniPlayer) {
                // Keep the chrome geometry while destinations and pages change.
                // Playback ending is the only transition that invalidates a
                // compact mini-player layout.
                isNavigationCompact = false
                navigationScrollAccumulator.reset()
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
        val pageTitle = when {
            currentPage == AppStackPage.AlbumDetails || currentPage == AppStackPage.PlaylistDetails || currentPage == AppStackPage.ArtistDetails || currentPage == AppStackPage.GenreDetails || currentPage == AppStackPage.ComposerDetails -> ""
            uiState.selectedDestination == AppDestination.Home && currentPage == AppStackPage.Root -> stringResource(homeGreetingTitleRes(LocalTime.now().hour))
            else -> stringResource(currentPage.titleRes(uiState.selectedDestination))
        }
        val showBack = currentPage != AppStackPage.Root
        val showSyncAddAction = currentPage == AppStackPage.SettingsSync && syncUiState.desktop == null && !syncUiState.isPairing
        val showLibrarySortAction = currentPage == AppStackPage.LibraryTracks ||
            currentPage == AppStackPage.LibraryArtists || currentPage == AppStackPage.LibraryAlbums ||
            currentPage == AppStackPage.LibraryGenres || currentPage == AppStackPage.LibraryComposers
        val showPlaylistAddAction = currentPage == AppStackPage.LibraryPlaylists
        var showCreatePlaylistSheet by rememberSaveable { mutableStateOf(false) }
        var createPlaylistForTracks by remember { mutableStateOf<List<String>?>(null) }
        BackHandler(enabled = showBack) { onIntent(AppIntent.NavigateBack) }

        val isContentScrolled by remember(uiState.selectedDestination, currentPage, homeListState, libraryListState, tracksListState, artistsListState, albumsListState, artistDetailsListState, genreDetailsListState, composerDetailsListState, genresListState, composersListState, playlistsListState) {
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
                    currentPage == AppStackPage.LibraryPlaylists ->
                        playlistsListState.firstVisibleItemIndex > 0 || playlistsListState.firstVisibleItemScrollOffset > 0
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
        AnchoredPopupMenuHost(
            hazeState = hazeState,
            dismissKey = currentStackPage,
        ) {
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
                playlistsListState = playlistsListState,
                albumDetailsUiState = albumDetailsUiState,
                playlistDetailsUiState = playlistDetailsUiState,
                artistDetailsUiState = artistDetailsUiState,
                genreDetailsUiState = genreDetailsUiState,
                composerDetailsUiState = composerDetailsUiState,
                selectedAlbumId = uiState.selectedAlbumId,
                selectedPlaylistId = uiState.selectedPlaylistId,
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
                playlistsUiState = playlistsUiState,
                onSortOptionSelected = onSortOptionSelected,
                onToggleSortOrder = onToggleSortOrder,
                onTrackClick = onTrackClick,
                onTracksPlayAll = onTracksPlayAll,
                onTracksFilterQueryChange = onTracksFilterQueryChange,
                onRecentTrackClick = onRecentTrackClick,
                onHomeTrackClick = onHomeTrackClick,
                playbackQueue = playbackQueue,
                onTrackPlayNext = onTrackPlayNext,
                onTrackAddToQueue = onTrackAddToQueue,
                onTrackFavoriteToggle = onFavoriteToggle,
                onAlbumPlayNext = onAlbumPlayNext,
                onAlbumAddToQueue = onAlbumAddToQueue,
                onAlbumAddToFavorites = onAlbumAddToFavorites,
                onAlbumSortOptionSelected = onAlbumSortOptionSelected,
                onAlbumToggleSortOrder = onAlbumToggleSortOrder,
                onAlbumPlay = onAlbumPlay,
                onAlbumsPlayAll = onAlbumsPlayAll,
                onAlbumsFilterQueryChange = onAlbumsFilterQueryChange,
                onAlbumTrackPlay = onAlbumTrackPlay,
                onPlaylistPlay = onPlaylistPlay,
                onPlaylistTrackPlay = onPlaylistTrackPlay,
                onPlaylistTrackRemove = onPlaylistTrackRemove,
                onPlaylistPlayNext = onPlaylistPlayNext,
                onPlaylistAddToQueue = onPlaylistAddToQueue,
                onPlaylistUpdate = onPlaylistUpdate,
                onPlaylistDelete = onPlaylistDelete,
                onTrackContextBottomSheet = { request -> trackContextSheet = request },
                onArtistPlay = onArtistPlay,
                onArtistPlayNext = onArtistPlayNext,
                onArtistAddToQueue = onArtistAddToQueue,
                orderedTrackIdsForArtist = orderedTrackIdsForArtist,
                onArtistTrackContextBottomSheet = { request -> trackContextSheet = request },
                onArtistsFilterQueryChange = onArtistsFilterQueryChange,
                onGenrePlay = onGenrePlay,
                onGenrePlayNext = onGenrePlayNext,
                onGenreAddToQueue = onGenreAddToQueue,
                orderedTrackIdsForGenre = orderedTrackIdsForGenre,
                onGenreTrackContextBottomSheet = { request -> trackContextSheet = request },
                onGenresFilterQueryChange = onGenresFilterQueryChange,
                onComposerPlay = onComposerPlay,
                onComposerPlayNext = onComposerPlayNext,
                onComposerAddToQueue = onComposerAddToQueue,
                orderedTrackIdsForComposer = orderedTrackIdsForComposer,
                onComposerTrackContextBottomSheet = { request -> trackContextSheet = request },
                onComposersFilterQueryChange = onComposersFilterQueryChange,
                onAlbumHeroColorChanged = { color ->
                    albumHeroColor = color
                    onAlbumHeroColorChanged(color)
                },
                onPairingQrScanned = onPairingQrScanned,
                onUnpair = onUnpair,
                onSyncScreenVisible = onSyncScreenVisible,
                onSyncScreenHidden = onSyncScreenHidden,
                lastFmStatus = lastFmStatus,
                onLastFmConnect = onLastFmConnect,
                onLastFmDisconnect = onLastFmDisconnect,
                crossfadeSeconds = crossfadeSeconds,
                lastEnabledCrossfadeSeconds = lastEnabledCrossfadeSeconds,
                onCrossfadeSecondsChanged = onCrossfadeSecondsChanged,
                blendArtworkDuringCrossfade = blendArtworkDuringCrossfade,
                onBlendArtworkDuringCrossfadeChanged = onBlendArtworkDuringCrossfadeChanged,
                normalizationAvailable = normalizationAvailable,
                normalization = normalization,
                onNormalizationChanged = onNormalizationChanged,
                onContentScroll = { delta ->
                    if (!showsMiniPlayer) return@AppDestinationContent
                    if (navigationScrollAccumulator.update(delta, navigationScrollThresholdPx)) {
                        isNavigationCompact = navigationScrollAccumulator.compact
                    }
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
                hasActions = showSyncAddAction || showLibrarySortAction || showPlaylistAddAction,
                animateChanges = animateHeaderChanges,
                titleStackKey = "${uiState.selectedDestination.name}:${currentPage.name}",
                isForward = isForwardHeaderTransition,
                backGlassTintAlpha = if (currentPage == AppStackPage.AlbumDetails || currentPage == AppStackPage.PlaylistDetails || currentPage == AppStackPage.ArtistDetails || currentPage == AppStackPage.GenreDetails || currentPage == AppStackPage.ComposerDetails) 0.08f else null,
                backHazeInputScale = if (currentPage == AppStackPage.AlbumDetails || currentPage == AppStackPage.PlaylistDetails || currentPage == AppStackPage.ArtistDetails || currentPage == AppStackPage.GenreDetails || currentPage == AppStackPage.ComposerDetails) HazeInputScale.Fixed(1f) else HazeInputScale.Auto,
            ) {
                if (showSyncAddAction) {
                    AirmedyGlassIconButton(
                        hazeState = hazeState,
                        symbol = MaterialSymbols.Add,
                        label = stringResource(R.string.sync_add_device),
                        onClick = { onIntent(AppIntent.OpenPage(AppStackPage.SettingsSyncScanner)) },
                    )
                } else if (showPlaylistAddAction) {
                    AirmedyGlassIconButton(
                        hazeState = hazeState,
                        symbol = MaterialSymbols.Add,
                        label = stringResource(R.string.playlist_create),
                        onClick = { showCreatePlaylistSheet = true },
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
                        layoutMode = albumsUiState.layoutMode,
                        onLayoutModeSelected = onAlbumLayoutModeSelected,
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
            if (showCreatePlaylistSheet) {
                CreatePlaylistBottomSheet(
                    onDismiss = { showCreatePlaylistSheet = false; createPlaylistForTracks = null },
                    onCreate = { name, artworkUri ->
                        showCreatePlaylistSheet = false
                        createPlaylistForTracks?.let { onCreatePlaylistWithTracks(name, artworkUri, it) }
                            ?: onCreatePlaylist(name, artworkUri)
                        createPlaylistForTracks = null
                    },
                )
            }
            NavigationChrome(
                selectedDestination = uiState.selectedDestination,
                playbackState = playbackState,
                playbackQueue = playbackQueue,
                hazeState = hazeState,
                compact = showsMiniPlayer && isNavigationCompact,
                onExpandClick = {
                    isNavigationCompact = false
                    navigationScrollAccumulator.reset()
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
                    navigationScrollAccumulator.reset()
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
                artworkCrossfade = artworkCrossfade,
                blendArtworkDuringCrossfade = blendArtworkDuringCrossfade,
                volume = systemVolume,
                onSeek = onPlaybackSeek,
                onVolumeChange = onSystemVolumeChange,
                onPrevious = onPlaybackPrevious,
                onPlayPause = onPlaybackPlayPause,
                onNext = onPlaybackNext,
                onQueueTrackSelected = onQueueTrackSelected,
                onQueueReordered = onQueueReordered,
                onQueueTrackRemoved = onQueueTrackRemoved,
                onShuffleChange = onShuffleChange,
                onRepeatModeChange = onRepeatModeChange,
                isFavorite = queueTracks.firstOrNull { track -> track.id == when (val state = playbackState) {
                    is PlaybackState.Preparing -> state.item.trackId
                    is PlaybackState.Playing -> state.item.trackId
                    is PlaybackState.Paused -> state.item.trackId
                    else -> ""
                } }?.isFavorite() == true,
                onFavoriteToggle = onFavoriteToggle,
                onTrackPlayNext = onTrackPlayNext,
                onTrackAddToQueue = onTrackAddToQueue,
                onTrackGoToAlbum = { albumId -> onIntent(AppIntent.OpenAlbumDetails(albumId)) },
                onTrackGoToArtist = { artistId -> onIntent(AppIntent.OpenArtistDetails(artistId)) },
                onTrackContextBottomSheet = { request -> trackContextSheet = request },
                onCloseFullscreenThen = ::closeFullScreenPlayerThen,
                onOpenMediaOutputSwitcher = onOpenMediaOutputSwitcher,
                onDismiss = {
                    setFullScreenPlayerVisible(false)
                    isFullScreenPlayerOpeningFromSwipe = false
                },
                onDismissAnimationFinished = {
                    pendingFullScreenPlayerAction?.let { action ->
                        pendingFullScreenPlayerAction = null
                        action()
                    }
                },
                hazeState = hazeState,
            )
            trackContextSheet?.let { request ->
                TrackContextBottomSheet(
                    request = request,
                    onDismiss = { trackContextSheet = null },
                    onArtistSelected = { artist ->
                        trackContextSheet = null
                        onIntent(AppIntent.OpenArtistDetails(artist.id))
                    },
                    playlists = playlistDetailsUiState.playlists,
                    onPlaylistMembershipChange = onPlaylistMembershipChange,
                    onCreatePlaylistRequested = { trackIds ->
                        trackContextSheet = null
                        createPlaylistForTracks = trackIds
                        showCreatePlaylistSheet = true
                    },
                )
            }
            }
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
