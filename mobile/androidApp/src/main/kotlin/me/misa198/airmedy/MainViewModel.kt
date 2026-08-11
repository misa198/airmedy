package me.misa198.airmedy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.misa198.airmedy.settings.ThemeMode
import me.misa198.airmedy.settings.ThemeModeStore

data class AppUiState(
    val selectedDestination: AppDestination = AppDestination.Home,
    val themeMode: ThemeMode = ThemeMode.System,
    val reduceTransparency: Boolean = false,
    val selectedAlbumId: String? = null,
    val selectedArtistId: String? = null,
    val destinationStacks: Map<AppDestination, List<AppStackPage>> = rootDestinationStacks(),
) {
    fun stackFor(destination: AppDestination): List<AppStackPage> =
        destinationStacks[destination].orEmpty().ifEmpty { listOf(AppStackPage.Root) }

    val currentPage: AppStackPage
        get() = stackFor(selectedDestination).last()
}

class MainViewModel(
    private val themeModeStore: ThemeModeStore,
) : ViewModel() {
    private val selectedDestination = MutableStateFlow(AppDestination.Home)
    private val destinationStacks = MutableStateFlow(rootDestinationStacks())
    private val selectedAlbumId = MutableStateFlow<String?>(null)
    private val selectedArtistId = MutableStateFlow<String?>(null)
    private val _effects = Channel<AppEffect>(Channel.BUFFERED)

    val effects = _effects.receiveAsFlow()

    val uiState: StateFlow<AppUiState> = combine(
        themeModeStore.themeMode,
        themeModeStore.reduceTransparency,
        selectedDestination,
        destinationStacks,
        selectedAlbumId,
    ) { themeMode, reduceTransparency, destination, pages, albumId ->
        AppUiState(
            selectedDestination = destination,
            themeMode = themeMode,
            reduceTransparency = reduceTransparency,
            selectedAlbumId = albumId,
            destinationStacks = pages,
        )
    }.combine(selectedArtistId) { state, artistId ->
        state.copy(selectedArtistId = artistId)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        initialValue = AppUiState(),
    )

    fun dispatch(intent: AppIntent) {
        when (intent) {
            is AppIntent.SelectDestination -> selectDestination(intent.destination)
            is AppIntent.OpenPage -> openPage(intent.page)
            is AppIntent.OpenAlbumDetails -> openAlbumDetails(intent.albumId)
            is AppIntent.OpenArtistDetails -> openArtistDetails(intent.artistId)
            AppIntent.NavigateBack -> navigateBack()
            is AppIntent.SetThemeMode -> setThemeMode(intent.themeMode)
            is AppIntent.SetReduceTransparency -> setReduceTransparency(intent.enabled)
            is AppIntent.OpenExternalUrl -> _effects.trySend(AppEffect.OpenExternalUrl(intent.url))
        }
    }

    private fun selectDestination(destination: AppDestination) {
        if (destination == selectedDestination.value) {
            destinationStacks.update { stacks ->
                stacks + (destination to listOf(AppStackPage.Root))
            }
        } else {
            selectedDestination.value = destination
        }
    }

    private fun openPage(page: AppStackPage) {
        val destination = page.destination
        selectedDestination.value = destination
        destinationStacks.update { stacks ->
            val stack = stacks.getValue(destination)
            if (stack.lastOrNull() == page) {
                stacks
            } else {
                stacks + (destination to stack + page)
            }
        }
    }

    private fun openAlbumDetails(albumId: String) {
        selectedAlbumId.value = albumId
        openPage(AppStackPage.AlbumDetails)
    }

    private fun openArtistDetails(artistId: String) {
        selectedArtistId.value = artistId
        openPage(AppStackPage.ArtistDetails)
    }

    private fun navigateBack() {
        val destination = selectedDestination.value
        destinationStacks.update { stacks ->
            val stack = stacks.getValue(destination)
            if (stack.size > 1) stacks + (destination to stack.dropLast(1)) else stacks
        }
    }

    private fun setThemeMode(themeMode: ThemeMode) {
        viewModelScope.launch {
            themeModeStore.setThemeMode(themeMode)
        }
    }

    private fun setReduceTransparency(enabled: Boolean) {
        viewModelScope.launch {
            themeModeStore.setReduceTransparency(enabled)
        }
    }

    override fun onCleared() {
        _effects.close()
    }

    class Factory(
        private val themeModeStore: ThemeModeStore,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            check(modelClass.isAssignableFrom(MainViewModel::class.java))
            return MainViewModel(themeModeStore) as T
        }
    }
}
