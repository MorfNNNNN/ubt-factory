package ru.ubt.familyshield.protect

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import ru.ubt.familyshield.BuildConfig
import ru.ubt.familyshield.core.db.EventEntity
import ru.ubt.familyshield.core.db.ShieldDatabase
import ru.ubt.familyshield.core.family.MessageSender
import ru.ubt.familyshield.core.family.send
import ru.ubt.familyshield.core.prefs.SettingsStore
import ru.ubt.familyshield.shared.detect.PhoneInfo
import ru.ubt.familyshield.shared.model.Alert
import ru.ubt.familyshield.shared.model.AlertSeverity
import ru.ubt.familyshield.shared.model.AlertType
import ru.ubt.familyshield.shared.model.AppInventory
import ru.ubt.familyshield.shared.model.DeviceStatus
import ru.ubt.familyshield.shared.model.GeoPoint
import ru.ubt.familyshield.shared.model.MemberRole
import ru.ubt.familyshield.shared.model.UsageReport
import ru.ubt.familyshield.shared.protocol.Audience
import ru.ubt.familyshield.shared.protocol.FamilyPayload
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Local journal + guardian alerts with per-key cooldowns so families are informed, not spammed. */
class AlertCenter(
    private val db: ShieldDatabase,
    private val settings: SettingsStore,
    private val sender: () -> MessageSender,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val lastSent = ConcurrentHashMap<String, Long>()

    fun journal(type: String, title: String, detail: String = "", pkg: String? = null) {
        scope.launch { db.events().insert(EventEntity(type = type, title = title, detail = detail, packageName = pkg, createdAt = clock())) }
    }

    /**
     * Sends an alert to guardians unless one with the same [dedupeKey] was sent within [cooldownMs].
     * Returns true when it was sent.
     */
    fun raise(
        type: AlertType,
        title: String,
        body: String,
        dedupeKey: String = type.name,
        cooldownMs: Long = 10 * 60_000L,
        severity: AlertSeverity = type.defaultSeverity,
        excerpt: String? = null,
        sourceApp: String? = null,
        sourceAppLabel: String? = null,
        categories: List<String> = emptyList(),
        location: GeoPoint? = null,
        counterparty: String? = null,
    ): Boolean {
        val now = clock()
        val last = lastSent[dedupeKey]
        if (last != null && now - last < cooldownMs) return false
        lastSent[dedupeKey] = now
        journal(type.name, title, body, sourceApp)
        if (!settings.current.isPaired || settings.current.role == MemberRole.GUARDIAN) return true
        val alert = Alert(
            id = UUID.randomUUID().toString(), type = type, severity = severity, title = title, body = body, createdAt = now,
            sourceApp = sourceApp, sourceAppLabel = sourceAppLabel, excerpt = excerpt?.take(200), categories = categories,
            location = location, counterparty = counterparty,
        )
        scope.launch { sender().send(FamilyPayload.AlertRaised(alert), Audience.GUARDIANS, urgent = severity >= AlertSeverity.WARNING) }
        return true
    }
}

/** Tracks the phone call state and the last screened incoming caller (elder protection). */
class CallMonitor(private val clock: () -> Long = System::currentTimeMillis) {
    data class Caller(val info: PhoneInfo, val inContacts: Boolean, val riskScore: Double, val at: Long)

    @Volatile var state: Int = 0 // TelephonyManager.CALL_STATE_*
        private set
    @Volatile var callStartedAt: Long = 0
        private set
    @Volatile var lastIncoming: Caller? = null
    @Volatile var callId: Long = 0
        private set

    val inCall: Boolean get() = state != 0

    /** In a call (or just after one) with someone who is not in the contacts. */
    val inCallWithUnknown: Boolean
        get() {
            val c = lastIncoming ?: return false
            return inCall && !c.inContacts && clock() - c.at < 2 * 60 * 60_000L
        }

    /** Codes arriving within 3 minutes after a call with an unknown number are also dangerous. */
    val recentlyTalkedToUnknown: Boolean
        get() {
            val c = lastIncoming ?: return false
            return !c.inContacts && (inCall || clock() - lastCallEndedAt < 3 * 60_000L) && clock() - c.at < 2 * 60 * 60_000L
        }

