package ru.ubt.familyshield.shared.detect

import java.net.IDN

data class UrlVerdict(
    val url: String,
    val host: String,
    /** Unicode form of the host for display (punycode decoded). */
    val displayHost: String,
    val registrableDomain: String,
    val score: Double,
    val reasons: List<String>,
    val official: Boolean,
    val impersonatedBrand: String? = null,
) {
    val level: RiskLevel get() = RiskLevel.of(score)
}

/** Heuristic phishing detection tuned for Russian brands and scam patterns. Works fully offline. */
object UrlAnalyzer {
    /** brand -> official registrable domains. */
    val officialDomains: Map<String, Set<String>> = mapOf(
        "Сбер" to setOf("sberbank.ru", "sber.ru", "sberbank.com", "sberdevices.ru", "sberhealth.ru", "kuper.ru", "samokat.ru"),
        "Госуслуги" to setOf("gosuslugi.ru", "gu-st.ru"),
        "Т-Банк" to setOf("tbank.ru", "tinkoff.ru", "t-j.ru", "tbank-online.com"),
        "ВТБ" to setOf("vtb.ru"),
        "Альфа-Банк" to setOf("alfabank.ru", "alfa-bank.ru"),
        "Газпромбанк" to setOf("gazprombank.ru", "gpb.ru"),
        "Райффайзенбанк" to setOf("raiffeisen.ru"),
        "Россельхозбанк" to setOf("rshb.ru"),
        "ПСБ" to setOf("psbank.ru"),
        "Совкомбанк" to setOf("sovcombank.ru", "halvacard.ru"),
        "Почта Банк" to setOf("pochtabank.ru"),
        "Почта России" to setOf("pochta.ru"),
        "Открытие" to setOf("open.ru"),
        "Росбанк" to setOf("rosbank.ru"),
        "МКБ" to setOf("mkb.ru"),
        "Уралсиб" to setOf("uralsib.ru"),
        "Ozon" to setOf("ozon.ru", "ozon.com", "ozonbank.ru"),
        "Wildberries" to setOf("wildberries.ru", "wb.ru", "wbbank.ru", "rwb.ru"),
        "Авито" to setOf("avito.ru", "avito.st"),
        "Мэр Москвы" to setOf("mos.ru"),
        "ФНС" to setOf("nalog.gov.ru", "nalog.ru"),
        "Социальный фонд" to setOf("sfr.gov.ru", "pfr.gov.ru", "pfrf.ru"),
        "Банк России" to setOf("cbr.ru", "fincult.info"),
        "Госавтоинспекция" to setOf("gibdd.ru"),
        "МТС" to setOf("mts.ru", "mtsbank.ru"),
        "Билайн" to setOf("beeline.ru"),
        "МегаФон" to setOf("megafon.ru"),
        "T2" to setOf("t2.ru", "tele2.ru"),
        "Яндекс" to setOf("yandex.ru", "ya.ru", "yandex.com", "yandex.net", "yastatic.net", "dzen.ru"),
        "ВКонтакте" to setOf("vk.com", "vk.ru", "vk.me", "vkontakte.ru", "userapi.com", "vkvideo.ru"),
        "Mail.ru" to setOf("mail.ru"),
        "Одноклассники" to setOf("ok.ru"),
        "Telegram" to setOf("telegram.org", "t.me", "telegram.me", "telegra.ph"),
        "WhatsApp" to setOf("whatsapp.com", "wa.me", "whatsapp.net"),
        "MAX" to setOf("max.ru"),
        "RuStore" to setOf("rustore.ru"),
        "Rutube" to setOf("rutube.ru"),
        "Google" to setOf("google.com", "google.ru", "youtube.com", "youtu.be", "goo.gl"),
    )

    private val officialSet: Set<String> = officialDomains.values.flatten().toSet()

