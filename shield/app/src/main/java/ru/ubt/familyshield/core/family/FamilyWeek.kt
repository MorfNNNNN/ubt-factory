package ru.ubt.familyshield.core.family

import ru.ubt.familyshield.core.db.DailyStatEntity
import ru.ubt.familyshield.core.db.ShieldDatabase
import ru.ubt.familyshield.core.notify.Notifier
import ru.ubt.familyshield.core.prefs.SettingsStore
import ru.ubt.familyshield.shared.content.DayStat
import ru.ubt.familyshield.shared.content.WeekMetric
import ru.ubt.familyshield.shared.content.WeekNumbers
import ru.ubt.familyshield.shared.content.WeeklyDigest
import ru.ubt.familyshield.shared.model.AlertSeverity
import ru.ubt.familyshield.shared.model.AlertType
import ru.ubt.familyshield.shared.model.MemberRole
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId

fun DailyStatEntity.toDayStat() = DayStat(date, deviceId, metric, value)

/**
 * «Неделя семьи» on the guardian's phone: events are counted into daily rows as they arrive (the details
 * may be deleted by the plan's history limit, the numbers stay), and on Sunday evening the week is
 * summed up in one notification.
 */
class FamilyWeek(
    private val settings: SettingsStore,
    private val db: ShieldDatabase,
    private val notifier: Notifier,
    private val clock: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) {
    private fun dateOf(t: Long) = Instant.ofEpochMilli(t).atZone(zone()).toLocalDate().toString()

    suspend fun countAlert(deviceId: String, type: AlertType, severity: AlertSeverity, at: Long) {
        val date = dateOf(at)
        db.stats().add(date, deviceId, type.name)
        if (severity >= AlertSeverity.HIGH) db.stats().add(date, deviceId, WeekMetric.DANGER)
    }

    suspend fun screenTime(deviceId: String, date: String, minutes: Int) =
        db.stats().put(DailyStatEntity(date, deviceId, WeekMetric.SCREEN, minutes))

    suspend fun lessonsPassed(deviceId: String, count: Int) {
        if (count > 0) db.stats().add(dateOf(clock()), deviceId, WeekMetric.LESSON, count)
    }

    /** Sunday 18:00–22:59, once a week, only for a family that already protects someone. */
    suspend fun maybeNotify() {
        val s = settings.current
        if (!s.isGuardian || !s.isPaired || !s.weeklyDigest) return
        val now = Instant.ofEpochMilli(clock()).atZone(zone())
        if (now.dayOfWeek != DayOfWeek.SUNDAY || now.hour !in 18..22) return
        val today = now.toLocalDate()
        if (s.lastDigestDate == today.toString()) return
        if (db.members().all().none { !it.isSelf && it.memberRole != MemberRole.GUARDIAN }) return
        val range = WeeklyDigest.thisWeek(today)
        val week = WeekNumbers.of(db.stats().between(range.start, range.endInclusive).map { it.toDayStat() })
        settings.setLastDigestDate(today.toString())
        notifier.digest(WeeklyDigest.title(week), WeeklyDigest.text(week))
    }
}
