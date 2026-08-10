package me.misa198.airmedy

enum class AppDestination {
    Home,
    Library,
    Search,
    Settings,
}

/** Pages that can be placed on a destination's independent navigation stack. */
enum class AppStackPage {
    Root,
    HomeSampleDetail,
    LibraryArtists,
    LibraryAlbums,
    AlbumDetails,
    LibraryTracks,
    LibraryGenres,
    LibraryComposers,
    SettingsAppearance,
    SettingsSync,
    SettingsSyncScanner,
    SettingsAbout,
}

/** Identifies the visible page and its actual position in a destination stack. */
data class StackPageEntry(
    val destination: AppDestination,
    val page: AppStackPage,
    val index: Int,
)

val AppStackPage.destination: AppDestination
    get() = when (this) {
        AppStackPage.Root, AppStackPage.HomeSampleDetail -> AppDestination.Home
        AppStackPage.LibraryArtists,
        AppStackPage.LibraryAlbums,
        AppStackPage.AlbumDetails,
        AppStackPage.LibraryTracks,
        AppStackPage.LibraryGenres,
        AppStackPage.LibraryComposers,
        -> AppDestination.Library
        AppStackPage.SettingsAppearance,
        AppStackPage.SettingsSync,
        AppStackPage.SettingsSyncScanner,
        AppStackPage.SettingsAbout,
        -> AppDestination.Settings
    }

fun rootDestinationStacks(): Map<AppDestination, List<AppStackPage>> =
    AppDestination.entries.associateWith { listOf(AppStackPage.Root) }

fun List<AppStackPage>.currentStackPage(destination: AppDestination): StackPageEntry =
    StackPageEntry(destination = destination, page = last(), index = lastIndex)
