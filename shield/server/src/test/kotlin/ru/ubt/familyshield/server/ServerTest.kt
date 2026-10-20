package ru.ubt.familyshield.server

import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.KSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.serializer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.ubt.familyshield.server.db.Db
import ru.ubt.familyshield.server.services.NoopPush
import ru.ubt.familyshield.server.services.PurchaseVerifier
import ru.ubt.familyshield.server.services.TrustingVerifier
import ru.ubt.familyshield.server.services.VerificationResult
import ru.ubt.familyshield.shared.ShieldJson
import ru.ubt.familyshield.shared.billing.EntitlementToken
import ru.ubt.familyshield.shared.billing.Feature
import ru.ubt.familyshield.shared.billing.Products
import ru.ubt.familyshield.shared.billing.Tier
import ru.ubt.familyshield.shared.crypto.Aead
import ru.ubt.familyshield.shared.crypto.B64
import ru.ubt.familyshield.shared.crypto.Crockford
import ru.ubt.familyshield.shared.crypto.EnvelopeCodec
import ru.ubt.familyshield.shared.crypto.FamilySecrets
import ru.ubt.familyshield.shared.crypto.PairingCode
import ru.ubt.familyshield.shared.crypto.Signatures
import ru.ubt.familyshield.shared.model.CheckIn
import ru.ubt.familyshield.shared.model.MemberRole
import ru.ubt.familyshield.shared.model.WebCategory
import ru.ubt.familyshield.shared.protocol.AcceptInviteRequest
import ru.ubt.familyshield.shared.protocol.AcceptInviteResponse
import ru.ubt.familyshield.shared.protocol.AckRequest
import ru.ubt.familyshield.shared.protocol.ApiError
import ru.ubt.familyshield.shared.protocol.ApiPaths
import ru.ubt.familyshield.shared.protocol.Audience
import ru.ubt.familyshield.shared.protocol.CreateFamilyResponse
import ru.ubt.familyshield.shared.protocol.CreateInviteRequest
import ru.ubt.familyshield.shared.protocol.EntitlementResponse
import ru.ubt.familyshield.shared.protocol.FamilyPayload
import ru.ubt.familyshield.shared.protocol.InnerMessage
import ru.ubt.familyshield.shared.protocol.InviteResponse
import ru.ubt.familyshield.shared.protocol.MembersResponse
import ru.ubt.familyshield.shared.protocol.PostMessageRequest
import ru.ubt.familyshield.shared.protocol.PostMessageResponse
import ru.ubt.familyshield.shared.protocol.PullResponse
import ru.ubt.familyshield.shared.protocol.RegisterRequest
import ru.ubt.familyshield.shared.protocol.RegisterResponse
import ru.ubt.familyshield.shared.protocol.ReportNumberRequest
import ru.ubt.familyshield.shared.protocol.ThreatDomainsResponse
import ru.ubt.familyshield.shared.protocol.ThreatNumbersResponse
import ru.ubt.familyshield.shared.protocol.VerifyPurchaseRequest
import java.util.UUID

class ServerTest {
    private val keys = Signatures.generateKeyPair()
    private var now = 1_800_000_000_000L
    private val db = Db("jdbc:h2:mem:${UUID.randomUUID()};DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE", "", "")
    private val config = ServerConfig(entitlementKeys = keys)

    @After
    fun close() = db.close()

    private fun app(verifier: PurchaseVerifier = TrustingVerifier { now }, block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        val services = ShieldServices(config, db, verifier, NoopPush) { now }
        application { shieldModule(services) }
        block()
    }

    private inline fun <reified T> HttpRequestBuilder.json(value: T) {
        contentType(ContentType.Application.Json)
        setBody(ShieldJson.encodeToString(value))
    }

    private suspend inline fun <reified T> HttpResponse.read(): T {
        assertTrue("HTTP $status: ${bodyAsText()}", status.value in 200..299)
        return ShieldJson.decodeFromString(serializer<T>() as KSerializer<T>, bodyAsText())
    }

    private suspend fun HttpResponse.error(): ApiError = ShieldJson.decodeFromString(ApiError.serializer(), bodyAsText())