    /** Brand markers searched inside hostnames (long ones as substrings, short ones as whole tokens). */
    private val brandTokens: Map<String, String> = mapOf(
        "sberbank" to "Сбер", "sber" to "Сбер", "sbrbank" to "Сбер", "sberbnk" to "Сбер",
        "gosuslugi" to "Госуслуги", "gosuslug" to "Госуслуги", "gosuslugl" to "Госуслуги", "gos-uslugi" to "Госуслуги", "gosusiugi" to "Госуслуги",
        "tinkoff" to "Т-Банк", "tbank" to "Т-Банк", "vtb" to "ВТБ", "vtb24" to "ВТБ", "alfabank" to "Альфа-Банк", "alfa-bank" to "Альфа-Банк",
        "gazprombank" to "Газпромбанк", "gazprom" to "Газпром", "raiffeisen" to "Райффайзенбанк", "rshb" to "Россельхозбанк",
        "psbank" to "ПСБ", "sovcombank" to "Совкомбанк", "pochtabank" to "Почта Банк", "pochta" to "Почта России",
        "ozon" to "Ozon", "wildberries" to "Wildberries", "wildberies" to "Wildberries", "avito" to "Авито",
        "nalog" to "ФНС", "sfr" to "Социальный фонд", "pfr" to "Социальный фонд", "cbr" to "Банк России", "centrobank" to "Банк России",
        "gibdd" to "Госавтоинспекция", "mts" to "МТС", "beeline" to "Билайн", "megafon" to "МегаФон", "tele2" to "T2",
        "yandex" to "Яндекс", "vkontakte" to "ВКонтакте", "telegram" to "Telegram", "whatsapp" to "WhatsApp", "rustore" to "RuStore",
        "mosru" to "Мэр Москвы", "госуслуги" to "Госуслуги", "сбер" to "Сбер", "сбербанк" to "Сбер",
    )

    private val shorteners = setOf(
        "bit.ly", "clck.ru", "tinyurl.com", "goo.su", "vk.cc", "t.ly", "cutt.ly", "is.gd", "u.to", "ya.cc", "rb.gy",
        "shorturl.at", "tiny.cc", "ow.ly", "bitly.com", "qps.ru", "lnkd.in", "s.id", "surl.li", "inlnk.ru", "to.click",
    )

    private val suspiciousTlds = setOf(
        "top", "xyz", "click", "site", "icu", "buzz", "monster", "shop", "work", "live", "fun", "pw", "tk", "ml", "ga", "cf", "gq",
        "online", "store", "rest", "cfd", "sbs", "lol", "quest", "cyou", "bond", "win", "bid", "loan", "zip", "mov", "su", "space", "website", "life",
    )

    private val dangerousExtensions = setOf("apk", "xapk", "apks", "exe", "scr", "bat", "msi", "jar", "vbs", "cmd", "js", "ps1")

    private val lureWords = listOf(
        "login", "signin", "verify", "verification", "secure", "account", "bonus", "prize", "gift", "free", "vyplat", "kompens",
        "oplata", "pay", "bank", "lk-", "-lk", "support", "update", "confirm", "dostavka", "delivery", "promo", "invest", "crypto",
    )

    private val multiLevelSuffixes = setOf(
        "com.ru", "net.ru", "org.ru", "pp.ru", "msk.ru", "spb.ru", "msk.su", "gov.ru", "edu.ru", "co.uk", "com.ua", "com.tr", "com.br", "com.cn", "co.jp",
    )

    private val tlds = "ru|рф|su|com|net|org|info|online|site|top|xyz|club|shop|store|me|io|pro|biz|cc|ly|gl|gy|at|li|link|click|live|app|dev|icu|buzz|space|website|fun|life|world|tk|ml|ga|cf|gq|ws|to|pw|cfd|sbs|bond|win|lol|by|kz|ua|uz"

    private val urlRegex = Regex(
        "(?i)(?:(?:https?|hxxps?)://|www\\.)[^\\s<>\"'«»]+|(?<![\\p{L}\\p{N}.-])(?:[a-z0-9\\u0430-\\u044f\\u0451-]+\\.)+(?:$tlds)(?:/[^\\s<>\"'«»]*)?(?![\\p{L}\\p{N}])",
    )

    fun extract(text: String): List<String> =
        urlRegex.findAll(text).map { it.value.trimEnd('.', ',', '!', '?', ':', ';', ')', ']', '»', '"', '\'') }
            .filter { it.length >= 4 && !it.contains("@") || it.startsWith("http") }
            .distinct()
            .take(10)
            .toList()

    fun registrableDomain(host: String): String {
        val labels = host.trimEnd('.').split('.')
        if (labels.size <= 2) return host
        val lastTwo = labels.takeLast(2).joinToString(".")
        return if (lastTwo in multiLevelSuffixes) labels.takeLast(3).joinToString(".") else lastTwo
    }

    fun isOfficial(host: String): Boolean {
        var h = host
        while (true) {
            if (h in officialSet) return true
            val dot = h.indexOf('.')
            if (dot < 0) return false
            h = h.substring(dot + 1)
        }
    }

    fun brandOfOfficial(host: String): String? =
        officialDomains.entries.firstOrNull { (_, domains) -> domains.any { host == it || host.endsWith(".$it") } }?.key

