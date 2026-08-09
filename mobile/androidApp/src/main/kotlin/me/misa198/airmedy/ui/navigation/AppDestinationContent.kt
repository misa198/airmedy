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

internal data class PageKey(
    val destination: AppDestination,
    val page: AppStackPage,
)

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
    destination: AppDestination,
    page: AppStackPage,
    themeMode: ThemeMode,
    reduceTransparency: Boolean,
    hazeState: HazeState?,
    navigationBottomPadding: Dp,
    homeListState: LazyListState,
    libraryListState: LazyListState = androidx.compose.foundation.lazy.rememberLazyListState(),
    tracksListState: LazyListState = androidx.compose.foundation.lazy.rememberLazyListState(),
    artistsListState: LazyListState = androidx.compose.foundation.lazy.rememberLazyListState(),
    albumsListState: LazyListState = androidx.compose.foundation.lazy.rememberLazyListState(),
    genresListState: LazyListState = androidx.compose.foundation.lazy.rememberLazyListState(),
    composersListState: LazyListState = androidx.compose.foundation.lazy.rememberLazyListState(),
    onIntent: (AppIntent) -> Unit,
    syncUiState: SyncUiState,
    tracksUiState: LibraryTracksUiState = LibraryTracksUiState(),
    artistsUiState: LibraryArtistsUiState = LibraryArtistsUiState(),
    albumsUiState: LibraryAlbumsUiState = LibraryAlbumsUiState(),
    genresUiState: LibraryGenresUiState = LibraryGenresUiState(),
    composersUiState: LibraryComposersUiState = LibraryComposersUiState(),
    onSortOptionSelected: (TrackSortOption) -> Unit = {},
    onToggleSortOrder: () -> Unit = {},
    onTrackClick: (String) -> Unit = {},
    onAlbumSortOptionSelected: (AlbumSortOption) -> Unit = {},
    onAlbumToggleSortOrder: () -> Unit = {},
    onPairingQrScanned: (String) -> Boolean,
    onUnpair: () -> Unit,
    onSyncScreenVisible: () -> Unit,
    onSyncScreenHidden: () -> Unit,
    onContentScroll: (ContentScrollDelta) -> Unit = {},
) {
    val colors = LocalAirmedyColors.current
    val pageKey = PageKey(destination = destination, page = page)
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
                targetState = pageKey,
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
                            )
                            AppStackPage.SettingsSync -> SyncContent(
                                syncUiState = syncUiState,
                                onUnpair = onUnpair,
                                onScreenVisible = onSyncScreenVisible,
                                onScreenHidden = onSyncScreenHidden,
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
                            )
                            AppStackPage.LibraryAlbums -> LibraryAlbumsContent(
                                uiState = albumsUiState,
                                listState = albumsListState,
                                contentPadding = contentPadding,
                                modifier = Modifier.fillMaxSize(),
                            )
                            AppStackPage.LibraryGenres -> LibraryGenresContent(
                                uiState = genresUiState,
                                listState = genresListState,
                                contentPadding = contentPadding,
                                modifier = Modifier.fillMaxSize(),
                            )
                            AppStackPage.LibraryComposers -> LibraryComposersContent(
                                uiState = composersUiState,
                                listState = composersListState,
                                contentPadding = contentPadding,
                                modifier = Modifier.fillMaxSize(),
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
                                onTrackClick = onTrackClick,
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
                            )
                        }
                        else -> PlaceholderContent(destination = currentPage.destination, modifier = Modifier.padding(contentPadding))
                    }
                }
            }
        }
    }
}

internal val AppStackPage.depth: Int
    get() = when (this) {
        AppStackPage.Root -> 0
        AppStackPage.HomeSampleDetail,
        AppStackPage.LibraryArtists,
        AppStackPage.LibraryAlbums,
        AppStackPage.LibraryTracks,
        AppStackPage.LibraryGenres,
        AppStackPage.LibraryComposers,
        AppStackPage.SettingsAppearance,
        AppStackPage.SettingsSync,
        AppStackPage.SettingsAbout -> 1
        AppStackPage.SettingsSyncScanner -> 2
    }

internal fun isForwardTransition(target: PageKey, initial: PageKey): Boolean =
    target.page.depth > initial.page.depth
