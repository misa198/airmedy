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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.lazy.LazyListState
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import me.misa198.airmedy.AppDestination
import me.misa198.airmedy.AppIntent
import me.misa198.airmedy.AppStackPage
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
import me.misa198.airmedy.ui.screens.PlaceholderContent
import me.misa198.airmedy.ui.screens.SettingsContent
import me.misa198.airmedy.ui.screens.IntegrationContent
import me.misa198.airmedy.ui.screens.PlaybackSettingsContent
import me.misa198.airmedy.ui.screens.VolumeNormalizationContent
import me.misa198.airmedy.ui.screens.SongTransitionContent
import me.misa198.airmedy.ui.screens.SyncContent
import me.misa198.airmedy.lastfm.LastFmStatus
import me.misa198.airmedy.ui.screens.SyncScannerContent
import me.misa198.airmedy.ui.theme.LocalAirmedyColors

import me.misa198.airmedy.ui.screens.LibraryTracksContent
import me.misa198.airmedy.ui.screens.LibraryTracksUiState
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
    albumDetailsUiState: AlbumDetailsUiState = AlbumDetailsUiState(),
    playlistDetailsUiState: PlaylistDetailsUiState = PlaylistDetailsUiState(),
    artistDetailsUiState: ArtistDetailsUiState = ArtistDetailsUiState(),
    genreDetailsUiState: GenreDetailsUiState = GenreDetailsUiState(),
    composerDetailsUiState: ComposerDetailsUiState = ComposerDetailsUiState(),
    selectedAlbumId: String? = null,
    selectedPlaylistId: String? = null,
    selectedArtistId: String? = null,
    selectedGenreId: String? = null,
    selectedComposerId: String? = null,
    onIntent: (AppIntent) -> Unit,
    syncUiState: SyncUiState,
    tracksUiState: LibraryTracksUiState = LibraryTracksUiState(),
    artistsUiState: LibraryArtistsUiState = LibraryArtistsUiState(),
    albumsUiState: LibraryAlbumsUiState = LibraryAlbumsUiState(),
    genresUiState: LibraryGenresUiState = LibraryGenresUiState(),
    composersUiState: LibraryComposersUiState = LibraryComposersUiState(),
    playlistsUiState: LibraryPlaylistsUiState = LibraryPlaylistsUiState(),
    searchUiState: LibrarySearchUiState = LibrarySearchUiState(),
    onSortOptionSelected: (TrackSortOption) -> Unit = {},
    onToggleSortOrder: () -> Unit = {},
    onTrackClick: (String) -> Unit = {},
    onSearchQueryChange: (String) -> Unit = {},
    onSearchTrackClick: (String) -> Unit = {},
    onTracksPlayAll: (Boolean) -> Unit = {},
    onTracksFilterQueryChange: (String) -> Unit = {},
    onRecentTrackClick: (String) -> Unit = {},
    onHomeTrackClick: (List<me.misa198.airmedy.sync.LibraryTrack>, String) -> Unit = { _, _ -> },
    playbackQueue: PlaybackQueueSnapshot = PlaybackQueueSnapshot(),
    onTrackPlayNext: (String) -> Unit = {},
    onTrackAddToQueue: (String) -> Unit = {},
    onTrackFavoriteToggle: (String, Boolean) -> Unit = { _, _ -> },
    onAlbumPlayNext: (List<String>) -> Unit = {},
    onAlbumAddToQueue: (List<String>) -> Unit = {},
    onAlbumAddToFavorites: (List<String>) -> Unit = {},
    onAlbumSortOptionSelected: (AlbumSortOption) -> Unit = {},
    onAlbumToggleSortOrder: () -> Unit = {},
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
    onTrackContextBottomSheet: (me.misa198.airmedy.ui.components.TrackContextBottomSheetRequest) -> Unit = {},
    onArtistPlay: (String, Boolean) -> Unit = { _, _ -> },
    onArtistPlayNext: (List<String>) -> Unit = {},
    onArtistAddToQueue: (List<String>) -> Unit = {},
    orderedTrackIdsForArtist: (String) -> List<String> = { emptyList() },
    onArtistTrackContextBottomSheet: (me.misa198.airmedy.ui.components.TrackContextBottomSheetRequest) -> Unit = {},
    onArtistsFilterQueryChange: (String) -> Unit = {},
    onGenrePlay: (String, Boolean) -> Unit = { _, _ -> },
    onGenrePlayNext: (List<String>) -> Unit = {},
    onGenreAddToQueue: (List<String>) -> Unit = {},
    onGenreTrackContextBottomSheet: (me.misa198.airmedy.ui.components.TrackContextBottomSheetRequest) -> Unit = {},
    orderedTrackIdsForGenre: (String) -> List<String> = { emptyList() },
    onGenresFilterQueryChange: (String) -> Unit = {},
    onComposerPlay: (String, Boolean) -> Unit = { _, _ -> },
    onComposerPlayNext: (List<String>) -> Unit = {},
    onComposerAddToQueue: (List<String>) -> Unit = {},
    onComposerTrackContextBottomSheet: (me.misa198.airmedy.ui.components.TrackContextBottomSheetRequest) -> Unit = {},
    orderedTrackIdsForComposer: (String) -> List<String> = { emptyList() },
    onComposersFilterQueryChange: (String) -> Unit = {},
    onAlbumHeroColorChanged: (Color) -> Unit = {},
    onPairingQrScanned: (String) -> Boolean,
    onUnpair: () -> Unit,
    onSyncScreenVisible: () -> Unit,
    onSyncScreenHidden: () -> Unit,
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
    onContentScroll: (ContentScrollDelta) -> Unit = {},
) {
    val colors = LocalAirmedyColors.current
    val destination = stackPage.destination
    val page = stackPage.page
    val currentOnContentScroll = rememberUpdatedState(onContentScroll)
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
                        .background(colors.background),
                ) {
                    when (currentPage.destination) {
                        AppDestination.Home -> if (currentPage.page == AppStackPage.Root) {
                            HomeContent(
                                modifier = Modifier.fillMaxSize(),
                                listState = homeListState,
                                contentPadding = contentPadding,
                                keepListeningTracks = tracksUiState.keepListeningTracks,
                                mostPlayedTracks = tracksUiState.mostPlayedTracks,
                                forgottenTracks = tracksUiState.forgottenTracks,
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
                        AppDestination.Settings -> key(currentPage.page) {
                            val settingsPageModifier = Modifier
                                .padding(contentPadding)
                                .verticalScroll(rememberScrollState())
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
                        AppDestination.Library -> when (currentPage.page) {
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
                        else -> PlaceholderContent(destination = currentPage.destination, modifier = Modifier.padding(contentPadding))
                    }
                }
            }
        }
    }
}

internal fun isForwardTransition(target: PageKey, initial: PageKey): Boolean =
    target.index > initial.index
