package ru.ubt.familyshield.shared.detect

import ru.ubt.familyshield.shared.model.AgeBand
import ru.ubt.familyshield.shared.model.AlertSeverity
import ru.ubt.familyshield.shared.model.RiskCategory

data class MessageContext(
    /** null when unknown (e.g. contacts permission not granted). */
    val senderInContacts: Boolean? = null,
    val duringCall: Boolean = false,
    val sourcePackage: String? = null,
    val sender: String? = null,
)

data class ScamFinding(val type: ScamType, val score: Double, val reasons: List<String>, val matched: List<String>)

data class ScamVerdict(
    val score: Double,
    val findings: List<ScamFinding>,
    val markers: Set<ScamType>,
    val urls: List<UrlVerdict>,
    val phones: List<PhoneInfo>,
    val otp: OtpMatch?,
    val obfuscated: Boolean,
    val warningContext: Boolean,
) {
    val level: RiskLevel get() = RiskLevel.of(score)
    val top: ScamFinding? get() = findings.firstOrNull()

    /** Up to three distinct explanations for the user, most important first. */
    val reasons: List<String> get() = findings.flatMap { it.reasons }.distinct().take(3)

    val advice: String? get() = top?.type?.adviceRu
}

/**
 * Scores a message for fraud. Combines lexical evidence by scheme, generic pressure markers
 * (urgency, secrecy, money), link analysis and the delivery context (during a call, unknown sender).
 */
class ScamAnalyzer(private val isKnownBadDomain: (String) -> Boolean = { false }) {

    private companion object {
        /** Negation-sensitive rules that only fire on a direct instruction to the reader. */
        const val TOPIC_CAP = 0.4
        val DIRECTIVE_RULES = setOf("code_request", "m_money", "take_loan", "courier_money", "install_app", "screen_share", "receive_payment", "m_card")
    }

    private val markerBoost = mapOf(
        ScamType.URGENCY to 0.2,
        ScamType.SECRECY to 0.35,
        ScamType.MONEY to 0.3,
        ScamType.CARD_DATA to 0.45,
        ScamType.PERSONAL_DATA to 0.2,
        ScamType.CODE_MENTION to 0.1,
    )

    private val warningEngine = LexiconEngine(
        listOf(
            Rule(
                "warn", "warn", 1.0,
                listOf(
                    "будьте бдительн*|осторожн*", "осторожно мошенник*", "сотрудник* банк* никогда не", "банк* никогда не",
                    "мошенник* могут", "остерегайт* мошенник*", "не сообщайт* код* никому", "никому не сообщайт* код*",
                    "никогда не сообщайт*", "не переходит* по ссылк*", "это мошенник*", "памятк*", "как распознать",
                ),
                "warning",
            ),
        ),
    )

    fun analyze(text: String, ctx: MessageContext = MessageContext()): ScamVerdict {
        val norm = TextNormalizer.normalize(text)
        val hits = ScamLexicon.engine.evaluate(norm)
        val otp = OtpDetector.detect(text)
        val warning = warningEngine.evaluate(norm).isNotEmpty()

        val markers = hits.filter { it.rule.category.isMarker }.map { it.rule.category }.toMutableSet()
        // A genuine OTP notification always says "do not share this code" — that is not a scam signal.
        if (otp != null) {
            markers.remove(ScamType.SECRECY)
            markers.remove(ScamType.CODE_MENTION)
        }

        val phones = PhoneNumbers.extract(text)
        val contextBoost = buildList {
            if (ctx.duringCall) add(0.3)
            if (phones.isNotEmpty()) add(0.15)
            if (ctx.senderInContacts == false) add(0.1)
            if (norm.hadHomoglyphs || norm.hadInvisibleChars) add(0.3)
        }
        val boost = noisyOr(markers.mapNotNull { markerBoost[it] } + contextBoost)

        val byType = hits.filter { !it.rule.category.isMarker }.groupBy { it.rule.category }
        val findings = ArrayList<ScamFinding>()
        for ((type, typeHits) in byType) {
            val raw = noisyOr(typeHits.map { it.rule.weight })
            val base = if (typeHits.all { it.rule.id in ScamLexicon.topicRules }) minOf(raw, TOPIC_CAP) else raw
            // Markers amplify real evidence but cannot create it from nothing.
            val amplified = base + (1 - base) * boost * minOf(1.0, base / 0.3)
            findings += ScamFinding(type, amplified, typeHits.map { it.rule.explanation }, typeHits.map { it.matchedText })
        }

        // Pressure pattern without a recognised scheme: money + urgency/secrecy, or card data requested.
        val pressure = buildList {
            if (ScamType.MONEY in markers && (ScamType.SECRECY in markers || ScamType.URGENCY in markers)) add(0.55)
            if (ScamType.CARD_DATA in markers) add(0.6)
            if (ScamType.SECRECY in markers && otp == null) add(0.35)
            if (ScamType.PERSONAL_DATA in markers && ScamType.URGENCY in markers) add(0.35)
        }
        if (pressure.isNotEmpty()) {
            val base = noisyOr(pressure)
            findings += ScamFinding(
                ScamType.PRESSURE,
                base + (1 - base) * noisyOr(contextBoost),
                hits.filter { it.rule.category.isMarker && it.rule.category in markers }.map { it.rule.explanation },
                hits.filter { it.rule.category.isMarker }.map { it.matchedText },
            )
        }

        val urls = UrlAnalyzer.extract(text).map { UrlAnalyzer.analyze(it, isKnownBadDomain) }
        val riskyUrls = urls.filter { it.score >= 0.25 }
        if (riskyUrls.isNotEmpty()) {
            val worst = riskyUrls.maxBy { it.score }
            val textEvidence = findings.maxOfOrNull { it.score } ?: 0.0
            // A risky link inside a scam-like text is worse than either alone.
            val combined = 1 - (1 - worst.score) * (1 - 0.5 * textEvidence)
            findings += ScamFinding(ScamType.DANGEROUS_LINK, combined, worst.reasons, listOf(worst.displayHost))
        }

        findings.sortByDescending { it.score }
        var score = when (findings.size) {
            0 -> 0.0
            1 -> findings[0].score
            else -> 1 - (1 - findings[0].score) * (1 - 0.5 * findings[1].score)
        }
        // Awareness messages ("никогда не сообщайте код") mention scam words in negated advice. They are
        // discounted unless the text also gives a direct, non-negated instruction to act.
        if (warning && hits.none { it.rule.id in DIRECTIVE_RULES }) score *= 0.35
        return ScamVerdict(
            score = score.coerceIn(0.0, 1.0),
            findings = findings,
            markers = markers,
            urls = urls,
            phones = phones,
            otp = otp,
            obfuscated = norm.hadHomoglyphs || norm.hadInvisibleChars,
            warningContext = warning,
        )
    }
}

