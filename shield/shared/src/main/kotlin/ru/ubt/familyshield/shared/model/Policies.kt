package ru.ubt.familyshield.shared.model

import kotlinx.serialization.Serializable

/** ISO day of week: 1 = Monday ... 7 = Sunday. */
typealias IsoDay = Int

val WEEKDAYS: Set<IsoDay> = setOf(1, 2, 3, 4, 5)
val WEEKEND: Set<IsoDay> = setOf(6, 7)
val ALL_DAYS: Set<IsoDay> = (1..7).toSet()

/**
 * A daily time window in minutes from midnight. When [endMinute] <= [startMinute] the window
 * wraps past midnight (e.g. 22:00–07:00) and [days] refers to the day on which it starts.
 */
@Serializable
data class TimeWindow(
    val startMinute: Int,
    val endMinute: Int,
    val days: Set<IsoDay> = ALL_DAYS,
) {
    init {
        require(startMinute in 0 until MINUTES_PER_DAY) { "startMinute out of range: $startMinute" }
        require(endMinute in 0..MINUTES_PER_DAY) { "endMinute out of range: $endMinute" }
    }

    val wrapsMidnight: Boolean get() = endMinute <= startMinute

    companion object {
        const val MINUTES_PER_DAY = 24 * 60
        fun hm(h: Int, m: Int = 0): Int = h * 60 + m
    }
}

@Serializable
enum class AppCategory {
    SOCIAL, MESSAGING, GAMES, VIDEO, BROWSER, EDUCATION, MUSIC, SHOPPING, FINANCE, PRODUCTIVITY, NAVIGATION, PHOTO, OTHER
}

@Serializable
enum class AppMode {
    /** Counts toward limits, otherwise usable. */
    DEFAULT,

    /** Never blocked by limits or schedules (calls, school apps, maps...). */
    ALWAYS_ALLOWED,

    /** Completely blocked. */
    BLOCKED,
}

@Serializable
data class AppRule(
    val packageName: String,
    val mode: AppMode = AppMode.DEFAULT,
    val dailyLimitMinutes: Int? = null,
)

@Serializable
enum class ScheduleMode {
    /** Only always-allowed apps work during the window (bedtime, lessons). */
    BLOCK_ALL,

    /** Only the listed categories are blocked during the window. */
    BLOCK_CATEGORIES,
}

@Serializable
data class ScheduleRule(
    val id: String,
    val name: String,
    val window: TimeWindow,
    val mode: ScheduleMode = ScheduleMode.BLOCK_ALL,
    val categories: Set<AppCategory> = emptySet(),
    val enabled: Boolean = true,
)

@Serializable
data class BonusGrant(
    val id: String,
    /** Local date, ISO format yyyy-MM-dd, for which the bonus is valid. */
    val date: String,
    val minutes: Int,
    val reason: String = "",
)

@Serializable
enum class WebCategory {
    ADULT, GAMBLING, DRUGS, VIOLENCE, PHISHING, SCAM, MALWARE, PIRACY, SOCIAL, VIDEO, GAMES, DATING, PROXY_VPN, ADS
}

@Serializable
enum class DnsUpstream(val primary: String, val secondary: String) {
    /** Яндекс DNS «Семейный»: blocks adult content and fraudulent sites. */
    YANDEX_FAMILY("77.88.8.7", "77.88.8.3"),

    /** Яндекс DNS «Безопасный»: blocks fraudulent and infected sites. */
    YANDEX_SAFE("77.88.8.88", "77.88.8.2"),
    ADGUARD_FAMILY("94.140.14.15", "94.140.15.16"),
    CLOUDFLARE_FAMILY("1.1.1.3", "1.0.0.3"),
    YANDEX_BASIC("77.88.8.8", "77.88.8.1"),
}

