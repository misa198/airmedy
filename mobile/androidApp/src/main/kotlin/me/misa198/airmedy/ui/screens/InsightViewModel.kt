package me.misa198.airmedy.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import me.misa198.airmedy.pairing.MobileIdentity
import me.misa198.airmedy.pairing.PairedDesktop
import me.misa198.airmedy.pairing.PairingPreferences
import me.misa198.airmedy.player.DailyPlaybackAttemptStat
import me.misa198.airmedy.player.DailyTrackListeningStat
import me.misa198.airmedy.player.PlaybackController
import me.misa198.airmedy.player.PlaybackRequest
import me.misa198.airmedy.sync.AndroidLibrarySyncStore
import me.misa198.airmedy.sync.LibraryAlbum
import me.misa198.airmedy.sync.LibraryArtist
import me.misa198.airmedy.sync.LibraryPlaylist
import me.misa198.airmedy.sync.LibraryTrack
import me.misa198.airmedy.sync.metadataObject
import me.misa198.airmedy.ui.components.TrackAudioQuality
import me.misa198.airmedy.ui.components.trackAudioQuality

internal enum class InsightPeriod(val days: Long?) { SevenDays(7), ThirtyDays(30), All(null) }
internal enum class InsightSourceFilter { All, ThisPhone, Desktop, Other }

internal data class InsightPoint(val date: String, val value: Int)
internal data class InsightQuality(val quality: TrackAudioQuality, val count: Int)
internal data class InsightBreakdown(val name: String, val listenedSeconds: Int, val isOther: Boolean = false)
internal data class InsightTopArtist(val id: String, val name: String, val artworkPath: String?, val listenedSeconds: Int)
internal data class InsightTopTrack(val track: LibraryTrack, val playCount: Int, val listenedSeconds: Int)

internal data class LibraryInsightState(
    val tracks: Int = 0,
    val albums: Int = 0,
    val artists: Int = 0,
    val playlists: Int = 0,
    val bytes: Long = 0,
    val growth: List<InsightPoint> = emptyList(),
    val quality: List<InsightQuality> = emptyList(),
)

internal data class ListeningInsightState(
    val listenedSeconds: Int = 0,
    val plays: Int = 0,
    val attempts: Int = 0,
    val completed: Int = 0,
    val skipped: Int = 0,
    val stopped: Int = 0,
    val averageSessionSeconds: Int = 0,
    val streakDays: Int = 0,
    val changePercent: Double? = null,
    val activity: List<InsightPoint> = emptyList(),
    val genres: List<InsightBreakdown> = emptyList(),
    val topArtists: List<InsightTopArtist> = emptyList(),
    val topTracks: List<InsightTopTrack> = emptyList(),
)

internal data class InsightUiState(
    val libraryPeriod: InsightPeriod = InsightPeriod.SevenDays,
    val listeningPeriod: InsightPeriod = InsightPeriod.SevenDays,
    val sourceFilter: InsightSourceFilter = InsightSourceFilter.All,
    val desktopName: String? = null,
    val hasDesktopSource: Boolean = false,
    val hasOtherSources: Boolean = false,
    val library: LibraryInsightState = LibraryInsightState(),
    val listening: ListeningInsightState = ListeningInsightState(),
)

internal data class LibraryBundle(
    val tracks: List<LibraryTrack>,
    val artists: List<LibraryArtist>,
    val albums: List<LibraryAlbum>,
    val playlists: List<LibraryPlaylist>,
)

internal data class InsightRawData(
    val library: LibraryBundle,
    val dailyTracks: List<DailyTrackListeningStat>,
    val dailyAttempts: List<DailyPlaybackAttemptStat>,
    val identity: MobileIdentity,
    val desktop: PairedDesktop?,
)

