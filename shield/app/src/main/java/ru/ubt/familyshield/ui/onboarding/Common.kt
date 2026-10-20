package ru.ubt.familyshield.ui.onboarding

import ru.ubt.familyshield.core.net.ApiException
import ru.ubt.familyshield.shared.crypto.CryptoException

/** Turns low-level errors into calm, actionable Russian messages for onboarding screens. */
fun friendlyError(e: Throwable): String = when {
    e is ApiException && e.code == "invite_not_found" -> "Код не найден. Проверьте, что он введён без ошибок, или создайте новый."
    e is ApiException && e.code == "invite_used" -> "Этот код уже использован. Попросите близкого создать новый."
    e is ApiException && e.code == "invite_expired" -> "Срок действия кода истёк. Создайте новый в приложении взрослого."
    e is ApiException && e.code == "rate_limited" -> "Слишком много попыток. Подождите несколько минут."
    e is ApiException && e.code == "already_in_family" -> "Этот телефон уже состоит в семье."
    e is ApiException && e.code.startsWith("plan_limit") -> e.message ?: "Достигнут лимит тарифа"
    e is ApiException -> e.message ?: "Ошибка сервера"
    e is CryptoException -> "Код не подошёл. Проверьте, что все символы введены верно."
    e is java.io.IOException -> "Нет связи с сервером. Проверьте интернет и попробуйте ещё раз."
    else -> e.message ?: "Что-то пошло не так"
}
