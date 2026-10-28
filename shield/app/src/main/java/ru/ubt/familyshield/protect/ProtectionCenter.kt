package ru.ubt.familyshield.protect

import ru.ubt.familyshield.shared.model.withTrust
import android.content.Context
import android.content.Intent
import android.net.VpnService
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import ru.ubt.familyshield.core.billing.EntitlementRepository
import ru.ubt.familyshield.core.db.ShieldDatabase
import ru.ubt.familyshield.core.family.MessageSender
import ru.ubt.familyshield.core.family.send
import ru.ubt.familyshield.core.notify.Notifier
import ru.ubt.familyshield.core.prefs.SettingsStore
import ru.ubt.familyshield.core.sync.DeviceController
import ru.ubt.familyshield.core.threat.ThreatRepository
import ru.ubt.familyshield.protect.service.ShieldService
import ru.ubt.familyshield.protect.service.ShieldVpnService
import ru.ubt.familyshield.shared.billing.Feature
import ru.ubt.familyshield.shared.detect.ChildRiskAnalyzer
import ru.ubt.familyshield.shared.detect.ScamAnalyzer
import ru.ubt.familyshield.shared.model.AlertType
import ru.ubt.familyshield.shared.model.BonusGrant
import ru.ubt.familyshield.shared.model.ChildPolicy
import ru.ubt.familyshield.shared.model.Command
import ru.ubt.familyshield.shared.model.ElderPolicy
import ru.ubt.familyshield.shared.model.FamilyNote
import ru.ubt.familyshield.shared.model.GeoPoint
import ru.ubt.familyshield.shared.model.MemberRole
import ru.ubt.familyshield.shared.model.RequestDecision
import ru.ubt.familyshield.shared.model.RequestKind
import ru.ubt.familyshield.shared.protocol.Audience
import ru.ubt.familyshield.shared.protocol.FamilyPayload
import java.time.LocalDate
import java.util.UUID

