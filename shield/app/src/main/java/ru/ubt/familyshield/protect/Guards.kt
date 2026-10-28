package ru.ubt.familyshield.protect

import ru.ubt.familyshield.shared.model.withTrust
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import ru.ubt.familyshield.core.billing.EntitlementRepository
import ru.ubt.familyshield.core.family.MessageSender
import ru.ubt.familyshield.core.family.send
import ru.ubt.familyshield.core.notify.Notifier
import ru.ubt.familyshield.core.prefs.SettingsStore
import ru.ubt.familyshield.core.threat.ThreatRepository
import ru.ubt.familyshield.protect.ui.BlockActivity
import ru.ubt.familyshield.protect.ui.WarningActivity
import ru.ubt.familyshield.shared.billing.Feature
import ru.ubt.familyshield.shared.detect.AppCatalog
import ru.ubt.familyshield.shared.policy.formatRub
import ru.ubt.familyshield.shared.detect.BankDebit
import ru.ubt.familyshield.shared.detect.BankDebitParser
import ru.ubt.familyshield.shared.detect.CallContext
import ru.ubt.familyshield.shared.detect.CallRisk
import ru.ubt.familyshield.shared.detect.CallRiskAnalyzer
import ru.ubt.familyshield.shared.detect.ChildRiskAnalyzer
import ru.ubt.familyshield.shared.detect.MessageContext
import ru.ubt.familyshield.shared.detect.NumberKind
import ru.ubt.familyshield.shared.detect.OtpDetector
import ru.ubt.familyshield.shared.detect.PhoneInfo
import ru.ubt.familyshield.shared.detect.PhoneNumbers
import ru.ubt.familyshield.shared.detect.RiskLevel
import ru.ubt.familyshield.shared.detect.ScamAnalyzer
import ru.ubt.familyshield.shared.model.AlertSeverity
import ru.ubt.familyshield.shared.model.AlertType
import ru.ubt.familyshield.shared.model.MemberRequest
import ru.ubt.familyshield.shared.model.MemberRole
import ru.ubt.familyshield.shared.model.RequestKind
import ru.ubt.familyshield.shared.policy.AccessDecision
import ru.ubt.familyshield.shared.policy.BlockReason
import ru.ubt.familyshield.shared.policy.Clock
import ru.ubt.familyshield.shared.policy.ScreenTimeEngine
import ru.ubt.familyshield.shared.policy.UsageSnapshot
import ru.ubt.familyshield.shared.policy.formatMinutes
import ru.ubt.familyshield.shared.protocol.Audience
import ru.ubt.familyshield.shared.protocol.FamilyPayload
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Looks up whether a number is saved in the phone's contacts (needs READ_CONTACTS). */
class ContactsLookup(private val context: Context) {
    fun granted() = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    fun isInContacts(number: String?): Boolean {
        if (number.isNullOrBlank() || !granted()) return false
        return runCatching {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
            context.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup._ID), null, null, null)?.use { it.count > 0 } ?: false
        }.getOrDefault(false)
    }

    fun nameFor(number: String?): String? {
        if (number.isNullOrBlank() || !granted()) return null
        return runCatching {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
            context.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        }.getOrNull()
    }
}

/**
 * Child rules enforcement on foreground app changes: limits, schedules, blocked and unapproved apps,
 * plus protection of the app's own settings and uninstall dialog.
 */
