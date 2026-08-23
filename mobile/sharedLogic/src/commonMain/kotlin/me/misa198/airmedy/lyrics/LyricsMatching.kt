package me.misa198.airmedy.lyrics

import kotlin.math.abs

data class LyricsCandidate(val title: String, val artist: String, val durationSeconds: Double, val providerScore: Int = 0)

fun normalizeLyricsText(value: String): String = value
    .replace(Regex("(?i)\\s*\\((feat\\.?|ft\\.?)[^)]*\\)|\\s*\\[(feat\\.?|ft\\.?)[^]]*]|\\s*\\((official\\s*(video|audio|lyric.*?|music video)|lyrics?|hd|4k|remaster.*?)\\)|\\s*\\[(official\\s*(video|audio|lyric.*?|music video)|lyrics?|hd|4k|remaster.*?)\\]"), "")
    .trim().lowercase().split(Regex("\\s+")).filter(String::isNotEmpty).joinToString(" ")

fun removeFeaturedLyricsTitle(value: String): String = value
    .replace(Regex("(?i)\\s*[\\(\\[]fe?a?t\\.?\\s*[^\\)\\]]+[\\)\\]]"), "").trim()

fun lyricsCandidateScore(candidate: LyricsCandidate, title: String, artist: String, durationSeconds: Int): Double {
    val titleSimilarity = lyricsSimilarity(normalizeLyricsText(candidate.title), title)
    if (titleSimilarity < .7) return -1.0
    val difference = abs(candidate.durationSeconds - durationSeconds)
    if (difference > 5.0) return -1.0
    return titleSimilarity * .5 + lyricsSimilarity(normalizeLyricsText(candidate.artist), artist) * .3 + (1 - difference / 5) * .2
}

fun bestLyricsCandidate(candidates: List<LyricsCandidate>, title: String, artist: String, durationSeconds: Int): LyricsCandidate? =
    candidates.map { it to lyricsCandidateScore(it, title, artist, durationSeconds) }
        .filter { it.second >= 0 }
        .maxWithOrNull(compareBy<Pair<LyricsCandidate, Double>> { it.second }.thenBy { it.first.providerScore })?.first

private fun lyricsSimilarity(left: String, right: String): Double {
    if (left == right) return 1.0
    if (left.isEmpty() || right.isEmpty()) return 0.0
    var previous = IntArray(right.length + 1) { it }
    left.forEachIndexed { index, l ->
        val current = IntArray(right.length + 1)
        current[0] = index + 1
        right.forEachIndexed { rightIndex, r -> current[rightIndex + 1] = if (l == r) previous[rightIndex] else 1 + minOf(previous[rightIndex], current[rightIndex], previous[rightIndex + 1]) }
        previous = current
    }
    return 1.0 - previous[right.length].toDouble() / maxOf(left.length, right.length)
}
