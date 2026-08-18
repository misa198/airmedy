package me.misa198.airmedy.lastfm

enum class LastFmPlaybackEvent {
    NowPlaying,
    Scrobble,
}

/** Desktop-compatible Last.fm thresholds for one playback instance. */
class LastFmScrobbleTracker {
    private var trackId: String? = null
    private var nowPlayingSent = false
    private var scrobbled = false

    fun start(trackId: String) {
        this.trackId = trackId
        nowPlayingSent = false
        scrobbled = false
    }

    fun update(trackId: String, positionMs: Long, durationMs: Long, playing: Boolean): List<LastFmPlaybackEvent> {
        if (!playing || trackId != this.trackId) return emptyList()
        return buildList {
            if (!nowPlayingSent && positionMs >= 3_000) {
                nowPlayingSent = true
                add(LastFmPlaybackEvent.NowPlaying)
            }
            if (!scrobbled && durationMs >= 30_000 &&
                (positionMs >= durationMs / 2 || positionMs >= 240_000)
            ) {
                scrobbled = true
                add(LastFmPlaybackEvent.Scrobble)
            }
        }
    }
}
