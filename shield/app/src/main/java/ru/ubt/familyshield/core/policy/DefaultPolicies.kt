package ru.ubt.familyshield.core.policy

import ru.ubt.familyshield.shared.model.AgeBand
import ru.ubt.familyshield.shared.model.AppCategory
import ru.ubt.familyshield.shared.model.ChildPolicy
import ru.ubt.familyshield.shared.model.DnsUpstream
import ru.ubt.familyshield.shared.model.MessageMonitoring
import ru.ubt.familyshield.shared.model.ScheduleMode
import ru.ubt.familyshield.shared.model.ScheduleRule
import ru.ubt.familyshield.shared.model.TimeWindow
import ru.ubt.familyshield.shared.model.TimeWindow.Companion.hm
import ru.ubt.familyshield.shared.model.WEEKDAYS
import ru.ubt.familyshield.shared.model.WebCategory
import ru.ubt.familyshield.shared.model.WebFilterSettings

/** Age-appropriate starting rules, based on common paediatric screen-time guidance. Parents adjust them freely. */
object DefaultPolicies {
    private val baseWeb = setOf(
        WebCategory.ADULT, WebCategory.GAMBLING, WebCategory.DRUGS, WebCategory.PHISHING, WebCategory.SCAM,
        WebCategory.MALWARE, WebCategory.PROXY_VPN,
    )

    private fun sleep(start: Int, end: Int, days: Set<Int> = (1..7).toSet()) =
        ScheduleRule("sleep", "Сон", TimeWindow(start, end, days))

    private fun lessons(start: Int, end: Int, categories: Set<AppCategory>) =
        ScheduleRule("school", "Уроки", TimeWindow(start, end, WEEKDAYS), ScheduleMode.BLOCK_CATEGORIES, categories)

    fun forChild(age: AgeBand): ChildPolicy = when (age) {
        AgeBand.UNDER_7 -> ChildPolicy(
            ageBand = age,
            dailyLimitWeekdayMinutes = 60,
            dailyLimitWeekendMinutes = 90,
            schedules = listOf(sleep(hm(20), hm(7, 30))),
            webFilter = WebFilterSettings(enabled = true, categories = baseWeb + setOf(WebCategory.VIOLENCE, WebCategory.SOCIAL, WebCategory.DATING), upstream = DnsUpstream.YANDEX_FAMILY),
            messageMonitoring = MessageMonitoring(enabled = true),
            categoryLimits = mapOf(AppCategory.GAMES to 30, AppCategory.VIDEO to 30),
        )
        AgeBand.AGE_7_9 -> ChildPolicy(
            ageBand = age,
            dailyLimitWeekdayMinutes = 90,
            dailyLimitWeekendMinutes = 150,
            schedules = listOf(sleep(hm(21), hm(7)), lessons(hm(8), hm(13, 30), setOf(AppCategory.GAMES, AppCategory.SOCIAL, AppCategory.VIDEO))),
            webFilter = WebFilterSettings(enabled = true, categories = baseWeb + setOf(WebCategory.VIOLENCE, WebCategory.DATING)),
            messageMonitoring = MessageMonitoring(enabled = true),
            categoryLimits = mapOf(AppCategory.GAMES to 60),
        )
        AgeBand.AGE_10_12 -> ChildPolicy(
            ageBand = age,
            dailyLimitWeekdayMinutes = 120,
            dailyLimitWeekendMinutes = 180,
            schedules = listOf(sleep(hm(21, 30), hm(7)), lessons(hm(8, 30), hm(14), setOf(AppCategory.GAMES, AppCategory.SOCIAL))),
            webFilter = WebFilterSettings(enabled = true, categories = baseWeb + setOf(WebCategory.DATING)),
            messageMonitoring = MessageMonitoring(enabled = true),
        )
        AgeBand.AGE_13_15 -> ChildPolicy(
            ageBand = age,
            dailyLimitWeekdayMinutes = 180,
            dailyLimitWeekendMinutes = 240,
            schedules = listOf(sleep(hm(22, 30), hm(7)), lessons(hm(8, 30), hm(14, 30), setOf(AppCategory.GAMES))),
            webFilter = WebFilterSettings(enabled = true, categories = baseWeb + setOf(WebCategory.DATING), youtubeRestricted = false),
            messageMonitoring = MessageMonitoring(enabled = true),
        )
        AgeBand.AGE_16_17 -> ChildPolicy(
            ageBand = age,
            dailyLimitWeekdayMinutes = null,
            dailyLimitWeekendMinutes = null,
            schedules = listOf(sleep(hm(23, 30), hm(7), WEEKDAYS)),
            requireApprovalForNewApps = false,
            webFilter = WebFilterSettings(enabled = true, categories = baseWeb, safeSearch = false, youtubeRestricted = false, upstream = DnsUpstream.YANDEX_SAFE),
            // Older teens keep their privacy: only life-threatening situations are reported.
            messageMonitoring = MessageMonitoring(enabled = true, criticalOnly = true),
        )
    }
}
