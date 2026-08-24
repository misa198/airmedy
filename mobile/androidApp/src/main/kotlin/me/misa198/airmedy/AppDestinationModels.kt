package me.misa198.airmedy

import android.net.Uri
import androidx.compose.ui.graphics.Color
import me.misa198.airmedy.lastfm.LastFmStatus
import me.misa198.airmedy.lyrics.LyricsSettings
import me.misa198.airmedy.lyrics.LyricsSource
import me.misa198.airmedy.lyrics.LyricsSearchResult
import me.misa198.airmedy.player.ArtworkCrossfadeTransition
import me.misa198.airmedy.player.EqualizerSettings
import me.misa198.airmedy.player.NormalizationSettings
import me.misa198.airmedy.player.PlaybackQueueSnapshot
import me.misa198.airmedy.player.PlaybackState
import me.misa198.airmedy.player.RepeatMode
import me.misa198.airmedy.sync.LibraryPlaylist
import me.misa198.airmedy.sync.LibraryTrack
import me.misa198.airmedy.ui.screens.AlbumDetailsUiState
import me.misa198.airmedy.ui.screens.AlbumLayoutMode
import me.misa198.airmedy.ui.screens.AlbumSortOption
import me.misa198.airmedy.ui.screens.ArtistDetailsUiState
import me.misa198.airmedy.ui.screens.ArtistSortOption
import me.misa198.airmedy.ui.screens.ComposerDetailsUiState
import me.misa198.airmedy.ui.screens.ComposerSortOption
import me.misa198.airmedy.ui.screens.GenreDetailsUiState
import me.misa198.airmedy.ui.screens.GenreSortOption
import me.misa198.airmedy.ui.screens.HomeUiState
import me.misa198.airmedy.ui.screens.InsightPeriod
import me.misa198.airmedy.ui.screens.InsightSourceFilter
import me.misa198.airmedy.ui.screens.InsightUiState
import me.misa198.airmedy.ui.screens.LibraryAlbumsUiState
import me.misa198.airmedy.ui.screens.LibraryArtistsUiState
import me.misa198.airmedy.ui.screens.LibraryComposersUiState
import me.misa198.airmedy.ui.screens.LibraryGenresUiState
import me.misa198.airmedy.ui.screens.LibraryPlaylistsUiState
import me.misa198.airmedy.ui.screens.LibrarySearchUiState
import me.misa198.airmedy.ui.screens.LibraryTracksUiState
import me.misa198.airmedy.ui.screens.PlaylistDetailsUiState
import me.misa198.airmedy.ui.screens.TrackSortOption

internal data class HomeDestinationModel(
    val state: HomeUiState = HomeUiState(),
    val onTrackClick: (List<LibraryTrack>, String) -> Unit = { _, _ -> },
)

internal data class InsightDestinationModel(
    val state: InsightUiState = InsightUiState(),
    val onLibraryPeriodSelected: (InsightPeriod) -> Unit = {},
    val onListeningPeriodSelected: (InsightPeriod) -> Unit = {},
    val onSourceSelected: (InsightSourceFilter) -> Unit = {},
    val onTrackClick: (String) -> Unit = {},
)

internal data class LibraryTracksModel(
    val state: LibraryTracksUiState = LibraryTracksUiState(),
    val onSortOptionSelected: (TrackSortOption) -> Unit = {},
    val onToggleSortOrder: () -> Unit = {},
    val onTrackClick: (String) -> Unit = {},
    val onPlayAll: (Boolean) -> Unit = {},
    val onFilterQueryChange: (String) -> Unit = {},
    val onRecentTrackClick: (String) -> Unit = {},
)

internal data class LibraryArtistsModel(
    val state: LibraryArtistsUiState = LibraryArtistsUiState(),
    val onSortOptionSelected: (ArtistSortOption) -> Unit = {},
    val onToggleSortOrder: () -> Unit = {},
    val onFilterQueryChange: (String) -> Unit = {},
    val onPlay: (String, Boolean) -> Unit = { _, _ -> },
    val onPlayNext: (List<String>) -> Unit = {},
    val onAddToQueue: (List<String>) -> Unit = {},
    val orderedTrackIds: (String) -> List<String> = { emptyList() },
)

