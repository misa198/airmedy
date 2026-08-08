package me.misa198.airmedy

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.R as LucideR
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.launch
import me.misa198.airmedy.ui.components.AirmedyGlassIconButton
import me.misa198.airmedy.ui.components.StackPageHeader
import me.misa198.airmedy.ui.navigation.AppDestinationContent
import me.misa198.airmedy.ui.navigation.FloatingNavigationBar
import me.misa198.airmedy.ui.navigation.FloatingNavigationBottomMargin
import me.misa198.airmedy.ui.navigation.FloatingNavigationHeight
import me.misa198.airmedy.ui.navigation.titleRes
import me.misa198.airmedy.ui.theme.AirmedyTheme

internal fun shouldShowHeaderBlur(
    isContentScrolled: Boolean,
    destinationChanged: Boolean,
    previousHeaderWasBlurred: Boolean,
): Boolean = isContentScrolled || (destinationChanged && previousHeaderWasBlurred)

@Composable
fun App(
    uiState: AppUiState = AppUiState(),
    syncUiState: SyncUiState = SyncUiState(),
    onIntent: (AppIntent) -> Unit = {},
    onPairingQrScanned: (String) -> Boolean = { false },
    onUnpair: () -> Unit = {},
) {
    AirmedyTheme(themeMode = uiState.themeMode) {
        val hazeState = rememberHazeState()
        val homeListState = rememberLazyListState()
        val coroutineScope = rememberCoroutineScope()
        var previousDestination by remember { mutableStateOf(uiState.selectedDestination) }
        var previousHeaderWasBlurred by remember { mutableStateOf(false) }
        val destinationChanged = previousDestination != uiState.selectedDestination
        val animateHeaderChanges = !destinationChanged
        val currentPage = uiState.currentPage
        val navigationBottomPadding = FloatingNavigationHeight + FloatingNavigationBottomMargin +
            WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val pageTitle = stringResource(currentPage.titleRes(uiState.selectedDestination))
        val showBack = currentPage != AppStackPage.Root
        val showSyncAddAction = currentPage == AppStackPage.SettingsSync && syncUiState.desktop == null && !syncUiState.isPairing
        BackHandler(enabled = showBack) { onIntent(AppIntent.NavigateBack) }
        val isContentScrolled = uiState.selectedDestination == AppDestination.Home &&
            currentPage == AppStackPage.Root &&
            (homeListState.firstVisibleItemIndex > 0 || homeListState.firstVisibleItemScrollOffset > 0)
        val showHeaderBlur = shouldShowHeaderBlur(
            isContentScrolled = isContentScrolled,
            destinationChanged = destinationChanged,
            previousHeaderWasBlurred = previousHeaderWasBlurred,
        )
        SideEffect {
            previousDestination = uiState.selectedDestination
            previousHeaderWasBlurred = isContentScrolled
        }
        Box(modifier = Modifier.fillMaxSize()) {
            AppDestinationContent(
                destination = uiState.selectedDestination,
                page = currentPage,
                themeMode = uiState.themeMode,
                hazeState = hazeState,
                navigationBottomPadding = navigationBottomPadding,
                homeListState = homeListState,
                onIntent = onIntent,
                syncUiState = syncUiState,
                onPairingQrScanned = onPairingQrScanned,
                onUnpair = onUnpair,
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
                hasActions = showSyncAddAction,
                animateChanges = animateHeaderChanges,
                titleStackKey = "${uiState.selectedDestination.name}:${currentPage.name}",
            ) {
                AirmedyGlassIconButton(
                    hazeState = hazeState,
                    iconRes = LucideR.drawable.lucide_ic_plus,
                    label = stringResource(R.string.sync_add_device),
                    onClick = { onIntent(AppIntent.OpenPage(AppStackPage.SettingsSyncScanner)) },
                )
            }
            FloatingNavigationBar(
                selectedDestination = uiState.selectedDestination,
                hazeState = hazeState,
                onDestinationSelected = { destination ->
                    if (destination == uiState.selectedDestination && destination == AppDestination.Home) {
                        coroutineScope.launch {
                            homeListState.animateScrollToItem(0)
                        }
                    }
                    onIntent(AppIntent.SelectDestination(destination))
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(start = 20.dp, end = 20.dp, bottom = FloatingNavigationBottomMargin),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AppPreview() {
    App()
}
