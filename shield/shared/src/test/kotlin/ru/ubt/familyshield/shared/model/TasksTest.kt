package ru.ubt.familyshield.shared.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.ubt.familyshield.shared.ShieldJson
import ru.ubt.familyshield.shared.protocol.FamilyPayload
import ru.ubt.familyshield.shared.protocol.InnerMessage
import java.time.LocalDateTime
import java.time.ZoneId

class TasksTest {
    private val zone = ZoneId.of("Europe/Moscow")
    private fun at(day: Int, hour: Int = 12) = LocalDateTime.of(2026, 10, day, hour, 0).atZone(zone).toInstant().toEpochMilli()
    private fun task(repeat: TaskRepeat, state: TaskState, doneAt: Long?) =
        FamilyTask("t", "kid", "Уроки", repeat = repeat, createdBy = "mom", createdAt = at(1), state = state, doneAt = doneAt)

    @Test
    fun onceTasksKeepTheirState() {
        assertEquals(TaskState.APPROVED, task(TaskRepeat.ONCE, TaskState.APPROVED, at(5)).effectiveState(at(20), zone))
    }

    @Test
    fun dailyTasksReopenTheNextDay() {
        val t = task(TaskRepeat.DAILY, TaskState.APPROVED, at(21, 18))
        assertEquals(TaskState.APPROVED, t.effectiveState(at(21, 23), zone))
        assertEquals(TaskState.OPEN, t.effectiveState(at(22, 8), zone))
    }

    @Test
    fun weeklyTasksReopenTheNextWeek() {
        // 2026-10-19 is a Monday
        val t = task(TaskRepeat.WEEKLY, TaskState.DONE, at(20))
        assertEquals(TaskState.DONE, t.effectiveState(at(25), zone))
        assertEquals(TaskState.OPEN, t.effectiveState(at(26), zone))
    }

    @Test
    fun safetyProgressCountsPassedLessons() {
        val p = SafetyProgress(lessons = mapOf("a" to 3, "b" to 1, "c" to 2))
        assertEquals(2, p.passedCount)
        assertTrue(p.passed("a"))
        assertFalse(p.passed("b"))
    }

    @Test
    fun newPayloadsRoundTrip() {
        val t = task(TaskRepeat.DAILY, TaskState.OPEN, null)
        for (p in listOf(FamilyPayload.TaskUpdate(t), FamilyPayload.TaskDone("t", at(3)), FamilyPayload.TaskReview("t", true, at(3)),
            FamilyPayload.Learning(SafetyProgress(mapOf("bank_security" to 3), setOf("credit_ban"), at(3))))) {
            val json = ShieldJson.encodeToString(InnerMessage.serializer(), InnerMessage(at(3), p))
            assertEquals(p, ShieldJson.decodeFromString(InnerMessage.serializer(), json).payload)
        }
        assertTrue(FamilyPayload.TaskUpdate(t).requiresGuardianSignature)
        assertFalse(FamilyPayload.TaskDone("t", 0).requiresGuardianSignature)
    }
}
