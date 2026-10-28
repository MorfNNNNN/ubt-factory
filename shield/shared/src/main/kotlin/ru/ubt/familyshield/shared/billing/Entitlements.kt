package ru.ubt.familyshield.shared.billing

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import ru.ubt.familyshield.shared.ShieldJson
import ru.ubt.familyshield.shared.crypto.B64
import ru.ubt.familyshield.shared.crypto.Signatures
import java.security.PrivateKey
import java.security.PublicKey

@Serializable
enum class Tier(val titleRu: String) {
    FREE("Базовый"),
    PREMIUM("Премиум"),
    FAMILY_MAX("Семья Макс"),
}

/** One-time "forever" feature packs for people who do not want a subscription. */
@Serializable
enum class Pack(val titleRu: String) {
    ELDER_PRO("Защита родителей"),
    KIDS_PRO("Детский контроль"),
}

@Serializable
enum class FeatureGroup { BASIC, ELDER, KIDS, FAMILY }

/**
 * Every paid capability of the app. A feature is available when the tier is at least [minTier]
 * or when the user owns a pack that covers its [group].
 */
@Serializable
enum class Feature(val group: FeatureGroup, val minTier: Tier, val titleRu: String) {
    // Always free: safety must never be behind a paywall.
    SOS(FeatureGroup.BASIC, Tier.FREE, "Кнопка SOS"),
    PAIRING(FeatureGroup.BASIC, Tier.FREE, "Подключение семьи"),
    SAFETY_TIPS(FeatureGroup.BASIC, Tier.FREE, "Памятки против мошенников"),
    MANUAL_CHECK(FeatureGroup.BASIC, Tier.FREE, "Проверка номера, ссылки и сообщения"),
    SCREEN_TIME_VIEW(FeatureGroup.BASIC, Tier.FREE, "Статистика экранного времени"),
    BEDTIME(FeatureGroup.BASIC, Tier.FREE, "Режим сна"),
    CALL_WARNING(FeatureGroup.BASIC, Tier.FREE, "Предупреждение о незнакомых звонках"),
    SIMPLE_MODE(FeatureGroup.BASIC, Tier.FREE, "Простой режим с крупными кнопками"),
    DEVICE_SCAN(FeatureGroup.BASIC, Tier.FREE, "Проверка безопасности телефона"),
    FAMILY_CHAT(FeatureGroup.BASIC, Tier.FREE, "Семейный чат"),
    LOCATION_ON_DEMAND(FeatureGroup.BASIC, Tier.FREE, "Где сейчас близкий"),

    // Elder protection.
    CALL_BLOCKING(FeatureGroup.ELDER, Tier.PREMIUM, "Блокировка мошеннических звонков"),
    IN_CALL_GUARD(FeatureGroup.ELDER, Tier.PREMIUM, "Защита во время звонка"),
    OTP_GUARD(FeatureGroup.ELDER, Tier.PREMIUM, "Защита кодов из СМС"),
    REMOTE_ACCESS_GUARD(FeatureGroup.ELDER, Tier.PREMIUM, "Защита от удалённого доступа"),
    SCAM_MESSAGE_SCAN(FeatureGroup.ELDER, Tier.PREMIUM, "Анализ сообщений на мошенничество"),
    LINK_SCAN(FeatureGroup.ELDER, Tier.PREMIUM, "Проверка ссылок"),
    SIDELOAD_GUARD(FeatureGroup.ELDER, Tier.PREMIUM, "Защита от вредоносных APK"),
    COOLING_OFF(FeatureGroup.ELDER, Tier.PREMIUM, "Защита настроек от мошенников"),
    CHECK_IN(FeatureGroup.ELDER, Tier.PREMIUM, "Ежедневное «Я в порядке»"),
    INACTIVITY_ALERT(FeatureGroup.ELDER, Tier.PREMIUM, "Тревога при бездействии"),
    FALL_DETECTION(FeatureGroup.ELDER, Tier.PREMIUM, "Определение падения"),
    MEDICATIONS(FeatureGroup.ELDER, Tier.PREMIUM, "Напоминания о лекарствах"),
    REMOTE_SETUP(FeatureGroup.ELDER, Tier.PREMIUM, "Удалённая настройка телефона"),
    BIG_DEBIT_ALERT(FeatureGroup.ELDER, Tier.PREMIUM, "Оповещение о крупных списаниях"),