@Serializable
data class WebFilterSettings(
    val enabled: Boolean = false,
    val categories: Set<WebCategory> = setOf(
        WebCategory.ADULT, WebCategory.GAMBLING, WebCategory.DRUGS, WebCategory.PHISHING,
        WebCategory.SCAM, WebCategory.MALWARE, WebCategory.PROXY_VPN,
    ),
    val safeSearch: Boolean = true,
    val youtubeRestricted: Boolean = true,
    val blockedDomains: Set<String> = emptySet(),
    val allowedDomains: Set<String> = emptySet(),
    val upstream: DnsUpstream = DnsUpstream.YANDEX_FAMILY,
    /** Only allow [allowedDomains]; for the youngest children. */
    val allowListOnly: Boolean = false,
)

@Serializable
enum class RiskCategory(val titleRu: String) {
    BULLYING("Травля и оскорбления"),
    SELF_HARM("Самоповреждение и суицидальные мысли"),
    GROOMING("Подозрительный взрослый (груминг)"),
    SEXTORTION("Шантаж интимными фото"),
    DRUGS("Наркотики"),
    GAMBLING("Азартные игры и ставки"),
    MONEY_MULE("Вербовка в «дропы» и обнал"),
    RECRUITMENT("Вербовка на поджоги и «задания»"),
    VIOLENCE("Насилие и угрозы школе"),
    KID_SCAM("Мошенничество против детей"),
    ADULT_CONTENT("Контент 18+"),
}

@Serializable
data class MessageMonitoring(
    val enabled: Boolean = false,
    val categories: Set<RiskCategory> = RiskCategory.entries.toSet(),
    /** Teens can be given a private mode where only critical categories are reported. */
    val criticalOnly: Boolean = false,
)

@Serializable
data class ChildPolicy(
    val version: Long = 0,
    val ageBand: AgeBand = AgeBand.AGE_10_12,
    val dailyLimitWeekdayMinutes: Int? = 120,
    val dailyLimitWeekendMinutes: Int? = 180,
    val categoryLimits: Map<AppCategory, Int> = emptyMap(),
    val appRules: Map<String, AppRule> = emptyMap(),
    val schedules: List<ScheduleRule> = listOf(
        ScheduleRule(
            id = "sleep",
            name = "Сон",
            window = TimeWindow(TimeWindow.hm(21, 30), TimeWindow.hm(7, 0)),
        ),
    ),
    val bonuses: List<BonusGrant> = emptyList(),
    /** Epoch millis until which the device is paused ("Пауза"); null when not paused. */
    val pausedUntil: Long? = null,
    val requireApprovalForNewApps: Boolean = true,
    val approvedPackages: Set<String> = emptySet(),
    val webFilter: WebFilterSettings = WebFilterSettings(enabled = true),
    val messageMonitoring: MessageMonitoring = MessageMonitoring(),
    val geofences: List<Geofence> = emptyList(),
    val locationIntervalMinutes: Int = 15,
    val uninstallProtection: Boolean = true,
    val blockAppStoresDuringLimits: Boolean = true,
    val sosContacts: List<TrustedContact> = emptyList(),
    val warnBeforeLimitMinutes: Int = 5,
    /** Lets a parent temporarily unlock protected settings on the child's phone. */
    val parentPin: PinHash? = null,
)

@Serializable
data class PinHash(val salt: String, val hash: String, val iterations: Int = 20_000) {
    fun matches(pin: String): Boolean = compute(pin, salt, iterations) == hash

    companion object {
        fun create(pin: String): PinHash {
            val salt = ru.ubt.familyshield.shared.crypto.B64.encode(ru.ubt.familyshield.shared.crypto.Rng.bytes(16))
            return PinHash(salt, compute(pin, salt, 20_000))
        }

        private fun compute(pin: String, salt: String, iterations: Int): String {
            val spec = javax.crypto.spec.PBEKeySpec(pin.toCharArray(), salt.toByteArray(), iterations, 256)
            val bytes = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded
            return ru.ubt.familyshield.shared.crypto.B64.encode(bytes)
        }
    }
}

