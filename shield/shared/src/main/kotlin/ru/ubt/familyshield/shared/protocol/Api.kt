package ru.ubt.familyshield.shared.protocol

import kotlinx.serialization.Serializable
import ru.ubt.familyshield.shared.model.MemberRole

/** REST contract between devices and the relay server. Version prefix: /v1. */
object ApiPaths {
    const val REGISTER = "/v1/devices/register"
    const val PUSH_TOKEN = "/v1/devices/push-token"
    const val FAMILIES = "/v1/families"
    const val MEMBERS = "/v1/family/members"
    const val INVITES = "/v1/family/invites"
    const val ACCEPT_INVITE = "/v1/invites/accept"
    const val MESSAGES = "/v1/messages"
    const val ACK = "/v1/messages/ack"
    const val VERIFY_PURCHASE = "/v1/billing/verify"
    const val ENTITLEMENT = "/v1/billing/entitlement"
    const val ENTITLEMENT_PUBLIC_KEY = "/v1/billing/public-key"
    const val THREAT_NUMBERS = "/v1/threats/numbers"
    const val REPORT_NUMBER = "/v1/threats/numbers/report"
    const val THREAT_DOMAINS = "/v1/threats/domains"
    const val LEAVE_FAMILY = "/v1/family/leave"
    const val DELETE_DEVICE = "/v1/devices/me"
    const val HEALTH = "/health"
}

@Serializable
data class ApiError(val code: String, val message: String)

@Serializable
data class RegisterRequest(
    val platform: String = "android",
    val appVersion: String,
    val model: String = "",
)

@Serializable
data class RegisterResponse(val deviceId: String, val deviceToken: String)

@Serializable
data class PushTokenRequest(val token: String, val provider: String = "rustore")

@Serializable
data class CreateFamilyResponse(val familyId: String)

@Serializable
data class CreateInviteRequest(
    /**
     * Client-chosen invite id (10 Crockford chars). The guardian needs it before contacting the
     * server because it salts the key that wraps [wrappedSecrets]. 409 means "taken, pick another".
     */
    val inviteId: String? = null,
    val role: MemberRole,
    /** Family secrets wrapped with a key derived from the out-of-band pairing secret (see [PairingCode]). */
    val wrappedSecrets: String,
    val ttlSeconds: Int = 900,
)

@Serializable
data class InviteResponse(val inviteId: String, val expiresAt: Long)

@Serializable
data class AcceptInviteRequest(val inviteId: String)

@Serializable
data class AcceptInviteResponse(
    val familyId: String,
    val role: MemberRole,
    val wrappedSecrets: String,
    val invitedBy: String,
    val members: List<MemberDto>,
)

@Serializable
data class MemberDto(
    val deviceId: String,
    val role: MemberRole,
    val joinedAt: Long,
    val lastSeenAt: Long?,
)

@Serializable
data class MembersResponse(val familyId: String, val members: List<MemberDto>)

@Serializable
enum class Audience { GUARDIANS, PROTECTED, ALL, DEVICES }

@Serializable
data class PostMessageRequest(
    /** Client-generated UUID; also bound into the encryption AAD. Re-posting the same id is idempotent. */
    val id: String,
    val audience: Audience,
    val recipients: List<String> = emptyList(),
    val body: String,
    val signature: String? = null,
    /** Wake recipients with a push notification (alerts, commands). */
    val urgent: Boolean = false,
)

@Serializable
data class PostMessageResponse(val id: String, val deliveredTo: Int)

@Serializable
data class MessageDto(
    val seq: Long,
    val id: String,
    val senderDeviceId: String,
    val createdAt: Long,
    val body: String,
    val signature: String? = null,
)

@Serializable
data class PullResponse(val messages: List<MessageDto>, val nextCursor: Long, val hasMore: Boolean)

@Serializable
data class AckRequest(val upToSeq: Long)

@Serializable
data class VerifyPurchaseRequest(
    val productId: String,
    val purchaseId: String,
    /** Required for one-time products: verified via RuStore /public/v2/invoices/{invoiceId}. */
    val invoiceId: String? = null,
    val orderId: String? = null,
    val sandbox: Boolean = false,
)

@Serializable
data class MemberRemovalResponse(val removed: Boolean)

@Serializable
data class HealthResponse(val status: String, val version: String)

@Serializable
data class EntitlementResponse(
    /** Compact signed token: base64url(payload JSON) + "." + base64url(signature). */
    val token: String,
)

@Serializable
data class ThreatNumber(
    /** First 16 hex chars of SHA-256 of the E.164 number; plain numbers are not distributed. */
    val hash: String,
    val category: String,
    val reports: Int,
)

@Serializable
data class ThreatNumbersResponse(val version: Long, val numbers: List<ThreatNumber>, val removed: List<String> = emptyList())

@Serializable
data class ReportNumberRequest(val number: String, val category: String, val comment: String = "")

@Serializable
data class ThreatDomainsResponse(
    val version: Long,
    val category: String,
    val domains: List<String>,
    val removed: List<String> = emptyList(),
    /** Pass back as `cursor` (with the same `since`) to fetch the next page of a large import. */
    val cursor: String? = null,
    val hasMore: Boolean = false,
)
