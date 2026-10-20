package ru.ubt.familyshield.protect

import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.app.admin.DevicePolicyManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import ru.ubt.familyshield.shared.detect.AppCatalog
import ru.ubt.familyshield.shared.model.AlertSeverity
import ru.ubt.familyshield.shared.model.AppCategory
import ru.ubt.familyshield.shared.model.AppUsage
import ru.ubt.familyshield.shared.model.InstalledApp
import ru.ubt.familyshield.shared.model.LocationFix
import ru.ubt.familyshield.shared.model.SecurityFinding
import ru.ubt.familyshield.shared.model.SecurityReport
import ru.ubt.familyshield.shared.policy.UsageSnapshot
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.Executors
import kotlin.coroutines.resume

/** Screen time from UsageStatsManager events (resume/pause pairs), the same source as Android's Digital Wellbeing. */
class UsageTracker(private val context: Context) {
    private val usm = context.getSystemService(UsageStatsManager::class.java)
    private val pm = context.packageManager
    private val labelCache = HashMap<String, String>()
    private val categoryCache = HashMap<String, AppCategory>()

    data class Today(
        val snapshot: UsageSnapshot,
        val apps: List<AppUsage>,
        val unlocks: Int,
        val lastEventAt: Long?,
        val currentForeground: String?,
    )

    fun label(pkg: String): String = labelCache.getOrPut(pkg) {
        runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
    }

    fun category(pkg: String): AppCategory = categoryCache.getOrPut(pkg) {
        val info = runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull()
        @Suppress("DEPRECATION")
        val isGame = info != null && (info.flags and ApplicationInfo.FLAG_IS_GAME) != 0
        AppCatalog.categoryOf(pkg, if (Build.VERSION.SDK_INT >= 26) info?.category else null, isGame)
    }

    /** Packages that never count toward screen time: launcher, keyboards, system UI, this app. */
    fun excluded(): Set<String> {
        val result = HashSet<String>()
        result += context.packageName
        result += "com.android.systemui"
        pm.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName?.let { result += it }
        Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            ?.substringBefore('/')?.let { result += it }
        return result
    }

    fun today(now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): Today {
        val start = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
        return range(start, now)
    }

    fun range(start: Long, end: Long): Today {
        val events = runCatching { usm.queryEvents(start, end) }.getOrNull()
        val durations = HashMap<String, Long>()
        val launches = HashMap<String, Int>()
        var unlocks = 0
        var current: String? = null
        var currentStart = 0L
        var lastEventAt: Long? = null
        val e = UsageEvents.Event()
        val resumed = if (Build.VERSION.SDK_INT >= 29) UsageEvents.Event.ACTIVITY_RESUMED else @Suppress("DEPRECATION") UsageEvents.Event.MOVE_TO_FOREGROUND
        val paused = if (Build.VERSION.SDK_INT >= 29) UsageEvents.Event.ACTIVITY_PAUSED else @Suppress("DEPRECATION") UsageEvents.Event.MOVE_TO_BACKGROUND
        while (events != null && events.hasNextEvent()) {
            events.getNextEvent(e)
            lastEventAt = e.timeStamp
            when (e.eventType) {
                resumed -> {
                    if (current != null && current != e.packageName) {
                        durations[current!!] = (durations[current!!] ?: 0) + (e.timeStamp - currentStart)
                    }
                    if (current != e.packageName) {
                        launches[e.packageName] = (launches[e.packageName] ?: 0) + 1
                        current = e.packageName
                        currentStart = e.timeStamp
                    }
                }
                paused -> if (current == e.packageName) {
                    durations[e.packageName] = (durations[e.packageName] ?: 0) + (e.timeStamp - currentStart)
                    current = null
                }
                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> {
                    current?.let { durations[it] = (durations[it] ?: 0) + (e.timeStamp - currentStart) }
                    current = null
                }
                UsageEvents.Event.KEYGUARD_HIDDEN -> unlocks++
            }
        }
        current?.let { durations[it] = (durations[it] ?: 0) + (end - currentStart) }
        val excluded = excluded()
        val apps = durations.filterKeys { it !in excluded }.map { (pkg, ms) ->
            AppUsage(pkg, label(pkg), (ms / 60_000).toInt(), category(pkg), launches[pkg] ?: 0)
        }.filter { it.minutes > 0 || it.launches > 0 }.sortedByDescending { it.minutes }
        val counted = apps.filter { !AppCatalog.isEssential(it.packageName) }
        val snapshot = UsageSnapshot(
            totalMinutes = counted.sumOf { it.minutes },
            perApp = apps.associate { it.packageName to it.minutes },
            perCategory = counted.groupBy { it.category }.mapValues { (_, list) -> list.sumOf { it.minutes } },
        )
        return Today(snapshot, apps, unlocks, lastEventAt, current)
    }
}

