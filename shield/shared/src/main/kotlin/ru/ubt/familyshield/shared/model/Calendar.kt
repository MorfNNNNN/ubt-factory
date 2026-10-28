package ru.ubt.familyshield.shared.model

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Serializable
enum class EventKind(val titleRu: String) {
    PICKUP("Забрать"),
    CLUB("Кружок"),
    SCHOOL("Учёба"),
    DOCTOR("Врач"),
    BIRTHDAY("День рождения"),
    VISIT("В гости"),
    OTHER("Другое"),
}

@Serializable
enum class EventRepeat { ONCE, WEEKLY, YEARLY }

/**
 * «Семейный календарь»: who picks up whom, clubs, doctor visits and birthdays. A guardian creates an event,
 * it reaches the people it concerns, and every phone reminds its owner in time.
 */
@Serializable
data class FamilyEvent(
    val id: String,
    val title: String,
    val kind: EventKind = EventKind.OTHER,
    /** First occurrence, epoch millis. Repeats keep its local time of day. */
    val startsAt: Long,
    val repeat: EventRepeat = EventRepeat.ONCE,
    /** Weekly events: ISO days to repeat on; empty means the weekday of [startsAt]. */
    val days: Set<IsoDay> = emptySet(),
    /** Who the event is about; empty means the whole family. */
    val forDeviceIds: Set<String> = emptySet(),
    /** Who takes care of it ("кто забирает"); null while nobody has been chosen. */
    val assigneeDeviceId: String? = null,
    /** Kept with the event, so phones that do not know every family member still show the name. */
    val assigneeName: String = "",
    val place: String = "",
    val note: String = "",
    /** Reminder lead time in minutes; null turns reminders off. */
    val remindMinutesBefore: Int? = 30,
    val createdBy: String,
    val createdAt: Long,
    val updatedAt: Long = createdAt,
    val deleted: Boolean = false,
) {
    val isRepeating: Boolean get() = repeat != EventRepeat.ONCE

    fun concerns(deviceId: String): Boolean = forDeviceIds.isEmpty() || deviceId in forDeviceIds || deviceId == assigneeDeviceId

    /**
     * Whose phone reminds: the one who takes care of it, the people it is about, everyone for family-wide
     * events, and every guardian while nobody has taken the event yet.
     */
    fun remindsDevice(deviceId: String, isGuardian: Boolean): Boolean =
        remindMinutesBefore != null && !deleted && (concerns(deviceId) || (assigneeDeviceId == null && isGuardian))

    /** When the reminder for the next occurrence after [now] fires; null without reminders or occurrences. */
    fun nextReminderAt(now: Long, zone: ZoneId = ZoneId.systemDefault()): Long? {
        val lead = (remindMinutesBefore ?: return null) * 60_000L
        return nextOccurrence(now + lead, zone)?.minus(lead)
    }

    /** Start times of the occurrences in [from, to), oldest first. */
    fun occurrences(from: Long, to: Long, zone: ZoneId = ZoneId.systemDefault()): List<Long> {
        if (deleted || to <= from) return emptyList()
        if (repeat == EventRepeat.ONCE) return if (startsAt >= from && startsAt < to) listOf(startsAt) else emptyList()
        val start = Instant.ofEpochMilli(startsAt).atZone(zone)
        val time = start.toLocalTime()
        val weekly = days.ifEmpty { setOf(start.dayOfWeek.value) }
        var day = maxOf(start.toLocalDate(), Instant.ofEpochMilli(from).atZone(zone).toLocalDate())
        val lastDay = Instant.ofEpochMilli(to).atZone(zone).toLocalDate()
        val out = ArrayList<Long>()
        while (!day.isAfter(lastDay)) {
            val matches = when (repeat) {
                EventRepeat.WEEKLY -> day.dayOfWeek.value in weekly
                EventRepeat.YEARLY -> sameDayOfYear(start.toLocalDate(), day)
                EventRepeat.ONCE -> false
            }
            if (matches) {
                val t = day.atTime(time).atZone(zone).toInstant().toEpochMilli()
                if (t >= from && t < to) out += t
            }
            day = day.plusDays(1)
        }
        return out
    }

    /** The first occurrence starting at or after [after]; null when there is none (past one-off events). */
    fun nextOccurrence(after: Long, zone: ZoneId = ZoneId.systemDefault()): Long? =
        occurrences(after, after + 367L * DAY_MILLIS, zone).firstOrNull()

    private fun sameDayOfYear(origin: LocalDate, day: LocalDate): Boolean {
        if (day.monthValue != origin.monthValue) return false
        if (day.dayOfMonth == origin.dayOfMonth) return true
        // A birthday on 29 February is celebrated on the 28th in other years.
        return origin.monthValue == 2 && origin.dayOfMonth == 29 && !day.isLeapYear && day.dayOfMonth == 28
    }

    companion object {
        const val DAY_MILLIS = 86_400_000L
    }
}

/** One occurrence of an event, ready to show in a list. */
data class EventOccurrence(val event: FamilyEvent, val startsAt: Long)

/** All occurrences of [events] in [from, to), in time order. */
fun occurrencesOf(events: Iterable<FamilyEvent>, from: Long, to: Long, zone: ZoneId = ZoneId.systemDefault()): List<EventOccurrence> =
    events.flatMap { e -> e.occurrences(from, to, zone).map { EventOccurrence(e, it) } }.sortedBy { it.startsAt }