    private suspend fun HttpClient.register(): RegisterResponse =
        post(ApiPaths.REGISTER) { json(RegisterRequest(appVersion = "1.0.0")) }.read()

    private data class Family(val guardian: RegisterResponse, val familyId: String, val key: ByteArray)

    private suspend fun HttpClient.newFamily(): Family {
        val g = register()
        val fam = post(ApiPaths.FAMILIES) { bearerAuth(g.deviceToken) }.read<CreateFamilyResponse>()
        return Family(g, fam.familyId, Aead.newKey())
    }

    private suspend fun HttpClient.join(f: Family, role: MemberRole): Pair<RegisterResponse, AcceptInviteResponse> {
        // Mimics the app: the guardian picks the invite id, wraps the family secrets with the pairing code,
        // and only the wrapped blob reaches the server.
        val code = PairingCode(Crockford.random(10), PairingCode.newSecret())
        val secrets = FamilySecrets(f.familyId, 1, B64.encode(f.key), f.guardian.deviceId, "pub", role)
        val invite = post(ApiPaths.INVITES) {
            bearerAuth(f.guardian.deviceToken)
            json(CreateInviteRequest(inviteId = code.inviteId, role = role, wrappedSecrets = code.wrap(secrets)))
        }.read<InviteResponse>()
        assertEquals(code.inviteId, invite.inviteId)
        val device = register()
        val typed = PairingCode.parse(code.displayCode())!!
        val accepted = post(ApiPaths.ACCEPT_INVITE) {
            bearerAuth(device.deviceToken)
            json(AcceptInviteRequest(typed.inviteId))
        }.read<AcceptInviteResponse>()
        assertEquals(secrets, typed.unwrap(accepted.wrappedSecrets))
        return device to accepted
    }

    @Test
    fun healthAndAuthRequired() = app {
        assertEquals(HttpStatusCode.OK, client.get(ApiPaths.HEALTH).status)
        val r = client.get(ApiPaths.MEMBERS)
        assertEquals(HttpStatusCode.Unauthorized, r.status)
        assertEquals(HttpStatusCode.Unauthorized, client.get(ApiPaths.MEMBERS) { bearerAuth("forged") }.status)
    }

    @Test
    fun pairingAndEndToEndMessaging() = app {
        val c = client
        val f = c.newFamily()
        val (child, accepted) = c.join(f, MemberRole.CHILD)
        assertEquals(f.familyId, accepted.familyId)
        assertEquals(MemberRole.CHILD, accepted.role)
        assertEquals(f.guardian.deviceId, accepted.invitedBy)
        assertEquals(2, accepted.members.size)

        // Child sends an encrypted check-in to guardians.
        val msgId = UUID.randomUUID().toString()
        val body = EnvelopeCodec.seal(InnerMessage(now, FamilyPayload.CheckInDone(CheckIn("c", now))), f.key, 1, f.familyId, msgId, child.deviceId)
        val posted = c.post(ApiPaths.MESSAGES) {
            bearerAuth(child.deviceToken)
            json(PostMessageRequest(msgId, Audience.GUARDIANS, body = body))
        }.read<PostMessageResponse>()
        assertEquals(1, posted.deliveredTo)
        // Idempotent re-post.
        c.post(ApiPaths.MESSAGES) {
            bearerAuth(child.deviceToken)
            json(PostMessageRequest(msgId, Audience.GUARDIANS, body = body))
        }.read<PostMessageResponse>()

        val pulled = c.get("${ApiPaths.MESSAGES}?after=0") { bearerAuth(f.guardian.deviceToken) }.read<PullResponse>()
        assertEquals(1, pulled.messages.size)
        val m = pulled.messages.single()
        assertEquals(child.deviceId, m.senderDeviceId)
        val opened = EnvelopeCodec.open(m.body, { f.key }, f.familyId, m.id, m.senderDeviceId)
        assertTrue(opened.payload is FamilyPayload.CheckInDone)

        // The child does not receive its own message; guardian ack clears the mailbox.
        assertTrue(c.get(ApiPaths.MESSAGES) { bearerAuth(child.deviceToken) }.read<PullResponse>().messages.isEmpty())
        assertEquals(HttpStatusCode.NoContent, c.post(ApiPaths.ACK) { bearerAuth(f.guardian.deviceToken); json(AckRequest(pulled.nextCursor)) }.status)
        assertTrue(c.get(ApiPaths.MESSAGES) { bearerAuth(f.guardian.deviceToken) }.read<PullResponse>().messages.isEmpty())
    }

