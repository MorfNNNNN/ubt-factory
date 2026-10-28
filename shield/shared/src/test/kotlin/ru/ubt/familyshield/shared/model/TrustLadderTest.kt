package ru.ubt.familyshield.shared.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.ubt.familyshield.shared.policy.PolicyDiff

class TrustLadderTest {
    private val base = ChildPolicy(
        ageBand = AgeBand.AGE_13_15,
        dailyLimitWeekdayMinutes = 120,
        dailyLimitWeekendMinutes = 180,
        schedules = listOf(ScheduleRule("sleep", "Сон", TimeWindow(TimeWindow.hm(22), TimeWindow.hm(7)))),
        requireApprovalForNewApps = true,
        messageMonitoring = MessageMonitoring(enabled = true),
        locationIntervalMinutes = 15,
    )

    @Test
    fun startKeepsTheParentsRules() {
        assertEquals(base, base.withTrust())
    }

    @Test
    fun perksAddUpLevelByLevel() {
        val steady = base.copy(trustLevel = TrustLevel.STEADY).withTrust()
        assertEquals(150, steady.dailyLimitWeekdayMinutes)
        assertEquals(210, steady.dailyLimitWeekendMinutes)
        assertEquals(TimeWindow.hm(22, 30), steady.schedules.single().window.startMinute)
        assertTrue(steady.requireApprovalForNewApps)

        val independent = base.copy(trustLevel = TrustLevel.INDEPENDENT).withTrust()
        assertFalse(independent.requireApprovalForNewApps)
        assertTrue(independent.messageMonitoring.criticalOnly)
        assertEquals(150, independent.dailyLimitWeekdayMinutes)

        val adult = base.copy(trustLevel = TrustLevel.ADULT).withTrust()
        assertNull(adult.dailyLimitWeekendMinutes)
        assertEquals(150, adult.dailyLimitWeekdayMinutes)
        assertEquals(60, adult.locationIntervalMinutes)
    }

    @Test
    fun goingBackRestoresTheParentsSettings() {
        val raised = base.copy(trustLevel = TrustLevel.ADULT)
        assertEquals(base, raised.copy(trustLevel = TrustLevel.START).withTrust())
    }

    @Test
    fun bedtimeNeverMovesPastMidnight() {
        assertEquals(TimeWindow.hm(23, 45), TimeWindow(TimeWindow.hm(23, 45), TimeWindow.hm(7)).startLater(30).startMinute)
        assertEquals(TimeWindow.hm(20, 30), TimeWindow(TimeWindow.hm(20, 30), TimeWindow.hm(20, 50)).startLater(30).startMinute)
    }

    @Test
    fun levelsNeedPointsAndAge() {
        assertTrue(TrustLevel.STEADY.reachable(60, AgeBand.AGE_10_12))
        assertFalse(TrustLevel.STEADY.reachable(59, AgeBand.AGE_10_12))
        assertFalse(TrustLevel.ADULT.reachable(1000, AgeBand.AGE_10_12))
        assertTrue(TrustLevel.ADULT.reachable(450, AgeBand.AGE_13_15))
        assertFalse(TrustLevel.availableFor(AgeBand.AGE_7_9))
        assertTrue(TrustLevel.availableFor(AgeBand.AGE_10_12))
        assertNull(TrustLevel.ADULT.next)
    }

    @Test
    fun levelChangeIsJournaled() {
        val lines = PolicyDiff.child(base, base.copy(trustLevel = TrustLevel.STEADY))
        assertEquals(listOf("Уровень доверия: «Старт» → «Надёжный»"), lines)
    }
}
