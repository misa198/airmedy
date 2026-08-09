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
import com.composables.icons.lucide.R as LucideR
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.launch
import me.misa198.airmedy.ui.components.AirmedyGlassIconButton
import me.misa198.airmedy.ui.components.StackPageHeader
import me.misa198.airmedy.ui.components.TrackSortHeaderButton
import me.misa198.airmedy.ui.navigation.AppDestinationContent
import me.misa198.airmedy.ui.navigation.CompactNavigationHeight
import me.misa198.airmedy.ui.navigation.ContentScrollDirection
import me.misa198.airmedy.ui.navigation.FloatingNavigationBottomMargin
import me.misa198.airmedy.ui.navigation.FloatingNavigationContentGap
import me.misa198.airmedy.ui.navigation.FloatingNavigationHeight
import me.misa198.airmedy.ui.navigation.FullScreenPlayerPlaceholder
import me.misa198.airmedy.ui.navigation.MiniPlayerHeight
import me.misa198.airmedy.ui.navigation.MiniPlayerNavigationGap
import me.misa198.airmedy.ui.navigation.NavigationChrome
import me.misa198.airmedy.ui.navigation.NavigationChromeScrollState
import me.misa198.airmedy.ui.navigation.reduceNavigationChromeScroll
import me.misa198.airmedy.ui.navigation.depth
import me.misa198.airmedy.ui.navigation.showsMiniPlayer
import me.misa198.airmedy.ui.navigation.titleRes
import me.misa198.airmedy.ui.screens.LibraryTracksUiState
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
    onIntent: (AppIntent) -> Unit = {},
    onSortOptionSelected: (TrackSortOption) -> Unit = {},
    onToggleSortOrder: () -> Unit = {},
    onTrackClick: (String) -> Unit = {},
    onPairingQrScanned: (String) -> Boolean = { false },
    onUnpair: () -> Unit = {},
    onSyncScreenVisible: () -> Unit = {},
    onSyncScreenHidden: () -> Unit = {},
    playbackState: PlaybackState = PlaybackState.Idle,
    onPlaybackPrevious: () -> Unit = {},
    onPlaybackPlayPause: () -> Unit = {},
    onPlaybackNext: () -> Unit = {},
    onMiniPlayerDismiss: () -> Unit = {},
) {
    AirmedyTheme(themeMode = uiState.themeMode) {
        val hazeState = if (uiState.reduceTransparency) null else rememberHazeState()
        val homeListState = rememberLazyListState()
        val tracksListState = remember(tracksUiState.sortOption, tracksUiState.sortOrder) {
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
        var fullScreenPlayerDragProgress by remember { mutableFloatStateOf(0f) }
        var isFullScreenPlayerDragging by remember { mutableStateOf(false) }
        var isNavigationCompact by remember { mutableStateOf(false) }
        var navigationScrollState by remember { mutableStateOf(NavigationChromeScrollState()) }
        val navigationScrollThresholdPx = with(LocalDensity.current) { 24.dp.toPx() }
        LaunchedEffect(showsMiniPlayer, uiState.selectedDestination, currentPage) {
            isNavigationCompact = false
            navigationScrollState = NavigationChromeScrollState()
        }
        LaunchedEffect(showsMiniPlayer) {
            if (!showsMiniPlayer) {
                isFullScreenPlayerVisible = false
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
        val showTracksSortAction = currentPage == AppStackPage.LibraryTracks
        BackHandler(enabled = showBack) { onIntent(AppIntent.NavigateBack) }

        val isContentScrolled by remember(uiState.selectedDestination, currentPage, homeListState, tracksListState) {
            derivedStateOf {
                when {
                    uiState.selectedDestination == AppDestination.Home && currentPage == AppStackPage.Root ->
                        homeListState.firstVisibleItemIndex > 0 || homeListState.firstVisibleItemScrollOffset > 0
                    currentPage == AppStackPage.LibraryTracks ->
                        tracksListState.firstVisibleItemIndex > 0 || tracksListState.firstVisibleItemScrollOffset > 0
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
                onIntent = onIntent,
                syncUiState = syncUiState,
                tracksUiState = tracksUiState,
                onSortOptionSelected = onSortOptionSelected,
                onToggleSortOrder = onToggleSortOrder,
                onTrackClick = onTrackClick,
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
                hasActions = showSyncAddAction || showTracksSortAction,
                animateChanges = animateHeaderChanges,
                titleStackKey = "${uiState.selectedDestination.name}:${currentPage.name}",
                isForward = isForwardHeaderTransition,
            ) {
                if (showSyncAddAction) {
                    AirmedyGlassIconButton(
                        hazeState = hazeState,
                        iconRes = LucideR.drawable.lucide_ic_plus,
                        label = stringResource(R.string.sync_add_device),
                        onClick = { onIntent(AppIntent.OpenPage(AppStackPage.SettingsSyncScanner)) },
                    )
                } else if (showTracksSortAction) {
                    TrackSortHeaderButton(
                        hazeState = hazeState,
                        sortOption = tracksUiState.sortOption,
                        sortOrder = tracksUiState.sortOrder,
                        onSortOptionSelected = onSortOptionSelected,
                        onToggleSortOrder = onToggleSortOrder,
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
                onOpenFullScreenPlayer = { isFullScreenPlayerVisible = true },
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
                    isFullScreenPlayerVisible = shouldOpen
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(start = 20.dp, end = 20.dp, bottom = FloatingNavigationBottomMargin),
            )
            FullScreenPlayerPlaceholder(
                visible = isFullScreenPlayerVisible,
                dragProgress = fullScreenPlayerDragProgress,
                isDragging = isFullScreenPlayerDragging,
                onDismiss = { isFullScreenPlayerVisible = false },
            )
        }
        BackHandler(enabled = isFullScreenPlayerVisible) { isFullScreenPlayerVisible = false }
    }
}

@Preview(showBackground = true)
@Composable
private fun AppPreview() {
    App()
}
