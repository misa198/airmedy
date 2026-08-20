package me.misa198.airmedy

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import me.misa198.airmedy.settings.ThemeMode
import me.misa198.airmedy.settings.ThemeModeStore
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `selecting a new destination retains other destination stacks`() = runTest {
        val viewModel = MainViewModel(FakeThemeModeStore())
        activateState(viewModel)

        viewModel.dispatch(AppIntent.OpenPage(AppStackPage.SettingsAppearance))
        viewModel.dispatch(AppIntent.SelectDestination(AppDestination.Library))
        advanceUntilIdle()

        assertEquals(AppDestination.Library, viewModel.uiState.value.selectedDestination)
        assertEquals(
            listOf(AppStackPage.Root, AppStackPage.SettingsAppearance),
            viewModel.uiState.value.stackFor(AppDestination.Settings),
        )
    }

    @Test
    fun `reselecting the destination restores its root stack`() = runTest {
        val viewModel = MainViewModel(FakeThemeModeStore())
        activateState(viewModel)

        viewModel.dispatch(AppIntent.OpenPage(AppStackPage.SettingsAppearance))
        viewModel.dispatch(AppIntent.SelectDestination(AppDestination.Settings))
        advanceUntilIdle()

        assertEquals(listOf(AppStackPage.Root), viewModel.uiState.value.stackFor(AppDestination.Settings))
    }

    @Test
    fun `opening a settings page selects settings and back pops it`() = runTest {
        val viewModel = MainViewModel(FakeThemeModeStore())
        activateState(viewModel)

        viewModel.dispatch(AppIntent.OpenPage(AppStackPage.SettingsAppearance))
        advanceUntilIdle()
        assertEquals(AppDestination.Settings, viewModel.uiState.value.selectedDestination)
        assertEquals(AppStackPage.SettingsAppearance, viewModel.uiState.value.currentPage)

        viewModel.dispatch(AppIntent.NavigateBack)
        advanceUntilIdle()
        assertEquals(AppStackPage.Root, viewModel.uiState.value.currentPage)
    }

    @Test
    fun `opening genre details selects the genre and pushes the library page`() = runTest {
        val viewModel = MainViewModel(FakeThemeModeStore())
        activateState(viewModel)

        viewModel.dispatch(AppIntent.OpenGenreDetails("electronic"))
        advanceUntilIdle()

        assertEquals("electronic", viewModel.uiState.value.selectedGenreId)
        assertEquals(AppStackPage.GenreDetails, viewModel.uiState.value.currentPage)
    }

    @Test
    fun `opening composer details selects the composer and pushes the library page`() = runTest {
        val viewModel = MainViewModel(FakeThemeModeStore())
        activateState(viewModel)

        viewModel.dispatch(AppIntent.OpenComposerDetails("glass"))
        advanceUntilIdle()

        assertEquals("glass", viewModel.uiState.value.selectedComposerId)
        assertEquals(AppStackPage.ComposerDetails, viewModel.uiState.value.currentPage)
    }

    @Test
    fun `opening playlist details selects the playlist and pushes the library page`() = runTest {
        val viewModel = MainViewModel(FakeThemeModeStore())
        activateState(viewModel)

        viewModel.dispatch(AppIntent.OpenPlaylistDetails("favorites"))
        advanceUntilIdle()

        assertEquals("favorites", viewModel.uiState.value.selectedPlaylistId)
        assertEquals(AppStackPage.PlaylistDetails, viewModel.uiState.value.currentPage)
    }

    @Test
    fun `popping a detail page clears its selected page state`() = runTest {
        val viewModel = MainViewModel(FakeThemeModeStore())
        activateState(viewModel)

        viewModel.dispatch(AppIntent.OpenAlbumDetails("album-1"))
        advanceUntilIdle()
        viewModel.dispatch(AppIntent.NavigateBack)
        advanceUntilIdle()

        assertEquals(AppStackPage.Root, viewModel.uiState.value.currentPage)
        assertEquals(null, viewModel.uiState.value.selectedAlbumId)
    }

    @Test
    fun `popping a nested detail only clears the page that was popped`() = runTest {
        val viewModel = MainViewModel(FakeThemeModeStore())
        activateState(viewModel)

        viewModel.dispatch(AppIntent.OpenArtistDetails("artist-1"))
        viewModel.dispatch(AppIntent.OpenAlbumDetails("album-1"))
        advanceUntilIdle()
        viewModel.dispatch(AppIntent.NavigateBack)
        advanceUntilIdle()

        assertEquals(AppStackPage.ArtistDetails, viewModel.uiState.value.currentPage)
        assertEquals("artist-1", viewModel.uiState.value.selectedArtistId)
        assertEquals(null, viewModel.uiState.value.selectedAlbumId)
    }

    @Test
    fun `popping a list page invalidates only that page's cached ui state`() = runTest {
        val viewModel = MainViewModel(FakeThemeModeStore())
        activateState(viewModel)

        viewModel.dispatch(AppIntent.OpenPage(AppStackPage.LibraryAlbums))
        advanceUntilIdle()
        viewModel.dispatch(AppIntent.NavigateBack)
        advanceUntilIdle()

        assertEquals(
            1,
            viewModel.uiState.value.pageStateGenerationFor(AppDestination.Library, AppStackPage.LibraryAlbums),
        )
        assertEquals(
            0,
            viewModel.uiState.value.pageStateGenerationFor(AppDestination.Library, AppStackPage.Root),
        )
    }

    @Test
    fun `setting the theme persists it and updates ui state`() = runTest {
        val store = FakeThemeModeStore()
        val viewModel = MainViewModel(store)
        activateState(viewModel)

        viewModel.dispatch(AppIntent.SetThemeMode(ThemeMode.Dark))
        advanceUntilIdle()

        assertEquals(listOf(ThemeMode.Dark), store.savedModes)
        assertEquals(ThemeMode.Dark, viewModel.uiState.value.themeMode)
    }

    @Test
    fun `setting reduce transparency persists it and updates ui state`() = runTest {
        val store = FakeThemeModeStore()
        val viewModel = MainViewModel(store)
        activateState(viewModel)

        viewModel.dispatch(AppIntent.SetReduceTransparency(true))
        advanceUntilIdle()

        assertEquals(listOf(true), store.savedReduceTransparencyValues)
        assertEquals(true, viewModel.uiState.value.reduceTransparency)
    }

    @Test
    fun `opening an external url emits one host effect`() = runTest {
        val viewModel = MainViewModel(FakeThemeModeStore())
        val expected = AppEffect.OpenExternalUrl("https://example.com")
        val effect = async { viewModel.effects.first() }

        viewModel.dispatch(AppIntent.OpenExternalUrl(expected.url))

        assertEquals(expected, effect.await())
    }

    @Test
    fun `popping a page emits its reset effect`() = runTest {
        val viewModel = MainViewModel(FakeThemeModeStore())
        activateState(viewModel)
        viewModel.dispatch(AppIntent.OpenPage(AppStackPage.LibraryTracks))
        advanceUntilIdle()
        val effect = async { viewModel.effects.first() }

        viewModel.dispatch(AppIntent.NavigateBack)

        assertEquals(AppEffect.ResetPoppedPage(AppStackPage.LibraryTracks), effect.await())
    }

    private fun TestScope.activateState(viewModel: MainViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
    }
}

private class FakeThemeModeStore(initialThemeMode: ThemeMode = ThemeMode.System) : ThemeModeStore {
    private val mutableThemeMode = MutableStateFlow(initialThemeMode)
    private val mutableReduceTransparency = MutableStateFlow(false)
    override val themeMode: Flow<ThemeMode> = mutableThemeMode
    override val reduceTransparency: Flow<Boolean> = mutableReduceTransparency
    val savedModes = mutableListOf<ThemeMode>()
    val savedReduceTransparencyValues = mutableListOf<Boolean>()

    override suspend fun setThemeMode(themeMode: ThemeMode) {
        savedModes += themeMode
        mutableThemeMode.value = themeMode
    }

    override suspend fun setReduceTransparency(enabled: Boolean) {
        savedReduceTransparencyValues += enabled
        mutableReduceTransparency.value = enabled
    }

}