    fun analyze(rawUrl: String, isKnownBad: (String) -> Boolean = { false }): UrlVerdict {
        val reasons = ArrayList<String>()
        val weights = ArrayList<Double>()
        fun flag(w: Double, why: String) {
            weights += w
            reasons += why
        }

        var url = rawUrl.trim()
        if (url.startsWith("hxxp", ignoreCase = true)) url = "http" + url.substring(4)
        val hasScheme = url.contains("://")
        val withoutScheme = if (hasScheme) url.substringAfter("://") else url
        val authority = withoutScheme.substringBefore('/').substringBefore('?').substringBefore('#')
        val path = withoutScheme.removePrefix(authority)
        if (authority.contains('@')) flag(0.6, "В адресе есть символ «@» — настоящий сайт может быть скрыт")
        var host = authority.substringAfterLast('@').substringBefore(':').lowercase().trimEnd('.')
        val unicodeHost = runCatching { IDN.toUnicode(host) }.getOrDefault(host)
        host = runCatching { IDN.toASCII(host) }.getOrDefault(host).lowercase()
        val regDomain = registrableDomain(host)

        if (isOfficial(host)) {
            return UrlVerdict(rawUrl, host, unicodeHost, regDomain, 0.0, listOf("Официальный сайт: ${brandOfOfficial(host)}"), official = true)
        }

        if (isKnownBad(host) || isKnownBad(regDomain)) flag(0.95, "Сайт есть в базе мошеннических адресов")

        if (Regex("^\\d{1,3}(\\.\\d{1,3}){3}$").matches(host) || host.startsWith("[")) {
            flag(0.6, "Вместо названия сайта указан IP-адрес")
        }

        val labels = host.split('.')
        val tld = labels.lastOrNull().orEmpty()
        val unicodeLabels = unicodeHost.split('.')
        if (host.contains("xn--")) {
            val mixed = unicodeLabels.any { label -> label.any { it in 'a'..'z' } && label.any { Character.UnicodeBlock.of(it) == Character.UnicodeBlock.CYRILLIC } }
            if (mixed) {
                flag(0.85, "В адресе смешаны русские и латинские буквы — сайт маскируется под другой")
            } else if (tld != "xn--p1ai") {
                flag(0.35, "Адрес записан нестандартными символами")
            }
        }

        var brand: String? = null
        val hostTokens = unicodeHost.split('.', '-').filter { it.isNotEmpty() }
        for ((token, b) in brandTokens) {
            val hit = if (token.length >= 5) unicodeHost.contains(token) else token in hostTokens
            if (hit) {
                brand = b
                break
            }
        }
        if (brand != null) {
            flag(0.85, "Сайт выдаёт себя за «$brand», но это не официальный адрес")
        } else {
            val mainLabel = regDomain.substringBefore('.')
            // Short names (vtb, mts...) collide with too many legitimate domains, so only longer ones are compared.
            val lookalike = if (mainLabel.length < 4) null else officialSet.firstOrNull { official ->
                val officialLabel = official.substringBefore('.')
                val d = levenshtein(mainLabel, officialLabel)
                when {
                    officialLabel.length >= 7 -> d in 1..2
                    officialLabel.length >= 5 -> d == 1
                    else -> false
                }
            }
            if (lookalike != null) {
                brand = brandOfOfficial(lookalike)
                flag(0.85, "Адрес похож на официальный $lookalike, но отличается несколькими буквами")
            }
        }

        if (regDomain in shorteners || host in shorteners) flag(0.4, "Короткая ссылка скрывает настоящий адрес сайта")
        if (tld in suspiciousTlds) flag(0.3, "Доменная зона .$tld часто используется мошенниками")

        val ext = path.substringBefore('?').substringBefore('#').substringAfterLast('/').substringAfterLast('.', "").lowercase()
        if (ext in dangerousExtensions) flag(0.92, "Ссылка ведёт на установочный файл (.$ext) — так распространяют вирусы")

        if (url.startsWith("http://", ignoreCase = true)) flag(0.15, "Соединение не защищено (нет https)")
        if (labels.size >= 5) flag(0.2, "Слишком много поддоменов в адресе")
        if (host.count { it == '-' } >= 3) flag(0.15, "В адресе много дефисов")
        if (host.length > 40) flag(0.1, "Очень длинный адрес сайта")
        val lower = (host + path).lowercase()
        val lure = lureWords.firstOrNull { lower.contains(it) }
        if (lure != null && weights.isNotEmpty()) flag(0.2, "В адресе есть слово-приманка «$lure»")

        return UrlVerdict(rawUrl, host, unicodeHost, regDomain, noisyOr(weights), reasons, official = false, impersonatedBrand = brand)
    }

    fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            }
            val t = prev
            prev = cur
            cur = t
        }
        return prev[b.length]
    }
}
