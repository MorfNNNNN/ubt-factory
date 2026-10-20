package ru.ubt.familyshield.shared.detect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScamAnalyzerTest {
    private val analyzer = ScamAnalyzer()

    private fun assertAtLeast(level: RiskLevel, text: String, ctx: MessageContext = MessageContext()): ScamVerdict {
        val v = analyzer.analyze(text, ctx)
        assertTrue("expected >= $level but was ${v.level} (${"%.2f".format(v.score)}) ${v.findings.map { it.type }} for: $text", v.level >= level)
        return v
    }

    private fun assertAtMost(level: RiskLevel, text: String): ScamVerdict {
        val v = analyzer.analyze(text)
        assertTrue("expected <= $level but was ${v.level} (${"%.2f".format(v.score)}) ${v.findings.map { it.type to it.reasons }} for: $text", v.level <= level)
        return v
    }

    @Test
    fun bankSecurityWithCodeRequestIsCritical() {
        val v = assertAtLeast(
            RiskLevel.CRITICAL,
            "Здравствуйте, вас беспокоит служба безопасности Сбербанка. По вашей карте зафиксирована подозрительная операция. " +
                "Чтобы отменить её, продиктуйте код из СМС.",
        )
        assertTrue(v.findings.any { it.type == ScamType.CODE_REQUEST })
        assertNotNull(v.advice)
    }

    @Test
    fun safeAccountIsCritical() {
        val v = assertAtLeast(RiskLevel.CRITICAL, "Ваши сбережения под угрозой, необходимо срочно перевести их на безопасный счёт Центрального банка")
        assertEquals(ScamType.SAFE_ACCOUNT, v.top?.type)
    }

    @Test
    fun relativeInTrouble() {
        assertAtLeast(RiskLevel.HIGH, "Мама, это я, попал в ДТП, нужны деньги срочно, чтобы не завели дело. Передай деньги курьеру, он приедет через час")
    }

    @Test
    fun gosuslugiHack() {
        assertAtLeast(RiskLevel.HIGH, "Ваш аккаунт на Госуслугах взломан. Для восстановления доступа назовите код, который придёт вам в СМС")
    }

    @Test
    fun courierAndIntercomCodes() {
        assertAtLeast(RiskLevel.HIGH, "Курьер не может доставить вашу посылку. Сообщите код для получения")
        assertAtLeast(RiskLevel.HIGH, "Завтра замена домофонов в вашем доме, продиктуйте код из смс для получения нового ключа")
    }

    @Test
    fun operatorContract() {
        assertAtLeast(RiskLevel.HIGH, "Срок действия вашего договора с оператором истекает. Для продления назовите код из сообщения")
    }

    @Test
    fun votingWithSuspiciousLink() {
        val v = assertAtLeast(RiskLevel.HIGH, "Привет! Проголосуй за мою племянницу в конкурсе, пожалуйста: http://golosovanie-konkurs.top/vote")
        assertTrue(v.urls.isNotEmpty())
    }

    @Test
    fun apkLure() {
        assertAtLeast(RiskLevel.HIGH, "Это ты на фото? photo_2024.apk")
    }

    @Test
    fun investmentAndPrize() {
        assertAtLeast(RiskLevel.HIGH, "Гарантированный доход от 50 000 рублей в месяц без риска! Инвестиции в акции Газпрома")
        assertAtLeast(RiskLevel.HIGH, "Вы выиграли iPhone! Для получения приза оплатите доставку по ссылке")
    }

    @Test
    fun remoteAccess() {
        assertAtLeast(RiskLevel.HIGH, "Установите AnyDesk, чтобы наш специалист проверил ваш телефон, и включите демонстрацию экрана")
    }

    @Test
    fun fakeInvestigator() {
        assertAtLeast(
            RiskLevel.HIGH,
            "С вами говорит следователь. Вы дали подписку о неразглашении, никому не говорите о нашем разговоре и не кладите трубку",
        )
    }

    @Test
    fun phishingLink() {
        val v = assertAtLeast(RiskLevel.HIGH, "Вам положена компенсация! Перейдите на sberbank-bonus.ru, чтобы получить выплату")
        assertTrue(v.urls.any { it.impersonatedBrand == "Сбер" })
    }

    @Test
    fun obfuscatedTextStillDetected() {
        assertAtLeast(RiskLevel.HIGH, "Пeрeвeдите дeньги на бeзопасный счeт")
    }

    @Test
    fun duringCallRaisesRisk() {
        val text = "Сотрудник банка попросит подтвердить операцию"
        val normal = analyzer.analyze(text)
        val inCall = analyzer.analyze(text, MessageContext(duringCall = true))
        assertTrue(inCall.score >= normal.score)
    }

    @Test
    fun legitimateOtpIsNotFlagged() {
        val v = assertAtMost(RiskLevel.LOW, "Код для входа в СберБанк Онлайн: 12345. Никому не сообщайте этот код.")
        assertEquals("12345", v.otp?.code)
    }

    @Test
    fun everydayMessagesAreSafe() {
        assertAtMost(RiskLevel.SAFE, "Мам, я задержусь после школы, буду в 18:00")
        assertAtMost(RiskLevel.SAFE, "Привет, как дела? Завтра идём в кино?")
        assertAtMost(RiskLevel.LOW, "Ваш заказ доставлен в пункт выдачи Ozon. Срок хранения 7 дней.")
        assertAtMost(RiskLevel.SAFE, "Покупка 560 р. Карта *1234. Баланс 10 000 р.")
        assertAtMost(RiskLevel.LOW, "Добрый день! Ваша запись к врачу подтверждена на 25.10 в 10:30")
        assertAtMost(RiskLevel.SAFE, "Бабушка, с днём рождения! Целуем, приедем в субботу")
    }

    @Test
    fun bankWarningsAreNotFlagged() {
        assertAtMost(RiskLevel.LOW, "Напоминаем: мошенники звонят от имени банка. Никогда не сообщайте коды из СМС и не переводите деньги на «безопасный счёт».")
    }
}
