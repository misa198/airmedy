package me.misa198.airmedy.mood

import kotlin.math.sqrt

const val MoodRadioCandidateLimit = 80
const val MoodRadioBatchSize = 15
const val MoodRadioRefillThreshold = 3

data class MoodRadioTrack(
    val id: String,
    val albumId: String = "",
    val primaryArtistId: String = "",
    val energy: Double?,
    val danceability: Double?,
    val brightness: Double?,
    val tempo: Double?,
)

/** Matches desktop's nearest-neighbour, album/artist-diverse radio selection. */
fun selectMoodRadio(
    seedId: String,
    tracks: Iterable<MoodRadioTrack>,
    excludedIds: Set<String>,
    limit: Int = MoodRadioBatchSize,
    random: () -> Double = { kotlin.random.Random.nextDouble() },
): List<MoodRadioTrack> {
    val seed = tracks.firstOrNull { it.id == seedId }?.takeIf(::hasMoodFeatures) ?: return emptyList()
    val nearest = mutableListOf<Pair<Double, MoodRadioTrack>>()
    tracks.forEach { track ->
        if (!hasMoodFeatures(track) || track.id == seedId || track.id in excludedIds) return@forEach
        val candidate = moodDistance(seed, track) to track
        val found = nearest.binarySearch { it.first.compareTo(candidate.first) }
        val index = if (found < 0) -found - 1 else found
        if (index < MoodRadioCandidateLimit) {
            nearest.add(index, candidate)
            if (nearest.size > MoodRadioCandidateLimit) nearest.removeAt(nearest.lastIndex)
        }
    }
    val candidates = nearest.mapTo(mutableListOf()) { it.second }
    val selected = mutableListOf<MoodRadioTrack>()
    val albums = mutableSetOf<String>()
    val artists = ArrayDeque<String>()
    while (selected.size < limit && candidates.isNotEmpty()) {
        val window = candidates.take(if (selected.size < 3) 20 else candidates.size)
        val index = pickCandidate(window, albums, artists, random)
        val track = candidates.removeAt(index)
        selected += track
        track.albumId.takeIf(String::isNotBlank)?.let(albums::add)
        track.primaryArtistId.takeIf(String::isNotBlank)?.let { artist ->
            artists.addLast(artist)
            if (artists.size > 3) artists.removeFirst()
        }
    }
    return selected
}

fun hasMoodFeatures(track: MoodRadioTrack): Boolean =
    track.energy != null && track.danceability != null && track.brightness != null && track.tempo != null

private fun moodDistance(a: MoodRadioTrack, b: MoodRadioTrack): Double = sqrt(
    sq(a.energy!! - b.energy!!) + sq(a.danceability!! - b.danceability!!) +
        sq(a.brightness!! - b.brightness!!) + sq((a.tempo!! - b.tempo!!) / 200.0),
)

private fun sq(value: Double) = value * value

private fun pickCandidate(window: List<MoodRadioTrack>, albums: Set<String>, artists: Collection<String>, random: () -> Double): Int {
    for ((avoidAlbum, avoidArtist) in listOf(true to true, false to true, false to false)) {
        val eligible = window.indices.filter { index ->
            val track = window[index]
            (!avoidAlbum || track.albumId.isBlank() || track.albumId !in albums) &&
                (!avoidArtist || track.primaryArtistId.isBlank() || track.primaryArtistId !in artists)
        }
        if (eligible.isNotEmpty()) {
            var target = random() * eligible.sumOf { 1 / sqrt((it + 1).toDouble()) }
            eligible.forEach { index ->
                target -= 1 / sqrt((index + 1).toDouble())
                if (target <= 0) return index
            }
            return eligible.last()
        }
    }
    return 0
}