internal class InsightViewModel(
    store: AndroidLibrarySyncStore,
    identity: Flow<MobileIdentity>,
    desktop: Flow<PairedDesktop?>,
    private val playbackController: PlaybackController,
    private val today: () -> LocalDate = LocalDate::now,
) : ViewModel() {
    class Factory(
        private val store: AndroidLibrarySyncStore,
        private val preferences: PairingPreferences,
        private val playbackController: PlaybackController,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = InsightViewModel(
            store,
            flow { emit(preferences.identity()) },
            preferences.pairedDesktop,
            playbackController,
        ) as T
    }

    private val libraryPeriod = MutableStateFlow(InsightPeriod.SevenDays)
    private val listeningPeriod = MutableStateFlow(InsightPeriod.SevenDays)
    private val sourceFilter = MutableStateFlow(InsightSourceFilter.All)

    private val library = combine(store.tracks, store.artists, store.albums, store.playlists) { tracks, artists, albums, playlists ->
        LibraryBundle(tracks, artists, albums, playlists)
    }
    private val raw = combine(library, store.dailyTrackListeningStats, store.dailyPlaybackAttemptStats, identity, desktop) { library, tracks, attempts, mobile, paired ->
        InsightRawData(library, tracks, attempts, mobile, paired)
    }

    val uiState = combine(raw, libraryPeriod, listeningPeriod, sourceFilter) { data, libraryRange, listeningRange, source ->
        buildInsightUiState(data, libraryRange, listeningRange, source, today())
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InsightUiState())

    fun setLibraryPeriod(value: InsightPeriod) { libraryPeriod.value = value }
    fun setListeningPeriod(value: InsightPeriod) { listeningPeriod.value = value }
    fun setSourceFilter(value: InsightSourceFilter) { sourceFilter.value = value }

    fun playTopTrack(trackId: String) {
        val tracks = uiState.value.listening.topTracks.map { it.track }
        val index = tracks.indexOfFirst { it.id == trackId }
        if (index >= 0) playbackController.play(PlaybackRequest(tracks.map { it.id }, index))
    }
}

internal fun buildInsightUiState(
    data: InsightRawData,
    libraryPeriod: InsightPeriod,
    listeningPeriod: InsightPeriod,
    source: InsightSourceFilter,
    today: LocalDate,
): InsightUiState {
    val desktopId = data.desktop?.desktopId
    val sourceIds = (data.dailyTracks.map { it.sourceDeviceId } + data.dailyAttempts.map { it.sourceDeviceId }).toSet()
    val effectiveSource = source.takeIf {
        it != InsightSourceFilter.Desktop || desktopId != null
    }?.takeIf {
        it != InsightSourceFilter.Other || sourceIds.any { id -> id != data.identity.id && id != desktopId }
    } ?: InsightSourceFilter.All
    return InsightUiState(
        libraryPeriod = libraryPeriod,
        listeningPeriod = listeningPeriod,
        sourceFilter = effectiveSource,
        desktopName = data.desktop?.displayName,
        hasDesktopSource = desktopId != null,
        hasOtherSources = sourceIds.any { it != data.identity.id && it != desktopId },
        library = libraryInsights(data.library, libraryPeriod, today),
        listening = listeningInsights(data, listeningPeriod, effectiveSource, today),
    )
}

private fun libraryInsights(bundle: LibraryBundle, period: InsightPeriod, today: LocalDate): LibraryInsightState {
    val datedTracks = bundle.tracks.mapNotNull { track -> track.createdAt.toLocalDateOrNull()?.let { it to track } }
    val growth = if (period == InsightPeriod.All) {
        var total = 0
        datedTracks.groupingBy { it.first.year }.eachCount().toSortedMap().map { (year, count) ->
            total += count
            InsightPoint(year.toString(), total)
        }
    } else {
        val start = today.minusDays(period.days!! - 1)
        var total = datedTracks.count { it.first < start }
        (0 until period.days).map { offset ->
            val date = start.plusDays(offset)
            total += datedTracks.count { it.first == date }
            InsightPoint(date.toString(), total)
        }
    }
    val quality = bundle.tracks.groupingBy(::trackAudioQuality).eachCount().entries
        .sortedByDescending { it.value }
        .map { InsightQuality(it.key, it.value) }
    return LibraryInsightState(
        tracks = bundle.tracks.size,
        albums = bundle.albums.size,
        artists = bundle.artists.size,
        playlists = bundle.playlists.count { it.id != "favorites" },
        bytes = bundle.tracks.sumOf { it.metadataObject().long("file_size") ?: 0L },
        growth = growth,
        quality = quality,
    )
}

