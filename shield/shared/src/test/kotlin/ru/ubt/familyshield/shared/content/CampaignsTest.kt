package ru.ubt.familyshield.shared.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import ru.ubt.familyshield.shared.model.AlertSeverity
import ru.ubt.familyshield.shared.model.AlertType
import java.time.LocalDate

class CampaignsTest {
    @Test
    fun newYearCampaignWrapsAroundTheYear() {
        assertEquals("new_year", Campaigns.active(LocalDate.of(2026, 12, 20))?.id)
        assertEquals("new_year", Campaigns.active(LocalDate.of(2027, 1, 10))?.id)
        assertEquals("pension", Campaigns.active(LocalDate.of(2027, 1, 20))?.id)
    }

    @Test
    fun familyDayWinsOverSummer() {
        assertEquals("family_day", Campaigns.active(LocalDate.of(2026, 7, 8))?.id)
        assertEquals("summer", Campaigns.active(LocalDate.of(2026, 7, 20))?.id)
    }

    @Test
    fun someMonthsHaveNoCampaign() {
        assertNull(Campaigns.active(LocalDate.of(2026, 4, 15)))
    }

    @Test
    fun lessonLinksPointToRealLessons() {
        Campaigns.all.mapNotNull { it.lessonId }.forEach { assertNotNull(it, SafetySchool.byId(it)) }
        AlertType.entries.mapNotNull { AlertAdvices.of(it)?.lessonId }.forEach { assertNotNull(it, SafetySchool.byId(it)) }
    }

    @Test
    fun everyWarningHasAdvice() {
        AlertType.entries.filter { it.defaultSeverity >= AlertSeverity.WARNING }.forEach { assertNotNull(it.name, AlertAdvices.of(it)) }
    }
}
