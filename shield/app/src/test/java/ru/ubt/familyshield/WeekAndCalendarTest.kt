package ru.ubt.familyshield

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import ru.ubt.familyshield.core.db.MemberEntity
import ru.ubt.familyshield.core.db.ShieldDatabase
import ru.ubt.familyshield.core.family.FamilyCalendar
import ru.ubt.familyshield.core.family.FamilyRepository
import ru.ubt.familyshield.core.family.FamilyWeek
import ru.ubt.familyshield.core.family.MessageSender
import ru.ubt.familyshield.core.net.ApiClient
import ru.ubt.familyshield.core.notify.Notifier
import ru.ubt.familyshield.core.prefs.SettingsStore
import ru.ubt.familyshield.core.security.MemoryGuardianSigner
import ru.ubt.familyshield.core.security.MemorySecretBox
import ru.ubt.familyshield.core.sync.DeviceController
import ru.ubt.familyshield.core.sync.MessageHandler
import ru.ubt.familyshield.core.sync.SenderAuth
import ru.ubt.familyshield.shared.content.WeekMetric
import ru.ubt.familyshield.shared.model.Alert
import ru.ubt.familyshield.shared.model.AlertType
import ru.ubt.familyshield.shared.model.ChildPolicy
import ru.ubt.familyshield.shared.model.Command
import ru.ubt.familyshield.shared.model.ElderPolicy
import ru.ubt.familyshield.shared.model.FamilyEvent
import ru.ubt.familyshield.shared.model.FamilyNote
import ru.ubt.familyshield.shared.model.MemberRole
import ru.ubt.familyshield.shared.model.RequestDecision
import ru.ubt.familyshield.shared.model.SafetyProgress
import ru.ubt.familyshield.shared.protocol.FamilyPayload
import ru.ubt.familyshield.shared.protocol.InnerMessage
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class WeekAndCalendarTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private val zone = ZoneId.systemDefault()
    private lateinit var db: ShieldDatabase
    private lateinit var settings: SettingsStore
    private var now = System.currentTimeMillis()
    private val sent = mutableListOf<FamilyPayload>()
    private val sender = MessageSender { payload, _, _, _, _ -> sent += payload }
    private lateinit var week: FamilyWeek
    private lateinit var calendar: FamilyCalendar
    private lateinit var handler: MessageHandler

    private val noDevice = object : DeviceController {
        override suspend fun applyChildPolicy(policy: ChildPolicy) = Unit
        override suspend fun applyElderPolicy(policy: ElderPolicy) = Unit
        override suspend fun execute(command: Command) = Unit
        override suspend fun onDecision(decision: RequestDecision) = Unit
        override suspend fun onNote(from: String, note: FamilyNote) = Unit
    }

    @Before
    fun setup() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        db = Room.inMemoryDatabaseBuilder(app, ShieldDatabase::class.java).allowMainThreadQueries().build()
        val box = MemorySecretBox()
        settings = SettingsStore(app, box, CoroutineScope(SupervisorJob() + Dispatchers.Default))
        val notifier = Notifier(app).also { it.createChannels() }
        week = FamilyWeek(settings, db, notifier, clock = { now }, zone = { zone })
        calendar = FamilyCalendar(app, settings, db, notifier, { sender }, clock = { now })
        val family = FamilyRepository(settings, ApiClient({ "http://localhost" }, { null }), db, box, { MemoryGuardianSigner() }, { sender })
        handler = MessageHandler(settings, db, family, notifier, box, { sender }, { noDevice }, { week }, { calendar }, clock = { now })
        runBlocking {
            settings.wipe()
            settings.setCredentials("mom", "token")
            settings.setRole(MemberRole.GUARDIAN)
            settings.setFamily("family", "Семья", now)
            settings.setProfile("Анна", "")
            db.members().upsert(MemberEntity("mom", "GUARDIAN", "Анна", isSelf = true))
            db.members().upsert(MemberEntity("kid", "CHILD", "Маша"))
            db.members().upsert(MemberEntity("grandma", "ELDER", "Мама"))
        }
    }

    @After
    fun tearDown() = db.close()

    private fun at(date: LocalDate, h: Int, m: Int = 0) = date.atTime(h, m).atZone(zone).toInstant().toEpochMilli()

    private suspend fun receive(from: String, payload: FamilyPayload, auth: SenderAuth = SenderAuth.MEMBER_GUARDIAN) =
        handler.handle(from, InnerMessage(now, payload), auth)

    private fun notifications() = shadowOf(app.getSystemService(NotificationManager::class.java)).allNotifications

    @Test
    fun alertsCountIntoTheWeekOnceEvenWhenDeliveredTwice() = runBlocking {
        val alert = Alert("a1", AlertType.SCAM_MESSAGE, title = "Подозрительное СМС", body = "", createdAt = now)
        receive("grandma", FamilyPayload.AlertRaised(alert), SenderAuth.NONE)
        receive("grandma", FamilyPayload.AlertRaised(alert), SenderAuth.NONE)
        val today = LocalDate.now(zone).toString()
        val rows = db.stats().between(today, today).associate { it.metric to it.value }
        assertEquals(1, rows[AlertType.SCAM_MESSAGE.name])
        assertEquals(1, rows[WeekMetric.DANGER])
    }

    @Test
    fun lessonsCountByTheirGrowth() = runBlocking {
        receive("grandma", FamilyPayload.Learning(SafetyProgress(lessons = mapOf("bank_security" to 3))), SenderAuth.NONE)
        receive("grandma", FamilyPayload.Learning(SafetyProgress(lessons = mapOf("bank_security" to 3, "gosuslugi" to 2))), SenderAuth.NONE)
        receive("grandma", FamilyPayload.Learning(SafetyProgress(lessons = mapOf("bank_security" to 3, "gosuslugi" to 2))), SenderAuth.NONE)
        val today = LocalDate.now(zone).toString()
        assertEquals(2, db.stats().between(today, today).single { it.metric == WeekMetric.LESSON }.value)
    }

    @Test
    fun sundaySummaryArrivesOncePerWeek() = runBlocking {
        val sunday = LocalDate.of(2026, 11, 1)
        now = at(sunday, 19)
        week.countAlert("grandma", AlertType.SCAM_CALL_BLOCKED, AlertType.SCAM_CALL_BLOCKED.defaultSeverity, at(sunday.minusDays(2), 11))
        week.maybeNotify()
        week.maybeNotify()
        assertEquals(1, notifications().size)
        assertEquals(sunday.toString(), settings.current.lastDigestDate)

        now = at(sunday.plusDays(1), 19)
        week.maybeNotify()
        assertEquals(1, notifications().size)
    }

    @Test
    fun guardianKeepsTheNewestEditOfAnEvent() = runBlocking {
        val event = FamilyEvent("e1", "Бассейн", startsAt = now + 3 * 3_600_000L, forDeviceIds = setOf("kid"), createdBy = "dad", createdAt = now, updatedAt = now)
        receive("dad", FamilyPayload.CalendarUpdate(event))
        receive("dad", FamilyPayload.CalendarUpdate(event.copy(title = "Старое название", updatedAt = now - 60_000)))
        assertEquals("Бассейн", calendar.events().single().title)
    }

    @Test
    fun childPhoneKeepsOnlyItsOwnEvents() = runBlocking {
        settings.setCredentials("kid", "token")
        settings.setRole(MemberRole.CHILD)
        val doctor = FamilyEvent("e1", "Кардиолог", startsAt = now + 86_400_000L, forDeviceIds = setOf("grandma"), createdBy = "mom", createdAt = now, updatedAt = now)
        val pool = FamilyEvent("e2", "Бассейн", startsAt = now + 86_400_000L, forDeviceIds = setOf("kid"), createdBy = "mom", createdAt = now, updatedAt = now)
        receive("mom", FamilyPayload.CalendarUpdate(doctor), SenderAuth.TRUSTED)
        receive("mom", FamilyPayload.CalendarUpdate(pool), SenderAuth.TRUSTED)
        assertEquals(listOf("Бассейн"), calendar.events().map { it.title })

        // The event no longer concerns the child: it disappears from the phone.
        receive("mom", FamilyPayload.CalendarUpdate(pool.copy(forDeviceIds = setOf("grandma"), updatedAt = now + 1)), SenderAuth.TRUSTED)
        assertTrue(calendar.events().isEmpty())
    }

    @Test
    fun unsignedCalendarChangesAreIgnoredOnProtectedPhones() = runBlocking {
        settings.setCredentials("kid", "token")
        settings.setRole(MemberRole.CHILD)
        val fake = FamilyEvent("e1", "Встреча", startsAt = now + 86_400_000L, createdBy = "stranger", createdAt = now)
        receive("stranger", FamilyPayload.CalendarUpdate(fake), SenderAuth.NONE)
        assertTrue(calendar.events().isEmpty())
    }

    @Test
    fun reminderIsArmedAndShownOnce() = runBlocking {
        val today = LocalDate.now(zone).plusDays(1)
        now = at(today, 12)
        val pickup = FamilyEvent(
            "e1", "Забрать из школы", startsAt = at(today, 15), forDeviceIds = setOf("kid"),
            assigneeDeviceId = "mom", assigneeName = "Анна", remindMinutesBefore = 30, createdBy = "mom", createdAt = now,
        )
        calendar.save(pickup)
        assertTrue(sent.last() is FamilyPayload.CalendarUpdate)
        val alarm = shadowOf(app.getSystemService(AlarmManager::class.java)).peekNextScheduledAlarm()
        assertEquals(at(today, 14, 30), alarm?.triggerAtMs)

        now = at(today, 14, 30)
        calendar.onAlarm()
        calendar.onAlarm()
        assertEquals(1, notifications().size)
        assertTrue(notifications().single().extras.getString("android.title").orEmpty().startsWith("Забрать из школы"))
    }

    @Test
    fun answeredRequestStopsBeingPendingOnTheChildPhone() = runBlocking {
        settings.setCredentials("kid", "token")
        settings.setRole(MemberRole.CHILD)
        ru.ubt.familyshield.core.family.FamilyActions(settings, db, { sender }).askTrustLevel(ru.ubt.familyshield.shared.model.TrustLevel.STEADY)
        val id = db.requests().observePending().first().single().id
        receive("mom", FamilyPayload.Decision("kid", RequestDecision(id, approved = true, decidedAt = now)), SenderAuth.TRUSTED)
        assertTrue(db.requests().observePending().first().isEmpty())
    }

    @Test
    fun answerToCheckInRequestNotifiesButDailyCheckInStaysQuiet() = runBlocking {
        val daily = ru.ubt.familyshield.shared.model.CheckIn("c1", now)
        receive("grandma", FamilyPayload.CheckInDone(daily), SenderAuth.NONE)
        assertEquals(0, notifications().size)
        val answer = ru.ubt.familyshield.shared.model.CheckIn("c2", now, mood = ru.ubt.familyshield.shared.model.CheckIn.MOOD_ANSWERED)
        receive("grandma", FamilyPayload.CheckInDone(answer), SenderAuth.NONE)
        assertEquals(1, notifications().size)
        assertTrue(notifications().single().extras.getString("android.title").orEmpty().startsWith("Мама"))
    }

    @Test
    fun ownLessonsCountForTheGuardian() = runBlocking {
        ru.ubt.familyshield.core.family.FamilyActions(settings, db, { sender }).recordLesson("bank_security", 3)
        val today = LocalDateTime.now(zone).toLocalDate().toString()
        assertEquals(1, db.stats().between(today, today).single { it.metric == WeekMetric.LESSON && it.deviceId == "mom" }.value)
    }
}
