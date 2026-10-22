package ru.ubt.familyshield.core.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import ru.ubt.familyshield.BuildConfig
import ru.ubt.familyshield.shared.ShieldJson
import ru.ubt.familyshield.shared.model.MemberRole
import ru.ubt.familyshield.shared.model.WebCategory
import ru.ubt.familyshield.shared.protocol.AcceptInviteRequest
import ru.ubt.familyshield.shared.protocol.AcceptInviteResponse
import ru.ubt.familyshield.shared.protocol.AckRequest
import ru.ubt.familyshield.shared.protocol.ApiError
import ru.ubt.familyshield.shared.protocol.ApiPaths
import ru.ubt.familyshield.shared.protocol.CreateFamilyResponse
import ru.ubt.familyshield.shared.protocol.CreateInviteRequest
import ru.ubt.familyshield.shared.protocol.EntitlementResponse
import ru.ubt.familyshield.shared.protocol.InviteResponse
import ru.ubt.familyshield.shared.protocol.MemberRemovalResponse
import ru.ubt.familyshield.shared.protocol.MembersResponse
import ru.ubt.familyshield.shared.protocol.PostMessageRequest
import ru.ubt.familyshield.shared.protocol.PostMessageResponse
import ru.ubt.familyshield.shared.protocol.PullResponse
import ru.ubt.familyshield.shared.protocol.PushTokenRequest
import ru.ubt.familyshield.shared.protocol.RegisterRequest
import ru.ubt.familyshield.shared.protocol.RegisterResponse
import ru.ubt.familyshield.shared.protocol.ReportNumberRequest
import ru.ubt.familyshield.shared.protocol.ThreatDomainsResponse
import ru.ubt.familyshield.shared.protocol.ThreatNumbersResponse
import ru.ubt.familyshield.shared.protocol.VerifyPurchaseRequest
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Server-side error with a stable [code] (see the server's ApiException). */
class ApiException(val status: Int, val code: String, message: String) : IOException(message)

/**
 * Thin typed client for the relay server. [baseUrl] and [token] are read on every call so a
 * server change in settings or a fresh registration takes effect immediately.
 */
class ApiClient(
    private val baseUrl: () -> String,
    private val token: () -> String?,
    private val http: OkHttpClient = defaultClient(),
) {
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    private suspend fun <T> call(
        method: String,
        path: String,
        body: String?,
        responseSerializer: KSerializer<T>?,
        auth: Boolean = true,
    ): T? = withContext(Dispatchers.IO) {
        val url = baseUrl().trimEnd('/') + path
        val builder = Request.Builder().url(url).header("User-Agent", "FamilyShield/${BuildConfig.VERSION_NAME} Android")
        if (auth) {
            val t = token() ?: throw ApiException(401, "not_registered", "Устройство не зарегистрировано")
            builder.header("Authorization", "Bearer $t")
        }
        val requestBody = body?.toRequestBody(jsonType)
        builder.method(method, requestBody ?: if (method == "POST" || method == "PUT") ByteArray(0).toRequestBody(jsonType) else null)
        http.newCall(builder.build()).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val error = runCatching { ShieldJson.decodeFromString(ApiError.serializer(), text) }.getOrNull()
                throw ApiException(response.code, error?.code ?: "http_${response.code}", error?.message ?: "Ошибка сервера (${response.code})")
            }
            if (responseSerializer == null || text.isEmpty()) null else ShieldJson.decodeFromString(responseSerializer, text)
        }
    }

    private fun <T> encode(serializer: KSerializer<T>, value: T) = ShieldJson.encodeToString(serializer, value)

    suspend fun register(appVersion: String, model: String): RegisterResponse =
        call("POST", ApiPaths.REGISTER, encode(RegisterRequest.serializer(), RegisterRequest(appVersion = appVersion, model = model)), RegisterResponse.serializer(), auth = false)!!

    suspend fun setPushToken(token: String) {
        call<Unit>("PUT", ApiPaths.PUSH_TOKEN, encode(PushTokenRequest.serializer(), PushTokenRequest(token)), null)
    }

    suspend fun createFamily(): CreateFamilyResponse = call("POST", ApiPaths.FAMILIES, null, CreateFamilyResponse.serializer())!!

    suspend fun members(): MembersResponse = call("GET", ApiPaths.MEMBERS, null, MembersResponse.serializer())!!

    suspend fun removeMember(deviceId: String): Boolean =
        call("DELETE", "${ApiPaths.MEMBERS}/$deviceId", null, MemberRemovalResponse.serializer())!!.removed

    suspend fun leaveFamily() {
        call<Unit>("POST", ApiPaths.LEAVE_FAMILY, null, null)
    }

    suspend fun deleteDevice() {
        call<Unit>("DELETE", ApiPaths.DELETE_DEVICE, null, null)
    }

    suspend fun createInvite(inviteId: String, role: MemberRole, wrapped: String, ttlSeconds: Int): InviteResponse =
        call("POST", ApiPaths.INVITES, encode(CreateInviteRequest.serializer(), CreateInviteRequest(inviteId, role, wrapped, ttlSeconds)), InviteResponse.serializer())!!

    suspend fun acceptInvite(inviteId: String): AcceptInviteResponse =
        call("POST", ApiPaths.ACCEPT_INVITE, encode(AcceptInviteRequest.serializer(), AcceptInviteRequest(inviteId)), AcceptInviteResponse.serializer())!!

    suspend fun postMessage(req: PostMessageRequest): PostMessageResponse =
        call("POST", ApiPaths.MESSAGES, encode(PostMessageRequest.serializer(), req), PostMessageResponse.serializer())!!

    suspend fun pull(after: Long, limit: Int = 100): PullResponse =
        call("GET", "${ApiPaths.MESSAGES}?after=$after&limit=$limit", null, PullResponse.serializer())!!

    suspend fun ack(upTo: Long) {
        call<Unit>("POST", ApiPaths.ACK, encode(AckRequest.serializer(), AckRequest(upTo)), null)
    }

    suspend fun verifyPurchase(req: VerifyPurchaseRequest): String =
        call("POST", ApiPaths.VERIFY_PURCHASE, encode(VerifyPurchaseRequest.serializer(), req), EntitlementResponse.serializer())!!.token

    suspend fun entitlement(): String = call("GET", ApiPaths.ENTITLEMENT, null, EntitlementResponse.serializer())!!.token

    /** The server's entitlement public key (plain base64url text). */
    suspend fun entitlementPublicKey(): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(baseUrl().trimEnd('/') + ApiPaths.ENTITLEMENT_PUBLIC_KEY)
            .header("User-Agent", "FamilyShield/${BuildConfig.VERSION_NAME} Android").build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw ApiException(response.code, "http_${response.code}", "Ошибка сервера (${response.code})")
            response.body?.string().orEmpty().trim()
        }
    }

    suspend fun threatNumbers(since: Long): ThreatNumbersResponse =
        call("GET", "${ApiPaths.THREAT_NUMBERS}?since=$since", null, ThreatNumbersResponse.serializer())!!

    suspend fun reportNumber(number: String, category: String) {
        call<Unit>("POST", ApiPaths.REPORT_NUMBER, encode(ReportNumberRequest.serializer(), ReportNumberRequest(number, category)), null)
    }

    suspend fun threatDomains(category: WebCategory, since: Long, cursor: String?): ThreatDomainsResponse {
        val c = cursor?.let { "&cursor=" + java.net.URLEncoder.encode(it, "UTF-8") }.orEmpty()
        return call("GET", "${ApiPaths.THREAT_DOMAINS}?category=${category.name}&since=$since$c", null, ThreatDomainsResponse.serializer())!!
    }

    suspend fun health(): Boolean = runCatching {
        call("GET", ApiPaths.HEALTH, null, ru.ubt.familyshield.shared.protocol.HealthResponse.serializer(), auth = false)?.status == "ok"
    }.getOrDefault(false)

    companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}
