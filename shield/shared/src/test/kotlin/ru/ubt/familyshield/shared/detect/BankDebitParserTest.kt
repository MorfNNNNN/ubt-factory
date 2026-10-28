package ru.ubt.familyshield.shared.detect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BankDebitParserTest {
    @Test
    fun sberPurchase() {
        val d = BankDebitParser.parse("MIR-1234 10:15 Покупка 2 500р PYATEROCHKA Баланс: 15 430р")!!
        assertEquals(2500L, d.amountRub)
        assertEquals(BankDebit.Kind.PURCHASE, d.kind)
        assertEquals("PYATEROCHKA", d.merchant)
    }

    @Test
    fun sbpTransferWithKopecks() {
        val d = BankDebitParser.parse("Перевод по СБП 150 000,00 ₽ получателю Иван И. Доступно 1 204,10 ₽")!!
        assertEquals(150_000L, d.amountRub)
        assertEquals(BankDebit.Kind.TRANSFER, d.kind)
    }

    @Test
    fun tinkoffStyleRub() {
        val d = BankDebitParser.parse("Покупка, карта *1234. 12500 RUB. OZON. Доступно 15 430 RUB")!!
        assertEquals(12_500L, d.amountRub)
    }

    @Test
    fun cashWithdrawal() {
        val d = BankDebitParser.parse("Снятие наличных 30 000 руб. Банкомат 123. Остаток 2 000 руб.")!!
        assertEquals(30_000L, d.amountRub)
        assertEquals(BankDebit.Kind.CASH, d.kind)
    }

    @Test
    fun balanceIsNotTheAmount() {
        val d = BankDebitParser.parse("Списание 900р. Баланс 250 000р")!!
        assertEquals(900L, d.amountRub)
    }

    @Test
    fun incomingMoneyIsIgnored() {
        assertNull(BankDebitParser.parse("Зачисление 25 000р от ПАО Работодатель. Баланс 30 000р"))
        assertNull(BankDebitParser.parse("Вам перевели 5 000 ₽ от Анна А."))
        assertNull(BankDebitParser.parse("Возврат покупки 1 200р"))
    }

    @Test
    fun confirmationCodesAreNotDebits() {
        assertNull(BankDebitParser.parse("Код 1234. Никому его не сообщайте. Перевод 15 000р на карту *5678"))
    }

    @Test
    fun bankSenders() {
        assertTrue(BankDebitParser.isBankSender("900"))
        assertTrue(BankDebitParser.isBankSender("T-Bank"))
        assertTrue(!BankDebitParser.isBankSender("Мама"))
    }
}
