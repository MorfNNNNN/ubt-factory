package ru.ubt.familyshield.shared.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Commands sent by a guardian to a protected device. */
@Serializable
sealed class Command {
    abstract val id: String

    @Serializable
    @SerialName("lock")
    data class LockNow(override val id: String, val untilEpochMillis: Long?) : Command()

    @Serializable
    @SerialName("unlock")
    data class Unlock(override val id: String) : Command()

    @Serializable
    @SerialName("bonus")
    data class GrantBonus(override val id: String, val minutes: Int, val reason: String = "") : Command()

    /** "Найти телефон": rings loudly even in silent mode. */
    @Serializable
    @SerialName("ring")
    data class Ring(override val id: String, val seconds: Int = 30) : Command()

    @Serializable
    @SerialName("locate")
    data class RequestLocation(override val id: String) : Command()

    @Serializable
    @SerialName("approve_app")
    data class ApproveApp(override val id: String, val packageName: String) : Command()

    @Serializable
    @SerialName("deny_app")
    data class DenyApp(override val id: String, val packageName: String) : Command()

    @Serializable
    @SerialName("scan")
    data class RunSecurityScan(override val id: String) : Command()

    /** Guardian approved disabling protection on an elder device before the cooling-off period ends. */
    @Serializable
    @SerialName("approve_protection_change")
    data class ApproveProtectionChange(override val id: String, val requestId: String) : Command()

    @Serializable
    @SerialName("report")
    data class RequestReport(override val id: String) : Command()
}

@Serializable
enum class RequestKind { MORE_TIME, APP_APPROVAL, SITE_UNBLOCK, PROTECTION_CHANGE, REMOTE_ACCESS }

/** Requests initiated on a protected device (by a child or an elder) that a guardian decides on. */
@Serializable
data class MemberRequest(
    val id: String,
    val kind: RequestKind,
    val createdAt: Long,
    val minutes: Int? = null,
    val packageName: String? = null,
    val appLabel: String? = null,
    val domain: String? = null,
    val reason: String = "",
)

@Serializable
data class RequestDecision(
    val requestId: String,
    val approved: Boolean,
    val minutes: Int? = null,
    val note: String = "",
    val decidedAt: Long,
)

@Serializable
enum class QuickReply(val textRu: String) {
    ALL_GOOD("У меня всё хорошо"),
    CALL_ME("Перезвони мне, пожалуйста"),
    COME_HOME("Приезжай, нужна помощь"),
    ON_MY_WAY("Уже еду"),
    LOVE("Люблю тебя"),
    BUSY("Сейчас занят, позвоню позже"),
}

/** Short family chat message (end-to-end encrypted like everything else). */
@Serializable
data class FamilyNote(
    val id: String,
    val text: String,
    val createdAt: Long,
    val quickReply: QuickReply? = null,
)

@Serializable
data class CheckIn(
    val id: String,
    val createdAt: Long,
    val mood: String = "ok",
    val location: GeoPoint? = null,
)
