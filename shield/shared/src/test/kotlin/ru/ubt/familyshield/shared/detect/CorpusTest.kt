package ru.ubt.familyshield.shared.detect

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Quality gate on a labelled corpus of realistic messages. Fails if recall of scams (>= MEDIUM) or
 * precision on everyday messages (< MEDIUM) drops — protects against regressions when tuning rules.
 */
class CorpusTest {
    private val analyzer = ScamAnalyzer()

    @Test
    fun corpusQuality() {
        val lines = javaClass.getResource("/scam_corpus.tsv")!!.readText().lines().filter { it.isNotBlank() }
        var scams = 0
        var caught = 0
        var safe = 0
        var falseAlarms = 0
        val misses = ArrayList<String>()
        for (line in lines) {
            val (label, text) = line.split('\t', limit = 2)
            val v = analyzer.analyze(text)
            val flagged = v.level >= RiskLevel.MEDIUM
            if (label == "scam") {
                scams++
                if (flagged) caught++ else misses += "MISS ${v.level} ${"%.2f".format(v.score)}: $text"
            } else {
                safe++
                if (flagged) {
                    falseAlarms++
                    misses += "FALSE ${v.level} ${"%.2f".format(v.score)} ${v.findings.map { it.type }}: $text"
                }
            }
        }
        val recall = caught.toDouble() / scams
        val falseRate = falseAlarms.toDouble() / safe
        println("Corpus: recall=${"%.2f".format(recall)} ($caught/$scams), false alarms=$falseAlarms/$safe")
        misses.forEach { println(it) }
        assertTrue("recall too low: $recall\n${misses.joinToString("\n")}", recall >= 0.95)
        assertTrue("too many false alarms: $falseRate\n${misses.joinToString("\n")}", falseRate <= 0.05)
    }
}
