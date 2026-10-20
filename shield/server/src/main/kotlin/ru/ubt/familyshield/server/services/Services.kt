package ru.ubt.familyshield.server.services

import io.ktor.http.HttpStatusCode
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greater
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.SqlExpressionBuilder.lessEq
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.upsert
import ru.ubt.familyshield.server.db.Counters
import ru.ubt.familyshield.server.db.Db
import ru.ubt.familyshield.server.db.Devices
import ru.ubt.familyshield.server.db.Families
import ru.ubt.familyshield.server.db.Invites
import ru.ubt.familyshield.server.db.Messages
import ru.ubt.familyshield.server.db.NumberReports
import ru.ubt.familyshield.server.db.Purchases
import ru.ubt.familyshield.server.db.ThreatDomains
import ru.ubt.familyshield.server.db.ThreatNumbers
import ru.ubt.familyshield.shared.billing.Entitlement
import ru.ubt.familyshield.shared.billing.EntitlementToken
import ru.ubt.familyshield.shared.billing.Products
import ru.ubt.familyshield.shared.crypto.B64
import ru.ubt.familyshield.shared.crypto.Crockford
import ru.ubt.familyshield.shared.crypto.Rng
import ru.ubt.familyshield.shared.crypto.sha256
import ru.ubt.familyshield.shared.crypto.toHex
import ru.ubt.familyshield.shared.detect.PhoneNumbers
import ru.ubt.familyshield.shared.model.MemberRole
import ru.ubt.familyshield.shared.model.WebCategory
import ru.ubt.familyshield.shared.protocol.AcceptInviteResponse
import ru.ubt.familyshield.shared.protocol.Audience
import ru.ubt.familyshield.shared.protocol.MemberDto
import ru.ubt.familyshield.shared.protocol.MessageDto
import ru.ubt.familyshield.shared.protocol.PostMessageRequest
import ru.ubt.familyshield.shared.protocol.PullResponse
import ru.ubt.familyshield.shared.protocol.ThreatDomainsResponse
import ru.ubt.familyshield.shared.protocol.ThreatNumber
import ru.ubt.familyshield.shared.protocol.ThreatNumbersResponse
import java.security.PrivateKey

class ApiException(val status: HttpStatusCode, val code: String, override val message: String) : Exception(message)

fun badRequest(code: String, message: String): Nothing = throw ApiException(HttpStatusCode.BadRequest, code, message)
fun forbidden(code: String, message: String): Nothing = throw ApiException(HttpStatusCode.Forbidden, code, message)
fun notFound(code: String, message: String): Nothing = throw ApiException(HttpStatusCode.NotFound, code, message)
fun conflict(code: String, message: String): Nothing = throw ApiException(HttpStatusCode.Conflict, code, message)

data class DevicePrincipal(val deviceId: String, val familyId: String?, val role: MemberRole?)

fun hashToken(token: String): String = sha256(token.toByteArray(Charsets.US_ASCII)).toHex()

class DeviceService(private val db: Db, private val clock: () -> Long = System::currentTimeMillis) {
    suspend fun register(appVersion: String): Pair<String, String> {
        val id = Crockford.random(20)
        val token = B64.encode(Rng.bytes(32))
        val now = clock()
        db.tx {
            Devices.insert {
                it[Devices.id] = id
                it[tokenHash] = hashToken(token)
                it[Devices.appVersion] = appVersion.take(32)
                it[createdAt] = now
                it[lastSeenAt] = now
            }
        }
        return id to token
    }

    /** Resolves a bearer token; also refreshes last-seen at most once a minute. */
    suspend fun authenticate(token: String): DevicePrincipal? = db.tx {
        val row = Devices.selectAll().where { Devices.tokenHash eq hashToken(token) }.singleOrNull() ?: return@tx null
        val now = clock()
        if (now - row[Devices.lastSeenAt] > 60_000) {
            Devices.update({ Devices.id eq row[Devices.id] }) { it[lastSeenAt] = now }
        }
        DevicePrincipal(row[Devices.id], row[Devices.familyId], row[Devices.role]?.let { MemberRole.valueOf(it) })
    }