data class ChildRiskFinding(
    val category: RiskCategory,
    val score: Double,
    val severity: AlertSeverity,
    val reasons: List<String>,
    val matched: List<String>,
)

data class ChildRiskVerdict(val findings: List<ChildRiskFinding>, val urls: List<UrlVerdict>) {
    val top: ChildRiskFinding? get() = findings.firstOrNull()
    val maxScore: Double get() = findings.maxOfOrNull { it.score } ?: 0.0
}

/** Detects dangerous situations in messages a child receives (bullying, grooming, recruitment...). */
class ChildRiskAnalyzer(private val isKnownBadDomain: (String) -> Boolean = { false }) {

    fun threshold(ageBand: AgeBand): Double = when (ageBand) {
        AgeBand.UNDER_7, AgeBand.AGE_7_9 -> 0.45
        AgeBand.AGE_10_12 -> 0.5
        AgeBand.AGE_13_15, AgeBand.AGE_16_17 -> 0.6
    }

    fun analyze(text: String): ChildRiskVerdict {
        val norm = TextNormalizer.normalize(text)
        val hits = ChildRiskLexicon.engine.evaluate(norm)
        val obfuscationBoost = if (norm.hadHomoglyphs || norm.hadInvisibleChars) 0.15 else 0.0
        val findings = hits.groupBy { it.rule.category }.map { (category, catHits) ->
            val base = noisyOr(catHits.map { it.rule.weight })
            ChildRiskFinding(
                category = category,
                score = base + (1 - base) * obfuscationBoost * minOf(1.0, base / 0.3),
                severity = ChildRiskLexicon.severityOf(category),
                reasons = catHits.map { it.rule.explanation }.distinct(),
                matched = catHits.map { it.matchedText },
            )
        }.toMutableList()

        val urls = UrlAnalyzer.extract(text).map { UrlAnalyzer.analyze(it, isKnownBadDomain) }
        val worst = urls.maxByOrNull { it.score }
        if (worst != null && worst.score >= 0.65) {
            val existing = findings.indexOfFirst { it.category == RiskCategory.KID_SCAM }
            val linkFinding = ChildRiskFinding(
                RiskCategory.KID_SCAM, worst.score, AlertSeverity.HIGH, worst.reasons, listOf(worst.displayHost),
            )
            if (existing >= 0) {
                val e = findings[existing]
                findings[existing] = e.copy(score = noisyOr(listOf(e.score, worst.score)), reasons = e.reasons + worst.reasons)
            } else {
                findings += linkFinding
            }
        }
        findings.sortWith(compareByDescending<ChildRiskFinding> { it.severity.ordinal }.thenByDescending { it.score })
        return ChildRiskVerdict(findings, urls)
    }

    /** Findings that should be reported to parents given the monitoring settings. */
    fun reportable(
        verdict: ChildRiskVerdict,
        ageBand: AgeBand,
        enabledCategories: Set<RiskCategory>,
        criticalOnly: Boolean,
    ): List<ChildRiskFinding> {
        val t = threshold(ageBand)
        return verdict.findings.filter { f ->
            f.category in enabledCategories && f.score >= t && (!criticalOnly || f.severity == AlertSeverity.CRITICAL)
        }
    }
}
