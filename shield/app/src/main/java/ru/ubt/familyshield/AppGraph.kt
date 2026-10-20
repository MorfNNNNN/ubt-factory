package ru.ubt.familyshield

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import ru.ubt.familyshield.core.billing.BillingManager
import ru.ubt.familyshield.core.billing.EntitlementRepository
import ru.ubt.familyshield.core.db.ShieldDatabase
import ru.ubt.familyshield.core.family.FamilyRepository
import ru.ubt.familyshield.core.family.send
import ru.ubt.familyshield.core.net.ApiClient
import ru.ubt.familyshield.core.notify.Notifier
import ru.ubt.familyshield.core.prefs.SettingsStore
import ru.ubt.familyshield.core.security.GuardianSigner
import ru.ubt.familyshield.core.security.KeystoreGuardianSigner
import ru.ubt.familyshield.core.security.KeystoreSecretBox
import ru.ubt.familyshield.core.security.SecretBox
import ru.ubt.familyshield.core.sync.MaintenanceWorker
import ru.ubt.familyshield.core.sync.MessageHandler
import ru.ubt.familyshield.core.sync.SyncEngine
import ru.ubt.familyshield.core.sync.SyncWorker
import ru.ubt.familyshield.core.threat.ThreatRepository
import ru.ubt.familyshield.protect.ProtectionCenter
import ru.ubt.familyshield.shared.detect.ChildRiskAnalyzer
import ru.ubt.familyshield.shared.detect.ScamAnalyzer
import ru.ubt.familyshield.shared.model.CheckIn
import ru.ubt.familyshield.shared.model.GeoPoint
import ru.ubt.familyshield.shared.model.MemberRole
import ru.ubt.familyshield.shared.protocol.Audience
import ru.ubt.familyshield.shared.protocol.FamilyPayload
import java.time.LocalDate
import java.util.UUID

/** Manual dependency graph: explicit, fast to start and easy to replace in tests. */
class AppGraph(val app: Application, box: SecretBox = KeystoreSecretBox()) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val secretBox: SecretBox = box
    val signer: GuardianSigner by lazy { KeystoreGuardianSigner() }
    val settings = SettingsStore(app, secretBox, appScope)
    val db: ShieldDatabase by lazy { ShieldDatabase.create(app) }
    val api = ApiClient({ settings.current.serverUrl }, { settings.current.deviceToken })
    val notifier = Notifier(app)

    val sync: SyncEngine by lazy {
        SyncEngine(settings, api, db, { signer }, { handler }, appScope, onNeedsRetry = { SyncWorker.retryLater(app) })
    }
    val family: FamilyRepository by lazy { FamilyRepository(settings, api, db, secretBox, { signer }, { sync }) }
    val guardian: ru.ubt.familyshield.core.family.GuardianActions by lazy { ru.ubt.familyshield.core.family.GuardianActions(settings, db, { sync }) }
    val handler: MessageHandler by lazy { MessageHandler(settings, db, family, notifier, secretBox, { sync }, { protection.controller }) }
    val entitlements: EntitlementRepository by lazy { EntitlementRepository(settings, api, appScope) }
    val billing: BillingManager by lazy { BillingManager(settings, api, entitlements) }
    val threats: ThreatRepository by lazy { ThreatRepository(app, db, api, settings) }
    val scamAnalyzer: ScamAnalyzer by lazy { ScamAnalyzer { threats.isKnownBadDomain(it) } }
    val childRiskAnalyzer: ChildRiskAnalyzer by lazy { ChildRiskAnalyzer { threats.isKnownBadDomain(it) } }
    val protection: ProtectionCenter by lazy {
        ProtectionCenter(app, settings, db, notifier, entitlements, threats, scamAnalyzer, childRiskAnalyzer, { sync }, appScope)
    }

    fun scheduleWork() {
        SyncWorker.schedule(app)
        MaintenanceWorker.schedule(app)
    }

    suspend fun registerPushToken(token: String) {
        settings.setPushToken(token)
        if (settings.current.deviceToken != null) runCatching { api.setPushToken(token) }
    }

    /** Called after each periodic sync: keeps guardians' view fresh even if the foreground service was killed. */
    suspend fun onPeriodicTick() {
        val s = settings.current
        if (!s.isPaired || s.role == MemberRole.GUARDIAN) return
        if (System.currentTimeMillis() - s.lastStatusAt > 14 * 60_000L) runCatching { protection.reporter.sendStatus() }
        protection.apply()
    }

    /** Elder taps "Я в порядке". */
    suspend fun checkIn() {
        val now = System.currentTimeMillis()
        settings.setLastCheckInAt(now)
        notifier.cancel(Notifier.ID_CHECK_IN)
        val p = protection.location.lastKnown()?.let { GeoPoint(it.latitude, it.longitude) }
        sync.send(FamilyPayload.CheckInDone(CheckIn(UUID.randomUUID().toString(), now, location = p)), Audience.GUARDIANS, urgent = true)
        protection.alerts.journal("CHECK_IN", "Отметка «Я в порядке»")
    }

    /** History is kept according to the plan (1/7/30/90 days). */
    suspend fun cleanupHistory() {
        val days = entitlements.limits().historyDays.toLong().coerceAtLeast(1)
        val cutoff = System.currentTimeMillis() - days * 86_400_000L
        db.alerts().deleteOlderThan(cutoff)
        db.locations().deleteOlderThan(cutoff)
        db.events().deleteOlderThan(System.currentTimeMillis() - 30 * 86_400_000L)
        db.usage().deleteOlderThan(LocalDate.now().minusDays(days).toString())
    }
}