    suspend fun setPushToken(deviceId: String, token: String?) = db.tx {
        Devices.update({ Devices.id eq deviceId }) { it[pushToken] = token?.take(512) }
    }

    /** Deletes everything the server knows about a device (right to erasure). */
    suspend fun delete(deviceId: String) = db.tx {
        Messages.deleteWhere { (recipient eq deviceId) or (sender eq deviceId) }
        NumberReports.deleteWhere { reporter eq deviceId }
        Devices.deleteWhere { id eq deviceId }
    }
}

class FamilyService(private val db: Db, private val billing: BillingService, private val clock: () -> Long = System::currentTimeMillis) {

    suspend fun create(principal: DevicePrincipal): String {
        if (principal.familyId != null) conflict("already_in_family", "Устройство уже состоит в семье")
        val id = Crockford.random(20)
        db.tx {
            Families.insert {
                it[Families.id] = id
                it[createdBy] = principal.deviceId
                it[createdAt] = clock()
            }
            Devices.update({ Devices.id eq principal.deviceId }) {
                it[familyId] = id
                it[role] = MemberRole.GUARDIAN.name
            }
        }
        return id
    }

    private fun Transaction.membersOf(familyId: String): List<MemberDto> =
        Devices.selectAll().where { Devices.familyId eq familyId }.orderBy(Devices.createdAt to SortOrder.ASC).map {
            MemberDto(it[Devices.id], MemberRole.valueOf(it[Devices.role]!!), it[Devices.createdAt], it[Devices.lastSeenAt])
        }

    suspend fun members(principal: DevicePrincipal): List<MemberDto> {
        val familyId = principal.familyId ?: notFound("no_family", "Устройство не состоит в семье")
        return db.tx { membersOf(familyId) }
    }

    suspend fun createInvite(principal: DevicePrincipal, role: MemberRole, wrappedSecrets: String, ttlSeconds: Int, proposedId: String? = null): Pair<String, Long> {
        val familyId = principal.familyId ?: notFound("no_family", "Сначала создайте семью")
        if (principal.role != MemberRole.GUARDIAN) forbidden("not_guardian", "Приглашать может только взрослый")
        if (wrappedSecrets.length > 8192) badRequest("too_large", "Слишком большое приглашение")
        val now = clock()
        val limits = billing.entitlementFor(familyId, principal.deviceId).limits(now)
        db.tx {
            val members = membersOf(familyId)
            val guardians = members.count { it.role == MemberRole.GUARDIAN }
            val protected = members.size - guardians
            if (role == MemberRole.GUARDIAN && guardians >= limits.maxGuardians) {
                forbidden("plan_limit_guardians", "Достигнут лимит взрослых в семье для вашего тарифа")
            }
            if (role != MemberRole.GUARDIAN && protected >= limits.maxProtectedDevices) {
                forbidden("plan_limit_devices", "Достигнут лимит защищаемых устройств для вашего тарифа")
            }
        }
        val id = if (proposedId == null) {
            Crockford.random(10)
        } else {
            Crockford.normalize(proposedId).takeIf { it.length == 10 } ?: badRequest("bad_invite_id", "Некорректный код приглашения")
        }
        val expiresAt = now + ttlSeconds.coerceIn(60, 86_400) * 1000L
        db.tx {
            if (!Invites.selectAll().where { Invites.id eq id }.empty()) conflict("invite_id_taken", "Код уже занят, создайте новый")
            Invites.insert {
                it[Invites.id] = id
                it[Invites.familyId] = familyId
                it[Invites.role] = role.name
                it[Invites.wrappedSecrets] = wrappedSecrets
                it[createdBy] = principal.deviceId
                it[Invites.expiresAt] = expiresAt
            }
        }
        return id to expiresAt
    }

