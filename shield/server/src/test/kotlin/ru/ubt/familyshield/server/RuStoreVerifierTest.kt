package ru.ubt.familyshield.server

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.ubt.familyshield.server.services.RuStoreApiVerifier
import ru.ubt.familyshield.shared.billing.Products
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64

class RuStoreVerifierTest {
    private val rsa = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    private val now = 1_800_000_000_000L
    private var authCalls = 0
    private val seenPaths = mutableListOf<String>()

    private fun client(subscriptionBody: String, invoiceBody: String) = HttpClient(MockEngine { request ->
        val json = headersOf(HttpHeaders.ContentType, "application/json")
        seenPaths += request.url.encodedPath
        when {
            request.url.encodedPath == "/public/auth/" -> {
                authCalls++
                val body = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
                val keyId = body["keyId"]!!.jsonPrimitive.content
                val ts = body["timestamp"]!!.jsonPrimitive.content
                val sig = Base64.getDecoder().decode(body["signature"]!!.jsonPrimitive.content)
                val ok = Signature.getInstance("SHA512withRSA").run {
                    initVerify(rsa.public)
                    update((keyId + ts).toByteArray())
                    verify(sig)
                }
                if (ok) respond("""{"code":"OK","body":{"jwe":"jwe-token","ttl":900}}""", HttpStatusCode.OK, json)
                else respond("""{"code":"ERROR","message":"bad signature"}""", HttpStatusCode.BadRequest, json)
            }
            request.headers["Public-Token"] != "jwe-token" -> respond("{}", HttpStatusCode.Unauthorized, json)
            request.url.encodedPath.contains("/v5/subscription/") -> respond(subscriptionBody, HttpStatusCode.OK, json)
            request.url.encodedPath.contains("/v2/invoices/") -> respond(invoiceBody, HttpStatusCode.OK, json)
            else -> respond("{}", HttpStatusCode.NotFound, json)
        }
    }) { expectSuccess = false }

    private fun verifier(sub: String = "{}", inv: String = "{}") = RuStoreApiVerifier(
        keyId = "42",
        privateKeyBase64 = Base64.getEncoder().encodeToString(rsa.private.encoded),
        packageName = "ru.ubt.familyshield",
        http = client(sub, inv),
        baseUrl = "https://public-api.rustore.ru",
        clock = { now },
    )

    @Test
    fun activeSubscription() = runBlocking {
        val v = verifier(sub = """{"code":"OK","body":{"expiryTimeMillis":"${now + 1000}","paymentState":1,"autoRenewing":true}}""")
        val r = v.verify(Products.PREMIUM_MONTH, "pid", null, sandbox = false)
        assertTrue(r.valid)
        assertEquals(now + 1000, r.expiresAt)
        // JWE is cached between calls.
        v.verify(Products.PREMIUM_MONTH, "pid", null, sandbox = true)
        assertEquals(1, authCalls)
        assertTrue(seenPaths.contains("/public/v5/subscription/ru.ubt.familyshield/shield_premium_month/pid"))
        assertTrue(seenPaths.contains("/public/sandbox/v5/subscription/ru.ubt.familyshield/shield_premium_month/pid"))
    }

    @Test
    fun expiredOrUnpaidSubscription() = runBlocking {
        assertFalse(verifier(sub = """{"code":"OK","body":{"expiryTimeMillis":"${now - 1}","paymentState":1}}""").verify(Products.PREMIUM_YEAR, "p", null, false).valid)
        assertFalse(verifier(sub = """{"code":"OK","body":{"expiryTimeMillis":"${now + 1000}","paymentState":0}}""").verify(Products.PREMIUM_YEAR, "p", null, false).valid)
        assertTrue(verifier(sub = """{"code":"OK","body":{"expiryTimeMillis":"${now + 1000}","paymentState":2}}""").verify(Products.PREMIUM_YEAR, "p", null, false).valid)
    }

    @Test
    fun oneTimeInvoice() = runBlocking {
        val confirmed = """{"code":"OK","body":{"invoiceStatus":"CONFIRMED","purchaseId":"p","order":{"itemCode":"shield_premium_lifetime"}}}"""
        assertTrue(verifier(inv = confirmed).verify(Products.PREMIUM_LIFETIME, "p", "inv1", false).valid)
        assertFalse(verifier(inv = confirmed).verify(Products.PACK_KIDS, "p", "inv1", false).valid)
        assertFalse(verifier(inv = confirmed).verify(Products.PREMIUM_LIFETIME, "other", "inv1", false).valid)
        assertFalse(verifier(inv = confirmed).verify(Products.PREMIUM_LIFETIME, "p", null, false).valid)
        val refunded = confirmed.replace("CONFIRMED", "REFUNDED")
        assertFalse(verifier(inv = refunded).verify(Products.PREMIUM_LIFETIME, "p", "inv1", false).valid)
    }
}
