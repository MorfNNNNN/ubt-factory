package ru.ubt.familyshield.shared.dns

import ru.ubt.familyshield.shared.model.WebCategory
import ru.ubt.familyshield.shared.model.WebFilterSettings
import java.nio.ByteBuffer

/** Iterates "a.b.example.com", "b.example.com", "example.com" (never the bare TLD). */
fun domainSuffixes(host: String): Sequence<String> = sequence {
    var h = host.trimEnd('.').lowercase()
    while (true) {
        yield(h)
        val dot = h.indexOf('.')
        if (dot < 0) break
        val rest = h.substring(dot + 1)
        if (!rest.contains('.')) break
        h = rest
    }
}

/**
 * Memory-efficient set of domains for large block lists: sorted 64-bit FNV-1a hashes.
 * One million domains take ~8 MB and lookups are a handful of binary searches.
 */
class HashedDomainSet private constructor(private val hashes: LongArray) {
    val size: Int get() = hashes.size

    fun containsExact(domain: String): Boolean = java.util.Arrays.binarySearch(hashes, fnv1a64(domain)) >= 0

    /** True when the host or any parent domain is in the set. */
    fun matches(host: String): Boolean = domainSuffixes(host).any { containsExact(it) }

    fun toBytes(): ByteArray = ByteBuffer.allocate(4 + hashes.size * 8).apply {
        putInt(hashes.size)
        hashes.forEach { putLong(it) }
    }.array()

    companion object {
        val EMPTY = HashedDomainSet(LongArray(0))

        fun of(domains: Iterable<String>): HashedDomainSet {
            val list = domains.mapNotNull { normalizeDomain(it) }.map { fnv1a64(it) }.distinct().toLongArray()
            list.sort()
            return HashedDomainSet(list)
        }

        fun fromBytes(bytes: ByteArray): HashedDomainSet {
            val buf = ByteBuffer.wrap(bytes)
            val n = buf.int
            require(n >= 0 && bytes.size == 4 + n * 8) { "corrupted domain set" }
            val arr = LongArray(n) { buf.long }
            for (i in 1 until n) require(arr[i - 1] <= arr[i]) { "domain set not sorted" }
            return HashedDomainSet(arr)
        }

        fun fnv1a64(s: String): Long {
            var h = -0x340d631b7bdddcdbL
            for (ch in s) {
                h = h xor (ch.code.toLong() and 0xFFFF)
                h *= 0x100000001b3L
            }
            return h
        }

        /** Accepts hosts-file lines ("0.0.0.0 example.com"), adblock-style "||example.com^" and plain domains. */
        fun normalizeDomain(line: String): String? {
            var s = line.substringBefore('#').trim()
            if (s.isEmpty() || s.startsWith("!")) return null
            val parts = s.split(Regex("\\s+"))
            if (parts.size >= 2 && (parts[0] == "0.0.0.0" || parts[0] == "127.0.0.1" || parts[0] == "::")) s = parts[1]
            s = s.removePrefix("||").removeSuffix("^").removePrefix("*.").removePrefix(".").trimEnd('.').lowercase()
            if (s == "localhost" || s.isEmpty() || !s.contains('.') || s.any { it.isWhitespace() || it == '/' }) return null
            return s
        }
    }
}

/** Where search engines expose their enforced "safe" mode via DNS. */
object SafeSearch {
    private val googleTlds = listOf(
        "com", "ru", "by", "kz", "com.ua", "de", "fr", "co.uk", "it", "es", "pl", "com.tr", "nl", "am", "az", "ge", "uz", "kg", "md", "lv", "lt", "ee",
    )
    private val googleHosts: Set<String> = googleTlds.flatMap { listOf("www.google.$it", "google.$it") }.toSet()
    private val youtubeHosts = setOf(
        "www.youtube.com", "m.youtube.com", "youtube.com", "youtubei.googleapis.com", "youtube.googleapis.com", "www.youtube-nocookie.com",
    )
    private val yandexHosts = setOf("yandex.ru", "www.yandex.ru", "ya.ru", "www.ya.ru", "yandex.com", "www.yandex.com", "yandex.by", "yandex.kz")

    /** Returns the hostname whose addresses should be served instead of [host], or null. */
    fun rewriteTarget(host: String, settings: WebFilterSettings): String? {
        val h = host.lowercase().trimEnd('.')
        if (settings.safeSearch) {
            if (h in googleHosts) return "forcesafesearch.google.com"
            if (h == "www.bing.com" || h == "bing.com") return "strict.bing.com"
            if (h == "duckduckgo.com" || h == "www.duckduckgo.com") return "safe.duckduckgo.com"
            // Yandex family DNS already enforces family search; elsewhere point Yandex at its family search frontend.
            if (h in yandexHosts && settings.upstream.name.startsWith("YANDEX_FAMILY").not()) return "familysearch.yandex.ru"
        }
        if (settings.youtubeRestricted && h in youtubeHosts) return "restrictmoderate.youtube.com"
        return null
    }
}

