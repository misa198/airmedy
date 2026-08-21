package me.misa198.airmedy.player

import android.media.session.MediaSession

/** Shares the currently live playback session with Android UI entry points. */
internal object AndroidPlaybackSession {
    @Volatile
    private var activeToken: MediaSession.Token? = null

    fun publish(token: MediaSession.Token) {
        activeToken = token
    }

    fun tokenOrNull(): MediaSession.Token? = activeToken

    fun clear() {
        activeToken = null
    }
}
