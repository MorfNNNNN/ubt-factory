package ru.ubt.familyshield.shared.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.ubt.familyshield.shared.model.AppCategory
import ru.ubt.familyshield.shared.model.AppMode
import ru.ubt.familyshield.shared.model.AppRule
import ru.ubt.familyshield.shared.model.BonusGrant
import ru.ubt.familyshield.shared.model.ChildPolicy
import ru.ubt.familyshield.shared.model.ScheduleMode
import ru.ubt.familyshield.shared.model.ScheduleRule
import ru.ubt.familyshield.shared.model.TimeWindow
import ru.ubt.familyshield.shared.model.WEEKDAYS
import ru.ubt.familyshield.shared.model.WebFilterSettings
import java.time.LocalDate

class ScreenTimeEngineTest {
    // 2026-10-20 is a Tuesday.
    private fun clock(h: Int, m: Int = 0, date: LocalDate = LocalDate.of(2026, 10, 20)) =
        Clock(date, h * 60 + m, date.dayOfWeek.value, 1_000_000L)

    private val noSchedules = ChildPolicy(schedules = emptyList(), webFilter = WebFilterSettings(enabled = false))

    private fun decide(policy: ChildPolicy, pkg: String = "com.roblox.client", usage: UsageSnapshot = UsageSnapshot.EMPTY, c: Clock = clock(15), newApp: Boolean = false) =
        ScreenTimeEngine.decide(policy, pkg, AppCategory.GAMES, usage, c, newUnapproved = newApp)

    @Test
    fun bedtimeWrapsMidnight() {
        val policy = ChildPolicy() // default "Сон" 21:30–07:00 every day
        assertTrue(decide(policy, c = clock(22)) is AccessDecision.Blocked)
        assertTrue(decide(policy, c = clock(6, 59)) is AccessDecision.Blocked)
        assertTrue(decide(policy, c = clock(7, 0)) is AccessDecision.Allowed)
        val blocked = decide(policy, c = clock(23)) as AccessDecision.Blocked
        assertEquals(BlockReason.SCHEDULE, blocked.reason)
        assertEquals(7 * 60, blocked.untilMinuteOfDay)
    }

    @Test
    fun wrappedWindowUsesStartDay() {
        // Only Friday night (Fri 22:00 → Sat 08:00).
        val policy = noSchedules.copy(schedules = listOf(ScheduleRule("f", "Пятница", TimeWindow(22 * 60, 8 * 60, setOf(5)))))
        val saturdayMorning = clock(7, date = LocalDate.of(2026, 10, 24))
        val sundayMorning = clock(7, date = LocalDate.of(2026, 10, 25))
        assertTrue(decide(policy, c = saturdayMorning) is AccessDecision.Blocked)
        assertTrue(decide(policy, c = sundayMorning) is AccessDecision.Allowed)
    }

    @Test
    fun dailyLimitWithBonusAndWarning() {
        val policy = noSchedules.copy(dailyLimitWeekdayMinutes = 60, warnBeforeLimitMinutes = 5)
        val used = UsageSnapshot(58, mapOf("com.roblox.client" to 58), mapOf(AppCategory.GAMES to 58))
        val allowed = decide(policy, usage = used) as AccessDecision.Allowed
        assertEquals(2, allowed.remainingMinutes)
        assertTrue(allowed.warn)
        val over = UsageSnapshot(60, emptyMap(), emptyMap())
        assertEquals(BlockReason.DAILY_LIMIT, (decide(policy, usage = over) as AccessDecision.Blocked).reason)
        val withBonus = policy.copy(bonuses = listOf(BonusGrant("b", "2026-10-20", 30)))
        assertEquals(30, (decide(withBonus, usage = over) as AccessDecision.Allowed).remainingMinutes)
        val oldBonus = policy.copy(bonuses = listOf(BonusGrant("b", "2026-10-19", 30)))
        assertTrue(decide(oldBonus, usage = over) is AccessDecision.Blocked)
    }

