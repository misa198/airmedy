package me.misa198.airmedy.lyrics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest

class RomanizationTest {
    @Test
    fun koreanDesktopReadingsAndPreservedText() {
        mapOf(
            "안녕하세요, Hello!" to "annyeonghaseyo, Hello!",
            "같이 꽃잎" to "gachi kkonnip", "한글" to "hangeul",
            "닭이 삶이 값이" to "dalgi salmi gapsi", "밟다 굳히다" to "bapda guchida",
            "문래역 선릉역 역량" to "mullaeyeok seolleungyeok yeongnyang",
            "흙 닭 삶 값 넋" to "heuk dak sam gap neok", "잃어 싫어 않아" to "ireo sireo ana",
            "백마 종로 신라" to "baengma jongno silla", "학여울 알약" to "hangnyeoul allyak",
            "Hello 한글! ᄀ ㄱ ㆆ 😀" to "Hello hangeul! ᄀ ㄱ ㆆ 😀",
        ).forEach { (input, expected) -> assertEquals(expected, KoreanRomanizer.romanize(input), input) }
    }

    @Test
    fun limitsFallbackAndSingleResultCache() = runTest {
        var calls = 0
        var released = false
        val service = RomanizeLyrics(object : RomanizationEngine {
            override val version = "test"
            override fun inspect(line: String) = RomanizationInspection(line != "Hello")
            override suspend fun romanize(line: String): String {
                calls++
                if (line == "fail") error("missing reading")
                return "reading"
            }
            override fun release() { released = true }
        })
        val lines = listOf("한글", "Hello")
        assertEquals(listOf(RomanizedStatus.Converted, RomanizedStatus.Unsupported), service(lines).map { it.status })
        service(lines)
        assertEquals(1, calls)
        service(listOf("fail"))
        service(listOf("fail"))
        assertEquals(3, calls)
        service.release()
        assertEquals(true, released)
        validateRomanization(listOf("😀".repeat(2_048)))
        listOf(List(2_001) { "" }, listOf("a".repeat(2_049)), List(65) { "a".repeat(2_048) }, listOf("\uD800"))
            .forEach { assertFailsWith<IllegalArgumentException> { validateRomanization(it) } }
    }
}