class ChildEnforcer(
    private val context: Context,
    private val settings: SettingsStore,
    private val usage: UsageTracker,
    private val inventory: AppInventoryScanner,
    private val alerts: AlertCenter,
    private val notifier: Notifier,
    private val entitlements: EntitlementRepository,
    private val sender: () -> MessageSender,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    @Volatile private var cachedUsage: UsageSnapshot = UsageSnapshot.EMPTY
    @Volatile private var usageAt = 0L
    @Volatile var tamperUnlockedUntil = 0L
    private val warnedAt = ConcurrentHashMap<String, Long>()
    private val requestedApproval = ConcurrentHashMap.newKeySet<String>()
    private val excludedPackages: Set<String> by lazy { usage.excluded() }

    private fun usageNow(): UsageSnapshot {
        val now = clock()
        if (now - usageAt > 30_000) {
            cachedUsage = runCatching { usage.today().snapshot }.getOrDefault(cachedUsage)
            usageAt = now
        }
        return cachedUsage
    }

    fun invalidateUsage() {
        usageAt = 0
    }

    private fun isNewUnapproved(pkg: String): Boolean {
        val pairedAt = settings.current.pairedAt
        if (pairedAt == 0L) return false
        val info = runCatching { context.packageManager.getPackageInfo(pkg, 0) }.getOrNull() ?: return false
        val system = (info.applicationInfo?.flags ?: 0) and android.content.pm.ApplicationInfo.FLAG_SYSTEM != 0
        return !system && info.firstInstallTime > pairedAt
    }

    /** Evaluates the rules for [pkg]; returns the decision that was enforced. */
    fun evaluate(pkg: String): AccessDecision {
        val s = settings.current
        if (s.role != MemberRole.CHILD || s.protectionPaused) return AccessDecision.Allowed(null, false)
        var policy = s.childPolicy.withTrust()
        // Paid rules are only enforced with an active plan; bedtime and pause work on the free plan.
        if (!entitlements.has(Feature.APP_LIMITS)) policy = policy.copy(categoryLimits = emptyMap(), appRules = policy.appRules.filterValues { it.mode != ru.ubt.familyshield.shared.model.AppMode.DEFAULT || it.dailyLimitMinutes == null })
        if (!entitlements.has(Feature.APP_BLOCKING)) policy = policy.copy(appRules = policy.appRules.filterValues { it.mode != ru.ubt.familyshield.shared.model.AppMode.BLOCKED })
        if (!entitlements.has(Feature.APP_APPROVAL)) policy = policy.copy(requireApprovalForNewApps = false)
        if (!entitlements.has(Feature.SCHEDULES)) policy = policy.copy(schedules = policy.schedules.filter { it.id == "sleep" })
        if (!entitlements.has(Feature.INSTANT_LOCK)) policy = policy.copy(pausedUntil = null)
        return ScreenTimeEngine.decide(
            policy = policy,
            packageName = pkg,
            category = usage.category(pkg),
            usage = usageNow(),
            clock = Clock.of(clock()),
            essential = pkg in excludedPackages,
            newUnapproved = isNewUnapproved(pkg),
        )
    }

    fun onForeground(pkg: String) {
        when (val d = evaluate(pkg)) {
            is AccessDecision.Blocked -> block(pkg, d)
            is AccessDecision.Allowed -> { val rem = d.remainingMinutes; if (d.warn && rem != null) warn(pkg, rem) }
        }
    }

    private fun block(pkg: String, d: AccessDecision.Blocked) {
        val label = usage.label(pkg)
        alerts.journal("BLOCKED", "Заблокировано: $label", d.reason.titleRu, pkg)
        context.startActivity(BlockActivity.intent(context, pkg, label, d))
        when (d.reason) {
            BlockReason.DAILY_LIMIT -> alerts.raise(
                AlertType.LIMIT_REACHED, "Время на сегодня закончилось", "Дневной лимит ${d.detail} исчерпан",
                dedupeKey = "limit-${LocalDate.now()}", cooldownMs = 24 * 3_600_000L,
            )
            BlockReason.PENDING_APPROVAL -> if (requestedApproval.add(pkg)) {
                scope.launch {
                    sender().send(
                        FamilyPayload.Request(MemberRequest(UUID.randomUUID().toString(), RequestKind.APP_APPROVAL, clock(), packageName = pkg, appLabel = label)),
                        Audience.GUARDIANS, urgent = true,
                    )
                }
            }
            BlockReason.BYPASS_TOOL -> alerts.raise(
                AlertType.FILTER_BYPASS, "Попытка обойти фильтр", "Ребёнок открыл $label — VPN или анонимайзер",
                dedupeKey = "bypass-$pkg", cooldownMs = 6 * 3_600_000L, sourceApp = pkg, sourceAppLabel = label,
            )
            else -> Unit
        }
    }

    private fun warn(pkg: String, minutes: Int) {
        val key = "$pkg-$minutes"
        val now = clock()
        if ((warnedAt[key] ?: 0) > now - 10 * 60_000) return
        warnedAt[key] = now
        notifier.info(Notifier.ID_LIMIT, "Осталось ${formatMinutes(minutes)}", "Скоро время закончится. Можно попросить ещё у родителей в приложении.", "child")
    }

    /** Cheap pre-check before inspecting window texts. */
    fun tamperGuardActive(): Boolean {
        val s = settings.current
        return s.role == MemberRole.CHILD && s.childPolicy.uninstallProtection && !s.protectionPaused &&
            entitlements.has(Feature.UNINSTALL_PROTECTION) && clock() >= tamperUnlockedUntil
    }

    /** Settings screens or the uninstall dialog that mention this app are protected from the child. */
    fun isTamperScreen(pkg: String, texts: List<CharSequence>): Boolean {
        if (!tamperGuardActive() || pkg !in TAMPER_PACKAGES) return false
        val label = context.applicationInfo.loadLabel(context.packageManager).toString()
        return texts.any { it.contains(label, ignoreCase = true) || it.contains("Семейный щит", ignoreCase = true) }
    }

    fun onTamper(pkg: String) {
        alerts.raise(AlertType.TAMPER, "Попытка отключить защиту", "Ребёнок открыл настройки защиты или удаление приложения", dedupeKey = "tamper", cooldownMs = 30 * 60_000L)
        context.startActivity(BlockActivity.tamperIntent(context))
    }

    companion object {
        val TAMPER_PACKAGES = setOf(
            "com.android.settings", "com.google.android.packageinstaller", "com.android.packageinstaller",
            "com.miui.securitycenter", "com.samsung.android.lool", "com.coloros.safecenter", "com.huawei.systemmanager",
            "com.oplus.safecenter", "com.google.android.permissioncontroller", "com.android.permissioncontroller",
        )
    }
}

