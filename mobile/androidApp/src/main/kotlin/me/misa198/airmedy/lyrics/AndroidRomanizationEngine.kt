package me.misa198.airmedy.lyrics

import java.io.InputStream
import java.text.Normalizer
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** All dictionary access is serialized by RomanizeLyrics. Streams also allow host-side fixtures. */
internal class AndroidRomanizationEngine(private val openAsset: (String) -> InputStream) : RomanizationEngine {
    override val version = "android-v1:pinyin-0.21.0:phrase-cee0ed6:opencc-5c764a5:korean-1.0.0"
    private data class Dictionaries(
        val phrases: Map<String, String>,
        val simplified: Map<Int, Int>,
        val characters: Map<Int, String>,
        val maxPhrase: Int,
    )
    private var dictionaries: Dictionaries? = null

    override fun inspect(line: String): RomanizationInspection {
        val points = Normalizer.normalize(line, Normalizer.Form.NFKC).codePoints().toArray()
        if (points.any { Character.UnicodeScript.of(it) in kana }) return RomanizationInspection()
        val han = points.any { Character.UnicodeScript.of(it) == Character.UnicodeScript.HAN }
        return RomanizationInspection(han || points.any { it in 0xAC00..0xD7A3 }, han)
    }

    override suspend fun romanize(line: String): String {
        val points = line.codePoints().toArray()
        return buildString {
            var start = 0
            while (start < points.size) {
                currentCoroutineContext().ensureActive()
                val kind = script(points[start])
                var end = start + 1
                while (end < points.size && script(points[end]) == kind) end++
                val run = String(points, start, end - start)
                if (start > 0 && (
                    (isLatin(points[start - 1]) && kind != 0) ||
                        (script(points[start - 1]) != 0 && isLatin(points[start]))
                )) append(' ')
                append(when (kind) {
                    1 -> chinese(Normalizer.normalize(run, Normalizer.Form.NFC))
                    2 -> KoreanRomanizer.romanize(Normalizer.normalize(run, Normalizer.Form.NFC))
                    else -> run
                })
                start = end
            }
        }
    }

    private suspend fun load(): Dictionaries {
        currentCoroutineContext().ensureActive()
        dictionaries?.let { return it }
        val phrases = mutableMapOf<String, String>()
        val simplified = mutableMapOf<Int, Int>()
        val characters = mutableMapOf<Int, String>()
        var maxPhrase = 0
        readLines("pinyin.txt") { line ->
            val separator = line.indexOf(": ")
            if (separator > 0) {
                val key = line.substring(0, separator)
                phrases[key] = line.substring(separator + 2).trim()
                maxPhrase = maxOf(maxPhrase, key.codePointCount(0, key.length))
            }
        }
        readLines("TSCharacters.txt") { line ->
            val fields = line.split('\t')
            if (fields.size >= 2 && fields[0].isNotEmpty() && fields[1].isNotEmpty()) {
                simplified[fields[0].codePointAt(0)] = fields[1].codePointAt(0)
            }
        }
        readLines("characters.tsv") { line ->
            val fields = line.split('\t', limit = 2)
            require(fields.size == 2) { "Invalid character dictionary" }
            characters[fields[0].toInt(16)] = fields[1]
        }
        currentCoroutineContext().ensureActive()
        return Dictionaries(phrases, simplified, characters, maxPhrase).also { dictionaries = it }
    }

    private suspend fun readLines(name: String, consume: (String) -> Unit) {
        openAsset("romanization/$name").bufferedReader().use { reader ->
            while (true) {
                currentCoroutineContext().ensureActive()
                val line = reader.readLine() ?: break
                if (line.isNotBlank() && !line.startsWith('#')) consume(line)
            }
        }
    }

    private suspend fun chinese(text: String): String {
        val data = load()
        val original = text.codePoints().toArray()
        val canonical = original.map { data.simplified[it] ?: it }.toIntArray()
        val words = mutableListOf<String>()
        var index = 0
        while (index < original.size) {
            currentCoroutineContext().ensureActive()
            var length = minOf(data.maxPhrase, original.size - index)
            var reading: String? = null
            while (length > 1) {
                reading = data.phrases[String(original, index, length)]
                    ?: data.phrases[String(canonical, index, length)]
                if (reading != null) break
                length--
            }
            if (reading == null) {
                length = 1
                reading = data.characters[original[index]] ?: error("No reading for U+${original[index].toString(16)}")
            }
            words += reading
            index += length
        }
        return words.joinToString(" ")
    }

    override fun release() { dictionaries = null }

    private fun isLatin(point: Int) = Character.UnicodeScript.of(point) == Character.UnicodeScript.LATIN
    private fun script(point: Int): Int = when {
        point in 0xAC00..0xD7A3 || point in 0x1100..0x1112 || point in 0x1161..0x1175 || point in 0x11A8..0x11C2 -> 2
        Character.UnicodeScript.of(point) == Character.UnicodeScript.HAN -> 1
        else -> 0
    }

    private companion object {
        val kana = setOf(Character.UnicodeScript.HIRAGANA, Character.UnicodeScript.KATAKANA)
    }
}
