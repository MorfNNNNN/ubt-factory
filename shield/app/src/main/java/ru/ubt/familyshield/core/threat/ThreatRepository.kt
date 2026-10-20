package ru.ubt.familyshield.core.threat

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.ubt.familyshield.core.db.ShieldDatabase
import ru.ubt.familyshield.core.db.ThreatNumberEntity
import ru.ubt.familyshield.core.net.ApiClient
import ru.ubt.familyshield.core.prefs.SettingsStore
import ru.ubt.familyshield.shared.detect.PhoneInfo
import ru.ubt.familyshield.shared.dns.HashedDomainSet
import ru.ubt.familyshield.shared.model.WebCategory
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Local threat intelligence: crowd-reported scam numbers and per-category domain lists. Seed lists
 * ship in assets; deltas come from the relay. Lookups never leave the device.
 */
class ThreatRepository(
    private val context: Context,
    private val db: ShieldDatabase,
    private val api: ApiClient,
    private val settings: SettingsStore,
) {
    private val tag = "Threats"
    private val dir = File(context.filesDir, "lists").apply { mkdirs() }
    private val sets = ConcurrentHashMap<WebCategory, HashedDomainSet>()

    suspend fun lookupNumber(info: PhoneInfo): ThreatNumberEntity? = info.hash?.let { db.threatNumbers().find(it) }

    suspend fun reportNumber(number: String, category: String) = runCatching { api.reportNumber(number, category) }.isSuccess

    private fun textFile(c: WebCategory) = File(dir, "${c.name}.txt")
    private fun binFile(c: WebCategory) = File(dir, "${c.name}.bin")

    private fun seed(c: WebCategory): List<String> = runCatching {
        context.assets.open("lists/${c.name.lowercase()}.txt").bufferedReader().readLines()
    }.getOrDefault(emptyList())

    /** Domain set for a category (seed + server updates), loaded lazily and cached in memory. */
    fun set(c: WebCategory): HashedDomainSet = sets.getOrPut(c) {
        val bin = binFile(c)
        runCatching { if (bin.exists()) HashedDomainSet.fromBytes(bin.readBytes()) else null }.getOrNull()
            ?: HashedDomainSet.of(seed(c))
    }

    fun categoryLists(categories: Set<WebCategory>): Map<WebCategory, HashedDomainSet> = categories.associateWith { set(it) }

    fun isKnownBadDomain(host: String): Boolean =
        set(WebCategory.PHISHING).matches(host) || set(WebCategory.SCAM).matches(host) || set(WebCategory.MALWARE).matches(host)

    suspend fun updateNumbers() {
        var since = settings.current.threatNumbersVersion
        repeat(20) {
            val r = api.threatNumbers(since)
            if (r.numbers.isEmpty() && r.removed.isEmpty()) return
            db.threatNumbers().upsertAll(r.numbers.map { ThreatNumberEntity(it.hash, it.category, it.reports) })
            if (r.removed.isNotEmpty()) db.threatNumbers().delete(r.removed)
            since = r.version
            settings.setThreatNumbersVersion(since)
            if (r.numbers.size < 5000) return
        }
    }

    suspend fun updateDomains(c: WebCategory) = withContext(Dispatchers.IO) {
        val since = settings.current.domainVersions[c.name] ?: 0L
        val added = HashSet<String>()
        val removed = HashSet<String>()
        var cursor: String? = null
        var version = since
        do {
            val page = api.threatDomains(c, since, cursor)
            added += page.domains
            removed += page.removed
            cursor = page.cursor
            version = page.version
        } while (page.hasMore)
        if (added.isEmpty() && removed.isEmpty()) return@withContext
        val current = if (textFile(c).exists()) textFile(c).readLines().toMutableSet() else seed(c).mapNotNull { HashedDomainSet.normalizeDomain(it) }.toMutableSet()
        current.removeAll(removed)
        current.addAll(added)
        textFile(c).writeText(current.joinToString("\n"))
        val set = HashedDomainSet.of(current)
        binFile(c).writeBytes(set.toBytes())
        sets[c] = set
        settings.setDomainVersion(c.name, version)
        Log.i(tag, "${c.name}: ${set.size} domains")
    }

    suspend fun updateAll() {
        runCatching { updateNumbers() }.onFailure { Log.w(tag, "numbers update failed: ${it.message}") }
        for (c in WebCategory.entries) runCatching { updateDomains(c) }.onFailure { Log.w(tag, "${c.name} update failed: ${it.message}") }
    }
}