internal data class LibraryAlbumsModel(
    val state: LibraryAlbumsUiState = LibraryAlbumsUiState(),
    val onSortOptionSelected: (AlbumSortOption) -> Unit = {},
    val onToggleSortOrder: () -> Unit = {},
    val onLayoutModeSelected: (AlbumLayoutMode) -> Unit = {},
    val onPlay: (String, Boolean) -> Unit = { _, _ -> },
    val onPlayAll: (Boolean) -> Unit = {},
    val onFilterQueryChange: (String) -> Unit = {},
    val onTrackPlay: (String, String) -> Unit = { _, _ -> },
    val onPlayNext: (List<String>) -> Unit = {},
    val onAddToQueue: (List<String>) -> Unit = {},
    val onAddToFavorites: (List<String>) -> Unit = {},
)

internal data class LibraryGenresModel(
    val state: LibraryGenresUiState = LibraryGenresUiState(),
    val onSortOptionSelected: (GenreSortOption) -> Unit = {},
    val onToggleSortOrder: () -> Unit = {},
    val onFilterQueryChange: (String) -> Unit = {},
    val onPlay: (String, Boolean) -> Unit = { _, _ -> },
    val onPlayNext: (List<String>) -> Unit = {},
    val onAddToQueue: (List<String>) -> Unit = {},
    val orderedTrackIds: (String) -> List<String> = { emptyList() },
)

internal data class LibraryComposersModel(
    val state: LibraryComposersUiState = LibraryComposersUiState(),
    val onSortOptionSelected: (ComposerSortOption) -> Unit = {},
    val onToggleSortOrder: () -> Unit = {},
    val onFilterQueryChange: (String) -> Unit = {},
    val onPlay: (String, Boolean) -> Unit = { _, _ -> },
    val onPlayNext: (List<String>) -> Unit = {},
    val onAddToQueue: (List<String>) -> Unit = {},
    val orderedTrackIds: (String) -> List<String> = { emptyList() },
)

internal data class LibraryPlaylistsModel(
    val state: LibraryPlaylistsUiState = LibraryPlaylistsUiState(),
    val availablePlaylists: List<LibraryPlaylist> = emptyList(),
    val onPlay: (String, Boolean) -> Unit = { _, _ -> },
    val onTrackPlay: (String, String) -> Unit = { _, _ -> },
    val onTrackRemove: (String, String) -> Unit = { _, _ -> },
    val onTrackMove: (String, String, String?, String?) -> Unit = { _, _, _, _ -> },
    val onPlayNext: (List<String>) -> Unit = {},
    val onAddToQueue: (List<String>) -> Unit = {},
    val onUpdate: (String, String, Uri?, Boolean) -> Unit = { _, _, _, _ -> },
    val onDelete: (String) -> Unit = {},
    val onCreate: (String, Uri?) -> Unit = { _, _ -> },
    val onCreateWithTracks: (String, Uri?, List<String>) -> Unit = { _, _, _ -> },
    val onMembershipChange: (String, List<String>, Boolean) -> Unit = { _, _, _ -> },
)

internal data class LibrarySearchModel(
    val state: LibrarySearchUiState = LibrarySearchUiState(),
    val onQueryChange: (String) -> Unit = {},
    val onTrackClick: (String) -> Unit = {},
)

internal data class LibraryDetailActions(
    val albums: AlbumDetailsUiState = AlbumDetailsUiState(),
    val playlists: PlaylistDetailsUiState = PlaylistDetailsUiState(),
    val artists: ArtistDetailsUiState = ArtistDetailsUiState(),
    val genres: GenreDetailsUiState = GenreDetailsUiState(),
    val composers: ComposerDetailsUiState = ComposerDetailsUiState(),
    val onHeroColorChanged: (Color) -> Unit = {},
)

