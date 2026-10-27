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
import ru.ubt.familyshield.shared.model.MemberRequest
import ru.ubt.familyshield.shared.model.MemberRole
import ru.ubt.familyshield.shared.model.ProtectionComponent
import ru.ubt.familyshield.shared.model.RequestKind
import ru.ubt.familyshield.shared.model.TrustedContact
import ru.ubt.familyshield.ui.ShieldViewModel
import ru.ubt.familyshield.ui.initialFamilyState
import ru.ubt.familyshield.ui.familyStateFlow
import ru.ubt.familyshield.ui.child.ChildHome
import ru.ubt.familyshield.ui.elder.ElderHome
import ru.ubt.familyshield.ui.guardian.ChildControls
import ru.ubt.familyshield.ui.guardian.GuardianRoot
import ru.ubt.familyshield.ui.onboarding.ConsentScreen
import ru.ubt.familyshield.ui.paywall.PaywallScreen
import ru.ubt.familyshield.ui.paywall.UpsellReason
import ru.ubt.familyshield.ui.theme.ShieldTheme
import ru.ubt.familyshield.ui.tools.CheckerScreen
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import ru.ubt.familyshield.ui.FamilyUiState
import ru.ubt.familyshield.ui.elder.ElderSettings
import ru.ubt.familyshield.ui.guardian.AppsScreen
import ru.ubt.familyshield.ui.guardian.ElderControls
import ru.ubt.familyshield.ui.guardian.MemberDetailScreen
import ru.ubt.familyshield.ui.guardian.SecurityScreen
import ru.ubt.familyshield.ui.invite.InviteScreen
import ru.ubt.familyshield.ui.location.LocationMapScreen
import ru.ubt.familyshield.ui.onboarding.OnboardingFlow
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
    fun onlyOnRequest() = assumeTrue(System.getProperty("shield.screenshots") == "true")

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

    private val childPolicy = ChildPolicy(ageBand = AgeBand.AGE_10_12, dailyLimitWeekdayMinutes = 150, dailyLimitWeekendMinutes = 210)
    private val elderPolicy = ElderPolicy(
        trustedContacts = listOf(
            TrustedContact("Анна, дочь", "+7 900 123-45-67", photoEmoji = "👩"),
            TrustedContact("Сергей, сын", "+7 900 765-43-21", photoEmoji = "👨"),
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
            MemberRole.GUARDIAN -> s.setProfile("Анна", "👩")
            MemberRole.CHILD -> { s.setProfile("Маша", "👧"); s.setChildPolicy(childPolicy) }
            else -> { s.setProfile("Мама", "👵"); s.setElderPolicy(elderPolicy); s.setSimpleHome(true) }
        }
        val db = graph.db
        db.members().upsert(MemberEntity("anna", "GUARDIAN", "Анна", "👩", isSelf = selfRole == MemberRole.GUARDIAN, phone = "+7 900 123-45-67", joinedAt = now - 30 * 24 * 60 * min))
        db.members().upsert(
            MemberEntity(
                "masha", "CHILD", "Маша", "👧", birthYear = 2014, deviceModel = "Samsung Galaxy A54", androidVersion = 14,
                isSelf = selfRole == MemberRole.CHILD, joinedAt = now - 29 * 24 * 60 * min, lastSeenAt = now - 3 * min,
                statusJson = status(76, 95), statusAt = now - 3 * min, todayMinutes = 95,
                lastLat = 55.7539, lastLon = 37.6208, lastAccuracy = 20f, lastLocationAt = now - 6 * min, lastAddress = "Школа № 57",
                policyJson = ShieldJson.encodeToString(ChildPolicy.serializer(), childPolicy),
            ),
        )
        db.members().upsert(
            MemberEntity(
                "mama", "ELDER", "Мама", "👵", birthYear = 1956, deviceModel = "Xiaomi Redmi Note 12", androidVersion = 13,
                isSelf = selfRole == MemberRole.ELDER, joinedAt = now - 28 * 24 * 60 * min, lastSeenAt = now - 8 * min,
                statusJson = status(54), statusAt = now - 8 * min, lastCheckInAt = now - 95 * min,
                lastLat = 55.7012, lastLon = 37.5703, lastAccuracy = 25f, lastLocationAt = now - 20 * min, lastAddress = "Дом",
                policyJson = ShieldJson.encodeToString(ElderPolicy.serializer(), elderPolicy),
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
        val more = MemberRequest("r1", RequestKind.MORE_TIME, now - 7 * min, minutes = 30, reason = "Доделать проект по биологии")
        db.requests().upsert(RequestEntity("r1", "masha", "MORE_TIME", ShieldJson.encodeToString(MemberRequest.serializer(), more), more.createdAt))
        val app = MemberRequest("r2", RequestKind.APP_APPROVAL, now - 25 * min, packageName = "com.duolingo", appLabel = "Duolingo")
        db.requests().upsert(RequestEntity("r2", "masha", "APP_APPROVAL", ShieldJson.encodeToString(MemberRequest.serializer(), app), app.createdAt))
        db.notes().insert(NoteEntity("n1", "mama", "Я дома, всё хорошо 😊", createdAt = now - 90 * min, mine = false))
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
    )

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
        for (screen in screens) {
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
                screen.action?.invoke()
                shot(screen.name)
            }.onFailure { failed += "${screen.name}: $it" }
        }
        println("Store screenshots failed: $failed")
    }
}