    suspend fun acceptInvite(principal: DevicePrincipal, inviteId: String): AcceptInviteResponse {
        if (principal.familyId != null) conflict("already_in_family", "Устройство уже состоит в семье")
        val normalized = Crockford.normalize(inviteId)
        val now = clock()
        return db.tx {
            val invite = Invites.selectAll().where { Invites.id eq normalized }.forUpdate().singleOrNull()
                ?: notFound("invite_not_found", "Код приглашения не найден")
            if (invite[Invites.usedBy] != null) conflict("invite_used", "Этот код уже использован")
            if (invite[Invites.expiresAt] < now) badRequest("invite_expired", "Срок действия кода истёк, создайте новый")
            val familyId = invite[Invites.familyId]
            val role = MemberRole.valueOf(invite[Invites.role])
            Invites.update({ Invites.id eq normalized }) {
                it[usedBy] = principal.deviceId
                it[usedAt] = now
            }
            Devices.update({ Devices.id eq principal.deviceId }) {
                it[Devices.familyId] = familyId
                it[Devices.role] = role.name
            }
            AcceptInviteResponse(familyId, role, invite[Invites.wrappedSecrets], invite[Invites.createdBy], membersOf(familyId))
        }
    }

    suspend fun removeMember(principal: DevicePrincipal, deviceId: String): Boolean {
        val familyId = principal.familyId ?: notFound("no_family", "Устройство не состоит в семье")
        if (principal.role != MemberRole.GUARDIAN) forbidden("not_guardian", "Удалять участников может только взрослый")
        if (deviceId == principal.deviceId) badRequest("use_leave", "Чтобы выйти из семьи, используйте выход")
        return db.tx {
            val updated = Devices.update({ (Devices.id eq deviceId) and (Devices.familyId eq familyId) }) {
                it[Devices.familyId] = null
                it[role] = null
            }
            Messages.deleteWhere { recipient eq deviceId }
            updated > 0
        }
    }

    suspend fun leave(principal: DevicePrincipal) {
        val familyId = principal.familyId ?: return
        db.tx {
            Devices.update({ Devices.id eq principal.deviceId }) {
                it[Devices.familyId] = null
                it[role] = null
            }
            Messages.deleteWhere { recipient eq principal.deviceId }
            // The last member leaving dissolves the family.
            if (Devices.selectAll().where { Devices.familyId eq familyId }.empty()) {
                Invites.deleteWhere { Invites.familyId eq familyId }
                Families.deleteWhere { id eq familyId }
            }
        }
    }
}

class MessageService(
    private val db: Db,
    private val maxBytes: Int,
    private val push: PushSender,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun post(principal: DevicePrincipal, req: PostMessageRequest): Int {
        val familyId = principal.familyId ?: notFound("no_family", "Устройство не состоит в семье")
        if (req.body.length > maxBytes) badRequest("too_large", "Сообщение слишком большое")
        if (req.id.isBlank() || req.id.length > 64) badRequest("bad_id", "Некорректный идентификатор сообщения")
        val now = clock()
        val recipients = db.tx {
            val family = Devices.selectAll().where { Devices.familyId eq familyId }
                .map { it[Devices.id] to MemberRole.valueOf(it[Devices.role]!!) }
                .filter { it.first != principal.deviceId }
            val targets = when (req.audience) {
                Audience.GUARDIANS -> family.filter { it.second == MemberRole.GUARDIAN }.map { it.first }
                Audience.PROTECTED -> family.filter { it.second != MemberRole.GUARDIAN }.map { it.first }
                Audience.ALL -> family.map { it.first }
                Audience.DEVICES -> family.map { it.first }.filter { it in req.recipients.toSet() }
            }
            for (target in targets) {
                Messages.insertIgnore {
                    it[messageId] = req.id
                    it[Messages.familyId] = familyId
                    it[sender] = principal.deviceId
                    it[recipient] = target
                    it[createdAt] = now
                    it[body] = req.body
                    it[signature] = req.signature
                }
            }
            targets
        }
        if (req.urgent && recipients.isNotEmpty()) push.wake(recipients)
        return recipients.size
    }

    suspend fun pull(principal: DevicePrincipal, after: Long, limit: Int): PullResponse {
        val n = limit.coerceIn(1, 200)
        val rows = db.tx {
            Messages.selectAll().where { (Messages.recipient eq principal.deviceId) and (Messages.seq greater after) }
                .orderBy(Messages.seq to SortOrder.ASC).limit(n + 1).map {
                    MessageDto(it[Messages.seq], it[Messages.messageId], it[Messages.sender], it[Messages.createdAt], it[Messages.body], it[Messages.signature])
                }
        }
        val page = rows.take(n)
        return PullResponse(page, page.lastOrNull()?.seq ?: after, rows.size > n)
    }

    suspend fun ack(principal: DevicePrincipal, upToSeq: Long) = db.tx {
        Messages.deleteWhere { (recipient eq principal.deviceId) and (seq lessEq upToSeq) }
    }

    suspend fun purgeOlderThan(retentionDays: Int): Int {
        val cutoff = clock() - retentionDays * 86_400_000L
        val now = clock()
        return db.tx {
            Invites.deleteWhere { (expiresAt less now - 86_400_000L) }
            Messages.deleteWhere { createdAt less cutoff }
        }
    }
}