    // Kids protection.
    APP_LIMITS(FeatureGroup.KIDS, Tier.PREMIUM, "Лимиты на приложения"),
    SCHEDULES(FeatureGroup.KIDS, Tier.PREMIUM, "Расписания (уроки, сон, выходные)"),
    APP_BLOCKING(FeatureGroup.KIDS, Tier.PREMIUM, "Блокировка приложений"),
    APP_APPROVAL(FeatureGroup.KIDS, Tier.PREMIUM, "Одобрение новых приложений"),
    WEB_FILTER(FeatureGroup.KIDS, Tier.PREMIUM, "Веб-фильтр и безопасный поиск"),
    RISK_ALERTS(FeatureGroup.KIDS, Tier.PREMIUM, "Оповещения об опасных переписках"),
    GEOFENCES(FeatureGroup.KIDS, Tier.PREMIUM, "Геозоны: дом, школа, секция"),
    REWARDS(FeatureGroup.KIDS, Tier.PREMIUM, "Бонусное время и награды"),
    UNINSTALL_PROTECTION(FeatureGroup.KIDS, Tier.PREMIUM, "Защита от удаления"),
    INSTANT_LOCK(FeatureGroup.KIDS, Tier.PREMIUM, "Мгновенная пауза телефона"),

    // Family-wide premium.
    LOCATION_HISTORY(FeatureGroup.FAMILY, Tier.PREMIUM, "История перемещений"),
    LIVE_LOCATION(FeatureGroup.FAMILY, Tier.PREMIUM, "Местоположение в реальном времени"),
    WEEKLY_REPORTS(FeatureGroup.FAMILY, Tier.PREMIUM, "Еженедельные отчёты"),
    FIND_PHONE(FeatureGroup.FAMILY, Tier.PREMIUM, "Найти телефон (громкий сигнал)"),
    UNLIMITED_CHECKS(FeatureGroup.FAMILY, Tier.PREMIUM, "Безлимитные проверки"),
    REPORT_EXPORT(FeatureGroup.FAMILY, Tier.FAMILY_MAX, "Экспорт отчётов"),
    PRIORITY_SUPPORT(FeatureGroup.FAMILY, Tier.FAMILY_MAX, "Приоритетная поддержка"),
}

@Serializable
data class PlanLimits(
    val maxProtectedDevices: Int,
    val maxGuardians: Int,
    val historyDays: Int,
    val manualChecksPerDay: Int,
)

/** Product ids as configured in the RuStore developer console. */
object Products {
    const val PREMIUM_MONTH = "shield_premium_month"
    const val PREMIUM_YEAR = "shield_premium_year"
    const val FAMILY_MAX_MONTH = "shield_family_max_month"
    const val FAMILY_MAX_YEAR = "shield_family_max_year"
    const val PREMIUM_LIFETIME = "shield_premium_lifetime"
    const val PACK_ELDER = "shield_pack_elder"
    const val PACK_KIDS = "shield_pack_kids"

    val subscriptions = listOf(PREMIUM_MONTH, PREMIUM_YEAR, FAMILY_MAX_MONTH, FAMILY_MAX_YEAR)
    val oneTime = listOf(PREMIUM_LIFETIME, PACK_ELDER, PACK_KIDS)
    val all = subscriptions + oneTime

    fun tierOf(productId: String): Tier? = when (productId) {
        PREMIUM_MONTH, PREMIUM_YEAR, PREMIUM_LIFETIME -> Tier.PREMIUM
        FAMILY_MAX_MONTH, FAMILY_MAX_YEAR -> Tier.FAMILY_MAX
        else -> null
    }

    fun packOf(productId: String): Pack? = when (productId) {
        PACK_ELDER -> Pack.ELDER_PRO
        PACK_KIDS -> Pack.KIDS_PRO
        else -> null
    }

    fun isLifetime(productId: String) = productId in oneTime
}

