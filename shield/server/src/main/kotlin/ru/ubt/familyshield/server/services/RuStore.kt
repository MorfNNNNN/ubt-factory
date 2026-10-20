package ru.ubt.familyshield.server.services

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.slf4j.LoggerFactory
import ru.ubt.familyshield.server.db.Db
import ru.ubt.familyshield.server.db.Devices
import ru.ubt.familyshield.shared.billing.Products
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Base64

data class VerificationResult(val valid: Boolean, val expiresAt: Long?, val status: String, val reason: String = "")

fun interface PurchaseVerifier {
    suspend fun verify(productId: String, purchaseId: String, invoiceId: String?, sandbox: Boolean): VerificationResult
}

/** Development-only verifier. Production deployments must configure RuStore API keys. */
class TrustingVerifier(private val clock: () -> Long = System::currentTimeMillis) : PurchaseVerifier {
    override suspend fun verify(productId: String, purchaseId: String, invoiceId: String?, sandbox: Boolean): VerificationResult {
        val month = 31L * 86_400_000L
        val expires = when (productId) {
            Products.PREMIUM_YEAR, Products.FAMILY_MAX_YEAR -> clock() + 12 * month
            in Products.subscriptions -> clock() + month
            else -> null
        }
        return VerificationResult(true, expires, "UNVERIFIED_DEV")
    }
}

class RejectingVerifier : PurchaseVerifier {
    override suspend fun verify(productId: String, purchaseId: String, invoiceId: String?, sandbox: Boolean) =
        VerificationResult(false, null, "DISABLED", "проверка покупок не настроена на сервере")
}

private val lenientJson = Json { ignoreUnknownKeys = true }

/**
 * RuStore Public API client: JWE auth (SHA512withRSA over keyId+timestamp), subscription status
 * (V5, by purchaseId) and one-time purchase invoices (V2, by invoiceId).
 */
