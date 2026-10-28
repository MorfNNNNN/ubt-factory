package ru.ubt.familyshield.shared.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class CalendarTest {
    private val zone = ZoneId.of("Europe/Moscow")
    private fun at(y: Int, m: Int, d: Int, h: Int = 0, min: Int = 0) = LocalDateTime.of(y, m, d, h, min).atZone(zone).toInstant().toEpochMilli()
    private fun day(t: Long) = java.time.Instant.ofEpochMilli(t).atZone(zone).toLocalDate()
    private fun event(startsAt: Long, repeat: EventRepeat = EventRepeat.ONCE, days: Set<Int> = emptySet()) =
        FamilyEvent(id = "e", title = "Футбол", startsAt = startsAt, repeat = repeat, days = days, createdBy = "mom", createdAt = 0)

    @Test
    fun oneOffEventHappensOnce() {
        val e = event(at(2026, 10, 28, 14, 30))
        assertEquals(listOf(at(2026, 10, 28, 14, 30)), e.occurrences(at(2026, 10, 28), at(2026, 10, 29), zone))
        assertTrue(e.occurrences(at(2026, 10, 29), at(2026, 11, 5), zone).isEmpty())
        assertNull(e.nextOccurrence(at(2026, 10, 28, 15), zone))
    }

    @Test
    fun weeklyEventRepeatsOnChosenDaysAtTheSameTime() {
        // Starts on Tuesday 27 October; repeats on Tuesdays and Thursdays at 17:00.
        val e = event(at(2026, 10, 27, 17), EventRepeat.WEEKLY, setOf(2, 4))
        val list = e.occurrences(at(2026, 10, 26), at(2026, 11, 9), zone)
        assertEquals(
            listOf(LocalDate.of(2026, 10, 27), LocalDate.of(2026, 10, 29), LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 5)),
            list.map(::day),
        )
        assertEquals(at(2026, 11, 3, 17), e.nextOccurrence(at(2026, 10, 29, 17, 1), zone))
    }

    @Test
    fun weeklyWithoutDaysUsesTheStartWeekday() {
        val e = event(at(2026, 10, 28, 9), EventRepeat.WEEKLY)
        assertEquals(listOf(at(2026, 10, 28, 9), at(2026, 11, 4, 9)), e.occurrences(at(2026, 10, 28), at(2026, 11, 5), zone))
    }

    @Test
    fun nothingBeforeTheFirstOccurrence() {
        val e = event(at(2026, 11, 3, 17), EventRepeat.WEEKLY)
        assertTrue(e.occurrences(at(2026, 10, 1), at(2026, 11, 3), zone).isEmpty())
    }

    @Test
    fun birthdaysRepeatEveryYearAndLeapDayFallsBackTo28th() {
        val birthday = event(at(2016, 2, 29, 10), EventRepeat.YEARLY)
        assertEquals(at(2027, 2, 28, 10), birthday.nextOccurrence(at(2026, 10, 28), zone))
        assertEquals(at(2028, 2, 29, 10), birthday.nextOccurrence(at(2027, 3, 1), zone))
    }

    @Test
    fun deletedEventsHaveNoOccurrences() {
        val e = event(at(2026, 10, 28, 9), EventRepeat.WEEKLY).copy(deleted = true)
        assertTrue(e.occurrences(at(2026, 10, 1), at(2026, 12, 1), zone).isEmpty())
    }

    @Test
    fun eventsConcernTheirPeopleOrTheWholeFamily() {
        val forKid = event(0).copy(forDeviceIds = setOf("kid"), assigneeDeviceId = "dad")
        assertTrue(forKid.concerns("kid"))
        assertTrue(forKid.concerns("dad"))
        assertFalse(forKid.concerns("grandma"))
        assertTrue(event(0).concerns("grandma"))
    }

    @Test
    fun remindersGoToTheRightPhones() {
        val unassigned = event(0).copy(forDeviceIds = setOf("kid"))
        assertTrue(unassigned.remindsDevice("kid", isGuardian = false))
        // Nobody took the event yet: every guardian is reminded.
        assertTrue(unassigned.remindsDevice("mom", isGuardian = true))
        val taken = unassigned.copy(assigneeDeviceId = "dad")
        assertFalse(taken.remindsDevice("mom", isGuardian = true))
        assertTrue(taken.remindsDevice("dad", isGuardian = true))
        assertFalse(taken.copy(remindMinutesBefore = null).remindsDevice("dad", isGuardian = true))
    }

    @Test
    fun reminderFiresLeadMinutesBeforeTheNextOccurrence() {
        val e = event(at(2026, 10, 27, 17), EventRepeat.WEEKLY, setOf(2, 4)).copy(remindMinutesBefore = 30)
        assertEquals(at(2026, 10, 29, 16, 30), e.nextReminderAt(at(2026, 10, 28, 12), zone))
        // Between the reminder and the start the next reminder belongs to the following occurrence.
        assertEquals(at(2026, 11, 3, 16, 30), e.nextReminderAt(at(2026, 10, 29, 16, 45), zone))
    }

    @Test
    fun occurrencesOfSeveralEventsAreSorted() {
        val a = event(at(2026, 10, 28, 18)).copy(id = "a")
        val b = event(at(2026, 10, 28, 8), EventRepeat.WEEKLY).copy(id = "b")
        val list = occurrencesOf(listOf(a, b), at(2026, 10, 28), at(2026, 10, 29), zone)
        assertEquals(listOf("b", "a"), list.map { it.event.id })
    }
}