internal data class LibraryDestinationModel(
    val tracks: LibraryTracksModel = LibraryTracksModel(),
    val artists: LibraryArtistsModel = LibraryArtistsModel(),
    val albums: LibraryAlbumsModel = LibraryAlbumsModel(),
    val genres: LibraryGenresModel = LibraryGenresModel(),
    val composers: LibraryComposersModel = LibraryComposersModel(),
    val playlists: LibraryPlaylistsModel = LibraryPlaylistsModel(),
    val search: LibrarySearchModel = LibrarySearchModel(),
    val details: LibraryDetailActions = LibraryDetailActions(),
)

internal data class SettingsDestinationModel(
    val syncState: SyncUiState = SyncUiState(),
    val onPairingQrScanned: (String) -> Boolean = { false },
    val onUnpair: () -> Unit = {},
    val onSyncScreenVisible: () -> Unit = {},
    val onSyncScreenHidden: () -> Unit = {},
    val lastFmStatus: LastFmStatus = LastFmStatus(),
    val onLastFmConnect: () -> Unit = {},
    val onLastFmDisconnect: () -> Unit = {},
    val lyricsSettings: LyricsSettings = LyricsSettings(),
    val onLyricsSourceChanged: (LyricsSource) -> Unit = {},
    val onLrclibChanged: (Boolean) -> Unit = {},
    val onKugouChanged: (Boolean) -> Unit = {},
    val crossfadeSeconds: Int = 0,
    val lastEnabledCrossfadeSeconds: Int = 4,
    val onCrossfadeSecondsChanged: (Int) -> Unit = {},
    val blendArtworkDuringCrossfade: Boolean = true,
    val onBlendArtworkDuringCrossfadeChanged: (Boolean) -> Unit = {},
    val normalizationAvailable: Boolean = false,
    val normalization: NormalizationSettings = NormalizationSettings(),
    val onNormalizationChanged: (NormalizationSettings) -> Unit = {},
    val equalizer: EqualizerSettings = EqualizerSettings(),
    val onEqualizerEnabledChanged: (Boolean) -> Unit = {},
    val onEqualizerPresetSelected: (String) -> Unit = {},
    val onEqualizerBandChanged: (Int, Float) -> Unit = { _, _ -> },
    val onEqualizerProfileCreate: (String) -> Unit = {},
    val onEqualizerProfileReset: (String) -> Unit = {},
    val onEqualizerProfileDelete: (String) -> Unit = {},
)

internal data class AppDestinationModels(
    val home: HomeDestinationModel = HomeDestinationModel(),
    val insight: InsightDestinationModel = InsightDestinationModel(),
    val library: LibraryDestinationModel = LibraryDestinationModel(),
    val settings: SettingsDestinationModel = SettingsDestinationModel(),
)

internal data class PlaybackModel(
    val state: PlaybackState = PlaybackState.Idle,
    val queue: PlaybackQueueSnapshot = PlaybackQueueSnapshot(),
    val queueTracks: List<LibraryTrack> = emptyList(),
    val lyrics: String? = null,
    val lyricsLoading: Boolean = false,
    val onSearchLyrics: suspend (LibraryTrack, String, String) -> List<LyricsSearchResult> = { _, _, _ -> emptyList() },
    val onLyricsSelected: suspend (String, LyricsSearchResult) -> Unit = { _, _ -> },
    val artworkCrossfade: ArtworkCrossfadeTransition? = null,
    val blendArtworkDuringCrossfade: Boolean = true,
    val systemVolume: Float = 0f,
    val onPrevious: () -> Unit = {},
    val onPlayPause: () -> Unit = {},
    val onNext: () -> Unit = {},
    val onSeek: (Long) -> Unit = {},
    val onQueueTrackSelected: (String) -> Unit = {},
    val onQueueReordered: (List<String>) -> Unit = {},
    val onQueueTrackRemoved: (String) -> Unit = {},
    val onShuffleChange: (Boolean) -> Unit = {},
    val onRepeatModeChange: (RepeatMode) -> Unit = {},
    val onSystemVolumeChange: (Float) -> Unit = {},
    val onMiniPlayerDismiss: () -> Unit = {},
    val onOpenMediaOutputSwitcher: () -> Unit = {},
    val onFavoriteToggle: (String, Boolean) -> Unit = { _, _ -> },
    val onTrackPlayNext: (String) -> Unit = {},
    val onTrackAddToQueue: (String) -> Unit = {},
)
