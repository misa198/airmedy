package me.misa198.airmedy.lyrics

// Port of misa198/koreanromanizer v1.0.0 (MIT); see mobile/tools/romanization/ATTRIBUTION.md.
object KoreanRomanizer {
    private val syllables = Regex("[가-힣]+")
    private val initials = "g kk n d tt r m b pp s ss - j jj ch k t p h".split(' ')
    private val vowels = "a ae ya yae eo e yeo ye o wa wae oe yo u wo we wi yu eu ui i".split(' ')
    private val finals = "k k k n n n t l k m p t t p l m p p t t ng t t k t p t".split(' ')
    private val rules = listOf(
        "ᆧ" to "",
        "[ᆸᇁᆹᆲᆵ](?=[ᄂᄆ])" to "ᆷ",
        "[ᆮᇀᆽᆾᆺᆻᇂ](?=[ᄂᄆ])" to "ᆫ",
        "[ᆨᆩᆿᆪᆰ](?=[ᄂᄆ])" to "ᆼ",
        "ᆨᄋ(?=[ᅣᅤᅧᅨᅭᅲ])" to "ᆼᄂ",
        "ᆯᄋ(?=[ᅣᅤᅧᅨᅭᅲ])" to "ᆯᄅ",
        "[ᆨᆼ]ᄅ" to "ᆼᄂ", "ᆫᄅ(?=ᅩ)" to "ᆫᄂ",
        "ᆯᄂ|ᆫᄅ" to "ᆯᄅ", "[ᆷᆸ]ᄅ" to "ᆷᄂ", "ᆰᄅ" to "ᆨᄅ",
        "ᆨᄏ" to "ᆨ-ᄏ", "ᆸᄑ" to "ᆸ-ᄑ", "ᆮᄐ" to "ᆮ-ᄐ",
        "ᆪᄋ" to "ᆨᄉ", "ᆬᄋ" to "ᆫᄌ", "ᆭᄋ" to "ᆫᄋ",
        "ᆰᄋ" to "ᆯᄀ", "ᆱᄋ" to "ᆯᄆ", "ᆲᄋ" to "ᆯᄇ",
        "ᆳᄋ" to "ᆯᄉ", "ᆴᄋ" to "ᆯᄐ", "ᆵᄋ" to "ᆯᄑ",
        "ᆶᄋ" to "ᆯᄋ", "ᆹᄋ" to "ᆸᄉ",
        "밟(?=[ᄀ-ᄊᄌ-ᄒ])" to "밥",
        "ᆪ" to "ᆨᆺ", "ᆬ" to "ᆫᆽ", "ᆭ" to "ᆫᇂ", "ᆰ" to "ᆨᆯ",
        "ᆱ" to "ᆷᆯ", "ᆲ" to "ᆯᆸ", "ᆳ" to "ᆯᆺ", "ᆴ" to "ᆯᇀ",
        "ᆵ" to "ᇁᆯ", "ᆶ" to "ᆯᇂ", "ᆹ" to "ᆸᆺ",
        "ᆮ이" to "지", "ᇀ이" to "치", "ᆮ히" to "치",
        "ᆨᄋ" to "ᄀ", "ᆩᄋ" to "ᄁ", "ᆮᄋ" to "ᄃ", "ᆯᄋ" to "ᄅ",
        "ᆸᄋ" to "ᄇ", "ᆺᄋ" to "ᄉ", "ᆻᄋ" to "ᄊ", "ᆽᄋ" to "ᄌ",
        "ᆾᄋ" to "ᄎ", "ᇂᄋ" to "",
        "ᇂᄀ|ᆨᄒ" to "ᄏ", "ᇂᄃ|ᆮᄒ" to "ᄐ", "ᇂᄌ|ᆽᄒ" to "ᄎ",
        "ᇂᄇ" to "ᄇ", "ᆸᄒ" to "ᄑ", "ᆯᄅ" to "ll",
        "([ᆨ-ᇂ])([ᆨ-ᇂ])" to "\$1",
    ).map { (pattern, replacement) -> Regex(pattern) to replacement }

    fun romanize(text: String): String = syllables.replace(text) { match ->
        val word = match.value.toCharArray()
        if (word.size > 1 && word.last() == '잎' && (word[word.lastIndex - 1].code - 0xAC00) % 28 != 0) {
            word[word.lastIndex] = '닢'
        }
        var decomposed = buildString {
            for (char in word) {
                val value = char.code - 0xAC00
                append((0x1100 + value / (21 * 28)).toChar())
                append((0x1161 + value / 28 % 21).toChar())
                if (value % 28 != 0) append((0x11A7 + value % 28).toChar())
            }
        }
        for ((pattern, replacement) in rules) decomposed = pattern.replace(decomposed, replacement)
        buildString {
            decomposed.forEachIndexed { index, char ->
                when {
                    char == 'ᇂ' && index < decomposed.lastIndex -> Unit
                    char == 'ᄋ' -> Unit
                    char.code in 0x1100..0x1112 -> append(initials[char.code - 0x1100])
                    char.code in 0x1161..0x1175 -> append(vowels[char.code - 0x1161])
                    char.code in 0x11A8..0x11C2 -> append(finals[char.code - 0x11A8])
                    else -> append(char)
                }
            }
        }
    }
}
