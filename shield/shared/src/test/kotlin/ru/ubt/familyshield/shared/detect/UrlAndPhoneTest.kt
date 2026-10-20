package ru.ubt.familyshield.shared.detect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlAndPhoneTest {
    @Test
    fun officialDomainsAreSafe() {
        for (u in listOf("https://online.sberbank.ru/CSAFront", "https://www.gosuslugi.ru/", "vk.com/id1", "https://mail.ru", "https://t.me/rustore")) {
            val v = UrlAnalyzer.analyze(u)
            assertTrue(u, v.official)
            assertEquals(0.0, v.score, 0.0)
        }
    }

    @Test
    fun brandImpersonationAndTyposquatting() {
        assertTrue(UrlAnalyzer.analyze("https://sberbank-online-help.ru/login").score >= 0.8)
        assertTrue(UrlAnalyzer.analyze("http://gosuslugl.ru").score >= 0.8)
        assertTrue(UrlAnalyzer.analyze("https://tinkoff.secure-pay.site").score >= 0.8)
        assertEquals("Госуслуги", UrlAnalyzer.analyze("gosuslugi-vyplata.online").impersonatedBrand)
    }

    @Test
    fun technicalRedFlags() {
        assertTrue(UrlAnalyzer.analyze("http://185.12.33.4/login").score >= 0.6)
        assertTrue(UrlAnalyzer.analyze("https://files.example.com/Gosuslugi_update.apk").score >= 0.9)
        assertTrue(UrlAnalyzer.analyze("https://bit.ly/3abcde").score >= 0.4)
        // Mixed Latin/Cyrillic host (Cyrillic "е").
        assertTrue(UrlAnalyzer.analyze("https://yandеx.ru").score >= 0.8)
    }

    @Test
    fun ordinarySitesAreLowRisk() {
        assertTrue(UrlAnalyzer.analyze("https://example.org/article").score < 0.25)
        assertTrue(UrlAnalyzer.analyze("https://habr.com/ru/articles/1/").score < 0.25)
    }

    @Test
    fun extractsUrlsFromText() {
        val urls = UrlAnalyzer.extract("Смотри www.site.ru/a, и ещё https://x.top/p?q=1. И почту a@mail.ru")
        assertTrue(urls.contains("www.site.ru/a"))
        assertTrue(urls.contains("https://x.top/p?q=1"))
    }

    @Test
    fun parsesRussianNumbers() {
        assertEquals("+79161234567", PhoneNumbers.parse("8 (916) 123-45-67").normalized)
        assertEquals(NumberKind.RU_MOBILE, PhoneNumbers.parse("+7 916 123 45 67").kind)
        assertEquals(NumberKind.RU_TOLL_FREE, PhoneNumbers.parse("88005553535").kind)
        assertEquals(NumberKind.RU_LANDLINE, PhoneNumbers.parse("+7 (495) 123-45-67").kind)
        assertEquals(NumberKind.RU_SHORT, PhoneNumbers.parse("900").kind)
        assertEquals(NumberKind.KAZAKHSTAN, PhoneNumbers.parse("+77011234567").kind)
        assertEquals(NumberKind.HIDDEN, PhoneNumbers.parse(null).kind)
        val ua = PhoneNumbers.parse("+380441234567")
        assertEquals(NumberKind.INTERNATIONAL, ua.kind)
        assertEquals("Украина", ua.countryRu)
        assertEquals("+7 916 123-45-67", PhoneNumbers.parse("89161234567").display())
        assertEquals(16, PhoneNumbers.parse("89161234567").hash!!.length)
    }

    @Test
    fun callRisk() {
        val intl = CallRiskAnalyzer.assess(PhoneNumbers.parse("+380441234567"), CallContext(inContacts = false))
        assertTrue(intl.level >= RiskLevel.MEDIUM)
        val contact = CallRiskAnalyzer.assess(PhoneNumbers.parse("+79161234567"), CallContext(inContacts = true))
        assertEquals(RiskLevel.SAFE, contact.level)
        val spam = CallRiskAnalyzer.assess(PhoneNumbers.parse("+79161234567"), CallContext(inContacts = false, threatCategory = "мошенники", threatReports = 25))
        assertEquals(RiskLevel.CRITICAL, spam.level)
        val allowed = CallRiskAnalyzer.assess(PhoneNumbers.parse("+380441234567"), CallContext(inContacts = false, userAllowed = true))
        assertEquals(0.0, allowed.score, 0.0)
    }

    @Test
    fun otpDetection() {
        val m = OtpDetector.detect("Никому не сообщайте код 4821 для подтверждения перевода 15 000 ₽ на карту *1234. СберБанк")
        assertEquals("4821", m?.code)
        assertEquals(OtpPurpose.PAYMENT, m?.purpose)
        assertEquals("СберБанк", m?.issuer)
        assertEquals("15 000", m?.amount)
        assertEquals(OtpPurpose.GOSUSLUGI, OtpDetector.detect("Код подтверждения: 123456. Госуслуги")?.purpose)
        assertEquals("777333", OtpDetector.detect("777333 — ваш код для входа в Telegram")?.code)
        assertNull(OtpDetector.detect("Встречаемся в 1830 у подъезда"))
    }

    @Test
    fun appCatalog() {
        assertTrue(AppCatalog.isMoneyApp("ru.sberbankmobile"))
        assertTrue(AppCatalog.isMoneyApp("ru.vtb24.mobilebanking.android"))
        assertEquals("AnyDesk", AppCatalog.remoteAccessName("com.anydesk.anydeskandroid"))
        assertTrue(AppCatalog.isBypassTool("com.psiphon3"))
        assertEquals(ru.ubt.familyshield.shared.model.AppCategory.GAMES, AppCatalog.categoryOf("com.roblox.client"))
        assertEquals(ru.ubt.familyshield.shared.model.AppCategory.GAMES, AppCatalog.categoryOf("com.unknown.game", AppCatalog.AndroidCategory.GAME))
        assertTrue(AppCatalog.isEssential("com.google.android.dialer"))
    }
}
