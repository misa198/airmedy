package me.misa198.airmedy.sync

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class ListeningPersistenceTest {
    @Test
    fun splitsListeningAcrossLocalMidnight() {
        val zone = ZoneId.systemDefault()
        val day = LocalDate.of(2026, 7, 22)
        val start = day.atTime(23, 59, 50).atZone(zone).toInstant().toEpochMilli()
        val end = day.plusDays(1).atTime(0, 0, 10).atZone(zone).toInstant().toEpochMilli()
        assertEquals(mapOf("2026-07-22" to 10, "2026-07-23" to 10), splitListeningByDate(start, end, 20))
    }
}
