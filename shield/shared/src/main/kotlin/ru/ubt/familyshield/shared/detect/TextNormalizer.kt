package ru.ubt.familyshield.shared.detect

import java.text.Normalizer

data class NormalizedText(
    val original: String,
    /** Lower-case, homoglyph-folded text with single spaces between tokens. */
    val text: String,
    val tokens: List<String>,
    val stems: List<String>,
    /** Latin/digit look-alikes were mixed into Cyrillic words — a classic filter-evasion trick. */
    val hadHomoglyphs: Boolean,
    val hadInvisibleChars: Boolean,
)

object TextNormalizer {
    private val invisible = setOf(
        '​', '‌', '‍', '‎', '‏', '⁠', '⁡', '⁢', '⁣', '⁤',
        '﻿', '­', '᠎', '͏',
    )

    /** Latin and digit characters that are visually identical to Cyrillic ones. */
    private val homoglyphs: Map<Char, Char> = mapOf(
        'A' to 'а', 'a' to 'а', 'B' to 'в', 'C' to 'с', 'c' to 'с', 'E' to 'е', 'e' to 'е', 'H' to 'н',
        'K' to 'к', 'k' to 'к', 'M' to 'м', 'O' to 'о', 'o' to 'о', 'P' to 'р', 'p' to 'р', 'T' to 'т',
        'X' to 'х', 'x' to 'х', 'Y' to 'у', 'y' to 'у', 'u' to 'и', 'n' to 'п', 'r' to 'г',
        '0' to 'о', '3' to 'з', '6' to 'б', '4' to 'ч',
    )

    private val tokenRegex = Regex("[\\p{L}\\p{N}]+")

    private fun isCyrillic(c: Char) = Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CYRILLIC

    fun normalize(input: String): NormalizedText {
        val nfkc = Normalizer.normalize(input, Normalizer.Form.NFKC)
        var hadInvisible = false
        val cleaned = buildString(nfkc.length) {
            for (ch in nfkc) {
                if (ch in invisible) hadInvisible = true else append(ch)
            }
        }
        var hadHomoglyphs = false
        val rawTokens = tokenRegex.findAll(cleaned).map { it.value }.toList()
        val folded = ArrayList<String>(rawTokens.size)
        for (token in rawTokens) {
            val cyr = token.count { isCyrillic(it) }
            // Digits only count as look-alikes inside a word ("к0д"), never in amounts ("5000руб").
            fun lookAlike(i: Int): Boolean {
                val ch = token[i]
                if (ch !in homoglyphs) return false
                if (!ch.isDigit()) return true
                return i > 0 && i < token.length - 1 && token[i - 1].isLetter() && token[i + 1].isLetter()
            }
            val foreign = token.indices.count { lookAlike(it) }
            var t = token
            if (cyr > 0 && foreign > 0 && cyr >= foreign) {
                hadHomoglyphs = true
                t = buildString { for (i in token.indices) append(if (lookAlike(i)) homoglyphs.getValue(token[i]) else token[i]) }
            }
            folded += collapseRepeats(t.lowercase().replace('ё', 'е'))
        }
        val tokens = joinSpacedLetters(folded)
        return NormalizedText(
            original = input,
            text = tokens.joinToString(" "),
            tokens = tokens,
            stems = tokens.map { RussianStemmer.stem(it) },
            hadHomoglyphs = hadHomoglyphs,
            hadInvisibleChars = hadInvisible,
        )
    }

    /** "ааааа" -> "аа": keeps legitimate double letters while defeating stretched words. */
    private fun collapseRepeats(s: String): String {
        if (s.length < 3) return s
        val sb = StringBuilder(s.length)
        var run = 0
        var prev = '\u0000'
        for (ch in s) {
            run = if (ch == prev) run + 1 else 1
            prev = ch
            if (run <= 2 || !ch.isLetter()) sb.append(ch)
        }
        return sb.toString()
    }

    /** "к о д" or "с.м.с" written letter by letter is joined back into a word. */
    private fun joinSpacedLetters(tokens: List<String>): List<String> {
        val out = ArrayList<String>(tokens.size)
        var i = 0
        while (i < tokens.size) {
            if (tokens[i].length == 1 && tokens[i][0].isLetter()) {
                var j = i
                while (j < tokens.size && tokens[j].length == 1 && tokens[j][0].isLetter()) j++
                if (j - i >= 3) {
                    out += tokens.subList(i, j).joinToString("")
                    i = j
                    continue
                }
            }
            out += tokens[i]
            i++
        }
        return out
    }
}
