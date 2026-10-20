package ru.ubt.familyshield.shared.detect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextTest {
    @Test
    fun stemsRussianInflections() {
        val cases = mapOf(
            "безопасный" to "безопасн",
            "безопасного" to "безопасн",
            "счёт" to "счет",
            "счета" to "счет",
            "переведите" to "перевед",
            "карты" to "карт",
            "кодом" to "код",
            "банками" to "банк",
            "красивейший" to "красив",
            "радость" to "радост",
            "бегущий" to "бегущ",
            "смс" to "смс",
        )
        for ((word, stem) in cases) assertEquals(word, stem, RussianStemmer.stem(word))
    }

    @Test
    fun foldsHomoglyphsAndInvisibleCharacters() {
        // Latin "e" and "o" inside a Cyrillic word, plus a zero-width space.
        val n = TextNormalizer.normalize("Бeзoпасный​ счёт")
        assertEquals(listOf("безопасный", "счет"), n.tokens)
        assertTrue(n.hadHomoglyphs)
        assertTrue(n.hadInvisibleChars)
    }

    @Test
    fun keepsAmountsIntact() {
        val n = TextNormalizer.normalize("Перевод 5000руб")
        assertEquals(listOf("перевод", "5000руб"), n.tokens)
        assertFalse(n.hadHomoglyphs)
    }

    @Test
    fun joinsSpacedLettersAndCollapsesStretching() {
        assertEquals(listOf("назови", "код"), TextNormalizer.normalize("назови к о д").tokens)
        assertEquals(listOf("срочноо"), TextNormalizer.normalize("СРОЧНОООООО").tokens)
    }

    @Test
    fun digitInsideWordIsTreatedAsLetter() {
        assertEquals(listOf("код"), TextNormalizer.normalize("к0д").tokens)
    }

    @Test
    fun phrasePatternRespectsGapsAndNegation() {
        val p = PhrasePattern("назови* код*", maxGap = 2, negationSensitive = true)
        assertTrue(p.find(TextNormalizer.normalize("Назовите, пожалуйста, код")) != null)
        assertTrue(p.find(TextNormalizer.normalize("никому не называйте код")) == null)
        assertTrue(p.find(TextNormalizer.normalize("назовите мне сейчас же этот код")) == null) // gap 3 > 2
    }
}