class RuStoreApiVerifier(
    private val keyId: String,
    privateKeyBase64: String,
    private val packageName: String,
    private val http: HttpClient = defaultHttpClient(),
    private val baseUrl: String = "https://public-api.rustore.ru",
    private val clock: () -> Long = System::currentTimeMillis,
) : PurchaseVerifier {
    private val log = LoggerFactory.getLogger(RuStoreApiVerifier::class.java)
    private val privateKey = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(Base64.getMimeDecoder().decode(privateKeyBase64)))
    private val mutex = Mutex()
    private var jwe: String? = null
    private var jweExpiresAt = 0L

    private suspend fun token(): String = mutex.withLock {
        val now = clock()
        jwe?.takeIf { now < jweExpiresAt }?.let { return it }
        val timestamp = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        val signature = Signature.getInstance("SHA512withRSA").run {
            initSign(privateKey)
            update((keyId + timestamp).toByteArray(Charsets.UTF_8))
            Base64.getEncoder().encodeToString(sign())
        }
        val body = buildJsonObject {
            put("keyId", keyId)
            put("timestamp", timestamp)
            put("signature", signature)
        }
        val response = http.post("$baseUrl/public/auth/") {
            contentType(ContentType.Application.Json)
            setBody(body.toString())
        }
        val json = lenientJson.parseToJsonElement(response.bodyAsText()).jsonObject
        val payload = json["body"]?.jsonObject ?: error("RuStore auth failed: ${json["message"]}")
        val token = payload["jwe"]!!.jsonPrimitive.content
        val ttl = payload["ttl"]?.jsonPrimitive?.longOrNull ?: 900
        jwe = token
        jweExpiresAt = now + (ttl - 60).coerceAtLeast(60) * 1000
        token
    }

    private suspend fun getJson(path: String): JsonObject {
        val response = http.get(baseUrl + path) { header("Public-Token", token()) }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) {
            log.warn("RuStore API {} -> {}", path.substringBefore('/', path), response.status)
            return JsonObject(mapOf())
        }
        return lenientJson.parseToJsonElement(text).jsonObject
    }

    override suspend fun verify(productId: String, purchaseId: String, invoiceId: String?, sandbox: Boolean): VerificationResult {
        val prefix = if (sandbox) "/public/sandbox" else "/public"
        return if (productId in Products.subscriptions) {
            val json = getJson("$prefix/v5/subscription/$packageName/$productId/$purchaseId")
            val body = json["body"]?.jsonObject ?: return VerificationResult(false, null, "NOT_FOUND", "подписка не найдена")
            val expiry = body["expiryTimeMillis"]?.jsonPrimitive?.content?.toLongOrNull()
            val paymentState = body["paymentState"]?.jsonPrimitive?.intOrNull
            val autoRenewing = body["autoRenewing"]?.jsonPrimitive?.booleanOrNull
            val paid = paymentState == 1 || paymentState == 2
            val active = paid && expiry != null && expiry > clock()
            VerificationResult(active, expiry, if (active) "ACTIVE" else "INACTIVE", if (active) "" else "подписка не активна (autoRenewing=$autoRenewing)")
        } else {
            if (invoiceId.isNullOrBlank()) return VerificationResult(false, null, "NO_INVOICE", "не передан invoiceId")
            val json = getJson("$prefix/v2/invoices/$invoiceId")
            val body = json["body"]?.jsonObject ?: return VerificationResult(false, null, "NOT_FOUND", "платёж не найден")
            val status = body["invoiceStatus"]?.jsonPrimitive?.content?.uppercase().orEmpty()
            val itemCode = body["order"]?.jsonObject?.get("itemCode")?.jsonPrimitive?.content
            val purchase = body["purchaseId"]?.jsonPrimitive?.content
            val ok = status == "CONFIRMED" && itemCode == productId && (purchase == null || purchase == purchaseId)
            VerificationResult(ok, null, status, if (ok) "" else "статус $status, товар $itemCode")
        }
    }

    companion object {
        fun defaultHttpClient() = HttpClient(CIO) {
            install(HttpTimeout) {
                requestTimeoutMillis = 15_000
                connectTimeoutMillis = 10_000
            }
            expectSuccess = false
        }
    }
}

fun interface PushSender {
    /** Sends a data-only "sync" push so recipients fetch their mailbox. Never carries content. */
    suspend fun wake(deviceIds: List<String>)
}

object NoopPush : PushSender {
    override suspend fun wake(deviceIds: List<String>) = Unit
}

class RuStorePushSender(
    private val db: Db,
    private val projectId: String,
    private val serviceToken: String,
    private val http: HttpClient = RuStoreApiVerifier.defaultHttpClient(),
    private val baseUrl: String = "https://vkpns.rustore.ru",
) : PushSender {
    private val log = LoggerFactory.getLogger(RuStorePushSender::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override suspend fun wake(deviceIds: List<String>) {
        val targets = db.tx {
            Devices.selectAll().where { Devices.id inList deviceIds }.mapNotNull { row -> row[Devices.pushToken]?.let { row[Devices.id] to it } }
        }
        if (targets.isEmpty()) return
        scope.launch {
            for ((deviceId, pushToken) in targets) {
                val body = buildJsonObject {
                    putJsonObject("message") {
                        put("token", pushToken)
                        putJsonObject("data") { put("type", "sync") }
                    }
                }
                runCatching {
                    val response = http.post("$baseUrl/v1/projects/$projectId/messages:send") {
                        header("Authorization", "Bearer $serviceToken")
                        contentType(ContentType.Application.Json)
                        setBody(body.toString())
                    }
                    if (response.status.value == 404 || response.status.value == 400) {
                        // The token is no longer valid; the app will register a fresh one.
                        db.tx { Devices.update({ Devices.id eq deviceId }) { it[Devices.pushToken] = null } }
                    } else if (!response.status.isSuccess()) {
                        log.warn("Push failed with {}", response.status)
                    }
                }.onFailure { log.warn("Push error: {}", it.message) }
            }
        }
    }
}
