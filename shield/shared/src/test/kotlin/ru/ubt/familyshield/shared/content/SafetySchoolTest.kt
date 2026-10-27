package ru.ubt.familyshield.shared.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SafetySchoolTest {
    @Test
    fun lessonsAreWellFormed() {
        val lessons = SafetySchool.lessons
        assertEquals("lesson ids must be unique", lessons.size, lessons.map { it.id }.toSet().size)
        for (l in lessons) {
            assertTrue("${l.id}: red flags", l.redFlags.size >= 2)
            assertTrue("${l.id}: what to do", l.whatToDo.isNotEmpty())
            assertEquals("${l.id}: three questions", 3, l.quiz.size)
            for (q in l.quiz) {
                assertTrue("${l.id}: options", q.options.size >= 3)
                assertTrue("${l.id}: correct answer index", q.correct in q.options.indices)
                assertTrue("${l.id}: explanation", q.explanation.isNotBlank())
            }
        }
    }

    @Test
    fun protectionStepsHaveInstructions() {
        val steps = GosProtections.steps
        assertEquals(steps.size, steps.map { it.id }.toSet().size)
        steps.forEach { assertTrue(it.id, it.how.isNotEmpty()) }
        steps.mapNotNull { it.link }.forEach { assertTrue(it, it.startsWith("https://")) }
    }
}
