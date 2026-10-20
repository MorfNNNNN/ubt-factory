package ru.ubt.familyshield.shared.detect

import ru.ubt.familyshield.shared.crypto.sha256
import ru.ubt.familyshield.shared.crypto.toHex

enum class NumberKind(val titleRu: String) {
    RU_MOBILE("Мобильный номер РФ"),
    RU_LANDLINE("Городской номер РФ"),
    RU_TOLL_FREE("Номер 8-800"),
    RU_SHORT("Короткий номер"),
    KAZAKHSTAN("Номер Казахстана"),
    INTERNATIONAL("Международный номер"),
    HIDDEN("Скрытый номер"),
    INVALID("Некорректный номер"),
}

data class PhoneInfo(
    val raw: String?,
    /** E.164 with leading '+', or the digits for short numbers; null for hidden/invalid. */
    val normalized: String?,
    val kind: NumberKind,
    val countryRu: String? = null,
) {
    val hash: String? get() = normalized?.let { PhoneNumbers.hash(it) }

    fun display(): String {
        val n = normalized ?: return kind.titleRu
        if (kind == NumberKind.RU_MOBILE || kind == NumberKind.RU_LANDLINE || kind == NumberKind.RU_TOLL_FREE || kind == NumberKind.KAZAKHSTAN) {
            val d = n.removePrefix("+7")
            if (d.length == 10) return "+7 ${d.substring(0, 3)} ${d.substring(3, 6)}-${d.substring(6, 8)}-${d.substring(8)}"
        }
        return n
    }
}

object PhoneNumbers {
    private val countries: Map<String, String> = mapOf(
        "380" to "Украина", "375" to "Беларусь", "998" to "Узбекистан", "992" to "Таджикистан", "996" to "Киргизия",
        "994" to "Азербайджан", "374" to "Армения", "995" to "Грузия", "373" to "Молдова", "371" to "Латвия", "370" to "Литва",
        "372" to "Эстония", "993" to "Туркменистан", "976" to "Монголия", "972" to "Израиль", "971" to "ОАЭ", "357" to "Кипр",
        "359" to "Болгария", "358" to "Финляндия", "351" to "Португалия", "381" to "Сербия", "420" to "Чехия", "234" to "Нигерия",
        "225" to "Кот-д'Ивуар", "880" to "Бангладеш", "855" to "Камбоджа", "856" to "Лаос", "852" to "Гонконг", "886" to "Тайвань",
        "44" to "Великобритания", "49" to "Германия", "33" to "Франция", "39" to "Италия", "34" to "Испания", "90" to "Турция",
        "86" to "Китай", "48" to "Польша", "66" to "Таиланд", "84" to "Вьетнам", "91" to "Индия", "62" to "Индонезия",
        "63" to "Филиппины", "60" to "Малайзия", "82" to "Южная Корея", "81" to "Япония", "31" to "Нидерланды", "46" to "Швеция",
        "47" to "Норвегия", "32" to "Бельгия", "41" to "Швейцария", "43" to "Австрия", "30" to "Греция", "40" to "Румыния",
        "36" to "Венгрия", "55" to "Бразилия", "52" to "Мексика", "20" to "Египет", "27" to "ЮАР", "61" to "Австралия",
        "1" to "США/Канада",
    )

    fun hash(normalized: String): String = sha256(normalized.toByteArray(Charsets.US_ASCII)).toHex().substring(0, 16)

    fun parse(raw: String?): PhoneInfo {
        if (raw == null) return PhoneInfo(null, null, NumberKind.HIDDEN)
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("-") || trimmed.equals("unknown", true) || trimmed.equals("private", true) ||
            trimmed.equals("anonymous", true) || trimmed.contains("скрыт", true)
        ) {
            return PhoneInfo(raw, null, NumberKind.HIDDEN)
        }
        val plus = trimmed.startsWith("+")
        val digits = trimmed.filter { it.isDigit() }
        if (digits.isEmpty()) return PhoneInfo(raw, null, NumberKind.INVALID)

        if (!plus && digits.length in 2..6) return PhoneInfo(raw, digits, NumberKind.RU_SHORT)

        val international: String? = when {
            plus -> digits
            digits.startsWith("810") && digits.length > 11 -> digits.substring(3)
            digits.startsWith("00") && digits.length > 10 -> digits.substring(2)
            digits.length == 11 && (digits[0] == '8' || digits[0] == '7') -> "7" + digits.substring(1)
            digits.length == 10 && digits[0] in "3489" -> "7$digits"
            else -> null
        }
        if (international == null) return PhoneInfo(raw, digits, NumberKind.INVALID)

