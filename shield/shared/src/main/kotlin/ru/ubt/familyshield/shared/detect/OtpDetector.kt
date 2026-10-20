package ru.ubt.familyshield.shared.detect

enum class OtpPurpose(val titleRu: String) {
    LOGIN("вход в аккаунт"),
    PAYMENT("оплата или перевод"),
    ACCOUNT_CHANGE("изменение данных аккаунта"),
    GOSUSLUGI("Госуслуги"),
    SIM_OR_DEVICE("привязка нового устройства"),
    UNKNOWN("подтверждение"),
}

data class OtpMatch(
    val code: String,
    val issuer: String?,
    val purpose: OtpPurpose,
    val amount: String? = null,
)

/** Recognises one-time-password messages (SMS or push) to warn when a code arrives during a call. */
object OtpDetector {
    private val codeAfterWord = Regex("(?iu)(?:код|code|пароль|password|otp)[^\\d\\n]{0,40}?(\\d{4,8})(?!\\d)")
    private val codeBeforeWord = Regex("(?iu)(?<!\\d)(\\d{4,8})\\s*[-—–:]?\\s*(?:ваш|это|is your)?\\s*(?:код|code|пароль)")
    private val amountRegex = Regex("(?iu)(\\d[\\d\\s]{0,9}(?:[.,]\\d{1,2})?)\\s?(?:₽|р\\.|руб|rub)")

    private val issuers = listOf(
        "госуслуг" to "Госуслуги", "gosuslugi" to "Госуслуги", "сбер" to "СберБанк", "sber" to "СберБанк", "900" to "СберБанк",
        "т-банк" to "Т-Банк", "tbank" to "Т-Банк", "тинькофф" to "Т-Банк", "tinkoff" to "Т-Банк", "втб" to "ВТБ", "vtb" to "ВТБ",
        "альфа" to "Альфа-Банк", "alfa" to "Альфа-Банк", "газпромбанк" to "Газпромбанк", "озон" to "Ozon", "ozon" to "Ozon",
        "wildberries" to "Wildberries", "вайлдберриз" to "Wildberries", "авито" to "Авито", "avito" to "Авито",
        "telegram" to "Telegram", "телеграм" to "Telegram", "whatsapp" to "WhatsApp", "вконтакте" to "ВКонтакте", "vk" to "ВКонтакте",
        "яндекс" to "Яндекс", "yandex" to "Яндекс", "max" to "MAX", "почта банк" to "Почта Банк", "мтс" to "МТС", "мегафон" to "МегаФон",
        "билайн" to "Билайн", "rustore" to "RuStore", "mos.ru" to "mos.ru",
    )

    fun detect(text: String): OtpMatch? {
        val code = codeAfterWord.find(text)?.groupValues?.get(1) ?: codeBeforeWord.find(text)?.groupValues?.get(1) ?: return null
        val lower = text.lowercase()
        // Year-like or amount-like numbers are not codes.
        if (code.length == 4 && (code.startsWith("19") || code.startsWith("20")) && lower.contains("год")) return null
        val issuer = issuers.firstOrNull { lower.contains(it.first) }?.second
        val purpose = when {
            lower.contains("госуслуг") || lower.contains("gosuslugi") -> OtpPurpose.GOSUSLUGI
            listOf("перевод", "оплат", "покупк", "списан", "платеж", "платёж", "сбп").any { lower.contains(it) } -> OtpPurpose.PAYMENT
            listOf("смен", "изменен", "восстановлен", "сброс", "привязк", "номер телефона").any { lower.contains(it) } -> OtpPurpose.ACCOUNT_CHANGE
            listOf("устройств", "сим", "sim", "новом телефоне").any { lower.contains(it) } -> OtpPurpose.SIM_OR_DEVICE
            listOf("вход", "войти", "login", "авторизац").any { lower.contains(it) } -> OtpPurpose.LOGIN
            else -> OtpPurpose.UNKNOWN
        }
        val amount = amountRegex.find(text)?.groupValues?.get(1)?.trim()
        return OtpMatch(code, issuer, purpose, amount)
    }
}
