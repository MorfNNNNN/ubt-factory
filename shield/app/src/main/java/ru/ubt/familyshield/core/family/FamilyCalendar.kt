package ru.ubt.familyshield.core.family

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import ru.ubt.familyshield.core.db.CalendarEntity
import ru.ubt.familyshield.core.db.ShieldDatabase
import ru.ubt.familyshield.core.notify.Notifier
import ru.ubt.familyshield.core.prefs.SettingsStore
import ru.ubt.familyshield.protect.service.ReminderReceiver
import ru.ubt.familyshield.shared.ShieldJson
import ru.ubt.familyshield.shared.content.plural
import ru.ubt.familyshield.shared.model.EventKind
import ru.ubt.familyshield.shared.model.FamilyEvent
import ru.ubt.familyshield.shared.model.MemberRole
import ru.ubt.familyshield.shared.protocol.Audience
import ru.ubt.familyshield.shared.protocol.FamilyPayload
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun FamilyEvent.toEntity() = CalendarEntity(id, startsAt, isRepeating, updatedAt, deleted, ShieldJson.encodeToString(FamilyEvent.serializer(), this))

fun CalendarEntity.eventOrNull(): FamilyEvent? = runCatching { ShieldJson.decodeFromString(FamilyEvent.serializer(), json) }.getOrNull()

/** Who takes care of the event, phrased for its kind; null when nobody has taken it. */
fun assigneeLine(event: FamilyEvent, me: String?): String? {
    if (event.assigneeDeviceId == null) return null
    if (event.assigneeDeviceId == me) {
        return when (event.kind) {
            EventKind.PICKUP -> "Забираете вы"
            EventKind.DOCTOR, EventKind.VISIT -> "Отвозите вы"
            else -> "Это ваше дело"
        }
    }
    val name = event.assigneeName.ifBlank { "близкий" }
    return when (event.kind) {
        EventKind.PICKUP -> "Заберёт: $name"
        EventKind.DOCTOR, EventKind.VISIT -> "Отвезёт: $name"
        else -> "Займётся: $name"
    }
}

/** "за 30 минут", "за час", "за день": the reminder choice in the editor. */
fun reminderLeadLabel(minutes: Int): String = when {
    minutes >= 24 * 60 -> "за день"
    minutes == 60 -> "за час"
    minutes % 60 == 0 -> "за ${minutes / 60} ${plural(minutes / 60, "час", "часа", "часов")}"
    else -> "за $minutes ${plural(minutes, "минуту", "минуты", "минут")}"
}

/** "через 30 минут", "через час", "завтра": the same lead as seen when the reminder fires. */
fun reminderSoonLabel(minutes: Int): String = when {
    minutes >= 24 * 60 -> "завтра"
    minutes == 60 -> "через час"
    minutes % 60 == 0 -> "через ${minutes / 60} ${plural(minutes / 60, "час", "часа", "часов")}"
    else -> "через $minutes ${plural(minutes, "минуту", "минуты", "минут")}"
}

/**
 * «Семейный календарь»: guardians add events (who picks up whom, clubs, doctors, birthdays), they reach the
 * phones they concern, and each phone reminds its owner with one alarm that re-arms itself.
 */
