package ru.ubt.familyshield.shared.detect

/** A money movement out of the account, found in a bank SMS or push. */
data class BankDebit(val amountRub: Long, val kind: Kind, val merchant: String? = null) {
    enum class Kind(val titleRu: String) {
        TRANSFER("Перевод"),
        PURCHASE("Покупка"),
        CASH("Снятие наличных"),
        PAYMENT("Оплата"),
        OTHER("Списание"),
    }
}

/**
 * Finds debits in bank notifications («Покупка 2 500р», «Перевод по СБП 15 000 ₽», «Списание 12 000,00 RUB»)
 * so the family can be told about unusually large ones. Incoming money and codes are ignored.
 * Everything runs on the phone; only the amount and kind go into the family alert.
 */
object BankDebitParser {
    /** Short numbers and sender names Russian banks send SMS from. */
    private val bankSenders = setOf(
        "900", "9000", "9001", "8900", "sberbank", "sber", "сбербанк", "tinkoff", "t-bank", "tbank", "т-банк",
        "vtb", "втб", "alfa-bank", "alfabank", "альфа-банк", "gazprombank", "газпромбанк", "raiffeisen", "райффайзен",
        "sovcombank", "совкомбанк", "otkritie", "открытие", "psb", "псб", "mtsbank", "mts-bank", "мтс банк",
        "pochtabank", "почта банк", "rshb", "россельхозбанк", "ozon bank", "ozonbank", "yandex bank", "яндекс банк",
        "rosbank", "росбанк", "uralsib", "уралсиб", "homebank", "home credit", "mkb", "мкб",
    )

    fun isBankSender(sender: String?): Boolean {
        val s = sender?.trim()?.lowercase() ?: return false
        return s in bankSenders || bankSenders.any { it.length > 3 && s.contains(it) }
    }

    private val incoming = Regex(
        "(зачислен|поступлен|пополнен|возврат|вам перевел|вам перевёл|получен перевод|перевод от|входящий перевод|кешб[эе]к|cashback)",
        RegexOption.IGNORE_CASE,
    )
    private val codeWords = Regex("(код|пароль|code)(?![а-яёa-z])", RegexOption.IGNORE_CASE)

    private val kinds: List<Pair<Regex, BankDebit.Kind>> = listOf(
        Regex("(перевод|переведено|сбп)", RegexOption.IGNORE_CASE) to BankDebit.Kind.TRANSFER,
        Regex("(снятие|выдача наличных|банкомат|atm)", RegexOption.IGNORE_CASE) to BankDebit.Kind.CASH,
        Regex("(покупка|оплата товаров|pokupka)", RegexOption.IGNORE_CASE) to BankDebit.Kind.PURCHASE,
        Regex("(оплата|платеж|платёж)", RegexOption.IGNORE_CASE) to BankDebit.Kind.PAYMENT,
        Regex("(списани|списано|расход|debit)", RegexOption.IGNORE_CASE) to BankDebit.Kind.OTHER,
    )

    /** An amount with a rouble marker: «15 000р», «15 000,00 ₽», «2500.50 RUB», «1 200 руб.» */
    private val amount = Regex("(\\d{1,3}(?:[ \\u00A0\\u202F]\\d{3})+|\\d+)(?:[.,](\\d{1,2}))?\\s?(?:руб|р(?![а-яёa-z])|₽|rub|rur)", RegexOption.IGNORE_CASE)

    fun parse(text: String): BankDebit? {
        if (text.isBlank() || incoming.containsMatchIn(text)) return null
        val kind = kinds.firstOrNull { it.first.containsMatchIn(text) }?.second ?: return null
        // A one-time code message mentions the amount of the operation it confirms; it is not a debit yet.
        if (codeWords.containsMatchIn(text) && !Regex("(списан|покупка|оплата)", RegexOption.IGNORE_CASE).containsMatchIn(text)) return null
        // The balance follows words like «Баланс»/«Доступно»: take the first amount before them.
        val cut = Regex("(баланс|доступно|остаток|balance)", RegexOption.IGNORE_CASE).find(text)?.range?.first ?: text.length
        val match = amount.find(text.substring(0, cut)) ?: return null
        val rub = match.groupValues[1].filter(Char::isDigit).toLongOrNull() ?: return null
        if (rub <= 0) return null
        return BankDebit(rub, kind, merchant(text, match.range.last + 1, cut))
    }

    private fun merchant(text: String, from: Int, to: Int): String? {
        if (from >= to) return null
        val tail = text.substring(from, to).trim().trimStart('.', ',', ' ').trim()
        val name = tail.split('.', ',', '\n').firstOrNull()?.trim().orEmpty()
        return name.takeIf { it.length in 3..40 && it.any(Char::isLetter) && !it.contains("карт", ignoreCase = true) }
    }
}
