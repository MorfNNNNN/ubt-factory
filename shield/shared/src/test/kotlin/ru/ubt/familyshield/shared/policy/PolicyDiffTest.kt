package ru.ubt.familyshield.shared.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.ubt.familyshield.shared.model.BonusGrant
import ru.ubt.familyshield.shared.model.ChildPolicy
import ru.ubt.familyshield.shared.model.ElderPolicy
import ru.ubt.familyshield.shared.model.GeoPoint
import ru.ubt.familyshield.shared.model.Geofence
import ru.ubt.familyshield.shared.model.ScheduleRule
import ru.ubt.familyshield.shared.model.TimeWindow

class PolicyDiffTest {
    @Test
    fun childChangesReadLikeSentences() {
        val old = ChildPolicy(dailyLimitWeekdayMinutes = 150)
        val new = old.copy(
            dailyLimitWeekdayMinutes = 120,
            schedules = old.schedules + ScheduleRule("study", "Уроки", TimeWindow(TimeWindow.hm(15), TimeWindow.hm(18))),
            bonuses = listOf(BonusGrant("b1", "2026-10-28", 20, "Задание: Сделать уроки")),
            geofences = listOf(Geofence("g", "Школа", GeoPoint(55.0, 37.0))),
        )
        val lines = PolicyDiff.child(old, new)
        assertTrue(lines.toString(), "Лимит в будни: 2 ч 30 мин → 2 ч" in lines)
        assertTrue(lines.toString(), "Добавлено расписание «Уроки» 15:00–18:00" in lines)
        assertTrue(lines.toString(), "Подарено +20 мин — Задание: Сделать уроки" in lines)
        assertTrue(lines.toString(), "Добавлена геозона «Школа»" in lines)
    }

    @Test
    fun unchangedPolicyHasNoLines() {
        assertEquals(emptyList<String>(), PolicyDiff.child(ChildPolicy(), ChildPolicy()))
        assertEquals(emptyList<String>(), PolicyDiff.elder(ElderPolicy(), ElderPolicy()))
    }

    @Test
    fun elderToggles() {
        val old = ElderPolicy()
        val lines = PolicyDiff.elder(old, old.copy(otpGuard = false, fallDetection = true))
        assertEquals(listOf("Выключена защита кодов из СМС", "Включено определение падения"), lines)
    }
}