    @Test
    fun appAndCategoryLimits() {
        val policy = noSchedules.copy(
            dailyLimitWeekdayMinutes = null,
            appRules = mapOf("com.roblox.client" to AppRule("com.roblox.client", dailyLimitMinutes = 30)),
            categoryLimits = mapOf(AppCategory.GAMES to 45),
        )
        val u1 = UsageSnapshot(40, mapOf("com.roblox.client" to 30), mapOf(AppCategory.GAMES to 30))
        assertEquals(BlockReason.APP_LIMIT, (decide(policy, usage = u1) as AccessDecision.Blocked).reason)
        val u2 = UsageSnapshot(50, mapOf("com.roblox.client" to 10), mapOf(AppCategory.GAMES to 45))
        assertEquals(BlockReason.CATEGORY_LIMIT, (decide(policy, usage = u2) as AccessDecision.Blocked).reason)
    }

    @Test
    fun alwaysAllowedEssentialBlockedAndPaused() {
        val policy = noSchedules.copy(
            pausedUntil = 2_000_000L,
            appRules = mapOf(
                "ru.dnevnik.app" to AppRule("ru.dnevnik.app", AppMode.ALWAYS_ALLOWED),
                "com.zhiliaoapp.musically" to AppRule("com.zhiliaoapp.musically", AppMode.BLOCKED),
            ),
        )
        assertEquals(BlockReason.PAUSED, (decide(policy) as AccessDecision.Blocked).reason)
        assertTrue(decide(policy, pkg = "ru.dnevnik.app") is AccessDecision.Allowed)
        assertTrue(decide(policy, pkg = "com.google.android.dialer") is AccessDecision.Allowed)
        val unpaused = policy.copy(pausedUntil = null)
        assertEquals(BlockReason.APP_BLOCKED, (decide(unpaused, pkg = "com.zhiliaoapp.musically") as AccessDecision.Blocked).reason)
    }

    @Test
    fun newAppsNeedApproval() {
        val policy = noSchedules.copy(requireApprovalForNewApps = true)
        assertEquals(BlockReason.PENDING_APPROVAL, (decide(policy, pkg = "com.new.game", newApp = true) as AccessDecision.Blocked).reason)
        assertTrue(decide(policy.copy(approvedPackages = setOf("com.new.game")), pkg = "com.new.game", newApp = true) is AccessDecision.Allowed)
    }

    @Test
    fun categoryScheduleOnlyBlocksListedCategories() {
        val lessons = ScheduleRule("l", "Уроки", TimeWindow(8 * 60 + 30, 14 * 60, WEEKDAYS), ScheduleMode.BLOCK_CATEGORIES, setOf(AppCategory.GAMES))
        val policy = noSchedules.copy(schedules = listOf(lessons))
        assertTrue(decide(policy, c = clock(10)) is AccessDecision.Blocked)
        val browser = ScreenTimeEngine.decide(policy, "com.android.chrome", AppCategory.BROWSER, UsageSnapshot.EMPTY, clock(10))
        assertTrue(browser is AccessDecision.Allowed)
    }

    @Test
    fun vpnAppsBlockedWhenFilterEnabled() {
        val policy = noSchedules.copy(webFilter = WebFilterSettings(enabled = true))
        assertEquals(BlockReason.BYPASS_TOOL, (decide(policy, pkg = "com.psiphon3") as AccessDecision.Blocked).reason)
    }

    @Test
    fun coolingOff() {
        val r = ProtectionChangeRequest("r", requestedAt = 0, coolingOffHours = 24)
        assertTrue(!r.canApply(23 * 3_600_000L))
        assertTrue(r.canApply(24 * 3_600_000L))
        assertTrue(r.copy(approvedByGuardian = true).canApply(1))
        assertEquals("1 ч 30 мин", formatMinutes(90))
        assertEquals("07:05", formatMinuteOfDay(425))
    }
}