/** Hosts used to bypass filtering through encrypted DNS. Blocking them makes browsers fall back to system DNS. */
object DnsBypass {
    val dohHosts: Set<String> = setOf(
        "dns.google", "dns.google.com", "8888.google", "cloudflare-dns.com", "mozilla.cloudflare-dns.com", "chrome.cloudflare-dns.com",
        "one.one.one.one", "1dot1dot1dot1.cloudflare-dns.com", "security.cloudflare-dns.com", "dns.quad9.net", "dns9.quad9.net",
        "dns10.quad9.net", "dns11.quad9.net", "doh.opendns.com", "doh.familyshield.opendns.com", "dns.adguard.com", "dns.adguard-dns.com",
        "unfiltered.adguard-dns.com", "doh.cleanbrowsing.org", "dns.nextdns.io", "doh.dns.sb", "dns.alidns.com", "doh.pub", "dns.controld.com",
        "freedns.controld.com", "dns.mullvad.net", "doh.mullvad.net", "dns.twnic.tw", "doh.libredns.gr", "common.dot.dns.yandex.net",
        "dns.yandex.ru", "doh.xfinity.com", "dns.brahma.world", "dns0.eu", "zero.dns0.eu",
    )

    /** Firefox disables its built-in DoH when this canary domain does not resolve. */
    const val FIREFOX_CANARY = "use-application-dns.net"

    /** Public resolvers apps may hard-code; the VPN routes these addresses so plain DNS to them is filtered too. */
    val publicResolversV4: List<String> = listOf(
        "8.8.8.8", "8.8.4.4", "1.1.1.1", "1.0.0.1", "9.9.9.9", "149.112.112.112", "208.67.222.222", "208.67.220.220",
        "94.140.14.14", "94.140.15.15", "77.88.8.8", "77.88.8.1", "76.76.2.0", "76.76.10.0", "185.228.168.9",
    )

    fun isBypassHost(host: String): Boolean = domainSuffixes(host).any { it in dohHosts } || host.trimEnd('.') == FIREFOX_CANARY
}

sealed class DnsDecision {
    data object Allow : DnsDecision()
    data class Block(val category: WebCategory?, val reason: String) : DnsDecision()
    data class Rewrite(val target: String) : DnsDecision()
}

/**
 * Decides what to do with a DNS name given the filter settings and the downloaded category lists.
 * Order: explicit allow > explicit block > bypass prevention > allow-list-only > categories > safe search.
 */
class DnsPolicy(
    private val settings: WebFilterSettings,
    private val categoryLists: Map<WebCategory, HashedDomainSet>,
    /** Extra hosts that must always resolve, e.g. the family relay server. */
    private val infrastructure: Set<String> = emptySet(),
) {
    private val allowed = settings.allowedDomains.mapNotNull { HashedDomainSet.normalizeDomain(it) }.toSet()
    private val blocked = settings.blockedDomains.mapNotNull { HashedDomainSet.normalizeDomain(it) }.toSet()

    fun decide(name: String): DnsDecision {
        if (!settings.enabled) return DnsDecision.Allow
        val host = name.lowercase().trimEnd('.')
        val suffixes = domainSuffixes(host).toList()
        if (suffixes.any { it in allowed }) return SafeSearch.rewriteTarget(host, settings)?.let { DnsDecision.Rewrite(it) } ?: DnsDecision.Allow
        if (suffixes.any { it in blocked }) return DnsDecision.Block(null, "Сайт заблокирован родителем")
        if (DnsBypass.isBypassHost(host)) return DnsDecision.Block(WebCategory.PROXY_VPN, "Обход фильтра через зашифрованный DNS")
        if (settings.allowListOnly && !isInfrastructure(host)) return DnsDecision.Block(null, "Разрешены только выбранные сайты")
        for (category in settings.categories) {
            val list = categoryLists[category] ?: continue
            if (list.matches(host)) return DnsDecision.Block(category, "Категория: ${category.name}")
        }
        return SafeSearch.rewriteTarget(host, settings)?.let { DnsDecision.Rewrite(it) } ?: DnsDecision.Allow
    }

    /** Domains the OS and our own app need even in allow-list-only mode. */
    private fun isInfrastructure(host: String): Boolean = (INFRA + infrastructure).any { host == it || host.endsWith(".$it") }

    companion object {
        private val INFRA = setOf(
            "connectivitycheck.gstatic.com", "clients3.google.com", "time.android.com", "pool.ntp.org", "rustore.ru",
            "vkpns.rustore.ru", "gstatic.com", "googleapis.com", "android.clients.google.com",
        )
    }
}
