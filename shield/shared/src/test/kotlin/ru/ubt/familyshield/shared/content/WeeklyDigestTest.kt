package ru.ubt.familyshield.shared.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.ubt.familyshield.shared.model.AlertType

class WeeklyDigestTest {
    private fun row(metric: String, value: Int = 1, device: String = "mom", date: String = "2026-10-26") = DayStat(date, device, metric, value)

    @Test
    fun countsEventsAndAveragesScreenTime() {
        val w = WeekNumbers.of(
            listOf(
                row(AlertType.SCAM_CALL_BLOCKED.name, 3),
                row(AlertType.SCAM_MESSAGE.name), row(AlertType.PHISHING_LINK.name, 2),
                row(AlertType.CHECK_IN_OK.name, 6),
                row(WeekMetric.DANGER, 2),
                row(WeekMetric.SCREEN, 120, device = "kid", date = "2026-10-26"),
                row(WeekMetric.SCREEN, 180, device = "kid", date = "2026-10-27"),
                // A day without screen time does not pull the average down.
                row(WeekMetric.SCREEN, 0, device = "kid", date = "2026-10-28"),
            ),
        )
        assertEquals(3, w.scamCalls)
        assertEquals(3, w.scamMessages)
        assertEquals(6, w.checkIns)
        assertEquals(2, w.dangerous)
        assertEquals(150, w.avgScreenMinutes)
    }

    @Test
    fun titleReflectsTheWorstOfTheWeek() {
        assertEquals("Неделя семьи: всё спокойно", WeeklyDigest.title(WeekNumbers(checkIns = 7)))
        assertEquals("Неделя семьи: 1 опасный момент", WeeklyDigest.title(WeekNumbers(dangerous = 1)))
        assertEquals("Неделя семьи: 3 опасных момента", WeeklyDigest.title(WeekNumbers(dangerous = 3)))
        assertEquals("Неделя семьи: 11 опасных моментов", WeeklyDigest.title(WeekNumbers(dangerous = 11)))
        assertEquals("Неделя семьи: был сигнал SOS", WeeklyDigest.title(WeekNumbers(dangerous = 1, sos = 1)))
    }

    @Test
    fun textUsesCorrectRussianForms() {
        assertEquals(
            "Не пропустили 1 звонок мошенников · Получили 2 отметки «Я в порядке» · Выполнено 5 заданий",
            WeeklyDigest.text(WeekNumbers(scamCalls = 1, checkIns = 2, tasksDone = 5, lessons = 1)),
        )
        assertEquals("Получили 1 отметку «Я в порядке»", WeeklyDigest.text(WeekNumbers(checkIns = 1)))
        assertEquals("Прошли 21 урок безопасности", WeeklyDigest.text(WeekNumbers(lessons = 21)))
        assertTrue(WeeklyDigest.text(WeekNumbers()).startsWith("Все телефоны семьи под защитой"))
    }

    @Test
    fun trendComparesWithThePreviousWeek() {
        assertNull(WeeklyDigest.trend(0, 0))
        assertEquals("+2 к прошлой неделе", WeeklyDigest.trend(5, 3))
        assertEquals("−3 к прошлой неделе", WeeklyDigest.trend(1, 4))
        assertEquals("как на прошлой неделе", WeeklyDigest.trend(2, 2))
    }

    @Test
    fun tipsPointToRealLessons() {
        assertEquals("bank_security", WeeklyDigest.tip(WeekNumbers(scamCalls = 1), 10).lessonId)
        assertEquals("phishing_links", WeeklyDigest.tip(WeekNumbers(scamMessages = 1), 10).lessonId)
        assertNull(WeeklyDigest.tip(WeekNumbers(dangerous = 1), 10).lessonId)
        (0..60).forEach { week ->
            val id = WeeklyDigest.tip(WeekNumbers(), week).lessonId
            assertNotNull(SafetySchool.byId(requireNotNull(id)))
        }
    }

    @Test
    fun pluralHandlesTeens() {
        assertEquals("заданий", plural(12, "задание", "задания", "заданий"))
        assertEquals("задание", plural(101, "задание", "задания", "заданий"))
        assertEquals("задания", plural(22, "задание", "задания", "заданий"))
    }
}