/** One-shot location using the platform LocationManager (works without Google services). */
class LocationTracker(private val context: Context) {
    private val lm = context.getSystemService(LocationManager::class.java)
    private val executor = Executors.newSingleThreadExecutor()

    @SuppressLint("MissingPermission")
    fun lastKnown(): Location? = runCatching {
        listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .mapNotNull { p -> runCatching { lm.getLastKnownLocation(p) }.getOrNull() }
            .maxByOrNull { it.time }
    }.getOrNull()

    @SuppressLint("MissingPermission")
    suspend fun current(timeoutMs: Long = 25_000): Location? {
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
        if (providers.isEmpty()) return lastKnown()
        val fresh = withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine<Location?> { cont ->
                if (Build.VERSION.SDK_INT >= 30) {
                    val provider = if (LocationManager.NETWORK_PROVIDER in providers) LocationManager.NETWORK_PROVIDER else providers.first()
                    val signal = android.os.CancellationSignal()
                    cont.invokeOnCancellation { signal.cancel() }
                    runCatching {
                        lm.getCurrentLocation(provider, signal, executor) { loc -> if (cont.isActive) cont.resume(loc) }
                    }.onFailure { if (cont.isActive) cont.resume(null) }
                } else {
                    val listener = object : android.location.LocationListener {
                        override fun onLocationChanged(location: Location) {
                            lm.removeUpdates(this)
                            if (cont.isActive) cont.resume(location)
                        }
                    }
                    cont.invokeOnCancellation { lm.removeUpdates(listener) }
                    runCatching {
                        @Suppress("DEPRECATION")
                        lm.requestSingleUpdate(providers.first(), listener, android.os.Looper.getMainLooper())
                    }.onFailure { if (cont.isActive) cont.resume(null) }
                }
            }
        }
        return fresh ?: lastKnown()
    }

    fun toFix(location: Location, battery: Int?): LocationFix = LocationFix(
        lat = location.latitude,
        lon = location.longitude,
        accuracyMeters = location.accuracy,
        time = location.time,
        provider = location.provider.orEmpty(),
        batteryPercent = battery,
        address = address(location),
    )

    private fun address(location: Location): String? = runCatching {
        if (!android.location.Geocoder.isPresent()) return null
        @Suppress("DEPRECATION")
        android.location.Geocoder(context, java.util.Locale("ru")).getFromLocation(location.latitude, location.longitude, 1)
            ?.firstOrNull()?.let { a ->
                listOfNotNull(a.thoroughfare, a.subThoroughfare, a.locality).joinToString(", ").ifBlank { a.getAddressLine(0) }
            }
    }.getOrNull()
}

/** Installed apps with risk flags (sideloaded, remote access, VPN, accessibility or SMS access). */
class AppInventoryScanner(private val context: Context, private val usage: UsageTracker) {
    private val pm = context.packageManager

    companion object {
        const val FLAG_SIDELOADED = "sideloaded"
        const val FLAG_REMOTE_ACCESS = "remote_access"
        const val FLAG_BYPASS = "vpn_or_proxy"
        const val FLAG_ACCESSIBILITY = "accessibility"
        const val FLAG_NOTIFICATIONS = "notification_access"
        const val FLAG_SMS = "reads_sms"
        const val FLAG_DEVICE_ADMIN = "device_admin"
        const val FLAG_OVERLAY = "overlay"
    }