/** Everything that runs on a protected device, wired together. Services reach it through the app graph. */
class ProtectionCenter(
    val context: Context,
    val settings: SettingsStore,
    val db: ShieldDatabase,
    val notifier: Notifier,
    val entitlements: EntitlementRepository,
    val threats: ThreatRepository,
    scam: ScamAnalyzer,
    childRisk: ChildRiskAnalyzer,
    private val sender: () -> MessageSender,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    val health = ProtectionHealth(context)
    val usage = UsageTracker(context)
    val location = LocationTracker(context)
    val inventory = AppInventoryScanner(context, usage)
    val security = SecurityScanner(context, inventory)
    val contacts = ContactsLookup(context)
    val calls = CallMonitor(clock)
    val alerts = AlertCenter(db, settings, sender, scope, clock)
    val reporter = Reporter(context, settings, health, usage, inventory, security, location, sender, clock)
    val child = ChildEnforcer(context, settings, usage, inventory, alerts, notifier, entitlements, sender, scope, clock)
    val elder = ElderGuard(context, settings, calls, alerts, entitlements, usage)
    val messages = MessageGuard(context, settings, calls, alerts, notifier, entitlements, scam, childRisk, contacts)
    val callGuard = CallGuard(settings, threats, contacts, entitlements, calls, alerts, clock)
    val sos = SosManager(context, settings, db, reporter, alerts, scope)
    val reminders = Reminders(context, settings, db, notifier, alerts)
    val ringer = Ringer(context)
    val fall = FallDetector(context) { context.startActivitySafely(Intent(context, ru.ubt.familyshield.protect.ui.FallActivity::class.java)) }

    private val geofenceState = HashMap<String, Boolean>()

    val isProtectedRole: Boolean get() = settings.current.role == MemberRole.CHILD || settings.current.role == MemberRole.ELDER

    /** Starts or stops background components according to role and policy. */
    fun apply() {
        if (isProtectedRole && !settings.current.protectionPaused) {
            ContextCompat.startForegroundService(context, Intent(context, ShieldService::class.java))
            updateVpn()
            reminders.rescheduleAll()
        } else {
            context.stopService(Intent(context, ShieldService::class.java))
            ShieldVpnService.stop(context)
        }
    }

    fun webFilterWanted(): Boolean {
        val s = settings.current
        return when (s.role) {
            MemberRole.CHILD -> s.childPolicy.webFilter.enabled && entitlements.has(Feature.WEB_FILTER)
            MemberRole.ELDER -> s.elderPolicy.webFilter.enabled && entitlements.has(Feature.LINK_SCAN)
            else -> false
        } && !s.protectionPaused
    }

    fun updateVpn() {
        if (webFilterWanted() && VpnService.prepare(context) == null) ShieldVpnService.start(context) else ShieldVpnService.stop(context)
    }

    fun onPolicyChanged() {
        child.invalidateUsage()
        apply()
        ShieldVpnService.reload(context)
    }

    /** Runs every minute from the foreground service. */
    suspend fun tick(minute: Long) {
        val s = settings.current
        if (s.protectionPaused) return
        when (s.role) {
            MemberRole.CHILD -> ShieldRuntime.lastForegroundPackage?.let { child.onForeground(it) }
            MemberRole.ELDER -> elderTick()
            else -> return
        }
        // Coming back from a manual "change protection" request whose cooling-off time has passed.
        s.protectionChange?.takeIf { it.canApply(clock()) }?.let {
            settings.setProtectionChange(null)
            settings.setProtectionPaused(true)
            alerts.raise(AlertType.TAMPER, "Защита отключена", "Защита на телефоне отключена после периода ожидания", dedupeKey = "paused")
            apply()
        }
        if (minute % 15 == 0L) runCatching { reporter.sendStatus() }
        if (minute % 30 == 0L) runCatching { reporter.sendUsage() }
        if (minute % 360 == 5L) runCatching { reporter.sendInventoryIfChanged() }
        val interval = when (s.role) {
            MemberRole.CHILD -> s.childPolicy.withTrust().locationIntervalMinutes
            else -> s.elderPolicy.locationIntervalMinutes
        }.coerceAtLeast(5)
        if (minute % interval == 0L && entitlements.has(Feature.LIVE_LOCATION)) {
            runCatching { reporter.sendLocation()?.let { checkGeofences(it) } }
        }
        val (battery, charging) = reporter.battery()
        val threshold = if (s.role == MemberRole.ELDER) s.elderPolicy.lowBatteryPercent else 15
        if (battery in 1..threshold && !charging) {
            alerts.raise(AlertType.LOW_BATTERY, "Садится батарея", "Заряд $battery%. Телефон скоро выключится.", dedupeKey = "battery", cooldownMs = 6 * 3_600_000L)
        }
    }

    private fun elderTick() {
        val policy = settings.current.elderPolicy
        val limit = policy.callShield.longCallAlertMinutes
        if (calls.inCallWithUnknown && limit > 0 && calls.callDurationMinutes() >= limit && entitlements.has(Feature.IN_CALL_GUARD)) {
            val caller = calls.lastIncoming?.info?.display().orEmpty()
            alerts.raise(AlertType.LONG_CALL_UNKNOWN, "Долгий разговор с незнакомым номером",
                "Уже ${calls.callDurationMinutes()} мин разговора с $caller. Мошенники держат на линии часами — позвоните близкому.",
                dedupeKey = "longcall-${calls.callId}", cooldownMs = 60 * 60_000L, counterparty = caller)
        }
        val hours = policy.inactivityHours
        if (hours != null && entitlements.has(Feature.INACTIVITY_ALERT)) {
            val idleMs = clock() - ShieldRuntime.lastInteractionAt
            val night = java.time.LocalTime.now().hour !in 8..22
            if (idleMs > hours * 3_600_000L && !night) {
                alerts.raise(AlertType.INACTIVITY, "Телефоном давно не пользовались", "Нет активности уже ${idleMs / 3_600_000} ч. Позвоните, чтобы убедиться, что всё хорошо.",
                    dedupeKey = "inactive-${LocalDate.now()}", cooldownMs = 12 * 3_600_000L)
            }
        }
        if (policy.fallDetection && entitlements.has(Feature.FALL_DETECTION)) fall.start() else fall.stop()
    }

    private fun checkGeofences(p: GeoPoint) {
        if (!entitlements.has(Feature.GEOFENCES)) return
        val fences = if (settings.current.role == MemberRole.CHILD) settings.current.childPolicy.geofences else settings.current.elderPolicy.geofences
        for (f in fences) {
            val inside = distanceMeters(p, f.center) <= f.radiusMeters
            val was = geofenceState.put(f.id, inside) ?: continue
            if (inside && !was && f.name.contains("дом", ignoreCase = true)) sayHome(f.id)
            if (inside && !was && f.notifyOnEnter) {
                alerts.raise(AlertType.GEOFENCE_ENTER, "Прибыл(а): ${f.name}", "Вошёл в зону «${f.name}»", dedupeKey = "in-${f.id}", cooldownMs = 30 * 60_000L, location = p)
            } else if (!inside && was && f.notifyOnExit) {
                alerts.raise(AlertType.GEOFENCE_EXIT, "Покинул(а): ${f.name}", "Вышел из зоны «${f.name}»", dedupeKey = "out-${f.id}", cooldownMs = 30 * 60_000L, location = p)
            }
        }
    }

    private val homeSaidAt = mutableMapOf<String, Long>()

    /** «Я дома»: on arriving home the phone writes to the family chat by itself (at most every two hours). */
    private fun sayHome(fenceId: String) {
        val now = clock()
        if ((homeSaidAt[fenceId] ?: 0L) > now - 2 * 3_600_000L) return
        homeSaidAt[fenceId] = now
        scope.launch {
            val note = ru.ubt.familyshield.shared.model.FamilyNote(UUID.randomUUID().toString(), "Я дома", now)
            db.notes().insert(ru.ubt.familyshield.core.db.NoteEntity(note.id, settings.current.deviceId.orEmpty(), note.text, null, now, mine = true))
            sender().send(ru.ubt.familyshield.shared.protocol.FamilyPayload.Note(note), ru.ubt.familyshield.shared.protocol.Audience.ALL)
        }
    }

    /**
     * Elder: switches protection off. Immediate by default; if the elder personally enabled
     * "protect my settings from scammers", it waits for the cooling-off period unless a guardian confirms.
     */
    suspend fun requestProtectionOff(): ru.ubt.familyshield.shared.policy.ProtectionChangeRequest {
        val selfProtected = settings.current.elderSelfProtect && entitlements.has(Feature.COOLING_OFF)
        val hours = if (selfProtected) settings.current.elderPolicy.coolingOffHours else 0
        val request = ru.ubt.familyshield.shared.policy.ProtectionChangeRequest(UUID.randomUUID().toString(), clock(), hours)
        if (hours == 0) {
            settings.setProtectionPaused(true)
            alerts.raise(AlertType.TAMPER, "Защита отключена", "Владелец телефона отключил защиту", dedupeKey = "paused")
            apply()
            return request
        }
        settings.setProtectionChange(request)
        sender().send(
            FamilyPayload.Request(ru.ubt.familyshield.shared.model.MemberRequest(request.id, RequestKind.PROTECTION_CHANGE, clock())),
            Audience.GUARDIANS, urgent = true,
        )
        return request
    }

    val controller: DeviceController = object : DeviceController {
        override suspend fun applyChildPolicy(policy: ChildPolicy) = onPolicyChanged()

        override suspend fun applyElderPolicy(policy: ElderPolicy) = onPolicyChanged()

        override suspend fun execute(command: Command) {
            when (command) {
                is Command.LockNow -> updateChild { it.copy(pausedUntil = command.untilEpochMillis ?: (clock() + 365L * 86_400_000L)) }
                is Command.Unlock -> updateChild { it.copy(pausedUntil = null) }
                is Command.GrantBonus -> {
                    updateChild { it.copy(bonuses = it.bonuses + BonusGrant(command.id, LocalDate.now().toString(), command.minutes, command.reason)) }
                    notifier.info(Notifier.ID_LIMIT, "Бонус +${command.minutes} мин", command.reason.ifBlank { "Родители добавили время" }, "child")
                }
                is Command.Ring -> ringer.ring(command.seconds)
                is Command.RequestLocation -> reporter.sendLocation(urgent = true)
                is Command.ApproveApp -> updateChild { it.copy(approvedPackages = it.approvedPackages + command.packageName) }
                is Command.DenyApp -> updateChild {
                    it.copy(appRules = it.appRules + (command.packageName to ru.ubt.familyshield.shared.model.AppRule(command.packageName, ru.ubt.familyshield.shared.model.AppMode.BLOCKED)))
                }
                is Command.RunSecurityScan -> reporter.sendSecurity()
                is Command.ApproveProtectionChange -> settings.current.protectionChange?.takeIf { it.id == command.requestId }?.let {
                    settings.setProtectionChange(it.copy(approvedByGuardian = true))
                }
                is Command.AskCheckIn -> reminders.onCheckInAsked(command.fromName)
                is Command.RequestReport -> {
                    reporter.sendStatus()
                    reporter.sendUsage()
                    reporter.sendInventoryIfChanged(force = true)
                }
            }
            onPolicyChanged()
        }

        override suspend fun onDecision(decision: RequestDecision) {
            val text = if (decision.approved) "Родители одобрили ваш запрос" + (decision.minutes?.let { ": +$it мин" } ?: "") else "Родители отклонили запрос" +
                decision.note.takeIf { it.isNotBlank() }?.let { ": $it" }.orEmpty()
            notifier.info(Notifier.ID_LIMIT + 1, if (decision.approved) "Одобрено" else "Отклонено", text, "child")
        }

        override suspend fun onNote(from: String, note: FamilyNote) = notifier.note(note.id, from, note.text)
    }

    private suspend fun updateChild(block: (ChildPolicy) -> ChildPolicy) {
        val p = block(settings.current.childPolicy)
        settings.setChildPolicy(p)
    }

    fun start(scope: CoroutineScope = this.scope) = scope.launch { apply() }
}

fun distanceMeters(a: GeoPoint, b: GeoPoint): Double {
    val r = 6_371_000.0
    val dLat = Math.toRadians(b.lat - a.lat)
    val dLon = Math.toRadians(b.lon - a.lon)
    val h = Math.sin(dLat / 2).let { it * it } + Math.cos(Math.toRadians(a.lat)) * Math.cos(Math.toRadians(b.lat)) * Math.sin(dLon / 2).let { it * it }
    return 2 * r * Math.asin(Math.sqrt(h))
}