    @Volatile private var lastCallEndedAt: Long = 0

    fun onState(newState: Int) {
        if (newState != 0 && state == 0) {
            callStartedAt = clock()
            callId++
        }
        if (newState == 0 && state != 0) lastCallEndedAt = clock()
        state = newState
    }

    fun callDurationMinutes(): Long = if (inCall) (clock() - callStartedAt) / 60_000 else 0
}

/** Builds and sends periodic telemetry from protected devices. */
class Reporter(
    private val context: Context,
    private val settings: SettingsStore,
    private val health: ProtectionHealth,
    private val usage: UsageTracker,
    private val inventory: AppInventoryScanner,
    private val security: SecurityScanner,
    private val location: LocationTracker,
    private val sender: () -> MessageSender,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun battery(): Pair<Int, Boolean> {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        return (if (level >= 0 && scale > 0) level * 100 / scale else -1) to charging
    }

    private fun networkType(): String {
        val cm = context.getSystemService(android.net.ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return "нет сети"
        return when {
            caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) -> "Wi‑Fi"
            caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) -> "мобильная сеть"
            else -> "сеть"
        }
    }

    fun status(): DeviceStatus {
        val s = settings.current
        val snap = health.snapshot(s.role, s.childPolicy, s.elderPolicy)
        val (bat, charging) = battery()
        val audio = context.getSystemService(android.media.AudioManager::class.java)
        val ringer = when (audio.ringerMode) {
            android.media.AudioManager.RINGER_MODE_SILENT -> "silent"
            android.media.AudioManager.RINGER_MODE_VIBRATE -> "vibrate"
            else -> "normal"
        }
        val today = if (health.usageAccessEnabled()) usage.today() else null
        return DeviceStatus(
            batteryPercent = bat,
            charging = charging,
            networkType = networkType(),
            activeComponents = snap.active,
            missingComponents = snap.missing.toSet(),
            lastInteractionAt = today?.lastEventAt ?: ShieldRuntime.lastInteractionAt,
            reportedAt = clock(),
            appVersion = BuildConfig.VERSION_NAME,
            ringerMode = ringer,
            screenTimeTodayMinutes = today?.snapshot?.totalMinutes,
            privateDnsBypass = android.provider.Settings.Global.getString(context.contentResolver, "private_dns_mode") == "hostname",
        )
    }

    suspend fun sendStatus() {
        sender().send(FamilyPayload.Status(status()), Audience.GUARDIANS)
        settings.setLastStatusAt(clock())
    }

    suspend fun sendUsage() {
        if (!health.usageAccessEnabled()) return
        val t = usage.today()
        val blocked = 0
        sender().send(
            FamilyPayload.Usage(UsageReport(LocalDate.now().toString(), t.snapshot.totalMinutes, t.apps.take(40), t.unlocks, blocked, clock())),
            Audience.GUARDIANS,
        )
    }

    /** Sends the app list only when it changed. */
    suspend fun sendInventoryIfChanged(force: Boolean = false) {
        val apps = inventory.scan()
        val hash = apps.map { it.packageName + it.riskFlags.sorted() }.hashCode()
        if (!force && hash == settings.current.lastInventoryHash) return
        sender().send(FamilyPayload.Apps(AppInventory(apps, clock())), Audience.GUARDIANS)
        settings.setLastInventoryHash(hash)
    }

    suspend fun sendSecurity() = sender().send(FamilyPayload.Security(security.scan()), Audience.GUARDIANS)

    suspend fun sendLocation(urgent: Boolean = false): GeoPoint? {
        if (!health.locationGranted()) return null
        val loc = location.current() ?: return null
        val fix = location.toFix(loc, battery().first)
        sender().send(FamilyPayload.Location(fix), Audience.GUARDIANS, urgent = urgent)
        return GeoPoint(fix.lat, fix.lon)
    }

    fun deviceModel() = "${Build.MANUFACTURER} ${Build.MODEL}"
}