    fun installer(pkg: String): String? = runCatching {
        if (Build.VERSION.SDK_INT >= 30) {
            val info = pm.getInstallSourceInfo(pkg)
            info.installingPackageName ?: info.initiatingPackageName
        } else {
            @Suppress("DEPRECATION")
            pm.getInstallerPackageName(pkg)
        }
    }.getOrNull()

    /** Initiating package for sideloads (e.g. the messenger the APK came from). */
    fun initiator(pkg: String): String? = if (Build.VERSION.SDK_INT >= 30) runCatching { pm.getInstallSourceInfo(pkg).initiatingPackageName }.getOrNull() else null

    private fun enabledAccessibilityPackages(): Set<String> =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
            .split(':').mapNotNull { ComponentName.unflattenFromString(it)?.packageName }.toSet()

    private fun notificationListenerPackages(): Set<String> =
        Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners").orEmpty()
            .split(':').mapNotNull { ComponentName.unflattenFromString(it)?.packageName }.toSet()

    private fun adminPackages(): Set<String> =
        context.getSystemService(DevicePolicyManager::class.java).activeAdmins?.map { it.packageName }?.toSet().orEmpty()

    fun launchablePackages(): Set<String> =
        pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0).map { it.activityInfo.packageName }.toSet()

    fun scan(): List<InstalledApp> {
        val accessibility = enabledAccessibilityPackages()
        val listeners = notificationListenerPackages()
        val admins = adminPackages()
        val launchable = launchablePackages()
        @Suppress("DEPRECATION")
        val packages: List<PackageInfo> = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
        return packages.mapNotNull { p ->
            val info = p.applicationInfo ?: return@mapNotNull null
            val isSystem = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0 && (info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0
            if (isSystem && p.packageName !in launchable) return@mapNotNull null
            if (p.packageName == context.packageName) return@mapNotNull null
            val installer = installer(p.packageName)
            val flags = HashSet<String>()
            val sideloaded = !isSystem && (installer == null || installer !in AppCatalog.trustedInstallers)
            if (sideloaded) flags += FLAG_SIDELOADED
            if (AppCatalog.remoteAccessName(p.packageName) != null) flags += FLAG_REMOTE_ACCESS
            if (AppCatalog.isBypassTool(p.packageName)) flags += FLAG_BYPASS
            if (p.packageName in accessibility) flags += FLAG_ACCESSIBILITY
            if (p.packageName in listeners) flags += FLAG_NOTIFICATIONS
            if (p.packageName in admins) flags += FLAG_DEVICE_ADMIN
            val requested = p.requestedPermissions.orEmpty().toSet()
            if ("android.permission.RECEIVE_SMS" in requested || "android.permission.READ_SMS" in requested) flags += FLAG_SMS
            if ("android.permission.SYSTEM_ALERT_WINDOW" in requested && sideloaded) flags += FLAG_OVERLAY
            InstalledApp(
                packageName = p.packageName,
                label = usage.label(p.packageName),
                category = usage.category(p.packageName),
                installer = installer,
                firstInstallTime = p.firstInstallTime,
                isSystem = isSystem,
                riskFlags = flags,
            )
        }.sortedBy { it.label.lowercase() }
    }

    /** A sideloaded app that reads SMS, listens to notifications or uses accessibility — typical of banking trojans. */
    fun isDangerous(app: InstalledApp): Boolean {
        val f = app.riskFlags
        return FLAG_REMOTE_ACCESS in f ||
            (FLAG_SIDELOADED in f && (FLAG_SMS in f || FLAG_ACCESSIBILITY in f || FLAG_NOTIFICATIONS in f || FLAG_DEVICE_ADMIN in f))
    }
}

/** Device security checkup with plain-language findings and a 0..100 score. */
class SecurityScanner(private val context: Context, private val inventory: AppInventoryScanner) {