class BillingService(
    private val db: Db,
    private val verifier: PurchaseVerifier,
    private val signingKey: PrivateKey,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun verifyAndStore(principal: DevicePrincipal, productId: String, purchaseId: String, invoiceId: String?, sandbox: Boolean): String {
        if (productId !in Products.all) badRequest("unknown_product", "Неизвестный продукт")
        val result = verifier.verify(productId, purchaseId, invoiceId, sandbox)
        if (!result.valid) forbidden("purchase_invalid", "Покупка не подтверждена магазином: ${result.reason}")
        val now = clock()
        db.tx {
            Purchases.upsert {
                it[Purchases.purchaseId] = purchaseId
                it[familyId] = principal.familyId
                it[deviceId] = principal.deviceId
                it[Purchases.productId] = productId
                it[expiresAt] = result.expiresAt
                it[verifiedAt] = now
                it[Purchases.sandbox] = sandbox
                it[status] = result.status
            }
        }
        return token(principal)
    }

    /** Merged entitlement of every valid purchase made by any device of the family (or by the device itself). */
    suspend fun entitlementFor(familyId: String?, deviceId: String): Entitlement {
        val now = clock()
        val rows = db.tx {
            val familyDevices = if (familyId != null) {
                Devices.selectAll().where { Devices.familyId eq familyId }.map { it[Devices.id] }
            } else {
                listOf(deviceId)
            }
            Purchases.selectAll().where { Purchases.deviceId inList familyDevices }.toList()
        }
        return rows.map {
            Entitlement.forProduct(it[Purchases.productId], it[Purchases.expiresAt], now, familyId)
        }.filter { it.isActive(now) }.fold(Entitlement(familyId = familyId, issuedAt = now)) { acc, e -> acc + e }
    }

    suspend fun token(principal: DevicePrincipal): String =
        EntitlementToken.issue(entitlementFor(principal.familyId, principal.deviceId).copy(issuedAt = clock()), signingKey)
}

class ThreatService(private val db: Db, private val reportThreshold: Int, private val clock: () -> Long = System::currentTimeMillis) {
    val categories = setOf("мошенники", "банк-фейк", "госорганы-фейк", "спам", "коллекторы", "робот", "опрос", "другое")

    private fun Transaction.nextVersion(counter: String): Long {
        val current = Counters.selectAll().where { Counters.name eq counter }.singleOrNull()?.get(Counters.value) ?: 0L
        Counters.upsert {
            it[name] = counter
            it[value] = current + 1
        }
        return current + 1
    }

    suspend fun report(principal: DevicePrincipal, number: String, category: String) {
        val info = PhoneNumbers.parse(number)
        val hash = info.hash ?: badRequest("bad_number", "Некорректный номер")
        val cat = if (category in categories) category else "другое"
        db.tx {
            val inserted = NumberReports.insertIgnore {
                it[reporter] = principal.deviceId
                it[NumberReports.hash] = hash
                it[NumberReports.category] = cat
                it[createdAt] = clock()
            }.insertedCount
            if (inserted == 0) return@tx
            val reports = NumberReports.selectAll().where { NumberReports.hash eq hash }.count().toInt()
            val topCategory = NumberReports.selectAll().where { NumberReports.hash eq hash }
                .groupBy { it[NumberReports.category] }.maxByOrNull { it.value.size }?.key ?: cat
            val version = nextVersion("numbers")
            ThreatNumbers.upsert {
                it[ThreatNumbers.hash] = hash
                it[ThreatNumbers.category] = topCategory
                it[ThreatNumbers.reports] = reports
                it[ThreatNumbers.version] = version
                it[published] = reports >= reportThreshold
            }
        }
    }

