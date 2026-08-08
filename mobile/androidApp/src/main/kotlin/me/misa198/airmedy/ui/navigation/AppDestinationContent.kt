package me.misa198.airmedy.ui.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.lazy.LazyListState
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import me.misa198.airmedy.AppDestination
import me.misa198.airmedy.AppStackPage
import me.misa198.airmedy.SyncDevice
import me.misa198.airmedy.settings.ThemeMode
import me.misa198.airmedy.ui.components.HomeDemoContent
import me.misa198.airmedy.ui.components.StackPageLayout
import me.misa198.airmedy.ui.screens.AboutContent
import me.misa198.airmedy.ui.screens.AppearanceContent
import me.misa198.airmedy.ui.screens.HomeSampleDetailContent
import me.misa198.airmedy.ui.screens.PlaceholderContent
import me.misa198.airmedy.ui.screens.SettingsContent
import me.misa198.airmedy.ui.screens.SyncContent
import me.misa198.airmedy.ui.theme.LocalAirmedyColors

private data class PageKey(
    val destination: AppDestination,
    val page: AppStackPage,
)

@Composable
internal fun AppDestinationContent(
    destination: AppDestination,
    page: AppStackPage,
    themeMode: ThemeMode,
    hazeState: HazeState,
    navigationBottomPadding: Dp,
    homeListState: LazyListState,
    onThemeModeSelected: (ThemeMode) -> Unit,
    onHomeSampleDetailSelected: () -> Unit,
    onAppearanceSelected: () -> Unit,
    onSyncSelected: () -> Unit,
    onAboutSelected: () -> Unit,
    onOpenExternalUrl: (String) -> Unit,
    syncDevice: SyncDevice?,
    onNavigateBack: () -> Unit,
) {
    val colors = LocalAirmedyColors.current
    val pageKey = PageKey(destination = destination, page = page)
    Surface(
        color = colors.background,
        modifier = Modifier
            .fillMaxSize()
            .hazeSource(hazeState),
    ) {
        StackPageLayout(
            title = stringResource(page.titleRes(destination)),
            hazeState = hazeState,
            contentBottomPadding = navigationBottomPadding,
            isContentScrolled = false,
            onBackClick = if (page != AppStackPage.Root) onNavigateBack else null,
            showHeader = false,
        ) { modifier, contentPadding ->
            AnimatedContent(
                targetState = pageKey,
                modifier = modifier,
                transitionSpec = {
                    if (targetState.destination != initialState.destination) {
                        EnterTransition.None togetherWith ExitTransition.None
                    } else if (targetState.isForwardFrom(initialState)) {
                        (slideInHorizontally { it } + fadeIn()) togetherWith
                            (slideOutHorizontally { -it / 4 } + fadeOut())
                    } else {
                        (slideInHorizontally { -it / 4 } + fadeIn()) togetherWith
                            (slideOutHorizontally { it } + fadeOut())
                    }
                },
                label = "stack-page-content",
            ) { currentPage ->
                when (currentPage.destination) {
                    AppDestination.Home -> if (currentPage.page == AppStackPage.Root) {
                        HomeDemoContent(
                            modifier = Modifier.fillMaxSize(),
                            listState = homeListState,
                            contentPadding = contentPadding,
                            onOpenSampleDetail = onHomeSampleDetailSelected,
                        )
                    } else {
                        HomeSampleDetailContent(modifier = Modifier.padding(contentPadding))
                    }
                    AppDestination.Settings -> when (currentPage.page) {
                        AppStackPage.SettingsAppearance -> AppearanceContent(
                            modifier = Modifier.padding(contentPadding),
                            themeMode = themeMode,
                            onThemeModeSelected = onThemeModeSelected,
                        )
                        AppStackPage.SettingsSync -> SyncContent(
                            syncDevice = syncDevice,
                            modifier = Modifier.padding(contentPadding),
                        )
                        AppStackPage.SettingsAbout -> AboutContent(
                            modifier = Modifier.padding(contentPadding),
                            onOpenExternalUrl = onOpenExternalUrl,
                        )
                        else -> SettingsContent(
                            modifier = Modifier.padding(contentPadding),
                            onAppearanceSelected = onAppearanceSelected,
                            onSyncSelected = onSyncSelected,
                            onAboutSelected = onAboutSelected,
                        )
                    }
                    else -> PlaceholderContent(
                        destination = currentPage.destination,
                        modifier = Modifier.padding(contentPadding),
                    )
                }
            }
        }
    }
}

private fun PageKey.isForwardFrom(previous: PageKey): Boolean = when {
    page == AppStackPage.HomeSampleDetail -> true
    page == AppStackPage.SettingsAppearance -> true
    page == AppStackPage.SettingsSync -> true
    page == AppStackPage.SettingsAbout -> true
    previous.page == AppStackPage.HomeSampleDetail -> false
    previous.page == AppStackPage.SettingsAppearance -> false
    previous.page == AppStackPage.SettingsSync -> false
    previous.page == AppStackPage.SettingsAbout -> false
    else -> false
}
