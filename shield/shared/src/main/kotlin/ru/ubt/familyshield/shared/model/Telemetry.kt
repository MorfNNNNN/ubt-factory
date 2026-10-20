package ru.ubt.familyshield.shared.model

import kotlinx.serialization.Serializable

/** Which protection components are active on a protected device. Shown to guardians as "health". */
@Serializable
enum class ProtectionComponent(val titleRu: String, val critical: Boolean) {
    FOREGROUND_SERVICE("Фоновая защита", true),
    NOTIFICATIONS("Уведомления", true),
    ACCESSIBILITY("Спецвозможности (контроль приложений)", true),
    NOTIFICATION_ACCESS("Доступ к уведомлениям (анализ сообщений)", true),
    USAGE_ACCESS("Доступ к статистике использования", true),
    CALL_SCREENING("Защита от звонков", false),
    LOCATION("Геолокация", false),
    BACKGROUND_LOCATION("Геолокация в фоне", false),
    VPN_FILTER("Веб-фильтр", false),
    DEVICE_ADMIN("Защита от удаления", false),
    OVERLAY("Показ поверх окон", false),
    BATTERY_OPTIMIZATION("Работа без ограничений батареи", true),
    CONTACTS("Контакты", false),
    PHONE_STATE("Состояние звонков", false),
}

@Serializable
data class DeviceStatus(
    val batteryPercent: Int,
    val charging: Boolean,
    val networkType: String,
    val activeComponents: Set<ProtectionComponent>,
    val missingComponents: Set<ProtectionComponent>,
    val lastInteractionAt: Long?,
    val reportedAt: Long,
    val appVersion: String,
    val ringerMode: String = "normal",
    val screenTimeTodayMinutes: Int? = null,
    val privateDnsBypass: Boolean = false,
)

@Serializable
data class LocationFix(
    val lat: Double,
    val lon: Double,
    val accuracyMeters: Float,
    val time: Long,
    val provider: String = "",
    val batteryPercent: Int? = null,
    val address: String? = null,
)

@Serializable
data class AppUsage(
    val packageName: String,
    val label: String,
    val minutes: Int,
    val category: AppCategory = AppCategory.OTHER,
    val launches: Int = 0,
)

@Serializable
data class UsageReport(
    /** Local date yyyy-MM-dd. */
    val date: String,
    val totalMinutes: Int,
    val apps: List<AppUsage>,
    val unlocks: Int = 0,
    val blockedAttempts: Int = 0,
    val reportedAt: Long,
)

@Serializable
data class InstalledApp(
    val packageName: String,
    val label: String,
    val category: AppCategory,
    val installer: String? = null,
    val firstInstallTime: Long = 0,
    val isSystem: Boolean = false,
    val riskFlags: Set<String> = emptySet(),
)

@Serializable
data class AppInventory(
    val apps: List<InstalledApp>,
    val reportedAt: Long,
)

@Serializable
data class SecurityFinding(
    val id: String,
    val severity: AlertSeverity,
    val title: String,
    val description: String,
    val fixAction: String? = null,
    val packageName: String? = null,
)

@Serializable
data class SecurityReport(
    val findings: List<SecurityFinding>,
    val scannedAt: Long,
    val score: Int,
)