class FamilyCalendar(
    private val context: Context,
    private val settings: SettingsStore,
    private val db: ShieldDatabase,
    private val notifier: Notifier,
    private val sender: () -> MessageSender,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    companion object {
        const val ACTION_ALARM = "ru.ubt.familyshield.CALENDAR"
        private const val REQUEST_CODE = 8_000
        private const val MAX_LATE = 30 * 60_000L
    }

    private val am by lazy { context.getSystemService(AlarmManager::class.java) }

    suspend fun events(): List<FamilyEvent> = db.calendar().all().mapNotNull { it.eventOrNull() }

    private suspend fun load(id: String): FamilyEvent? = db.calendar().get(id)?.eventOrNull()

    /** Guardian: creates or edits an event and sends it to everyone it concerns. */
    suspend fun save(event: FamilyEvent) {
        val previous = load(event.id)
        val stamped = event.copy(title = event.title.trim().take(80), place = event.place.trim().take(80), note = event.note.trim().take(300), updatedAt = clock())
        db.calendar().upsert(stamped.toEntity())
        publish(stamped, previous)
        reschedule()
    }

    suspend fun delete(id: String) {
        val event = load(id) ?: return
        save(event.copy(deleted = true))
    }

    /** "Я заберу": a guardian takes an event nobody has taken yet. */
    suspend fun take(id: String) {
        val event = load(id) ?: return
        val s = settings.current
        save(event.copy(assigneeDeviceId = s.deviceId, assigneeName = s.displayName))
    }

    private suspend fun publish(event: FamilyEvent, previous: FamilyEvent?) {
        val payload = FamilyPayload.CalendarUpdate(event)
        if (event.forDeviceIds.isEmpty() || previous?.forDeviceIds?.isEmpty() == true) {
            sender().send(payload, Audience.ALL, urgent = true)
            return
        }
        val me = settings.current.deviceId
        val guardians = db.members().all().filter { it.memberRole == MemberRole.GUARDIAN }.map { it.deviceId }
        // Everyone the old version concerned learns about the change too, so nobody keeps a stale event.
        val targets = (event.forDeviceIds + previous?.forDeviceIds.orEmpty() + listOfNotNull(event.assigneeDeviceId, previous?.assigneeDeviceId) + guardians)
            .filter { it != me }.distinct()
        if (targets.isNotEmpty()) sender().send(payload, Audience.DEVICES, targets, urgent = true)
    }

    /** An event from a guardian. Newer edits win; a phone keeps only what concerns it. */
    suspend fun receive(event: FamilyEvent) {
        val s = settings.current
        val me = s.deviceId ?: return
        val existing = load(event.id)
        if (existing != null && existing.updatedAt > event.updatedAt) return
        val visible = s.isGuardian || event.concerns(me)
        if (!visible && existing == null) return
        db.calendar().upsert((if (visible) event else event.copy(deleted = true)).toEntity())
        reschedule()
    }

    private suspend fun mine(): List<FamilyEvent> {
        val s = settings.current
        val me = s.deviceId ?: return emptyList()
        return events().filter { it.remindsDevice(me, s.isGuardian) }
    }

    private fun alarmIntent(): PendingIntent = PendingIntent.getBroadcast(
        context, REQUEST_CODE, Intent(context, ReminderReceiver::class.java).setAction(ACTION_ALARM),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** Arms one alarm for the nearest reminder of this phone (exact when the system allows it). */
    suspend fun reschedule() {
        val now = clock()
        val next = mine().mapNotNull { it.nextReminderAt(now) }.minOrNull()
        val pi = alarmIntent()
        runCatching {
            if (next == null) {
                am.cancel(pi)
            } else if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi)
            }
        }
    }

    /** Shows every reminder that became due since the last alarm (but not ones more than 30 minutes late). */
    suspend fun onAlarm() {
        val now = clock()
        val since = maxOf(settings.current.calendarNotifiedUntil, now - MAX_LATE)
        val until = now + 60_000L
        mine().forEach { event ->
            val lead = (event.remindMinutesBefore ?: return@forEach) * 60_000L
            event.occurrences(since + lead, until + lead).forEach { notify(event, it) }
        }
        settings.setCalendarNotifiedUntil(until)
        reschedule()
    }

    private fun notify(event: FamilyEvent, startsAt: Long) {
        val s = settings.current
        val lead = event.remindMinutesBefore ?: 0
        val time = SimpleDateFormat(if (lead >= 24 * 60) "d MMMM, HH:mm" else "HH:mm", Locale.forLanguageTag("ru")).format(Date(startsAt))
        val who = assigneeLine(event, s.deviceId) ?: if (s.isGuardian) "Пока никто не взял — откройте календарь" else null
        val text = listOfNotNull("Начало: $time", event.place.ifBlank { null }, who).joinToString(" · ")
        val id = ("calendar-${event.id}-$startsAt").hashCode()
        notifier.reminder(id, "${event.title} — ${reminderSoonLabel(lead)}", text, notifier.openAppIntent("calendar", id))
    }
}
