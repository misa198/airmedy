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
    SettingsAppearance,
    SettingsSync,
    SettingsAbout,
}

/** Temporary Android UI model until the sync service and its shared contract exist. */
data class SyncDevice(
    val name: String,
    val type: SyncDeviceType,
)

enum class SyncDeviceType {
    Desktop,
}

fun rootDestinationStacks(): Map<AppDestination, List<AppStackPage>> =
    AppDestination.entries.associateWith { listOf(AppStackPage.Root) }