    @Test
    fun inviteRulesAndPlanLimits() = app {
        val c = client
        val f = c.newFamily()
        val (child, _) = c.join(f, MemberRole.CHILD)

        // Free plan: one protected device only.
        val limited = c.post(ApiPaths.INVITES) { bearerAuth(f.guardian.deviceToken); json(CreateInviteRequest(role = MemberRole.ELDER, wrappedSecrets = "x")) }
        assertEquals(HttpStatusCode.Forbidden, limited.status)
        assertEquals("plan_limit_devices", limited.error().code)

        // Protected devices cannot invite.
        assertEquals(HttpStatusCode.Forbidden, c.post(ApiPaths.INVITES) { bearerAuth(child.deviceToken); json(CreateInviteRequest(role = MemberRole.CHILD, wrappedSecrets = "x")) }.status)

        // After a premium purchase the limit grows.
        c.post(ApiPaths.VERIFY_PURCHASE) {
            bearerAuth(f.guardian.deviceToken)
            json(VerifyPurchaseRequest(Products.PREMIUM_MONTH, "p1"))
        }.read<EntitlementResponse>()
        val invite = c.post(ApiPaths.INVITES) { bearerAuth(f.guardian.deviceToken); json(CreateInviteRequest(role = MemberRole.ELDER, wrappedSecrets = "wrapped")) }.read<InviteResponse>()

        // Invites are single-use and expire.
        val elder = c.register()
        val acc = c.post(ApiPaths.ACCEPT_INVITE) { bearerAuth(elder.deviceToken); json(AcceptInviteRequest(invite.inviteId.lowercase())) }.read<AcceptInviteResponse>()
        assertEquals("wrapped", acc.wrappedSecrets)
        val other = c.register()
        assertEquals(HttpStatusCode.Conflict, c.post(ApiPaths.ACCEPT_INVITE) { bearerAuth(other.deviceToken); json(AcceptInviteRequest(invite.inviteId)) }.status)
        val invite2 = c.post(ApiPaths.INVITES) { bearerAuth(f.guardian.deviceToken); json(CreateInviteRequest(role = MemberRole.ELDER, wrappedSecrets = "w", ttlSeconds = 60)) }.read<InviteResponse>()
        now += 61_000
        val expired = c.post(ApiPaths.ACCEPT_INVITE) { bearerAuth(other.deviceToken); json(AcceptInviteRequest(invite2.inviteId)) }
        assertEquals("invite_expired", expired.error().code)
        assertEquals(HttpStatusCode.NotFound, c.post(ApiPaths.ACCEPT_INVITE) { bearerAuth(other.deviceToken); json(AcceptInviteRequest(Crockford.random(10))) }.status)
    }

    @Test
    fun proposedInviteIdCollisionIsRejected() = app {
        val c = client
        val f = c.newFamily()
        c.post(ApiPaths.VERIFY_PURCHASE) { bearerAuth(f.guardian.deviceToken); json(VerifyPurchaseRequest(Products.PREMIUM_MONTH, "px")) }.read<EntitlementResponse>()
        val id = Crockford.random(10)
        c.post(ApiPaths.INVITES) { bearerAuth(f.guardian.deviceToken); json(CreateInviteRequest(id, MemberRole.CHILD, "w")) }.read<InviteResponse>()
        val again = c.post(ApiPaths.INVITES) { bearerAuth(f.guardian.deviceToken); json(CreateInviteRequest(id, MemberRole.CHILD, "w")) }
        assertEquals(HttpStatusCode.Conflict, again.status)
        assertEquals(HttpStatusCode.BadRequest, c.post(ApiPaths.INVITES) { bearerAuth(f.guardian.deviceToken); json(CreateInviteRequest("short", MemberRole.CHILD, "w")) }.status)
    }