@Serializable
data class CallShieldSettings(
    val enabled: Boolean = true,
    val blockKnownSpam: Boolean = true,
    val blockHiddenNumbers: Boolean = false,
    val blockInternational: Boolean = false,
    /** Unknown numbers ring silently instead of being rejected. */
    val silenceUnknown: Boolean = false,
    val warnUnknown: Boolean = true,
    val contactsOnly: Boolean = false,
    val blockedNumbers: Set<String> = emptySet(),
    val allowedNumbers: Set<String> = emptySet(),
    /** Alert guardians when a call with an unknown number lasts longer than this. */
    val longCallAlertMinutes: Int = 10,
)

@Serializable
data class CheckInSettings(
    val enabled: Boolean = false,
    /** Minute of day when the "Я в порядке" reminder fires. */
    val reminderMinute: Int = TimeWindow.hm(10, 0),
    /** Guardians are alerted when no check-in happened within this many minutes after the reminder. */
    val graceMinutes: Int = 120,
)

@Serializable
data class MedicationReminder(
    val id: String,
    val name: String,
    val dose: String = "",
    val minutesOfDay: List<Int>,
    val days: Set<IsoDay> = ALL_DAYS,
    val notifyGuardianIfMissed: Boolean = true,
)

@Serializable
enum class SimpleTile {
    CALL_FAMILY, SOS, CHECK_IN, MEDICATIONS, CAMERA, GALLERY, MESSAGES, PHONE, WHATSAPP, TELEGRAM, MAX, MAPS, FLASHLIGHT, MAGNIFIER, SAFETY_CHECK, FAMILY_CHAT, WEATHER, RADIO,
}

@Serializable
data class SimpleModeSettings(
    val enabled: Boolean = false,
    val fontScale: Float = 1.3f,
    val highContrast: Boolean = false,
    val tiles: List<SimpleTile> = listOf(
        SimpleTile.CALL_FAMILY, SimpleTile.SOS, SimpleTile.PHONE, SimpleTile.MESSAGES,
        SimpleTile.CHECK_IN, SimpleTile.MEDICATIONS, SimpleTile.CAMERA, SimpleTile.GALLERY,
        SimpleTile.WHATSAPP, SimpleTile.TELEGRAM, SimpleTile.SAFETY_CHECK, SimpleTile.FAMILY_CHAT,
    ),
    val favoriteContacts: List<TrustedContact> = emptyList(),
    val speakIncomingCaller: Boolean = true,
)

@Serializable
data class ElderPolicy(
    val version: Long = 0,
    val callShield: CallShieldSettings = CallShieldSettings(),
    val inCallBankGuard: Boolean = true,
    val otpGuard: Boolean = true,
    val remoteAccessGuard: Boolean = true,
    val messageScan: Boolean = true,
    val linkScan: Boolean = true,
    val newAppAlert: Boolean = true,
    val sideloadGuard: Boolean = true,
    val checkIn: CheckInSettings = CheckInSettings(),
    /** Alert guardians if the phone was not used for this many hours (null = off). */
    val inactivityHours: Int? = 12,
    val fallDetection: Boolean = false,
    val medications: List<MedicationReminder> = emptyList(),
    val simpleMode: SimpleModeSettings = SimpleModeSettings(),
    val lowBatteryPercent: Int = 15,
    /**
     * Turning protection off on the elder device only takes effect after this delay (or when a
     * guardian approves). Protects against scammers who coach victims to disable protection.
     */
    val coolingOffHours: Int = 24,
    val geofences: List<Geofence> = emptyList(),
    val locationIntervalMinutes: Int = 30,
    val trustedContacts: List<TrustedContact> = emptyList(),
    val webFilter: WebFilterSettings = WebFilterSettings(
        enabled = false,
        categories = setOf(WebCategory.PHISHING, WebCategory.SCAM, WebCategory.MALWARE, WebCategory.ADS),
        safeSearch = false,
        youtubeRestricted = false,
        upstream = DnsUpstream.YANDEX_SAFE,
    ),
)
