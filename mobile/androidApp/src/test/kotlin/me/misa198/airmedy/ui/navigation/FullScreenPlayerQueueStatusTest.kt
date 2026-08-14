package me.misa198.airmedy.ui.navigation

import me.misa198.airmedy.player.PlaybackQueueSnapshot
import me.misa198.airmedy.player.RepeatMode
import me.misa198.airmedy.ui.components.MaterialSymbols
import org.junit.Assert.assertEquals
import org.junit.Test

class FullScreenPlayerQueueStatusTest {
    @Test
    fun shuffleBadgeTakesPrecedenceOverRepeat() {
        assertEquals(
            MaterialSymbols.Shuffle,
            queueStatusBadgeSymbol(
                PlaybackQueueSnapshot(shuffle = true, repeatMode = RepeatMode.One),
            ),
        )
    }

    @Test
    fun repeatBadgeReflectsTheConfiguredRepeatModeWhenShuffleIsOff() {
        assertEquals(
            MaterialSymbols.Repeat,
            queueStatusBadgeSymbol(PlaybackQueueSnapshot(repeatMode = RepeatMode.All)),
        )
        assertEquals(
            MaterialSymbols.RepeatOne,
            queueStatusBadgeSymbol(PlaybackQueueSnapshot(repeatMode = RepeatMode.One)),
        )
    }

    @Test
    fun noBadgeIsShownWhenShuffleAndRepeatAreOff() {
        assertEquals(null, queueStatusBadgeSymbol(PlaybackQueueSnapshot()))
    }
}