private fun listeningInsights(data: InsightRawData, period: InsightPeriod, source: InsightSourceFilter, today: LocalDate): ListeningInsightState {
    val start = period.days?.let { today.minusDays(it - 1) }
    val matchesSource: (String) -> Boolean = { id -> when (source) {
        InsightSourceFilter.All -> true
        InsightSourceFilter.ThisPhone -> id == data.identity.id
        InsightSourceFilter.Desktop -> id == data.desktop?.desktopId
        InsightSourceFilter.Other -> id != data.identity.id && id != data.desktop?.desktopId
    } }
    val tracks = data.dailyTracks.filter { matchesSource(it.sourceDeviceId) && (start == null || it.localDate >= start.toString()) }
    val attempts = data.dailyAttempts.filter { matchesSource(it.sourceDeviceId) && (start == null || it.localDate >= start.toString()) }
    val listenedSeconds = tracks.sumOf { it.listenedSeconds }
    val plays = tracks.sumOf { it.playCount }
    val endedAttempts = attempts.sumOf { it.completed + it.skipped + it.stopped }
    val previous = start?.let { currentStart ->
        val previousStart = currentStart.minusDays(period.days)
        data.dailyTracks.filter {
            matchesSource(it.sourceDeviceId) && it.localDate >= previousStart.toString() && it.localDate < currentStart.toString()
        }.sumOf { it.listenedSeconds }
    }
    val byDate = tracks.groupBy { it.localDate }.mapValues { (_, rows) -> rows.sumOf { it.listenedSeconds } }
    val activity = if (start == null) {
        byDate.entries.groupBy { it.key.take(7) }.mapValues { (_, rows) -> rows.sumOf { it.value } }
            .toSortedMap().map { InsightPoint(it.key, it.value) }
    } else {
        (0 until period.days).map { offset -> start.plusDays(offset).toString() }.map { InsightPoint(it, byDate[it] ?: 0) }
    }
    val trackStats = tracks.groupBy { it.trackId }.mapValues { (_, rows) ->
        rows.sumOf { it.playCount } to rows.sumOf { it.listenedSeconds }
    }
    val tracksById = data.library.tracks.associateBy { it.id }
    val topTracks = trackStats.mapNotNull { (id, values) -> tracksById[id]?.let { InsightTopTrack(it, values.first, values.second) } }
        .filter { it.playCount > 0 || it.listenedSeconds > 0 }
        .sortedWith(compareByDescending<InsightTopTrack> { it.playCount }.thenByDescending { it.listenedSeconds }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.track.title })
        .take(50)
    val artistArtwork = data.library.artists.associateBy { it.id }
    val artistSeconds = mutableMapOf<Pair<String, String>, Int>()
    val genreSeconds = mutableMapOf<String, Int>()
    trackStats.forEach { (trackId, values) ->
        val metadata = tracksById[trackId]?.metadataObject() ?: return@forEach
        metadata.objects("artists").forEach { artist ->
            val id = artist.text("id") ?: return@forEach
            val name = artist.text("name") ?: return@forEach
            artistSeconds[id to name] = (artistSeconds[id to name] ?: 0) + values.second
        }
        metadata.objects("genres").forEach { genre ->
            val name = genre.text("name") ?: genre.text("title") ?: return@forEach
            genreSeconds[name] = (genreSeconds[name] ?: 0) + values.second
        }
    }
    val topArtists = artistSeconds.entries.sortedWith(compareByDescending<Map.Entry<Pair<String, String>, Int>> { it.value }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.key.second })
        .take(50).map { (key, seconds) -> InsightTopArtist(key.first, key.second, artistArtwork[key.first]?.artworkPath, seconds) }
    val sortedGenres = genreSeconds.entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.key })
    val genres = sortedGenres.take(5).map { InsightBreakdown(it.key, it.value) } +
        sortedGenres.drop(5).sumOf { it.value }.takeIf { it > 0 }?.let { listOf(InsightBreakdown("", it, true)) }.orEmpty()
    val activeDates = data.dailyTracks.filter { matchesSource(it.sourceDeviceId) && it.listenedSeconds > 0 && it.localDate <= today.toString() }.mapTo(mutableSetOf()) { it.localDate }
    var cursor = if (today.toString() in activeDates) today else today.minusDays(1)
    var streak = 0
    while (cursor.toString() in activeDates) { streak++; cursor = cursor.minusDays(1) }
    return ListeningInsightState(
        listenedSeconds = listenedSeconds,
        plays = plays,
        attempts = attempts.sumOf { it.attempts },
        completed = attempts.sumOf { it.completed },
        skipped = attempts.sumOf { it.skipped },
        stopped = attempts.sumOf { it.stopped },
        averageSessionSeconds = if (endedAttempts == 0) 0 else attempts.sumOf { it.listenedSeconds } / endedAttempts,
        streakDays = streak,
        changePercent = previous?.takeIf { it > 0 }?.let { (listenedSeconds - it) * 100.0 / it },
        activity = activity,
        genres = genres,
        topArtists = topArtists,
        topTracks = topTracks,
    )
}

private fun String.toLocalDateOrNull(): LocalDate? = runCatching {
    Instant.parse(this).atZone(ZoneId.systemDefault()).toLocalDate()
}.recoverCatching { LocalDate.parse(take(10)) }.getOrNull()

private fun JsonObject.objects(name: String): List<JsonObject> = (this[name] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
private fun JsonObject.text(name: String): String? = (this[name] as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank)
private fun JsonObject?.long(name: String): Long? = (this?.get(name) as? JsonPrimitive)?.contentOrNull?.toLongOrNull()
