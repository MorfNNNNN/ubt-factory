package ru.ubt.familyshield.shared.policy

import ru.ubt.familyshield.shared.detect.AppCatalog
import ru.ubt.familyshield.shared.model.AppCategory
import ru.ubt.familyshield.shared.model.AppMode
import ru.ubt.familyshield.shared.model.ChildPolicy
import ru.ubt.familyshield.shared.model.ScheduleMode
import ru.ubt.familyshield.shared.model.ScheduleRule
import ru.ubt.familyshield.shared.model.TimeWindow
import ru.ubt.familyshield.shared.model.WEEKEND
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

data class Clock(
    val date: LocalDate,
    val minuteOfDay: Int,
    /** ISO day of week, 1 = Monday. */
    val isoDay: Int,
    val epochMillis: Long,
) {
    val yesterdayIsoDay: Int get() = if (isoDay == 1) 7 else isoDay - 1

    companion object {
        fun of(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): Clock {
            val dt = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(epochMillis), zone)
            return Clock(dt.toLocalDate(), dt.hour * 60 + dt.minute, dt.dayOfWeek.value, epochMillis)
        }
    }
}

data class UsageSnapshot(
    val totalMinutes: Int,
    val perApp: Map<String, Int>,
    val perCategory: Map<AppCategory, Int>,
) {
    companion object {
        val EMPTY = UsageSnapshot(0, emptyMap(), emptyMap())
    }
}

enum class BlockReason(val titleRu: String) {
    PAUSED("Телефон на паузе"),
    SCHEDULE("Сейчас действует расписание"),
    DAILY_LIMIT("Время на сегодня закончилось"),
    APP_LIMIT("Лимит для этого приложения исчерпан"),
    CATEGORY_LIMIT("Лимит для этой категории исчерпан"),
    APP_BLOCKED("Приложение заблокировано родителем"),
    PENDING_APPROVAL("Новое приложение ждёт одобрения родителя"),
    BYPASS_TOOL("VPN и анонимайзеры недоступны"),
}

sealed class AccessDecision {
    data class Allowed(val remainingMinutes: Int?, val warn: Boolean) : AccessDecision()

    data class Blocked(
        val reason: BlockReason,
        val detail: String = "",
        /** Minute of day when access returns automatically (schedules), if known. */
        val untilMinuteOfDay: Int? = null,
        val untilEpochMillis: Long? = null,
    ) : AccessDecision()
}

/** Pure evaluation of parental rules. All Android-specific inputs are passed in. */
object ScreenTimeEngine {

    fun dailyLimit(policy: ChildPolicy, clock: Clock): Int? {
        val base = if (clock.isoDay in WEEKEND) policy.dailyLimitWeekendMinutes else policy.dailyLimitWeekdayMinutes
        base ?: return null
        return base + bonusMinutes(policy, clock)
    }

    fun bonusMinutes(policy: ChildPolicy, clock: Clock): Int {
        val today = clock.date.toString()
        return policy.bonuses.filter { it.date == today }.sumOf { it.minutes }
    }

    fun isActive(window: TimeWindow, clock: Clock): Boolean {
        val m = clock.minuteOfDay
        return if (!window.wrapsMidnight) {
            clock.isoDay in window.days && m >= window.startMinute && m < window.endMinute
        } else {
            (clock.isoDay in window.days && m >= window.startMinute) ||
                (clock.yesterdayIsoDay in window.days && m < window.endMinute)
        }
    }

    fun activeSchedules(policy: ChildPolicy, clock: Clock): List<ScheduleRule> =
        policy.schedules.filter { it.enabled && isActive(it.window, clock) }

