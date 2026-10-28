package ru.ubt.familyshield

import android.Manifest
import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.app.role.RoleManager
import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.key
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowSettings
import ru.ubt.familyshield.core.db.AlertEntity
import ru.ubt.familyshield.core.db.ChangeEntity
import ru.ubt.familyshield.core.db.DailyStatEntity
import ru.ubt.familyshield.core.db.LocationEntity
import ru.ubt.familyshield.core.family.toEntity
import ru.ubt.familyshield.core.db.MemberEntity
import ru.ubt.familyshield.core.db.NoteEntity
import ru.ubt.familyshield.core.db.RequestEntity
import ru.ubt.familyshield.core.security.MemorySecretBox
import ru.ubt.familyshield.protect.ShieldRuntime
import ru.ubt.familyshield.protect.service.ShieldAccessibilityService
import ru.ubt.familyshield.protect.service.ShieldDeviceAdmin
import ru.ubt.familyshield.protect.service.ShieldNotificationListener
import ru.ubt.familyshield.protect.service.ShieldVpnService
import ru.ubt.familyshield.shared.ShieldJson
import ru.ubt.familyshield.shared.model.AgeBand
import ru.ubt.familyshield.shared.model.ChildPolicy
import ru.ubt.familyshield.shared.model.DeviceStatus
import ru.ubt.familyshield.shared.model.ElderPolicy
import ru.ubt.familyshield.shared.model.EventKind
import ru.ubt.familyshield.shared.model.EventRepeat
import ru.ubt.familyshield.shared.model.FamilyEvent
import ru.ubt.familyshield.shared.model.MedicalCard
import ru.ubt.familyshield.shared.model.FamilyTask
import ru.ubt.familyshield.shared.model.GeoPoint
import ru.ubt.familyshield.shared.model.Geofence
import ru.ubt.familyshield.shared.model.SafetyProgress
import ru.ubt.familyshield.shared.model.TaskRepeat
import ru.ubt.familyshield.shared.model.TaskState
import ru.ubt.familyshield.shared.model.MemberRequest
import ru.ubt.familyshield.shared.model.MemberRole
import ru.ubt.familyshield.shared.model.ProtectionComponent
import ru.ubt.familyshield.shared.model.RequestKind
import ru.ubt.familyshield.shared.model.TrustLevel
import ru.ubt.familyshield.shared.model.TrustedContact
import ru.ubt.familyshield.ui.ShieldViewModel
import ru.ubt.familyshield.ui.initialFamilyState
import ru.ubt.familyshield.ui.familyStateFlow
import ru.ubt.familyshield.ui.child.ChildHome
import ru.ubt.familyshield.ui.elder.ElderHome
import ru.ubt.familyshield.ui.calendar.FamilyCalendarScreen
import ru.ubt.familyshield.ui.guardian.ChildControls
import ru.ubt.familyshield.ui.guardian.WeekReportScreen
import ru.ubt.familyshield.ui.trust.ChildTrustScreen
import ru.ubt.familyshield.ui.trust.GuardianTrustScreen
import ru.ubt.familyshield.ui.guardian.GuardianRoot
import ru.ubt.familyshield.ui.onboarding.ConsentScreen
import ru.ubt.familyshield.ui.paywall.PaywallScreen
import ru.ubt.familyshield.ui.paywall.UpsellReason
import ru.ubt.familyshield.ui.theme.ShieldTheme
import ru.ubt.familyshield.ui.tools.CheckerScreen
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import ru.ubt.familyshield.ui.FamilyUiState
import ru.ubt.familyshield.ui.elder.ElderSettings
import ru.ubt.familyshield.ui.elder.MedicalCardEditor
import ru.ubt.familyshield.ui.elder.MedicalCardScreen
import ru.ubt.familyshield.ui.guardian.AppsScreen
import ru.ubt.familyshield.ui.guardian.ChangeLogScreen
import ru.ubt.familyshield.ui.guardian.ElderControls
import ru.ubt.familyshield.ui.guardian.MemberDetailScreen
import ru.ubt.familyshield.ui.guardian.SecurityScreen
import ru.ubt.familyshield.ui.invite.InviteScreen
import ru.ubt.familyshield.ui.location.LocationMapScreen
import ru.ubt.familyshield.ui.onboarding.GuardianSetup
import ru.ubt.familyshield.ui.onboarding.JoinFamily
import ru.ubt.familyshield.ui.onboarding.OnboardingFlow
import ru.ubt.familyshield.ui.components.AvatarArt
import ru.ubt.familyshield.ui.safety.GosProtectionsScreen
import ru.ubt.familyshield.ui.safety.MemberSafetyScreen
import ru.ubt.familyshield.ui.safety.SafetySchoolScreen
import ru.ubt.familyshield.ui.tasks.ChildTasksScreen
import ru.ubt.familyshield.ui.tasks.GuardianTasksScreen
import ru.ubt.familyshield.ui.setup.PermissionWizard
import java.io.File

