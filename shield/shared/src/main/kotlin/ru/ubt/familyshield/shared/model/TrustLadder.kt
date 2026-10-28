package ru.ubt.familyshield.shared.model

import kotlinx.serialization.Serializable

/**
 * «Лестница доверия»: rules soften as a child grows up and earns trust with tasks. Every level adds to the
 * ones below it. The parents' own settings are never rewritten: the perks are applied on top of them by
 * [withTrust], so going back a level restores exactly what the parents had set.
 */
@Serializable
enum class TrustLevel(val title: String, val minPoints: Int, val minAge: Int, val perks: List<String>) {
    START("Старт", 0, 0, listOf("Правила, которые настроили родители")),
    STEADY("Надёжный", 60, 10, listOf("+30 минут к дневному лимиту", "Сон начинается на 30 минут позже")),
    INDEPENDENT("Самостоятельный", 200, 10, listOf("Новые приложения без одобрения", "Родители узнают только о серьёзных угрозах в переписке")),
    ADULT("Почти взрослый", 450, 13, listOf("Выходные без лимита", "Геопозиция раз в час, остальное — по просьбе «Отметься»"));

    val next: TrustLevel? get() = entries.getOrNull(ordinal + 1)

    /** A child of [age] with [points] from accepted tasks may ask for this level. */
    fun reachable(points: Int, age: AgeBand): Boolean = points >= minPoints && age.maxAge >= minAge

    companion object {
        /** Younger children keep the rules their parents set; the ladder starts at ten. */
        fun availableFor(age: AgeBand): Boolean = age.maxAge >= 10
    }
}

/** The rules as the child's phone enforces them: the parents' policy plus the perks of its trust level. */
fun ChildPolicy.withTrust(): ChildPolicy {
    var p = this
    if (trustLevel >= TrustLevel.STEADY) {
        p = p.copy(
            dailyLimitWeekdayMinutes = p.dailyLimitWeekdayMinutes?.plus(30),
            dailyLimitWeekendMinutes = p.dailyLimitWeekendMinutes?.plus(30),
            schedules = p.schedules.map { if (it.id == "sleep") it.copy(window = it.window.startLater(30)) else it },
        )
    }
    if (trustLevel >= TrustLevel.INDEPENDENT) {
        p = p.copy(requireApprovalForNewApps = false, messageMonitoring = p.messageMonitoring.copy(criticalOnly = true))
    }
    if (trustLevel >= TrustLevel.ADULT) {
        p = p.copy(dailyLimitWeekendMinutes = null, locationIntervalMinutes = maxOf(p.locationIntervalMinutes, 60))
    }
    return p
}

/**
 * The window starting [minutes] later. A start that would cross midnight stays as it is: [TimeWindow.days]
 * names the day a window starts on, so moving it past midnight would move it to another day.
 */
fun TimeWindow.startLater(minutes: Int): TimeWindow {
    val start = startMinute + minutes
    if (start >= TimeWindow.MINUTES_PER_DAY) return this
    if (!wrapsMidnight && start >= endMinute) return this
    return copy(startMinute = start)
}
