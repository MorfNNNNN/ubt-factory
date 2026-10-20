package ru.ubt.familyshield.shared.detect

/**
 * Small pattern language for Russian phrases, robust to inflection and word order gaps:
 *  - `слово`   matches any word form with the same Snowball stem;
 *  - `преф*`   matches any normalized token starting with the prefix;
 *  - `=слово`  matches the exact token only (for short or ambiguous words);
 *  - `a|b|c`   alternatives for one position.
 * Consecutive terms may be separated by up to `maxGap` other tokens.
 */
sealed interface Term {
    fun matches(token: String, stem: String): Boolean

    data class Stem(val stem: String) : Term {
        override fun matches(token: String, stem: String) = stem == this.stem || token == this.stem
    }

    data class Prefix(val prefix: String) : Term {
        override fun matches(token: String, stem: String) = token.startsWith(prefix)
    }

    data class Exact(val word: String) : Term {
        override fun matches(token: String, stem: String) = token == word
    }

    data class AnyOf(val terms: List<Term>) : Term {
        override fun matches(token: String, stem: String) = terms.any { it.matches(token, stem) }
    }

    companion object {
        fun parse(word: String): Term {
            val alts = word.split('|').filter { it.isNotBlank() }.map { parseSingle(it.trim().lowercase().replace('ё', 'е')) }
            return alts.singleOrNull() ?: AnyOf(alts)
        }

        private fun parseSingle(w: String): Term = when {
            w.endsWith("*") -> Prefix(w.dropLast(1))
            w.startsWith("=") -> Exact(w.drop(1))
            else -> Stem(RussianStemmer.stem(w))
        }
    }
}

class PhrasePattern(
    val source: String,
    private val maxGap: Int,
    /** Reject matches that are negated ("никому не называйте код"), typical for warnings and advice. */
    private val negationSensitive: Boolean = false,
) {
    private val terms: List<Term> = source.trim().split(Regex("\\s+")).map { Term.parse(it) }

    /** Returns the token span of the first match, or null. */
    fun find(text: NormalizedText): IntRange? {
        val n = text.tokens.size
        if (terms.isEmpty() || n < terms.size) return null
        for (start in 0 until n) {
            if (!terms[0].matches(text.tokens[start], text.stems[start])) continue
            val end = matchRest(text, 1, start + 1) ?: continue
            if (negationSensitive && isNegated(text, start, end)) continue
            return start..end
        }
        return null
    }

    private fun isNegated(text: NormalizedText, start: Int, end: Int): Boolean {
        if (start > 0 && text.tokens[start - 1] in NEGATORS) return true
        for (i in start..end) if (text.tokens[i] in NEGATORS) return true
        return false
    }

    companion object {
        val NEGATORS = setOf("не", "нельзя", "никогда", "никому", "ни", "запрещено", "запрещается")
    }

    private fun matchRest(text: NormalizedText, termIdx: Int, from: Int): Int? {
        if (termIdx == terms.size) return from - 1
        val last = minOf(text.tokens.size - 1, from + maxGap)
        for (j in from..last) {
            if (terms[termIdx].matches(text.tokens[j], text.stems[j])) {
                val end = matchRest(text, termIdx + 1, j + 1)
                if (end != null) return end
            }
        }
        return null
    }
}

class Rule<C>(
    val id: String,
    val category: C,
    /** Probability-like contribution in 0..1 (combined with noisy-OR). */
    val weight: Double,
    patterns: List<String>,
    /** Plain-language explanation shown to the user (Russian). */
    val explanation: String,
    maxGap: Int = 2,
    negationSensitive: Boolean = false,
) {
    val patterns: List<PhrasePattern> = patterns.map { PhrasePattern(it, maxGap, negationSensitive) }

    init {
        require(weight in 0.0..1.0) { "weight out of range in $id" }
        require(patterns.isNotEmpty()) { "no patterns in $id" }
    }
}

data class RuleHit<C>(val rule: Rule<C>, val matchedText: String, val span: IntRange)

class LexiconEngine<C>(val rules: List<Rule<C>>) {
    init {
        val dupes = rules.groupBy { it.id }.filterValues { it.size > 1 }.keys
        require(dupes.isEmpty()) { "duplicate rule ids: $dupes" }
    }

    fun evaluate(text: NormalizedText): List<RuleHit<C>> {
        if (text.tokens.isEmpty()) return emptyList()
        val hits = ArrayList<RuleHit<C>>()
        for (rule in rules) {
            for (p in rule.patterns) {
                val span = p.find(text) ?: continue
                hits += RuleHit(rule, text.tokens.subList(span.first, span.last + 1).joinToString(" "), span)
                break
            }
        }
        return hits
    }
}

/** Noisy-OR: independent pieces of evidence. */
fun noisyOr(weights: Iterable<Double>): Double = 1.0 - weights.fold(1.0) { acc, w -> acc * (1.0 - w.coerceIn(0.0, 1.0)) }

enum class RiskLevel(val titleRu: String) {
    SAFE("Опасных признаков не найдено"),
    LOW("Низкий риск"),
    MEDIUM("Подозрительно"),
    HIGH("Высокий риск"),
    CRITICAL("Очень похоже на мошенничество");

    companion object {
        fun of(score: Double): RiskLevel = when {
            score >= 0.85 -> CRITICAL
            score >= 0.65 -> HIGH
            score >= 0.45 -> MEDIUM
            score >= 0.25 -> LOW
            else -> SAFE
        }
    }
}
