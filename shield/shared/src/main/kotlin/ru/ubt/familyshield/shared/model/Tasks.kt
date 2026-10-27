package ru.ubt.familyshield.shared.model

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.WeekFields

@Serializable
enum class TaskRepeat { ONCE, DAILY, WEEKLY }

@Serializable
enum class TaskState { OPEN, DONE, APPROVED, DECLINED }

/**
 * A chore or goal a guardian sets for a child. The child marks it done, a guardian confirms, and the
 * reward minutes are granted as bonus screen time. Repeating tasks open again every day or week.
 */
@Serializable
data class FamilyTask(
    val id: String,
    val assigneeDeviceId: String,
    val title: String,
    /** Template id, used for the icon. */
    val kind: String = "custom",
    val rewardMinutes: Int = 15,
    val points: Int = 10,
    val repeat: TaskRepeat = TaskRepeat.ONCE,
    val dueAt: Long? = null,
    val createdBy: String,
    val createdAt: Long,
    val state: TaskState = TaskState.OPEN,
    val doneAt: Long? = null,
    val reviewedAt: Long? = null,
    val deleted: Boolean = false,
    val updatedAt: Long = createdAt,
) {
    /** The state to show now: a repeating task done in an earlier day/week is open again. */
    fun effectiveState(now: Long, zone: ZoneId = ZoneId.systemDefault()): TaskState {
        if (repeat == TaskRepeat.ONCE || state == TaskState.OPEN) return state
        val last = doneAt ?: reviewedAt ?: return state
        val then = Instant.ofEpochMilli(last).atZone(zone).toLocalDate()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val samePeriod = when (repeat) {
            TaskRepeat.DAILY -> then == today
            TaskRepeat.WEEKLY -> weekOf(then) == weekOf(today)
            TaskRepeat.ONCE -> true
        }
        return if (samePeriod) state else TaskState.OPEN
    }

    private fun weekOf(d: LocalDate): Pair<Int, Int> {
        // Weeks start on Monday in Russia.
        val wf = WeekFields.ISO
        return d.get(wf.weekBasedYear()) to d.get(wf.weekOfWeekBasedYear())
    }
}

data class TaskTemplate(val kind: String, val title: String, val rewardMinutes: Int, val points: Int, val repeat: TaskRepeat)

object TaskTemplates {
    val all = listOf(
        TaskTemplate("homework", "Сделать уроки", 20, 15, TaskRepeat.DAILY),
        TaskTemplate("reading", "Почитать 30 минут", 15, 10, TaskRepeat.DAILY),
        TaskTemplate("room", "Убрать в комнате", 15, 10, TaskRepeat.WEEKLY),
        TaskTemplate("bed", "Заправить кровать", 5, 5, TaskRepeat.DAILY),
        TaskTemplate("dishes", "Помыть посуду", 10, 10, TaskRepeat.DAILY),
        TaskTemplate("trash", "Вынести мусор", 5, 5, TaskRepeat.DAILY),
        TaskTemplate("walk", "Погулять на свежем воздухе", 15, 10, TaskRepeat.DAILY),
        TaskTemplate("sport", "Зарядка или тренировка", 15, 10, TaskRepeat.DAILY),
        TaskTemplate("pet", "Погулять с собакой", 10, 10, TaskRepeat.DAILY),
        TaskTemplate("grandma", "Позвонить бабушке", 10, 10, TaskRepeat.WEEKLY),
        TaskTemplate("poem", "Выучить стихотворение", 20, 20, TaskRepeat.ONCE),
        TaskTemplate("help", "Помочь по дому", 15, 10, TaskRepeat.ONCE),
    )

    fun byKind(kind: String): TaskTemplate? = all.firstOrNull { it.kind == kind }
}

/** A member's progress in «Школа безопасности» and the official-protection checklist. */
@Serializable
data class SafetyProgress(
    /** lesson id -> best quiz score (0..3). */
    val lessons: Map<String, Int> = emptyMap(),
    /** ids of protection steps the member marked as done. */
    val steps: Set<String> = emptySet(),
    val updatedAt: Long = 0,
) {
    /** A lesson counts as passed with at least two right answers out of three. */
    fun passed(lessonId: String): Boolean = (lessons[lessonId] ?: 0) >= 2

    val passedCount: Int get() = lessons.count { it.value >= 2 }
}