/**
 * Elder protection: the moments scammers rely on — a bank app opened during a call with a stranger,
 * a confirmation code arriving mid-call, a remote access tool started on request.
 */
class ElderGuard(
    private val context: Context,
    private val settings: SettingsStore,
    private val calls: CallMonitor,
    private val alerts: AlertCenter,
    private val entitlements: EntitlementRepository,
    private val usage: UsageTracker,
) {
    private fun elderActive(): Boolean = settings.current.role == MemberRole.ELDER && !settings.current.protectionPaused

    fun onForeground(pkg: String) {
        if (!elderActive()) return
        val policy = settings.current.elderPolicy
        if (policy.inCallBankGuard && entitlements.has(Feature.IN_CALL_GUARD) && AppCatalog.isMoneyApp(pkg) && calls.inCallWithUnknown) {
            val caller = calls.lastIncoming?.info?.display().orEmpty()
            val sent = alerts.raise(
                AlertType.BANK_APP_DURING_CALL, "Открыт банк во время звонка",
                "Во время разговора с незнакомым номером $caller открыто приложение «${usage.label(pkg)}». Срочно позвоните близкому.",
                dedupeKey = "bank-${calls.callId}", cooldownMs = 60 * 60_000L, sourceApp = pkg, sourceAppLabel = usage.label(pkg), counterparty = caller,
            )
            if (sent) context.startActivity(WarningActivity.intent(context, WarningActivity.Kind.BANK_DURING_CALL, caller))
        }
        val remote = AppCatalog.remoteAccessName(pkg)
        val allowedUntil = policy.remoteAccessAllowedUntil
        if (remote != null && policy.remoteAccessGuard && entitlements.has(Feature.REMOTE_ACCESS_GUARD) &&
            (allowedUntil == null || allowedUntil < System.currentTimeMillis())
        ) {
            alerts.raise(
                AlertType.REMOTE_ACCESS_APP, "Запущена программа удалённого доступа",
                "Открыта «$remote». Через неё мошенники управляют телефоном и видят коды." + if (calls.inCall) " Сейчас идёт звонок!" else "",
                dedupeKey = "remote-$pkg", cooldownMs = 60 * 60_000L, sourceApp = pkg, sourceAppLabel = remote,
                severity = if (calls.inCall) AlertSeverity.CRITICAL else AlertSeverity.HIGH,
            )
            // «Вторая рука»: the tool stays covered until a guardian approves; the screen offers to ask them.
            context.startActivity(WarningActivity.intent(context, WarningActivity.Kind.REMOTE_ACCESS, remote, pkg))
        }
        if (calls.inCallWithUnknown && pkg in INSTALLERS && policy.sideloadGuard && entitlements.has(Feature.SIDELOAD_GUARD)) {
            val sent = alerts.raise(
                AlertType.RISKY_APP_INSTALLED, "Установка приложения во время звонка",
                "Во время разговора с незнакомцем открыта установка приложения. Так мошенники заражают телефон.",
                dedupeKey = "install-${calls.callId}", cooldownMs = 60 * 60_000L, severity = AlertSeverity.CRITICAL,
            )
            if (sent) context.startActivity(WarningActivity.intent(context, WarningActivity.Kind.INSTALL_DURING_CALL, ""))
        }
    }

    companion object {
        val INSTALLERS = setOf("com.google.android.packageinstaller", "com.android.packageinstaller", "com.samsung.android.packageinstaller")
    }
}

