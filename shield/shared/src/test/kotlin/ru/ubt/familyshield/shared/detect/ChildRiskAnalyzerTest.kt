package ru.ubt.familyshield.shared.detect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.ubt.familyshield.shared.model.AgeBand
import ru.ubt.familyshield.shared.model.AlertSeverity
import ru.ubt.familyshield.shared.model.RiskCategory

class ChildRiskAnalyzerTest {
    private val analyzer = ChildRiskAnalyzer()
    private val all = RiskCategory.entries.toSet()

    private fun reported(text: String, age: AgeBand = AgeBand.AGE_10_12, criticalOnly: Boolean = false) =
        analyzer.reportable(analyzer.analyze(text), age, all, criticalOnly).map { it.category }.toSet()

    @Test
    fun detectsDangerCategories() {
        assertTrue(RiskCategory.BULLYING in reported("ты никому не нужен, лучше бы ты сдох"))
        assertTrue(RiskCategory.SELF_HARM in reported("я больше не хочу жить, всё надоело"))
        assertTrue(RiskCategory.GROOMING in reported("не говори родителям, это наш секрет. скинь фото"))
        assertTrue(RiskCategory.DRUGS in reported("Работа для подростков! Курьер закладок, 5000 в день"))
        assertTrue(RiskCategory.MONEY_MULE in reported("Есть тема: сдай карту в аренду, платим 3000 в неделю"))
        assertTrue(RiskCategory.RECRUITMENT in reported("Заплатим 10000 за видео как ты подожжёшь релейный шкаф"))
        assertTrue(RiskCategory.KID_SCAM in reported("Бесплатные робуксы! Сфоткай карту мамы с двух сторон"))
        assertTrue(RiskCategory.SEXTORTION in reported("Если не заплатишь, разошлю твои фото всем друзьям"))
        assertTrue(RiskCategory.VIOLENCE in reported("завтра принесу ствол в школу"))
        assertTrue(RiskCategory.GAMBLING in reported("Забирай фриспины и бонус на депозит в казино"))
    }

    @Test
    fun severityOrderingPutsCriticalFirst() {
        val v = analyzer.analyze("ты лох. не хочу жить")
        assertEquals(AlertSeverity.CRITICAL, v.top?.severity)
    }

    @Test
    fun ordinaryChildChatIsQuiet() {
        assertTrue(reported("Привет, пойдём завтра гулять после уроков?").isEmpty())
        assertTrue(reported("Дз по математике скинь фото пожалуйста").isEmpty())
        assertTrue(reported("Ты лох, проиграл в бравл").isEmpty())
        assertTrue(reported("Мама сказала купить хлеба и молока").isEmpty())
        assertTrue(reported("В субботу тренировка в 10, не опаздывай").isEmpty())
    }

    @Test
    fun teenCriticalOnlyModeSuppressesMinorCategories() {
        val text = "Забирай фриспины и бонус на депозит в казино"
        assertTrue(reported(text, AgeBand.AGE_16_17, criticalOnly = true).isEmpty())
        assertTrue(RiskCategory.SELF_HARM in reported("хочу умереть", AgeBand.AGE_16_17, criticalOnly = true))
    }

    @Test
    fun suspiciousLinksAreReportedAsKidScam() {
        assertTrue(RiskCategory.KID_SCAM in reported("Получи скины тут: http://free-skins.top/steam-login"))
    }
}
