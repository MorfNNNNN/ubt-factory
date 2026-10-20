package ru.ubt.familyshield.shared.detect

/**
 * Russian Snowball stemmer (Porter), implemented from the published algorithm description
 * (snowballstem.org/algorithms/russian/stemmer.html). Input is expected in lower case.
 */
object RussianStemmer {
    private const val VOWELS = "аеиоуыэюя"

    private class Group(val endings: List<String>, val needsAOrYa: Boolean)

    private val perfectiveGerund = listOf(
        Group(listOf("вшись", "вши", "в"), true),
        Group(listOf("ившись", "ывшись", "ивши", "ывши", "ив", "ыв"), false),
    )
    private val adjective = listOf(
        Group(
            listOf(
                "ими", "ыми", "его", "ого", "ему", "ому", "ее", "ие", "ые", "ое", "ей", "ий", "ый", "ой",
                "ем", "им", "ым", "ом", "их", "ых", "ую", "юю", "ая", "яя", "ою", "ею",
            ),
            false,
        ),
    )
    private val participle = listOf(
        Group(listOf("ем", "нн", "вш", "ющ", "щ"), true),
        Group(listOf("ивш", "ывш", "ующ"), false),
    )
    private val reflexive = listOf(Group(listOf("ся", "сь"), false))
    private val verb = listOf(
        Group(listOf("ла", "на", "ете", "йте", "ли", "й", "л", "ем", "н", "ло", "но", "ет", "ют", "ны", "ть", "ешь", "нно"), true),
        Group(
            listOf(
                "ила", "ыла", "ена", "ейте", "уйте", "ите", "или", "ыли", "ей", "уй", "ил", "ыл", "им", "ым", "ен",
                "ило", "ыло", "ено", "ят", "ует", "уют", "ит", "ыт", "ены", "ить", "ыть", "ишь", "ую", "ю",
            ),
            false,
        ),
    )
    private val noun = listOf(
        Group(
            listOf(
                "а", "ев", "ов", "ие", "ье", "е", "иями", "ями", "ами", "еи", "ии", "и", "ией", "ей", "ой", "ий", "й",
                "иям", "ям", "ием", "ем", "ам", "ом", "о", "у", "ах", "иях", "ях", "ы", "ь", "ию", "ью", "ю", "ия", "ья", "я",
            ),
            false,
        ),
    )
    private val derivational = listOf(Group(listOf("ость", "ост"), false))

    private fun isVowel(c: Char) = c in VOWELS

    /**
     * Removes the longest ending among [groups] that lies fully in the region starting at [limit].
     * Mirrors Snowball's `among`: if the longest match fails its condition, no shorter one is tried.
     */
    private fun removeLongest(word: String, limit: Int, groups: List<Group>): String? {
        var best: String? = null
        var bestGroup: Group? = null
        for (g in groups) {
            for (e in g.endings) {
                if (word.length - e.length >= limit && word.endsWith(e) && (best == null || e.length > best.length)) {
                    best = e
                    bestGroup = g
                }
            }
        }
        if (best == null || bestGroup == null) return null
        val cut = word.length - best.length
        if (bestGroup.needsAOrYa) {
            if (cut - 1 < limit) return null
            val prev = word[cut - 1]
            if (prev != 'а' && prev != 'я') return null
        }
        return word.substring(0, cut)
    }

    fun stem(input: String): String {
        val word = input.replace('ё', 'е')
        if (word.length < 3 || word.none { isVowel(it) }) return word

        val n = word.length
        var i = 0
        while (i < n && !isVowel(word[i])) i++
        val pV = if (i < n) i + 1 else n
        // p2: after pV, go past a non-vowel (p1), then past a vowel, then past a non-vowel.
        var c = pV
        fun gopast(vowel: Boolean): Boolean {
            while (c < n && isVowel(word[c]) != vowel) c++
            if (c >= n) return false
            c++
            return true
        }
        val p2 = if (gopast(false) && gopast(true) && gopast(false)) c else n

        var w = word
        // Step 1.
        val gerund = removeLongest(w, pV, perfectiveGerund)
        if (gerund != null) {
            w = gerund
        } else {
            removeLongest(w, pV, reflexive)?.let { w = it }
            val adj = removeLongest(w, pV, adjective)
            if (adj != null) {
                w = removeLongest(adj, pV, participle) ?: adj
            } else {
                val v = removeLongest(w, pV, verb)
                if (v != null) {
                    w = v
                } else {
                    removeLongest(w, pV, noun)?.let { w = it }
                }
            }
        }
        // Step 2.
        if (w.length - 1 >= pV && w.endsWith('и')) w = w.dropLast(1)
        // Step 3.
        removeLongest(w, p2, derivational)?.let { w = it }
        // Step 4.
        when {
            w.length - 4 >= pV && w.endsWith("ейше") -> w = undoubleN(w.dropLast(4), pV)
            w.length - 3 >= pV && w.endsWith("ейш") -> w = undoubleN(w.dropLast(3), pV)
            w.length - 2 >= pV && w.endsWith("нн") -> w = w.dropLast(1)
            w.length - 1 >= pV && w.endsWith('ь') -> w = w.dropLast(1)
        }
        return w
    }

    private fun undoubleN(w: String, pV: Int): String =
        if (w.length - 2 >= pV && w.endsWith("нн")) w.dropLast(1) else w
}