/** Analyses incoming SMS and messenger notifications (text never leaves the phone, only alerts do). */
class MessageGuard(
    private val context: Context,
    private val settings: SettingsStore,
    private val calls: CallMonitor,
    private val alerts: AlertCenter,
    private val notifier: Notifier,
    private val entitlements: EntitlementRepository,
    private val scam: ScamAnalyzer,
    private val childRisk: ChildRiskAnalyzer,
    private val contacts: ContactsLookup,
) {
    private val seen = object : LinkedHashMap<Int, Boolean>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, Boolean>?) = size > 500
    }

    @Synchronized
    private fun firstTime(key: Int): Boolean = seen.put(key, true) == null

    fun onMessage(pkg: String, sender: String?, text: String) {
        if (text.isBlank() || !firstTime((pkg + sender + text).hashCode())) return
        val s = settings.current
        if (s.protectionPaused) return
        when (s.role) {
            MemberRole.ELDER -> elder(pkg, sender, text)
            MemberRole.CHILD -> child(pkg, sender, text)
            else -> Unit
        }
    }

    private fun appLabel(pkg: String): String = AppCatalog.messengers[pkg] ?: if (pkg in AppCatalog.smsApps) "СМС" else
        runCatching { context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)

    private fun senderInContacts(sender: String?): Boolean? {
        sender ?: return null
        val info = PhoneNumbers.parse(sender)
        // Messengers show the saved contact name as the title; a raw number means an unknown sender.
        return if (info.kind == NumberKind.INVALID && sender.any { it.isLetter() }) true else contacts.isInContacts(sender)
    }

    private fun elder(pkg: String, sender: String?, text: String) {
        val policy = settings.current.elderPolicy
        val otp = OtpDetector.detect(text)
        if (otp != null && policy.otpGuard && entitlements.has(Feature.OTP_GUARD) && calls.recentlyTalkedToUnknown) {
            val caller = calls.lastIncoming?.info?.display().orEmpty()
            val sent = alerts.raise(
                AlertType.OTP_DURING_CALL, "Пришёл код во время звонка",
                "Пришёл код ${otp.issuer?.let { "от $it " }.orEmpty()}(${otp.purpose.titleRu}) во время разговора с незнакомым номером $caller. Мошенники просят назвать такие коды!",
                dedupeKey = "otp-${calls.callId}", cooldownMs = 30 * 60_000L, counterparty = caller, sourceApp = pkg,
            )
            if (sent) context.startActivity(WarningActivity.intent(context, WarningActivity.Kind.OTP_DURING_CALL, otp.issuer.orEmpty()))
        }
        bigDebit(pkg, sender, text)
        if (!policy.messageScan || !entitlements.has(Feature.SCAM_MESSAGE_SCAN)) return
        if (!AppCatalog.isMessaging(pkg)) return
        val verdict = scam.analyze(text, MessageContext(senderInContacts = senderInContacts(sender), duringCall = calls.inCall, sourcePackage = pkg, sender = sender))
        if (verdict.level < RiskLevel.MEDIUM) return
        val top = verdict.top ?: return
        notifier.warning(
            top.type.titleRu,
            "Сообщение в «${appLabel(pkg)}» похоже на мошенничество. ${verdict.advice.orEmpty()}",
        )
        alerts.journal("SCAM_MESSAGE", top.type.titleRu, verdict.reasons.joinToString("; "), pkg)
        if (verdict.level >= RiskLevel.HIGH) {
            val linkOnly = top.type == ru.ubt.familyshield.shared.detect.ScamType.DANGEROUS_LINK
            alerts.raise(
                if (linkOnly) AlertType.PHISHING_LINK else AlertType.SCAM_MESSAGE,
                top.type.titleRu,
                "Подозрительное сообщение в «${appLabel(pkg)}»: ${verdict.reasons.joinToString("; ")}",
                dedupeKey = "scam-${text.hashCode()}", cooldownMs = 24 * 3_600_000L,
                excerpt = text, sourceApp = pkg, sourceAppLabel = appLabel(pkg), counterparty = sender,
                categories = verdict.findings.map { it.type.name },
                severity = if (verdict.level == RiskLevel.CRITICAL) AlertSeverity.CRITICAL else AlertSeverity.HIGH,
            )
        }
    }

    /** Large debits from bank pushes and bank SMS: the family hears about the amount, never the full message. */
    private fun bigDebit(pkg: String, sender: String?, text: String) {
        val policy = settings.current.elderPolicy
        if (!policy.bigDebitAlert || !entitlements.has(Feature.BIG_DEBIT_ALERT)) return
        val fromBank = pkg in AppCatalog.banks || (pkg in AppCatalog.smsApps && BankDebitParser.isBankSender(sender))
        if (!fromBank) return
        val debit = BankDebitParser.parse(text) ?: return
        if (debit.amountRub < policy.bigDebitThresholdRub) return
        val afterCall = calls.recentlyTalkedToUnknown
        alerts.raise(
            AlertType.BIG_DEBIT,
            "${debit.kind.titleRu} ${formatRub(debit.amountRub)}",
            buildString {
                append("С карты ушло ${formatRub(debit.amountRub)}")
                debit.merchant?.let { append(" — $it") }
                append(if (afterCall) ". Это случилось после разговора с незнакомым номером — позвоните." else ". Если об этой трате ничего не известно — позвоните.")
            },
            dedupeKey = "debit-${text.hashCode()}", cooldownMs = 10 * 60_000L,
            severity = if (afterCall && debit.kind == BankDebit.Kind.TRANSFER) AlertSeverity.CRITICAL else AlertSeverity.HIGH,
            sourceApp = pkg, sourceAppLabel = appLabel(pkg),
        )
    }

    private fun child(pkg: String, sender: String?, text: String) {
        val policy = settings.current.childPolicy.withTrust()
        val monitoring = policy.messageMonitoring
        if (!monitoring.enabled || !entitlements.has(Feature.RISK_ALERTS) || !AppCatalog.isMessaging(pkg)) return
        val verdict = childRisk.analyze(text)
        val findings = childRisk.reportable(verdict, policy.ageBand, monitoring.categories, monitoring.criticalOnly)
        for (f in findings) {
            alerts.raise(
                AlertType.CHILD_RISK_MESSAGE,
                f.category.titleRu,
                "В «${appLabel(pkg)}»${sender?.let { " от «$it»" }.orEmpty()}: ${f.reasons.first()}. ${ru.ubt.familyshield.shared.detect.ChildRiskLexicon.adviceFor(f.category)}",
                dedupeKey = "risk-${f.category}-$sender", cooldownMs = 30 * 60_000L,
                severity = f.severity, excerpt = text, sourceApp = pkg, sourceAppLabel = appLabel(pkg),
                categories = listOf(f.category.name), counterparty = sender,
            )
        }
    }
}