    fun scan(now: Long = System.currentTimeMillis()): SecurityReport {
        val findings = ArrayList<SecurityFinding>()
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        if (!keyguard.isDeviceSecure) {
            findings += SecurityFinding("no_lock", AlertSeverity.HIGH, "Нет блокировки экрана",
                "Любой, кто возьмёт телефон, получит доступ к банку и переписке. Установите PIN-код или отпечаток.", Settings.ACTION_SECURITY_SETTINGS)
        }
        if (Settings.Global.getInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0) == 1) {
            findings += SecurityFinding("adb", AlertSeverity.WARNING, "Включена отладка по USB",
                "Через неё можно установить программы в обход магазина. Отключите в настройках разработчика.", Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
        } else if (Settings.Global.getInt(context.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1) {
            findings += SecurityFinding("dev", AlertSeverity.INFO, "Включён режим разработчика",
                "Если вы им не пользуетесь, его лучше выключить.", Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
        }
        val patch = runCatching { LocalDate.parse(Build.VERSION.SECURITY_PATCH) }.getOrNull()
        if (patch != null && patch.isBefore(LocalDate.now().minusMonths(12))) {
            findings += SecurityFinding("patch", AlertSeverity.WARNING, "Давно не было обновлений безопасности",
                "Последнее обновление: ${Build.VERSION.SECURITY_PATCH}. Проверьте обновления системы.", Settings.ACTION_DEVICE_INFO_SETTINGS)
        }
        if (Build.VERSION.SDK_INT < 29) {
            findings += SecurityFinding("old_android", AlertSeverity.INFO, "Устаревшая версия Android",
                "Android ${Build.VERSION.RELEASE} больше не получает исправлений. По возможности обновите телефон.")
        }
        if (isRooted()) {
            findings += SecurityFinding("root", AlertSeverity.HIGH, "На телефоне есть root-права",
                "Вредоносные программы с root-доступом могут обойти любую защиту и прочитать коды из СМС.")
        }
        val privateDns = Settings.Global.getString(context.contentResolver, "private_dns_mode")
        if (privateDns == "hostname") {
            findings += SecurityFinding("private_dns", AlertSeverity.INFO, "Включён «Частный DNS»",
                "Он может обходить веб-фильтр. Если его настраивали не вы, отключите в настройках сети.", Settings.ACTION_WIRELESS_SETTINGS)
        }
        val apps = inventory.scan()
        for (app in apps.filter { inventory.isDangerous(it) }) {
            val remote = AppInventoryScanner.FLAG_REMOTE_ACCESS in app.riskFlags
            findings += SecurityFinding(
                id = "app_${app.packageName}",
                severity = if (remote) AlertSeverity.HIGH else AlertSeverity.CRITICAL,
                title = if (remote) "Программа удалённого доступа: ${app.label}" else "Подозрительное приложение: ${app.label}",
                description = if (remote) {
                    "Через такие программы мошенники управляют телефоном. Если вы её не устанавливали сознательно — удалите."
                } else {
                    "Установлено не из магазина и имеет доступ к СМС, уведомлениям или управлению экраном. Так работают банковские вирусы. Удалите, если не знаете, что это."
                },
                fixAction = Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                packageName = app.packageName,
            )
        }
        val sideloaded = apps.count { AppInventoryScanner.FLAG_SIDELOADED in it.riskFlags }
        if (sideloaded > 0 && findings.none { it.id.startsWith("app_") }) {
            findings += SecurityFinding("sideloaded", AlertSeverity.INFO, "Приложения не из магазина: $sideloaded",
                "Устанавливайте программы только из RuStore или другого официального магазина.")
        }
        val penalty = findings.sumOf {
            when (it.severity) {
                AlertSeverity.CRITICAL -> 35
                AlertSeverity.HIGH -> 25
                AlertSeverity.WARNING -> 10
                AlertSeverity.INFO -> 3
            }.toInt()
        }
        return SecurityReport(findings.sortedByDescending { it.severity.ordinal }, now, (100 - penalty).coerceIn(0, 100))
    }

    private fun isRooted(): Boolean {
        val paths = listOf("/system/xbin/su", "/system/bin/su", "/sbin/su", "/su/bin/su", "/data/local/xbin/su", "/data/local/bin/su", "/system/app/Superuser.apk")
        return Build.TAGS?.contains("test-keys") == true || paths.any { java.io.File(it).exists() }
    }
}
