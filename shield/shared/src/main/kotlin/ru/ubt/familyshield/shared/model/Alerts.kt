package ru.ubt.familyshield.shared.model

import kotlinx.serialization.Serializable

@Serializable
enum class AlertSeverity { INFO, WARNING, HIGH, CRITICAL }

@Serializable
enum class AlertType(val defaultSeverity: AlertSeverity) {
    SOS(AlertSeverity.CRITICAL),
    FALL_DETECTED(AlertSeverity.CRITICAL),
    CHECK_IN_MISSED(AlertSeverity.HIGH),
    CHECK_IN_OK(AlertSeverity.INFO),
    INACTIVITY(AlertSeverity.HIGH),
    LOW_BATTERY(AlertSeverity.WARNING),

    SCAM_CALL_BLOCKED(AlertSeverity.INFO),
    SUSPICIOUS_CALL(AlertSeverity.WARNING),
    LONG_CALL_UNKNOWN(AlertSeverity.WARNING),
    BANK_APP_DURING_CALL(AlertSeverity.CRITICAL),
    REMOTE_ACCESS_APP(AlertSeverity.CRITICAL),
    OTP_DURING_CALL(AlertSeverity.CRITICAL),
    SCAM_MESSAGE(AlertSeverity.HIGH),
    PHISHING_LINK(AlertSeverity.HIGH),
    RISKY_APP_INSTALLED(AlertSeverity.HIGH),
    NEW_APP_INSTALLED(AlertSeverity.INFO),
    DEVICE_SECURITY_ISSUE(AlertSeverity.WARNING),
    PROTECTION_CHANGE_REQUESTED(AlertSeverity.HIGH),

    CHILD_RISK_MESSAGE(AlertSeverity.HIGH),
    BLOCKED_SITE(AlertSeverity.INFO),
    LIMIT_REACHED(AlertSeverity.INFO),
    GEOFENCE_ENTER(AlertSeverity.INFO),
    GEOFENCE_EXIT(AlertSeverity.INFO),
    TAMPER(AlertSeverity.HIGH),
    PERMISSION_REVOKED(AlertSeverity.HIGH),
    UNINSTALL_ATTEMPT(AlertSeverity.HIGH),
    FILTER_BYPASS(AlertSeverity.HIGH),
    MEDICATION_MISSED(AlertSeverity.WARNING),
    MEDICATION_TAKEN(AlertSeverity.INFO),

    TASK_DONE(AlertSeverity.INFO),
    BIG_DEBIT(AlertSeverity.HIGH),
}

/**
 * An alert raised on a protected device and delivered (end-to-end encrypted) to guardians.
 * [excerpt] is deliberately short: guardians see why the alert fired, not the full conversation.
 */
@Serializable
data class Alert(
    val id: String,
    val type: AlertType,
    val severity: AlertSeverity = type.defaultSeverity,
    val title: String,
    val body: String,
    val createdAt: Long,
    val sourceApp: String? = null,
    val sourceAppLabel: String? = null,
    val excerpt: String? = null,
    val categories: List<String> = emptyList(),
    val location: GeoPoint? = null,
    val counterparty: String? = null,
)