        if (international.startsWith("7")) {
            val national = international.substring(1)
            if (national.length != 10) return PhoneInfo(raw, "+$international", NumberKind.INVALID)
            val kind = when {
                national.startsWith("800") || national.startsWith("804") -> NumberKind.RU_TOLL_FREE
                national[0] == '9' -> NumberKind.RU_MOBILE
                national[0] == '6' || national[0] == '7' -> NumberKind.KAZAKHSTAN
                national[0] in "348" -> NumberKind.RU_LANDLINE
                else -> NumberKind.INVALID
            }
            val country = when (kind) {
                NumberKind.KAZAKHSTAN -> "Казахстан"
                NumberKind.INVALID -> null
                else -> "Россия"
            }
            return PhoneInfo(raw, "+$international", kind, country)
        }
        if (international.length < 8 || international.length > 15) return PhoneInfo(raw, "+$international", NumberKind.INVALID)
        val country = (3 downTo 1).firstNotNullOfOrNull { len -> countries[international.take(len)] } ?: "Другая страна"
        return PhoneInfo(raw, "+$international", NumberKind.INTERNATIONAL, country)
    }

    /** Matches phone-number-like sequences in free text (for "check a message" and SMS analysis). */
    private val inTextRegex = Regex("(?<![\\d+])(?:\\+7|8|\\+\\d{1,3})[\\s(\\-]*\\d{3}[\\s)\\-]*\\d{3}[\\s\\-]*\\d{2}[\\s\\-]*\\d{2}(?!\\d)")

    fun extract(text: String): List<PhoneInfo> = inTextRegex.findAll(text).map { parse(it.value) }
        .filter { it.kind != NumberKind.INVALID }.distinctBy { it.normalized }.take(5).toList()
}

data class CallContext(
    val inContacts: Boolean,
    /** Category from the threat database or the user's own block list, if any. */
    val threatCategory: String? = null,
    val threatReports: Int = 0,
    val userBlocked: Boolean = false,
    val userAllowed: Boolean = false,
    val callsFromNumberToday: Int = 0,
    /** Local hour 0..23. */
    val hourOfDay: Int = 12,
)

data class CallRisk(val score: Double, val reasons: List<String>) {
    val level: RiskLevel get() = RiskLevel.of(score)
}

object CallRiskAnalyzer {
    fun assess(info: PhoneInfo, ctx: CallContext): CallRisk {
        if (ctx.userAllowed) return CallRisk(0.0, listOf("Номер в списке разрешённых"))
        val reasons = ArrayList<String>()
        val weights = ArrayList<Double>()
        fun flag(w: Double, why: String) {
            weights += w
            reasons += why
        }
        if (ctx.userBlocked) flag(1.0, "Номер в вашем чёрном списке")
        if (ctx.threatCategory != null) {
            val w = when {
                ctx.threatReports >= 20 -> 0.95
                ctx.threatReports >= 5 -> 0.85
                else -> 0.7
            }
            flag(w, "На номер жаловались пользователи: ${ctx.threatCategory}")
        }
        if (ctx.inContacts) {
            return CallRisk(noisyOr(weights) * 0.5, reasons.ifEmpty { listOf("Номер есть в контактах") })
        }
        when (info.kind) {
            NumberKind.HIDDEN -> flag(0.55, "Номер скрыт")
            NumberKind.INTERNATIONAL -> flag(0.6, "Звонок из-за рубежа (${info.countryRu}) — мошеннические колл-центры часто звонят из других стран")
            NumberKind.RU_TOLL_FREE -> flag(0.5, "Входящий с номера 8-800: банки обычно не звонят с таких номеров, номер мог быть подменён")
            NumberKind.RU_SHORT -> flag(0.3, "Короткий номер может быть подменён — перезвоните организации сами")
            NumberKind.KAZAKHSTAN -> flag(0.35, "Номер Казахстана (+7 6xx/7xx) похож на российский")
            NumberKind.INVALID -> flag(0.5, "Номер имеет неправильный формат — возможна подмена")
            NumberKind.RU_MOBILE, NumberKind.RU_LANDLINE -> flag(0.12, "Номера нет в ваших контактах")
        }
        if (ctx.callsFromNumberToday >= 3) flag(0.3, "Этот номер звонит настойчиво (${ctx.callsFromNumberToday} раза за день)")
        if (ctx.hourOfDay < 8 || ctx.hourOfDay >= 22) flag(0.15, "Звонок в позднее время")
        return CallRisk(noisyOr(weights), reasons)
    }
}
