package ru.ubt.familyshield.server.services

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import org.slf4j.LoggerFactory
import ru.ubt.familyshield.shared.dns.HashedDomainSet
import ru.ubt.familyshield.shared.model.WebCategory
import java.io.File

/**
 * Imports domain block lists (hosts files, adblock-style or plain lists). Each configured feed fully
 * replaces its category, so domains removed upstream are also removed on devices.
 */
class FeedImporter(
    private val threats: ThreatService,
    private val http: HttpClient = RuStoreApiVerifier.defaultHttpClient(),
) {
    private val log = LoggerFactory.getLogger(FeedImporter::class.java)

    fun parse(text: String): Set<String> = text.lineSequence().mapNotNull { HashedDomainSet.normalizeDomain(it) }.toSet()

    suspend fun importAll(feeds: Map<String, String>) {
        for ((categoryName, url) in feeds) {
            val category = runCatching { WebCategory.valueOf(categoryName) }.getOrNull()
            if (category == null) {
                log.warn("Unknown feed category {}", categoryName)
                continue
            }
            runCatching {
                val response = http.get(url)
                if (!response.status.isSuccess()) error("HTTP ${response.status}")
                val domains = parse(response.bodyAsText())
                if (domains.size < 10) error("feed looks empty (${domains.size} entries), keeping the previous list")
                val added = threats.importDomains(category, domains, replace = true)
                log.info("Feed {}: {} domains ({} new)", category, domains.size, added)
            }.onFailure { log.warn("Feed {} failed: {}", category, it.message) }
        }
    }

    suspend fun importFile(category: WebCategory, file: File, replace: Boolean): Int =
        threats.importDomains(category, parse(file.readText()), replace)
}
