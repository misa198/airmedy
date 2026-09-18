package me.misa198.airmedy.lyrics

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class RomanizationInspection(val supported: Boolean = false, val mandarinDefault: Boolean = false)
enum class RomanizedStatus { Converted, Unsupported, Failed }
data class RomanizedLine(val text: String = "", val status: RomanizedStatus = RomanizedStatus.Unsupported)

interface RomanizationEngine {
    val version: String
    fun inspect(line: String): RomanizationInspection
    suspend fun romanize(line: String): String
    fun release()
}

fun validateRomanization(lines: List<String>) {
    require(lines.size <= 2_000) { "Too many lyric lines" }
    var bytes = 0
    for (line in lines) {
        var index = 0
        var points = 0
        while (index < line.length) {
            val char = line[index++]
            if (char.isHighSurrogate()) {
                require(index < line.length && line[index++].isLowSurrogate()) { "Invalid Unicode" }
            } else require(!char.isLowSurrogate()) { "Invalid Unicode" }
            points++
        }
        require(points <= 2_048) { "Lyric line too long" }
        bytes += line.encodeToByteArray().size
        require(bytes <= 128 * 1_024) { "Lyrics too large" }
    }
}

class RomanizeLyrics(private val engine: RomanizationEngine) {
    private val mutex = Mutex()
    private var cachedKey: Pair<String, List<String>>? = null
    private var cached: List<RomanizedLine> = emptyList()

    fun inspect(lines: List<String>): RomanizationInspection {
        validateRomanization(lines)
        val inspections = lines.map(engine::inspect)
        return RomanizationInspection(inspections.any { it.supported }, inspections.any { it.mandarinDefault })
    }

    suspend operator fun invoke(lines: List<String>): List<RomanizedLine> = mutex.withLock {
        validateRomanization(lines)
        val key = engine.version to lines.toList()
        if (cachedKey == key) return@withLock cached
        val result = lines.map { line ->
            currentCoroutineContext().ensureActive()
            if (!engine.inspect(line).supported) RomanizedLine()
            else try {
                val converted = engine.romanize(line)
                if (converted.isEmpty() || converted == line) RomanizedLine(status = RomanizedStatus.Failed)
                else RomanizedLine(converted, RomanizedStatus.Converted)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                RomanizedLine(status = RomanizedStatus.Failed)
            }
        }
        currentCoroutineContext().ensureActive()
        if (result.none { it.status == RomanizedStatus.Failed } &&
            result.sumOf { it.text.encodeToByteArray().size + 32 } <= 1_024 * 1_024
        ) {
            cachedKey = key
            cached = result
        } else {
            cachedKey = null
            cached = emptyList()
        }
        result
    }

    suspend fun release() = mutex.withLock { engine.release() }
}