    @Test
    fun memberRemovalAndLeaving() = app {
        val c = client
        val f = c.newFamily()
        val (child, _) = c.join(f, MemberRole.CHILD)
        assertEquals(HttpStatusCode.Forbidden, c.delete("${ApiPaths.MEMBERS}/${f.guardian.deviceId}") { bearerAuth(child.deviceToken) }.status)
        c.delete("${ApiPaths.MEMBERS}/${child.deviceId}") { bearerAuth(f.guardian.deviceToken) }.read<ru.ubt.familyshield.shared.protocol.MemberRemovalResponse>()
        val members = c.get(ApiPaths.MEMBERS) { bearerAuth(f.guardian.deviceToken) }.read<MembersResponse>()
        assertEquals(1, members.members.size)
        // Removed device can no longer post into the family.
        val r = c.post(ApiPaths.MESSAGES) { bearerAuth(child.deviceToken); json(PostMessageRequest("m", Audience.ALL, body = "x")) }
        assertEquals(HttpStatusCode.NotFound, r.status)
        assertEquals(HttpStatusCode.NoContent, c.post(ApiPaths.LEAVE_FAMILY) { bearerAuth(f.guardian.deviceToken) }.status)
        assertEquals(HttpStatusCode.NoContent, c.delete(ApiPaths.DELETE_DEVICE) { bearerAuth(f.guardian.deviceToken) }.status)
        assertEquals(HttpStatusCode.Unauthorized, c.get(ApiPaths.ENTITLEMENT) { bearerAuth(f.guardian.deviceToken) }.status)
    }

    @Test
    fun entitlementIsSharedWithFamilyAndSigned() = app {
        val c = client
        val f = c.newFamily()
        val (child, _) = c.join(f, MemberRole.CHILD)
        val free = EntitlementToken.verify(c.get(ApiPaths.ENTITLEMENT) { bearerAuth(child.deviceToken) }.read<EntitlementResponse>().token, keys.public)!!
        assertEquals(Tier.FREE, free.tier)
        c.post(ApiPaths.VERIFY_PURCHASE) { bearerAuth(f.guardian.deviceToken); json(VerifyPurchaseRequest(Products.FAMILY_MAX_YEAR, "p2")) }.read<EntitlementResponse>()
        val token = c.get(ApiPaths.ENTITLEMENT) { bearerAuth(child.deviceToken) }.read<EntitlementResponse>().token
        val e = EntitlementToken.verify(token, keys.public)!!
        assertEquals(Tier.FAMILY_MAX, e.tier)
        assertTrue(e.has(Feature.REPORT_EXPORT, now))
        assertEquals(f.familyId, e.familyId)
        assertNull(EntitlementToken.verify(token, Signatures.generateKeyPair().public))
        val unknown = c.post(ApiPaths.VERIFY_PURCHASE) { bearerAuth(f.guardian.deviceToken); json(VerifyPurchaseRequest("hack", "p3")) }
        assertEquals(HttpStatusCode.BadRequest, unknown.status)
    }

    @Test
    fun rejectedPurchaseGrantsNothing() = app(verifier = { _, _, _, _ -> VerificationResult(false, null, "REJECTED", "test") }) {
        val c = client
        val f = c.newFamily()
        val r = c.post(ApiPaths.VERIFY_PURCHASE) { bearerAuth(f.guardian.deviceToken); json(VerifyPurchaseRequest(Products.PREMIUM_MONTH, "p")) }
        assertEquals(HttpStatusCode.Forbidden, r.status)
        val e = EntitlementToken.verify(c.get(ApiPaths.ENTITLEMENT) { bearerAuth(f.guardian.deviceToken) }.read<EntitlementResponse>().token, keys.public)!!
        assertEquals(Tier.FREE, e.tier)
    }