    suspend fun numbersSince(since: Long, limit: Int = 5000): ThreatNumbersResponse = db.tx {
        val rows = ThreatNumbers.selectAll().where { (ThreatNumbers.version greater since) and (ThreatNumbers.published eq true) }
            .orderBy(ThreatNumbers.version to SortOrder.ASC).limit(limit).toList()
        ThreatNumbersResponse(
            version = rows.lastOrNull()?.get(ThreatNumbers.version) ?: since,
            numbers = rows.map { ThreatNumber(it[ThreatNumbers.hash], it[ThreatNumbers.category], it[ThreatNumbers.reports]) },
        )
    }

    suspend fun importDomains(category: WebCategory, domains: Collection<String>, replace: Boolean): Int = db.tx {
        val version = nextVersion("domains")
        val incoming = domains.toSet()
        if (replace) {
            val existing = ThreatDomains.selectAll().where { (ThreatDomains.category eq category.name) and (ThreatDomains.removed eq false) }
                .map { it[ThreatDomains.domain] }
            for (gone in existing.filter { it !in incoming }) {
                ThreatDomains.update({ (ThreatDomains.domain eq gone) and (ThreatDomains.category eq category.name) }) {
                    it[removed] = true
                    it[ThreatDomains.version] = version
                }
            }
            val fresh = incoming.filter { it !in existing.toSet() }
            fresh.forEach { d -> upsertDomain(d, category, version) }
            fresh.size
        } else {
            incoming.forEach { d -> upsertDomain(d, category, version) }
            incoming.size
        }
    }

    private fun upsertDomain(domain: String, category: WebCategory, version: Long) {
        ThreatDomains.upsert {
            it[ThreatDomains.domain] = domain
            it[ThreatDomains.category] = category.name
            it[ThreatDomains.version] = version
            it[removed] = false
        }
    }

    /**
     * Changes after [since]. Bulk imports share one version, so pages are ordered by (version, domain)
     * and continued with an opaque cursor "version:domain".
     */
    suspend fun domainsSince(category: WebCategory, since: Long, cursor: String?, limit: Int = 20_000): ThreatDomainsResponse = db.tx {
        val cursorVersion = cursor?.substringBefore(':')?.toLongOrNull()
        val cursorDomain = cursor?.substringAfter(':', "")
        val base = (ThreatDomains.category eq category.name) and (ThreatDomains.version greater since)
        val condition = if (cursorVersion != null && cursorDomain != null) {
            base and ((ThreatDomains.version greater cursorVersion) or ((ThreatDomains.version eq cursorVersion) and (ThreatDomains.domain greater cursorDomain)))
        } else {
            base
        }
        val rows = ThreatDomains.selectAll().where { condition }
            .orderBy(ThreatDomains.version to SortOrder.ASC, ThreatDomains.domain to SortOrder.ASC)
            .limit(limit + 1).toList()
        val page = rows.take(limit)
        val hasMore = rows.size > limit
        val last = page.lastOrNull()
        ThreatDomainsResponse(
            version = if (hasMore) since else (last?.get(ThreatDomains.version) ?: since),
            category = category.name,
            domains = page.filter { !it[ThreatDomains.removed] }.map { it[ThreatDomains.domain] },
            removed = page.filter { it[ThreatDomains.removed] }.map { it[ThreatDomains.domain] },
            cursor = if (hasMore && last != null) "${last[ThreatDomains.version]}:${last[ThreatDomains.domain]}" else null,
            hasMore = hasMore,
        )
    }
}
