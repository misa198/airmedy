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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
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
import me.misa198.airmedy.ui.components.HomeDemoContent
import me.misa198.airmedy.ui.components.StackPageLayout
import me.misa198.airmedy.ui.screens.AboutContent
import me.misa198.airmedy.ui.screens.AppearanceContent
import me.misa198.airmedy.ui.screens.HomeSampleDetailContent
import me.misa198.airmedy.ui.screens.LibraryContent
import me.misa198.airmedy.ui.screens.PlaceholderContent
import me.misa198.airmedy.ui.screens.SettingsContent
import me.misa198.airmedy.ui.screens.PlaybackSettingsContent
import me.misa198.airmedy.ui.screens.VolumeNormalizationContent
import me.misa198.airmedy.ui.screens.SongTransitionContent
import me.misa198.airmedy.ui.screens.SyncContent
import me.misa198.airmedy.ui.screens.SyncScannerContent
import me.misa198.airmedy.ui.theme.LocalAirmedyColors

import me.misa198.airmedy.ui.screens.LibraryTracksContent
import me.misa198.airmedy.ui.screens.LibraryTracksUiState
import me.misa198.airmedy.ui.screens.TrackSortOption
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
    onSortOptionSelected: (TrackSortOption) -> Unit = {},
    onToggleSortOrder: () -> Unit = {},
    onTrackClick: (String) -> Unit = {},
    onRecentTrackClick: (String) -> Unit = {},
    onAlbumSortOptionSelected: (AlbumSortOption) -> Unit = {},
    onAlbumToggleSortOrder: () -> Unit = {},
    onAlbumPlay: (String, Boolean) -> Unit = { _, _ -> },
    onAlbumTrackPlay: (String, String) -> Unit = { _, _ -> },
    onPlaylistPlay: (String, Boolean) -> Unit = { _, _ -> },
    onPlaylistTrackPlay: (String, String) -> Unit = { _, _ -> },
    onArtistPlay: (String, Boolean) -> Unit = { _, _ -> },
    onGenrePlay: (String, Boolean) -> Unit = { _, _ -> },
    onComposerPlay: (String, Boolean) -> Unit = { _, _ -> },
    onAlbumHeroColorChanged: (Color) -> Unit = {},
    onPairingQrScanned: (String) -> Boolean,
    onUnpair: () -> Unit,
    onSyncScreenVisible: () -> Unit,
    onSyncScreenHidden: () -> Unit,
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
                            HomeDemoContent(
                                modifier = Modifier.fillMaxSize(),
                                listState = homeListState,
                                contentPadding = contentPadding,
                                onOpenSampleDetail = {
                                    onIntent(AppIntent.OpenPage(AppStackPage.HomeSampleDetail))
                                },
                            )
                        } else {
                            HomeSampleDetailContent(modifier = Modifier.padding(contentPadding))
                        }
                        AppDestination.Settings -> when (currentPage.page) {
                            AppStackPage.SettingsAppearance -> AppearanceContent(
                                modifier = Modifier.padding(contentPadding),
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
                                modifier = Modifier.padding(contentPadding),
                            )
                            AppStackPage.SettingsPlayback -> PlaybackSettingsContent(
                                onSongTransitionSelected = {
                                    onIntent(AppIntent.OpenPage(AppStackPage.SettingsSongTransition))
                                },
                                onVolumeNormalizationSelected = {
                                    onIntent(AppIntent.OpenPage(AppStackPage.SettingsVolumeNormalization))
                                },
                                modifier = Modifier.padding(contentPadding),
                            )
                            AppStackPage.SettingsSongTransition -> SongTransitionContent(
                                crossfadeSeconds = crossfadeSeconds,
                                lastEnabledCrossfadeSeconds = lastEnabledCrossfadeSeconds,
                                onCrossfadeSecondsChanged = onCrossfadeSecondsChanged,
                                blendArtworkDuringCrossfade = blendArtworkDuringCrossfade,
                                onBlendArtworkDuringCrossfadeChanged = onBlendArtworkDuringCrossfadeChanged,
                                modifier = Modifier.padding(contentPadding),
                            )
                            AppStackPage.SettingsVolumeNormalization -> VolumeNormalizationContent(
                                normalizationAvailable = normalizationAvailable,
                                normalization = normalization,
                                onNormalizationChanged = onNormalizationChanged,
                                modifier = Modifier.padding(contentPadding),
                            )
                            AppStackPage.SettingsSyncScanner -> SyncScannerContent(
                                onQrScanned = onPairingQrScanned,
                                modifier = Modifier.padding(contentPadding),
                            )
                            AppStackPage.SettingsAbout -> AboutContent(
                                modifier = Modifier.padding(contentPadding),
                                onOpenExternalUrl = { url ->
                                    onIntent(AppIntent.OpenExternalUrl(url))
                                },
                            )
                            else -> SettingsContent(
                                modifier = Modifier.padding(contentPadding),
                                onAppearanceSelected = {
                                    onIntent(AppIntent.OpenPage(AppStackPage.SettingsAppearance))
                                },
                                onPlaybackSelected = {
                                    onIntent(AppIntent.OpenPage(AppStackPage.SettingsPlayback))
                                },
                                onSyncSelected = {
                                    onIntent(AppIntent.OpenPage(AppStackPage.SettingsSync))
                                },
                                onAboutSelected = {
                                    onIntent(AppIntent.OpenPage(AppStackPage.SettingsAbout))
                                },
                            )
                        }
                        AppDestination.Library -> when (currentPage.page) {
                            AppStackPage.LibraryArtists -> LibraryArtistsContent(
                                uiState = artistsUiState,
                                listState = artistsListState,
                                contentPadding = contentPadding,
                                modifier = Modifier.fillMaxSize(),
                                onArtistClick = { artist -> onIntent(AppIntent.OpenArtistDetails(artist.id)) },
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
                                onAlbumClick = { album -> onIntent(AppIntent.OpenAlbumDetails(album.id)) },
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
                                onAlbumClick = { album -> onIntent(AppIntent.OpenAlbumDetails(album.id)) },
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
                                onAlbumClick = { album -> onIntent(AppIntent.OpenAlbumDetails(album.id)) },
                            )
                            AppStackPage.LibraryAlbums -> LibraryAlbumsContent(
                                uiState = albumsUiState,
                                listState = albumsListState,
                                contentPadding = contentPadding,
                                modifier = Modifier.fillMaxSize(),
                                onAlbumClick = { album -> onIntent(AppIntent.OpenAlbumDetails(album.id)) },
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
                            )
                            AppStackPage.LibraryGenres -> LibraryGenresContent(
                                uiState = genresUiState,
                                listState = genresListState,
                                contentPadding = contentPadding,
                                modifier = Modifier.fillMaxSize(),
                                onGenreClick = { genre -> onIntent(AppIntent.OpenGenreDetails(genre.id)) },
                            )
                            AppStackPage.LibraryComposers -> LibraryComposersContent(
                                uiState = composersUiState,
                                listState = composersListState,
                                contentPadding = contentPadding,
                                modifier = Modifier.fillMaxSize(),
                                onComposerClick = { composer -> onIntent(AppIntent.OpenComposerDetails(composer.id)) },
                            )
                            AppStackPage.LibraryPlaylists -> LibraryPlaylistsContent(
                                uiState = playlistsUiState,
                                listState = playlistsListState,
                                contentPadding = contentPadding,
                                modifier = Modifier.fillMaxSize(),
                                onPlaylistClick = { playlistId -> onIntent(AppIntent.OpenPlaylistDetails(playlistId)) },
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
                            )
                            AppStackPage.LibraryTracks -> LibraryTracksContent(
                                uiState = tracksUiState,
                                onSortOptionSelected = onSortOptionSelected,
                                onToggleSortOrder = onToggleSortOrder,
                                listState = tracksListState,
                                contentPadding = contentPadding,
                                modifier = Modifier.fillMaxSize(),
                                onTrackClick = { track -> onTrackClick(track.id) },
                            )
                            else -> LibraryContent(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = contentPadding,
                                recentTracks = tracksUiState.recentTracks,
                                listState = libraryListState,
                                onTrackClick = onRecentTrackClick,
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