@Serializable
data class Entitlement(
    val familyId: String? = null,
    val tier: Tier = Tier.FREE,
    val packs: Set<Pack> = emptySet(),
    /** Epoch millis; null means it never expires (lifetime purchase or free). */
    val expiresAt: Long? = null,
    val issuedAt: Long = 0,
    val products: Set<String> = emptySet(),
) {
    fun isActive(now: Long): Boolean = expiresAt == null || expiresAt > now

    /** Effective tier at [now]: an expired subscription falls back to FREE. */
    fun effectiveTier(now: Long): Tier = if (isActive(now)) tier else Tier.FREE

    fun has(feature: Feature, now: Long): Boolean {
        val tier = effectiveTier(now)
        if (tier.ordinal >= feature.minTier.ordinal) return true
        if (feature.minTier != Tier.PREMIUM) return false
        // Packs are one-time purchases and stay valid after a subscription in the same bundle expires.
        return when (feature.group) {
            FeatureGroup.ELDER -> Pack.ELDER_PRO in packs
            FeatureGroup.KIDS -> Pack.KIDS_PRO in packs
            else -> false
        }
    }

    fun limits(now: Long): PlanLimits = when (effectiveTier(now)) {
        Tier.FREE -> if (packs.isEmpty()) FREE_LIMITS else PACK_LIMITS
        Tier.PREMIUM -> PREMIUM_LIMITS
        Tier.FAMILY_MAX -> MAX_LIMITS
    }

    /** Merges entitlements from several purchases: highest tier wins, packs accumulate. */
    operator fun plus(other: Entitlement): Entitlement {
        val tierWinner = if (other.tier.ordinal > tier.ordinal) other else this
        val expires = when {
            tier == other.tier -> if (expiresAt == null || other.expiresAt == null) null else maxOf(expiresAt, other.expiresAt)
            else -> tierWinner.expiresAt
        }
        return Entitlement(
            familyId = familyId ?: other.familyId,
            tier = tierWinner.tier,
            packs = packs + other.packs,
            expiresAt = expires,
            issuedAt = maxOf(issuedAt, other.issuedAt),
            products = products + other.products,
        )
    }

    companion object {
        val FREE = Entitlement()
        val FREE_LIMITS = PlanLimits(maxProtectedDevices = 1, maxGuardians = 1, historyDays = 1, manualChecksPerDay = 3)
        val PACK_LIMITS = PlanLimits(maxProtectedDevices = 2, maxGuardians = 2, historyDays = 7, manualChecksPerDay = 10)
        val PREMIUM_LIMITS = PlanLimits(maxProtectedDevices = 5, maxGuardians = 3, historyDays = 30, manualChecksPerDay = Int.MAX_VALUE)
        val MAX_LIMITS = PlanLimits(maxProtectedDevices = 10, maxGuardians = 5, historyDays = 90, manualChecksPerDay = Int.MAX_VALUE)

        /** Entitlement granted by a single product. Subscriptions carry [expiresAt]. */
        fun forProduct(productId: String, expiresAt: Long?, issuedAt: Long, familyId: String?): Entitlement {
            val tier = Products.tierOf(productId) ?: Tier.FREE
            val pack = Products.packOf(productId)
            return Entitlement(
                familyId = familyId,
                tier = tier,
                packs = setOfNotNull(pack),
                expiresAt = if (Products.isLifetime(productId)) null else expiresAt,
                issuedAt = issuedAt,
                products = setOf(productId),
            )
        }
    }
}

/**
 * Compact signed entitlement token issued by the server: base64url(JSON) "." base64url(ECDSA sig).
 * Protected devices verify it offline with the public key embedded in the app.
 */
object EntitlementToken {
    fun issue(entitlement: Entitlement, privateKey: PrivateKey): String {
        val payload = B64.encode(ShieldJson.encodeToString(entitlement).toByteArray(Charsets.UTF_8))
        val sig = B64.encode(Signatures.sign(privateKey, payload.toByteArray(Charsets.US_ASCII)))
        return "$payload.$sig"
    }

    fun verify(token: String, publicKey: PublicKey): Entitlement? {
        val parts = token.split('.')
        if (parts.size != 2) return null
        val sig = runCatching { B64.decode(parts[1]) }.getOrNull() ?: return null
        if (!Signatures.verify(publicKey, parts[0].toByteArray(Charsets.US_ASCII), sig)) return null
        return runCatching {
            ShieldJson.decodeFromString(Entitlement.serializer(), B64.decode(parts[0]).toString(Charsets.UTF_8))
        }.getOrNull()
    }
}