/** Incoming call decisions for the call screening service. */
class CallGuard(
    private val settings: SettingsStore,
    private val threats: ThreatRepository,
    private val contacts: ContactsLookup,
    private val entitlements: EntitlementRepository,
    private val calls: CallMonitor,
    private val alerts: AlertCenter,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    data class Decision(val block: Boolean, val silence: Boolean, val warn: Boolean, val risk: CallRisk, val info: PhoneInfo, val inContacts: Boolean)

    private val callsToday = ConcurrentHashMap<String, Int>()
    @Volatile private var countersDate = LocalDate.now()

    private fun normalizeSet(numbers: Set<String>) = numbers.mapNotNull { PhoneNumbers.parse(it).normalized }.toSet()

    suspend fun screen(number: String?): Decision {
        if (LocalDate.now() != countersDate) {
            callsToday.clear()
            countersDate = LocalDate.now()
        }
        val s = settings.current
        val shield = s.elderPolicy.callShield
        val info = PhoneNumbers.parse(number)
        val inContacts = contacts.isInContacts(number)
        val key = info.normalized ?: "hidden"
        val count = callsToday.merge(key, 1, Int::plus) ?: 1
        val userBlocked = info.normalized != null && info.normalized in normalizeSet(shield.blockedNumbers)
        val userAllowed = info.normalized != null && info.normalized in normalizeSet(shield.allowedNumbers)
        val threat = threats.lookupNumber(info)
        val risk = CallRiskAnalyzer.assess(
            info,
            CallContext(inContacts, threat?.category, threat?.reports ?: 0, userBlocked, userAllowed, count, java.time.LocalTime.now().hour),
        )
        calls.lastIncoming = CallMonitor.Caller(info, inContacts || userAllowed, risk.score, clock())
        val active = s.role == MemberRole.ELDER && shield.enabled && !s.protectionPaused
        if (!active) return Decision(false, false, false, risk, info, inContacts)
        val premium = entitlements.has(Feature.CALL_BLOCKING)
        val block = !userAllowed && (
            userBlocked ||
                (premium && shield.blockKnownSpam && threat != null && threat.reports >= 3) ||
                (premium && shield.blockHiddenNumbers && info.kind == NumberKind.HIDDEN) ||
                (premium && shield.blockInternational && info.kind == NumberKind.INTERNATIONAL && !inContacts) ||
                (premium && shield.contactsOnly && !inContacts)
            )
        val silence = !block && premium && shield.silenceUnknown && !inContacts && !userAllowed
        val warn = !block && shield.warnUnknown && !inContacts && !userAllowed && risk.score >= 0.25 && entitlements.has(Feature.CALL_WARNING)
        if (block) {
            alerts.raise(
                AlertType.SCAM_CALL_BLOCKED, "Заблокирован звонок", "${info.display()}: ${risk.reasons.firstOrNull().orEmpty()}",
                dedupeKey = "blocked-$key", cooldownMs = 60 * 60_000L, counterparty = info.display(),
            )
        } else if (risk.level >= RiskLevel.HIGH) {
            alerts.raise(
                AlertType.SUSPICIOUS_CALL, "Подозрительный звонок", "${info.display()}: ${risk.reasons.joinToString("; ")}",
                dedupeKey = "suspicious-$key", cooldownMs = 60 * 60_000L, counterparty = info.display(),
            )
        }
        return Decision(block, silence, warn, risk, info, inContacts)
    }
}

fun Context.canPostFullScreen(): Boolean = Build.VERSION.SDK_INT < 34 ||
    getSystemService(android.app.NotificationManager::class.java).canUseFullScreenIntent()

fun Context.startActivitySafely(intent: Intent) = runCatching { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
