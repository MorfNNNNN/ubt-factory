package ru.ubt.familyshield.protect.service

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import ru.ubt.familyshield.ShieldApp
import ru.ubt.familyshield.core.notify.Notifier
import ru.ubt.familyshield.protect.AppInventoryScanner
import ru.ubt.familyshield.protect.ProtectionCenter
import ru.ubt.familyshield.protect.ShieldRuntime
import ru.ubt.familyshield.shared.billing.Feature
import ru.ubt.familyshield.shared.detect.AppCatalog
import ru.ubt.familyshield.shared.model.AlertSeverity
import ru.ubt.familyshield.shared.model.AlertType
import ru.ubt.familyshield.shared.model.MemberRole
import java.util.concurrent.Executors

/**
 * Persistent, visible protection: the notification tells the owner of the phone that it is protected
 * and what the family can see. Hosts the per-minute rules loop, call-state tracking and package events.
 */
class ShieldService : LifecycleService() {
    private lateinit var p: ProtectionCenter
    private var loop: Job? = null
    private var telephonyCallback: Any? = null
    private val executor = Executors.newSingleThreadExecutor()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_USER_PRESENT, Intent.ACTION_SCREEN_ON -> ShieldRuntime.lastInteractionAt = System.currentTimeMillis()
                Intent.ACTION_PACKAGE_ADDED -> if (!intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
                    intent.data?.schemeSpecificPart?.let { onPackageAdded(it) }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        p = (application as ShieldApp).graph.protection
        startInForeground()
        ShieldRuntime.serviceRunning = true
        registerReceiver(receiver, IntentFilter().apply {
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_SCREEN_ON)
        })
        registerReceiver(receiver, IntentFilter(Intent.ACTION_PACKAGE_ADDED).apply { addDataScheme("package") })
        listenToCalls()
        loop = lifecycleScope.launch {
            var minute = 0L
            while (isActive) {
                runCatching { p.tick(minute) }
                minute++
                delay(60_000)
            }
        }
        lifecycleScope.launch {
            p.settings.state.map { Triple(it.role, it.protectionPaused, it.displayName) }.distinctUntilChanged().collect { startInForeground() }
        }
    }

    private fun startInForeground() {
        val s = p.settings.current
        val (title, text) = when (s.role) {
            MemberRole.CHILD -> "Семейный щит включён" to "Родители видят экранное время, местоположение и предупреждения об опасных сообщениях. Переписка целиком не передаётся."
            MemberRole.ELDER -> "Семейный щит защищает телефон" to "Защита от мошеннических звонков и сообщений. Близкие получают только предупреждения об опасности."
            else -> "Семейный щит" to "Защита работает"
        }
        val notification = p.notifier.foreground(if (s.protectionPaused) "Защита на паузе" else title, text)
        var types = 0
        if (Build.VERSION.SDK_INT >= 34) types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        if (Build.VERSION.SDK_INT >= 29 && p.health.locationGranted()) types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        runCatching { ServiceCompat.startForeground(this, Notifier.ID_FOREGROUND, notification, types) }
            .onFailure { ServiceCompat.startForeground(this, Notifier.ID_FOREGROUND, notification, if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0) }
    }

    private fun listenToCalls() {
        val tm = getSystemService(TelephonyManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= 31) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) return
            val cb = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                override fun onCallStateChanged(state: Int) = onCallState(state)
            }
            runCatching { tm.registerTelephonyCallback(executor, cb) }.onSuccess { telephonyCallback = cb }
        } else {
            @Suppress("DEPRECATION")
            val listener = object : PhoneStateListener() {
                @Deprecated("Deprecated in Java")
                override fun onCallStateChanged(state: Int, phoneNumber: String?) = onCallState(state)
            }
            @Suppress("DEPRECATION")
            runCatching { tm.listen(listener, PhoneStateListener.LISTEN_CALL_STATE) }.onSuccess { telephonyCallback = listener }
        }
    }

    private fun onCallState(state: Int) {
        p.calls.onState(state)
        if (state == TelephonyManager.CALL_STATE_IDLE) CallOverlay.hide(this)
        if (state == TelephonyManager.CALL_STATE_OFFHOOK && p.calls.inCallWithUnknown) CallOverlay.showInCallReminder(this)
    }

    private fun onPackageAdded(pkg: String) {
        lifecycleScope.launch {
            val app = p.inventory.scan().firstOrNull { it.packageName == pkg } ?: return@launch
            val role = p.settings.current.role
            val dangerous = p.inventory.isDangerous(app)
            val sideloaded = AppInventoryScanner.FLAG_SIDELOADED in app.riskFlags
            val source = p.inventory.initiator(pkg)?.let { AppCatalog.messengers[it] ?: AppCatalog.browsers[it] }
            if (role == MemberRole.ELDER && (dangerous || sideloaded) && p.entitlements.has(Feature.SIDELOAD_GUARD) && p.settings.current.elderPolicy.sideloadGuard) {
                p.alerts.raise(
                    AlertType.RISKY_APP_INSTALLED,
                    "Установлено приложение не из магазина",
                    "«${app.label}» установлено" + (source?.let { " из $it" } ?: " в обход магазина") +
                        (if (p.calls.inCall) " во время звонка" else "") + ". Так распространяются вирусы, ворующие деньги.",
                    dedupeKey = "install-$pkg", cooldownMs = 24 * 3_600_000L, sourceApp = pkg, sourceAppLabel = app.label,
                    severity = if (dangerous || p.calls.inCall) AlertSeverity.CRITICAL else AlertSeverity.HIGH,
                )
                p.notifier.warning("Опасное приложение", "«${app.label}» установлено не из магазина. Если вас попросили его установить по телефону — это мошенники. Удалите его.")
            } else if (p.settings.current.elderPolicy.newAppAlert || role == MemberRole.CHILD) {
                p.alerts.raise(AlertType.NEW_APP_INSTALLED, "Новое приложение: ${app.label}", app.category.name, dedupeKey = "new-$pkg",
                    cooldownMs = 24 * 3_600_000L, sourceApp = pkg, sourceAppLabel = app.label)
            }
            runCatching { p.reporter.sendInventoryIfChanged() }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    override fun onDestroy() {
        ShieldRuntime.serviceRunning = false
        loop?.cancel()
        runCatching { unregisterReceiver(receiver) }
        val tm = getSystemService(TelephonyManager::class.java)
        if (Build.VERSION.SDK_INT >= 31) {
            (telephonyCallback as? TelephonyCallback)?.let { runCatching { tm?.unregisterTelephonyCallback(it) } }
        } else {
            @Suppress("DEPRECATION")
            (telephonyCallback as? PhoneStateListener)?.let { runCatching { tm?.listen(it, PhoneStateListener.LISTEN_NONE) } }
        }
        p.fall.stop()
        super.onDestroy()
    }
}