/** App without Android Keystore, push or background work, for rendering screens in Robolectric. */
class ScreenshotApp : ShieldApp() {
    override fun createGraph(): AppGraph = AppGraph(this, MemorySecretBox())
    override fun startBackground() = Unit
}

/**
 * Renders real app screens with a fictional demo family into PNGs for the RuStore listing
 * (1080×2340). Skipped unless run with -Pshield.screenshots=true.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], application = ScreenshotApp::class, qualifiers = "w360dp-h780dp-xxhdpi")
class StoreScreenshots {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val graph get() = ApplicationProvider.getApplicationContext<ShieldApp>().graph
    private val now = System.currentTimeMillis()
    private val min = 60_000L

    @Before
    fun onlyOnRequest() {
        assumeTrue(System.getProperty("shield.screenshots") == "true")
        // Store screenshots should show a daytime family: pick the UTC offset where it is about 14:00 now.
        val utcHour = java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC).hour
        val offset = Math.floorMod(14 - utcHour + 12, 24) - 12
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone(java.time.ZoneOffset.ofHours(offset)))
    }

    private fun shot(name: String) {
        compose.waitForIdle()
        lateinit var bitmap: Bitmap
        compose.runOnUiThread {
            val view = compose.activity.window.decorView
            bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
        }
        val dir = File(System.getProperty("shield.screenshotsDir") ?: "build/store-screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun status(battery: Int, screenMinutes: Int? = null) = ShieldJson.encodeToString(
        DeviceStatus.serializer(),
        DeviceStatus(
            batteryPercent = battery, charging = false, networkType = "wifi",
            activeComponents = ProtectionComponent.entries.toSet(), missingComponents = emptySet(),
            lastInteractionAt = now - 2 * min, reportedAt = now - 3 * min, appVersion = "1.0.0",
            screenTimeTodayMinutes = screenMinutes,
        ),
    )

    private val childPolicy = ChildPolicy(
        ageBand = AgeBand.AGE_10_12, dailyLimitWeekdayMinutes = 150, dailyLimitWeekendMinutes = 210,
        geofences = listOf(
            Geofence("g1", "Дом", GeoPoint(55.7012, 37.5703), 150),
            Geofence("g2", "Школа № 57", GeoPoint(55.7539, 37.6208), 200),
            Geofence("g3", "Бассейн «Дельфин»", GeoPoint(55.7608, 37.6327), 150, notifyOnExit = false),
        ),
    )
    private val momLearning = SafetyProgress(
        lessons = mapOf("bank_security" to 3, "relative_trouble" to 3, "gosuslugi" to 2, "officials" to 1),
        steps = setOf("credit_ban", "code_word", "gos_2fa"),
        updatedAt = now - 2 * 60 * min,
    )
    private val medical = MedicalCard(
        "II (A) Rh+", "Пенициллин", "Гипертония, диабет 2 типа", "Эналаприл утром, метформин 2 раза в день",
        "+7 495 123-45-67", "Плохо слышит на левое ухо",
    )
    private val elderPolicy = ElderPolicy(
        medical = medical, bigDebitAlert = true,
        trustedContacts = listOf(
            TrustedContact("Анна, дочь", "+7 900 123-45-67", photoEmoji = AvatarArt.MOM.value),
            TrustedContact("Сергей, сын", "+7 900 765-43-21", photoEmoji = AvatarArt.DAD.value),
        ),
    )

    private suspend fun seedFamily(selfRole: MemberRole) {
        val s = graph.settings
        val selfId = when (selfRole) {
            MemberRole.GUARDIAN -> "anna"
            MemberRole.CHILD -> "masha"
            else -> "mama"
        }
        s.setRole(selfRole)
        s.setOnboardingDone(true)
        s.setConsentAccepted(now - 30 * 24 * 60 * min)
        s.setCredentials(selfId, "demo-token")
        s.setFamily("demo-family", "Семья Ивановых", now - 30 * 24 * 60 * min)
        s.setDevPremium(true)
        when (selfRole) {
            MemberRole.GUARDIAN -> s.setProfile("Анна", AvatarArt.MOM.value)
            MemberRole.CHILD -> { s.setProfile("Маша", AvatarArt.GIRL.value); s.setChildPolicy(childPolicy) }
            else -> { s.setProfile("Мама", AvatarArt.GRANDMA.value); s.setLearning(momLearning); s.setElderPolicy(elderPolicy); s.setSimpleHome(true) }
        }
        val db = graph.db
        db.members().upsert(MemberEntity("anna", "GUARDIAN", "Анна", AvatarArt.MOM.value, isSelf = selfRole == MemberRole.GUARDIAN, phone = "+7 900 123-45-67", joinedAt = now - 30 * 24 * 60 * min))
        db.members().upsert(
            MemberEntity(
                "masha", "CHILD", "Маша", AvatarArt.GIRL.value, birthYear = 2014, deviceModel = "Samsung Galaxy A54", androidVersion = 14,
                isSelf = selfRole == MemberRole.CHILD, joinedAt = now - 29 * 24 * 60 * min, lastSeenAt = now - 3 * min,
                statusJson = status(76, 95), statusAt = now - 3 * min, todayMinutes = 95,
                lastLat = 55.7539, lastLon = 37.6208, lastAccuracy = 20f, lastLocationAt = now - 6 * min, lastAddress = "Школа № 57",
                policyJson = ShieldJson.encodeToString(ChildPolicy.serializer(), childPolicy),
            ),
        )
        db.members().upsert(
            MemberEntity(
                "mama", "ELDER", "Мама", AvatarArt.GRANDMA.value, birthYear = 1956, deviceModel = "Xiaomi Redmi Note 12", androidVersion = 13,
                isSelf = selfRole == MemberRole.ELDER, joinedAt = now - 28 * 24 * 60 * min, lastSeenAt = now - 8 * min,
                statusJson = status(54), statusAt = now - 8 * min, lastCheckInAt = now - 95 * min,
                lastLat = 55.7012, lastLon = 37.5703, lastAccuracy = 25f, lastLocationAt = now - 20 * min, lastAddress = "Дом",
                policyJson = ShieldJson.encodeToString(ElderPolicy.serializer(), elderPolicy),
                learningJson = ShieldJson.encodeToString(SafetyProgress.serializer(), momLearning),
            ),
        )
    }

    private suspend fun seedEvents() {
        val db = graph.db
        db.alerts().insert(
            AlertEntity(
                "a1", "mama", "BANK_APP_DURING_CALL", "CRITICAL", "Мама открыла банк во время звонка",
                "Разговор с незнакомым номером длится 14 минут. Так часто действуют мошенники — позвоните маме.",
                counterparty = "+7 495 ***-**-17", sourceAppLabel = "Банк", createdAt = now - 12 * min,
            ),
        )
        db.alerts().insert(
            AlertEntity(
                "a2", "masha", "CHILD_RISK_MESSAGE", "HIGH", "Маше угрожают в переписке",
                "Похоже на травлю и вымогательство в групповом чате.", excerpt = "…если не скинешь 500 ₽, все узнают…",
                sourceAppLabel = "Мессенджер", categories = "BULLYING,EXTORTION", createdAt = now - 41 * min,
            ),
        )
        db.alerts().insert(
            AlertEntity(
                "a3", "mama", "SCAM_CALL_BLOCKED", "INFO", "Заблокирован звонок мошенников",
                "+7 800 ***-**-90 — 34 жалобы: «служба безопасности банка».", createdAt = now - 2 * 60 * min, read = true,
            ),
        )
        db.alerts().insert(
            AlertEntity(
                "a4", "masha", "BLOCKED_SITE", "INFO", "Заблокирован сайт с азартными играми",
                "Веб-фильтр не пустил на сайт онлайн-казино.", createdAt = now - 3 * 60 * min, read = true,
            ),
        )
        (1..5).forEach { d ->
            db.alerts().insert(AlertEntity("ok$d", "mama", "CHECK_IN_OK", "INFO", "Мама: «Я в порядке»", "Отметка получена", createdAt = now - d * 24 * 60 * min + 30 * min, read = true))
        }
        (1..4).forEach { d ->
            db.alerts().insert(AlertEntity("td$d", "masha", "TASK_DONE", "INFO", "Маша: задание выполнено", "«Сделать уроки»", createdAt = now - d * 24 * 60 * min + 90 * min, read = true))
        }
        db.alerts().insert(
            AlertEntity(
                "a0", "mama", "BIG_DEBIT", "CRITICAL", "Перевод 45 000 ₽",
                "С карты ушло 45 000 ₽. Это случилось после разговора с незнакомым номером — позвоните.",
                sourceAppLabel = "Банк", createdAt = now - 4 * min,
            ),
        )
        val remote = MemberRequest("r3", RequestKind.REMOTE_ACCESS, now - 3 * min, packageName = "com.anydesk.anydeskandroid", appLabel = "AnyDesk")
        db.requests().upsert(RequestEntity("r3", "mama", "REMOTE_ACCESS", ShieldJson.encodeToString(MemberRequest.serializer(), remote), remote.createdAt))
        val more = MemberRequest("r1", RequestKind.MORE_TIME, now - 7 * min, minutes = 30, reason = "Доделать проект по биологии")
        db.requests().upsert(RequestEntity("r1", "masha", "MORE_TIME", ShieldJson.encodeToString(MemberRequest.serializer(), more), more.createdAt))
        val app = MemberRequest("r2", RequestKind.APP_APPROVAL, now - 25 * min, packageName = "com.duolingo", appLabel = "Duolingo")
        db.requests().upsert(RequestEntity("r2", "masha", "APP_APPROVAL", ShieldJson.encodeToString(MemberRequest.serializer(), app), app.createdAt))
        listOf(
            FamilyTask("t1", "masha", "Сделать уроки", "homework", 20, 15, TaskRepeat.DAILY, createdBy = "anna", createdAt = now - 6 * 86_400_000L,
                approvals = 3, earnedPoints = 45, earnedMinutes = 60),
            FamilyTask("t2", "masha", "Убрать в комнате", "room", 15, 10, TaskRepeat.WEEKLY, createdBy = "anna", createdAt = now - 5 * 86_400_000L,
                state = TaskState.DONE, doneAt = now - 18 * min),
            FamilyTask("t3", "masha", "Погулять с собакой", "pet", 10, 10, TaskRepeat.DAILY, createdBy = "anna", createdAt = now - 4 * 86_400_000L,
                state = TaskState.APPROVED, doneAt = now - 150 * min, reviewedAt = now - 120 * min, approvals = 5, earnedPoints = 50, earnedMinutes = 50),
            FamilyTask("t4", "masha", "Позвонить бабушке", "grandma", 10, 10, TaskRepeat.WEEKLY, createdBy = "anna", createdAt = now - 3 * 86_400_000L),
        ).forEach { db.tasks().upsert(it.toEntity()) }
        listOf(
            Triple(now - 360 * min, "Дом", GeoPoint(55.7012, 37.5703)),
            Triple(now - 340 * min, "Бассейн «Дельфин»", GeoPoint(55.7608, 37.6327)),
            Triple(now - 300 * min, "Бассейн «Дельфин»", GeoPoint(55.7608, 37.6327)),
            Triple(now - 260 * min, "Бассейн «Дельфин»", GeoPoint(55.7608, 37.6327)),
            Triple(now - 230 * min, "Школа № 57", GeoPoint(55.7539, 37.6208)),
            Triple(now - 120 * min, "Школа № 57", GeoPoint(55.7539, 37.6208)),
            Triple(now - 6 * min, "Школа № 57", GeoPoint(55.7539, 37.6208)),
        ).forEach { (t, place, p) -> db.locations().insert(LocationEntity(deviceId = "masha", lat = p.lat, lon = p.lon, accuracy = 20f, time = t, address = place)) }
        db.changes().insertAll(
            listOf(
                ChangeEntity(memberDeviceId = "masha", actorDeviceId = "anna", text = "Лимит в будни: 3 ч → 2 ч 30 мин", createdAt = now - 26 * 60 * min),
                ChangeEntity(memberDeviceId = "masha", actorDeviceId = "anna", text = "Добавлено расписание «Уроки» 15:00–18:00", createdAt = now - 26 * 60 * min),
                ChangeEntity(memberDeviceId = "masha", actorDeviceId = "anna", text = "Добавлена геозона «Бассейн «Дельфин»»", createdAt = now - 26 * 60 * min),
                ChangeEntity(memberDeviceId = "masha", actorDeviceId = "anna", text = "Подарено +15 мин — Задание: Погулять с собакой", createdAt = now - 120 * min),
                ChangeEntity(memberDeviceId = "masha", actorDeviceId = "anna", text = "Разрешено новых приложений: 1", createdAt = now - 25 * min),
            ),
        )
        // «Неделя семьи»: counters of this week and of the week before, for the comparison.
        val today = java.time.LocalDate.now()
        fun day(back: Long) = today.minusDays(back).toString()
        listOf(0L, 2L, 5L).forEach { db.stats().add(day(it), "mama", "SCAM_CALL_BLOCKED") }
        db.stats().add(day(0), "mama", "DANGER", 2)
        db.stats().add(day(3), "mama", "SCAM_MESSAGE")
        db.stats().add(day(3), "mama", "DANGER")
        (1L..6L).forEach { db.stats().add(day(it), "mama", "CHECK_IN_OK") }
        db.stats().add(day(1), "mama", "LESSON")
        db.stats().add(day(0), "masha", "DANGER")
        (1L..4L).forEach { db.stats().add(day(it), "masha", "TASK_DONE") }
        listOf(1L, 4L).forEach { db.stats().add(day(it), "masha", "BLOCKED_SITE") }
        listOf(95, 140, 160, 120, 175, 110, 130).forEachIndexed { i, m -> db.stats().put(DailyStatEntity(day(i.toLong()), "masha", "SCREEN", m)) }
        listOf(8L, 9L, 11L, 12L, 13L).forEach { db.stats().add(day(it), "mama", "SCAM_CALL_BLOCKED") }
        (7L..11L).forEach { db.stats().add(day(it), "mama", "CHECK_IN_OK") }
        (8L..9L).forEach { db.stats().add(day(it), "masha", "TASK_DONE") }
        listOf(150, 170, 160, 180, 165, 140, 155).forEachIndexed { i, m -> db.stats().put(DailyStatEntity(day(7L + i), "masha", "SCREEN", m)) }

        // «Семейный календарь».
        val zone = java.time.ZoneId.systemDefault()
        fun at(ahead: Long, h: Int, m: Int = 0) = today.plusDays(ahead).atTime(h, m).atZone(zone).toInstant().toEpochMilli()
        val week = now - 7 * 86_400_000L
        listOf(
            FamilyEvent(
                "c1", "Забрать из школы", EventKind.PICKUP, at(0, 15), EventRepeat.WEEKLY, days = (1..5).toSet(),
                forDeviceIds = setOf("masha"), assigneeDeviceId = "anna", assigneeName = "Анна", place = "Школа № 57", createdBy = "anna", createdAt = week,
            ),
            FamilyEvent(
                "c2", "Бассейн", EventKind.CLUB, at(0, 17, 30), EventRepeat.WEEKLY, days = setOf(today.dayOfWeek.value, today.plusDays(2).dayOfWeek.value),
                forDeviceIds = setOf("masha"), place = "Бассейн «Дельфин»", note = "Шапочка и полотенце", createdBy = "anna", createdAt = week,
            ),
            FamilyEvent(
                "c3", "Кардиолог", EventKind.DOCTOR, at(1, 10), forDeviceIds = setOf("mama"), assigneeDeviceId = "anna", assigneeName = "Анна",
                place = "Поликлиника № 12", remindMinutesBefore = 120, createdBy = "anna", createdAt = week,
            ),
            FamilyEvent(
                "c4", "Родительское собрание", EventKind.SCHOOL, at(3, 18, 30), forDeviceIds = setOf("masha"), place = "Школа № 57",
                createdBy = "anna", createdAt = week,
            ),
            FamilyEvent(
                "c5", "День рождения Маши", EventKind.BIRTHDAY, at(5, 12), EventRepeat.YEARLY, remindMinutesBefore = 24 * 60,
                createdBy = "anna", createdAt = week,
            ),
        ).forEach { db.calendar().upsert(it.toEntity()) }

        db.notes().insert(NoteEntity("n1", "mama", "Я дома, всё хорошо", createdAt = now - 90 * min, mine = false))
        db.notes().insert(NoteEntity("n2", "anna", "Отлично! Вечером заеду", createdAt = now - 85 * min, mine = true))
    }

    /** Grants every permission and special access the protection health check looks at. */
    private fun grantProtection() {
        val app = ApplicationProvider.getApplicationContext<ShieldApp>()
        shadowOf(app).grantPermissions(
            Manifest.permission.POST_NOTIFICATIONS, Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_BACKGROUND_LOCATION,
            Manifest.permission.READ_CONTACTS, Manifest.permission.READ_PHONE_STATE,
        )
        val resolver = app.contentResolver
        Settings.Secure.putString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, ComponentName(app, ShieldAccessibilityService::class.java).flattenToString())
        Settings.Secure.putString(resolver, "enabled_notification_listeners", ComponentName(app, ShieldNotificationListener::class.java).flattenToString())
        shadowOf(app.getSystemService(AppOpsManager::class.java)).setMode(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), app.packageName, AppOpsManager.MODE_ALLOWED)
        shadowOf(app.getSystemService(RoleManager::class.java)).apply {
            addAvailableRole(RoleManager.ROLE_CALL_SCREENING)
            addHeldRole(RoleManager.ROLE_CALL_SCREENING)
        }
        shadowOf(app.getSystemService(DevicePolicyManager::class.java)).setActiveAdmin(ComponentName(app, ShieldDeviceAdmin::class.java))
        shadowOf(app.getSystemService(PowerManager::class.java)).setIgnoringBatteryOptimizations(app.packageName, true)
        ShadowSettings.setCanDrawOverlays(true)
        ShieldRuntime.serviceRunning = true
        // The service flips this when it starts; there is no service in the test, so set it directly.
        ShieldVpnService::class.java.getDeclaredField("running").apply { isAccessible = true }.setBoolean(null, true)
    }

    private class Screen(val name: String, val role: MemberRole, val action: (() -> Unit)? = null, val content: @Composable (ShieldViewModel, FamilyUiState) -> Unit)

    private fun FamilyUiState.member(id: String) = members.first { it.deviceId == id }

    private fun tab(label: String): () -> Unit = {
        compose.onAllNodes(hasText(label) and hasClickAction()).onLast().performClick()
        compose.mainClock.advanceTimeBy(1_000)
    }

    private val screens = listOf(
        Screen("01-family", MemberRole.GUARDIAN) { vm, state -> GuardianRoot(graph, vm, state) },
        Screen("02-alerts", MemberRole.GUARDIAN, tab("События")) { vm, state -> GuardianRoot(graph, vm, state) },
        Screen("03-chat", MemberRole.GUARDIAN, tab("Чат")) { vm, state -> GuardianRoot(graph, vm, state) },
        Screen("04-settings", MemberRole.GUARDIAN, tab("Ещё")) { vm, state -> GuardianRoot(graph, vm, state) },
        Screen("05-child-card", MemberRole.GUARDIAN) { vm, state -> MemberDetailScreen(graph, vm, state, state.member("masha")) {} },
        Screen("06-mom-card", MemberRole.GUARDIAN) { vm, state -> MemberDetailScreen(graph, vm, state, state.member("mama")) {} },
        Screen("07-child-rules", MemberRole.GUARDIAN) { _, state -> ChildControls(graph, state.member("masha")) {} },
        Screen("08-mom-protection", MemberRole.GUARDIAN) { _, state -> ElderControls(graph, state.member("mama")) {} },
        Screen("09-apps", MemberRole.GUARDIAN) { _, state -> AppsScreen(graph, state.member("masha")) {} },
        Screen("10-security", MemberRole.GUARDIAN) { _, state -> SecurityScreen(graph, state.member("mama")) {} },
        Screen("11-location", MemberRole.GUARDIAN) { _, state -> LocationMapScreen(graph, state.member("masha")) {} },
        Screen("12-invite", MemberRole.GUARDIAN) { _, state -> InviteScreen(graph, state) {} },
        Screen("13-checker", MemberRole.GUARDIAN) { _, _ -> CheckerScreen(graph) {} },
        Screen("14-premium", MemberRole.GUARDIAN) { _, _ -> PaywallScreen(graph, UpsellReason.GENERAL) {} },
        Screen("15-mom-home", MemberRole.ELDER) { vm, state -> ElderHome(graph, vm, state, compose.activity) },
        Screen("16-mom-settings", MemberRole.ELDER) { _, state -> ElderSettings(graph, state) {} },
        Screen("17-child-home", MemberRole.CHILD) { vm, state -> ChildHome(graph, vm, state) },
        Screen("18-consent", MemberRole.ELDER) { _, _ -> ConsentScreen(MemberRole.ELDER, onBack = {}, onContinue = {}) },
        Screen("19-welcome", MemberRole.GUARDIAN) { _, _ -> OnboardingFlow(graph, null) {} },
        Screen("20-permissions", MemberRole.ELDER) { _, _ -> PermissionWizard(graph, onboarding = true, onBack = null) {} },
        Screen("21-tasks-parent", MemberRole.GUARDIAN) { _, state -> GuardianTasksScreen(graph, state.member("masha")) {} },
        Screen("22-tasks-child", MemberRole.CHILD) { _, _ -> ChildTasksScreen(graph) {} },
        Screen("23-school", MemberRole.ELDER) { _, _ -> SafetySchoolScreen(graph) {} },
        Screen("24-lesson", MemberRole.ELDER, tab("Начать урок")) { _, _ -> SafetySchoolScreen(graph) {} },
        Screen("25-gos", MemberRole.ELDER) { _, _ -> GosProtectionsScreen(graph) {} },
        Screen("26-mom-safety", MemberRole.GUARDIAN) { _, state -> MemberSafetyScreen(state.member("mama")) {} },
        Screen("27-roles", MemberRole.GUARDIAN, tab("Пропустить")) { _, _ -> OnboardingFlow(graph, null) {} },
        Screen("28-create-family", MemberRole.GUARDIAN) { _, _ -> GuardianSetup(graph, {}, {}) },
        Screen("29-join", MemberRole.CHILD) { _, _ -> JoinFamily(graph, null, {}) {} },
        Screen("30-welcome-2", MemberRole.GUARDIAN, tab("Далее")) { _, _ -> OnboardingFlow(graph, null) {} },
        Screen("31-welcome-3", MemberRole.GUARDIAN, { tab("Далее")(); tab("Далее")() }) { _, _ -> OnboardingFlow(graph, null) {} },
        Screen("32-changes", MemberRole.GUARDIAN) { _, state -> ChangeLogScreen(graph, "masha", "Журнал: Маша", state.members) {} },
        Screen("33-family-more", MemberRole.GUARDIAN, {
            compose.onNode(hasScrollAction()).performScrollToNode(hasText("Настройте защиту семьи"))
            compose.mainClock.advanceTimeBy(1_000)
        }) { vm, state -> GuardianRoot(graph, vm, state) },
        Screen("34-family-bottom", MemberRole.GUARDIAN, scroll(9)) { vm, state -> GuardianRoot(graph, vm, state) },
        Screen("35-alerts-advice", MemberRole.GUARDIAN, { tab("События")(); scroll(1)() }) { vm, state -> GuardianRoot(graph, vm, state) },
        Screen("36-medical", MemberRole.ELDER) { _, _ -> MedicalCardScreen("Мама", medical) {} },
        Screen("37-medical-edit", MemberRole.GUARDIAN) { _, _ -> MedicalCardEditor("Мама", medical, {}) {} },
        Screen("38-mom-protection-more", MemberRole.GUARDIAN, {
            compose.onNode(hasScrollAction()).performScrollToNode(hasText("Сообщать о крупных списаниях"))
            compose.mainClock.advanceTimeBy(1_000)
        }) { _, state -> ElderControls(graph, state.member("mama")) {} },
        Screen("39-week", MemberRole.GUARDIAN) { _, state -> WeekReportScreen(graph, state, {}, {}, {}, {}) },
        Screen("40-week-more", MemberRole.GUARDIAN, scrollTo("Совет на неделю")) { _, state -> WeekReportScreen(graph, state, {}, {}, {}, {}) },
        Screen("41-calendar", MemberRole.GUARDIAN) { _, state -> FamilyCalendarScreen(graph, state) {} },
        Screen("42-calendar-new", MemberRole.GUARDIAN, click("Добавить дело")) { _, state -> FamilyCalendarScreen(graph, state) {} },
        Screen("43-calendar-new-more", MemberRole.GUARDIAN, { click("Добавить дело")(); scrollTo("Напомнить")() }) { _, state -> FamilyCalendarScreen(graph, state) {} },
        Screen("44-child-day", MemberRole.CHILD, scrollTo("Мои дела")) { vm, state -> ChildHome(graph, vm, state) },
        Screen("45-mom-day", MemberRole.ELDER, scrollTo("Мои дела")) { vm, state -> ElderHome(graph, vm, state, compose.activity) },
        Screen("46-family-today", MemberRole.GUARDIAN, scrollTo("Сегодня в семье")) { vm, state -> GuardianRoot(graph, vm, state) },
        Screen("49-week-bottom", MemberRole.GUARDIAN, scrollTo("Итоги по воскресеньям")) { _, state -> WeekReportScreen(graph, state, {}, {}, {}, {}) },
        Screen("50-trust-child", MemberRole.CHILD) { _, state -> ChildTrustScreen(graph, state, {}, {}) },
        Screen("51-trust-parent", MemberRole.GUARDIAN) { _, state -> GuardianTrustScreen(graph, state.member("masha")) {} },
        Screen("52-child-home-trust", MemberRole.CHILD, scrollTo("Лестница доверия")) { vm, state -> ChildHome(graph, vm, state) },
        Screen("53-trust-request", MemberRole.GUARDIAN, {
            val ask = MemberRequest("r4", RequestKind.TRUST_LEVEL, now - 2 * min, level = TrustLevel.STEADY)
            runBlocking { graph.db.requests().upsert(RequestEntity("r4", "masha", "TRUST_LEVEL", ShieldJson.encodeToString(MemberRequest.serializer(), ask), ask.createdAt)) }
            settle()
            tab("События")()
        }) { vm, state -> GuardianRoot(graph, vm, state) },
        Screen("47-calendar-add", MemberRole.GUARDIAN, scrollTo("Своё дело")) { _, state -> FamilyCalendarScreen(graph, state) {} },
        Screen("48-calendar-new-bottom", MemberRole.GUARDIAN, { click("Добавить дело")(); scrollTo("Сохранить")() }) { _, state -> FamilyCalendarScreen(graph, state) {} },
    )

    /** Room flows deliver on background threads: give them real time, then let compose draw the result. */
    private fun settle() = repeat(10) {
        Thread.sleep(40)
        compose.mainClock.advanceTimeBy(100)
    }

    private fun scrollTo(text: String): () -> Unit = {
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(text, substring = true))
        compose.mainClock.advanceTimeBy(1_000)
    }

    private fun click(description: String): () -> Unit = {
        compose.onNode(hasContentDescription(description) and hasClickAction()).performClick()
        compose.mainClock.advanceTimeBy(1_000)
    }

    private fun scroll(times: Int): () -> Unit = {
        repeat(times) {
            compose.onRoot().performTouchInput { swipeUp(startY = bottom * 0.8f, endY = bottom * 0.35f, durationMillis = 300) }
            compose.mainClock.advanceTimeBy(600)
        }
        compose.mainClock.advanceTimeBy(1_000)
    }

    /** One process, one DataStore: every screen is drawn in turn into the same window. */
    @Test
    fun allScreens() {
        grantProtection()
        runBlocking { seedFamily(MemberRole.GUARDIAN); seedEvents() }
        // ShieldViewModel shares its state in viewModelScope, which Robolectric's paused main looper never runs;
        // collect the same flow in the composition instead and keep the view model only for actions.
        val vm = ShieldViewModel(graph)
        val familyState = familyStateFlow(graph)
        var current by mutableStateOf<Screen?>(null)
        var latest: FamilyUiState? = null
        compose.setContent {
            val state by familyState.collectAsState(initialFamilyState(graph))
            latest = state
            val screen = current
            if (screen != null && state.members.size >= 3 && state.settings.role == screen.role) {
                ShieldTheme(dark = false) { key(screen.name) { screen.content(vm, state) } }
            }
        }
        val failed = mutableListOf<String>()
        val only = System.getProperty("shield.only").orEmpty().split(',').filter { it.isNotBlank() }
        for (screen in screens) {
            if (only.isNotEmpty() && only.none { screen.name.startsWith(it) }) continue
            runCatching {
                runBlocking { seedFamily(screen.role) }
                current = screen
                var waited = 0
                while (!(latest?.settings?.role == screen.role && (latest?.members?.size ?: 0) >= 3)) {
                    check(waited++ < 300) { "family state did not load" }
                    compose.mainClock.advanceTimeBy(100)
                    Thread.sleep(20)
                }
                compose.mainClock.advanceTimeBy(2_000)
                settle()
                screen.action?.invoke()
                settle()
                shot(screen.name)
            }.onFailure { failed += "${screen.name}: $it" }
        }
        println("Store screenshots failed: $failed")
    }
}