    @Test
    fun crowdNumberReportsPublishAfterThreshold() = app {
        val c = client
        val reporters = List(3) { c.register() }
        reporters.take(2).forEach { r ->
            c.post(ApiPaths.REPORT_NUMBER) { bearerAuth(r.deviceToken); json(ReportNumberRequest("+7 999 111-22-33", "мошенники")) }
            // Duplicate reports from the same device do not count.
            c.post(ApiPaths.REPORT_NUMBER) { bearerAuth(r.deviceToken); json(ReportNumberRequest("89991112233", "мошенники")) }
        }
        assertTrue(c.get("${ApiPaths.THREAT_NUMBERS}?since=0") { bearerAuth(reporters[0].deviceToken) }.read<ThreatNumbersResponse>().numbers.isEmpty())
        c.post(ApiPaths.REPORT_NUMBER) { bearerAuth(reporters[2].deviceToken); json(ReportNumberRequest("+79991112233", "банк-фейк")) }
        val feed = c.get("${ApiPaths.THREAT_NUMBERS}?since=0") { bearerAuth(reporters[0].deviceToken) }.read<ThreatNumbersResponse>()
        val n = feed.numbers.single()
        assertEquals(ru.ubt.familyshield.shared.detect.PhoneNumbers.parse("+79991112233").hash, n.hash)
        assertEquals("мошенники", n.category)
        assertEquals(3, n.reports)
        assertTrue(c.get("${ApiPaths.THREAT_NUMBERS}?since=${feed.version}") { bearerAuth(reporters[0].deviceToken) }.read<ThreatNumbersResponse>().numbers.isEmpty())
    }

    @Test
    fun domainFeedPaginatesLargeImports() = app {
        val c = client
        val d = c.register()
        val services = ShieldServices(config, db, TrustingVerifier(), NoopPush) { now }
        runBlocking { services.threats.importDomains(WebCategory.GAMBLING, (1..25).map { "casino$it.example" }, replace = true) }
        val all = mutableSetOf<String>()
        var cursor: String? = null
        var version = 0L
        var pages = 0
        do {
            val page = runBlocking { services.threats.domainsSince(WebCategory.GAMBLING, 0, cursor, limit = 10) }
            all += page.domains
            cursor = page.cursor
            version = page.version
            pages++
        } while (page.hasMore)
        assertEquals(25, all.size)
        assertEquals(3, pages)
        // Replacing the list marks missing domains as removed.
        runBlocking { services.threats.importDomains(WebCategory.GAMBLING, (1..20).map { "casino$it.example" }, replace = true) }
        val delta = c.get("${ApiPaths.THREAT_DOMAINS}?category=GAMBLING&since=$version") { bearerAuth(d.deviceToken) }.read<ThreatDomainsResponse>()
        assertEquals(5, delta.removed.size)
        assertFalse(delta.hasMore)
        assertEquals(HttpStatusCode.BadRequest, c.get("${ApiPaths.THREAT_DOMAINS}?category=NOPE") { bearerAuth(d.deviceToken) }.status)
    }

    @Test
    fun pushTokenAndValidation() = app {
        val c = client
        val d = c.register()
        assertEquals(HttpStatusCode.NoContent, c.put(ApiPaths.PUSH_TOKEN) { bearerAuth(d.deviceToken); json(ru.ubt.familyshield.shared.protocol.PushTokenRequest("tok")) }.status)
        val bad = c.post(ApiPaths.FAMILIES) { bearerAuth(d.deviceToken); contentType(ContentType.Application.Json); setBody("{") }
        assertTrue(bad.status == HttpStatusCode.OK || bad.status == HttpStatusCode.BadRequest)
        val f = c.post(ApiPaths.FAMILIES) { bearerAuth(d.deviceToken) }
        assertEquals(HttpStatusCode.Conflict, f.status)
        val garbage = c.post(ApiPaths.MESSAGES) { bearerAuth(d.deviceToken); contentType(ContentType.Application.Json); setBody("{\"nope\":1}") }
        assertEquals(HttpStatusCode.BadRequest, garbage.status)
    }

    @Test
    fun pairingSecretsStayOpaqueToServer() {
        // The wrapped secrets are just bytes for the server; only the code holder can unwrap them.
        val code = PairingCode(Crockford.random(10), PairingCode.newSecret())
        val secrets = FamilySecrets("fam", 1, B64.encode(Aead.newKey()), "g", "pub", MemberRole.ELDER)
        val wrapped = code.wrap(secrets)
        assertFalse(wrapped.contains(secrets.familyKey))
        assertEquals(secrets, code.unwrap(wrapped))
    }
}