    fun decide(
        policy: ChildPolicy,
        packageName: String,
        category: AppCategory,
        usage: UsageSnapshot,
        clock: Clock,
        /** Launcher, keyboard, dialer and other system packages resolved on the device. */
        essential: Boolean = false,
        /** Installed after protection was enabled and not approved yet. */
        newUnapproved: Boolean = false,
    ): AccessDecision {
        val rule = policy.appRules[packageName]
        if (essential || AppCatalog.isEssential(packageName) || rule?.mode == AppMode.ALWAYS_ALLOWED) {
            return AccessDecision.Allowed(null, false)
        }
        val paused = policy.pausedUntil
        if (paused != null && paused > clock.epochMillis) {
            return AccessDecision.Blocked(BlockReason.PAUSED, untilEpochMillis = paused)
        }
        if (rule?.mode == AppMode.BLOCKED) return AccessDecision.Blocked(BlockReason.APP_BLOCKED)
        if (policy.requireApprovalForNewApps && newUnapproved && packageName !in policy.approvedPackages) {
            return AccessDecision.Blocked(BlockReason.PENDING_APPROVAL)
        }
        if (policy.webFilter.enabled && AppCatalog.isBypassTool(packageName)) {
            return AccessDecision.Blocked(BlockReason.BYPASS_TOOL, AppCatalog.bypassTools[packageName].orEmpty())
        }

        for (schedule in activeSchedules(policy, clock)) {
            val applies = schedule.mode == ScheduleMode.BLOCK_ALL || category in schedule.categories
            if (applies) {
                return AccessDecision.Blocked(BlockReason.SCHEDULE, schedule.name, untilMinuteOfDay = schedule.window.endMinute % TimeWindow.MINUTES_PER_DAY)
            }
        }

        val remaining = ArrayList<Int>()
        rule?.dailyLimitMinutes?.let { limit ->
            val used = usage.perApp[packageName] ?: 0
            if (used >= limit) return AccessDecision.Blocked(BlockReason.APP_LIMIT, "$limit мин")
            remaining += limit - used
        }
        policy.categoryLimits[category]?.let { limit ->
            val used = usage.perCategory[category] ?: 0
            if (used >= limit) return AccessDecision.Blocked(BlockReason.CATEGORY_LIMIT, "$limit мин")
            remaining += limit - used
        }
        dailyLimit(policy, clock)?.let { limit ->
            if (usage.totalMinutes >= limit) return AccessDecision.Blocked(BlockReason.DAILY_LIMIT, "$limit мин")
            remaining += limit - usage.totalMinutes
        }
        val left = remaining.minOrNull()
        return AccessDecision.Allowed(left, left != null && left <= policy.warnBeforeLimitMinutes)
    }

    /** Minutes of total screen time left today, or null if unlimited. */
    fun remainingToday(policy: ChildPolicy, usage: UsageSnapshot, clock: Clock): Int? =
        dailyLimit(policy, clock)?.let { (it - usage.totalMinutes).coerceAtLeast(0) }

    /** Next schedule start today (minute of day) for the child's status screen. */
    fun nextScheduleStart(policy: ChildPolicy, clock: Clock): Pair<ScheduleRule, Int>? =
        policy.schedules.filter { it.enabled && clock.isoDay in it.window.days && it.window.startMinute > clock.minuteOfDay }
            .minByOrNull { it.window.startMinute }?.let { it to it.window.startMinute }
}

/**
 * A request to turn protection off on an elder's phone. It only takes effect after a cooling-off
 * period, or earlier if a guardian approves — scammers often coach victims to disable protection.
 */
@kotlinx.serialization.Serializable
data class ProtectionChangeRequest(
    val id: String,
    val requestedAt: Long,
    val coolingOffHours: Int,
    val approvedByGuardian: Boolean = false,
) {
    val effectiveAt: Long get() = requestedAt + coolingOffHours * 3_600_000L

    fun canApply(now: Long): Boolean = approvedByGuardian || now >= effectiveAt

    fun remainingMillis(now: Long): Long = if (canApply(now)) 0 else effectiveAt - now
}

fun formatMinutes(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0 -> "$m мин"
        m == 0 -> "$h ч"
        else -> "$h ч $m мин"
    }
}

fun formatMinuteOfDay(minute: Int): String = "%02d:%02d".format((minute / 60) % 24, minute % 60)
