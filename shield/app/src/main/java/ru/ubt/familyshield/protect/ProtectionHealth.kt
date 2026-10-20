package ru.ubt.familyshield.protect

import android.Manifest
import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import ru.ubt.familyshield.protect.service.ShieldAccessibilityService
import ru.ubt.familyshield.protect.service.ShieldDeviceAdmin
import ru.ubt.familyshield.protect.service.ShieldNotificationListener
import ru.ubt.familyshield.protect.service.ShieldVpnService
import ru.ubt.familyshield.shared.model.ChildPolicy
import ru.ubt.familyshield.shared.model.ElderPolicy
import ru.ubt.familyshield.shared.model.MemberRole
import ru.ubt.familyshield.shared.model.ProtectionComponent

/** Which protection components are switched on, and which ones the current role still needs. */
class ProtectionHealth(private val context: Context) {

    fun isGranted(permission: String) = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun accessibilityEnabled(): Boolean {
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        val me = ComponentName(context, ShieldAccessibilityService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    fun notificationAccessEnabled(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName) &&
            Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners").orEmpty()
                .contains(ComponentName(context, ShieldNotificationListener::class.java).flattenToString())

    fun usageAccessEnabled(): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java)
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun callScreeningRoleHeld(): Boolean {
        if (Build.VERSION.SDK_INT < 29) return false
        val rm = context.getSystemService(RoleManager::class.java) ?: return false
        return rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) && rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
    }

    fun locationGranted() = isGranted(Manifest.permission.ACCESS_FINE_LOCATION) || isGranted(Manifest.permission.ACCESS_COARSE_LOCATION)

    fun backgroundLocationGranted() = Build.VERSION.SDK_INT < 29 || isGranted(Manifest.permission.ACCESS_BACKGROUND_LOCATION)

    fun vpnPrepared(): Boolean = VpnService.prepare(context) == null

    fun deviceAdminActive(): Boolean =
        context.getSystemService(DevicePolicyManager::class.java).isAdminActive(ComponentName(context, ShieldDeviceAdmin::class.java))

    fun overlayGranted(): Boolean = Settings.canDrawOverlays(context)

    fun batteryUnrestricted(): Boolean = context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

    fun notificationsGranted(): Boolean = Build.VERSION.SDK_INT < 33 || isGranted(Manifest.permission.POST_NOTIFICATIONS)

    fun isActive(c: ProtectionComponent): Boolean = when (c) {
        ProtectionComponent.FOREGROUND_SERVICE -> ShieldRuntime.serviceRunning
        ProtectionComponent.NOTIFICATIONS -> notificationsGranted()
        ProtectionComponent.ACCESSIBILITY -> accessibilityEnabled()
        ProtectionComponent.NOTIFICATION_ACCESS -> notificationAccessEnabled()
        ProtectionComponent.USAGE_ACCESS -> usageAccessEnabled()
        ProtectionComponent.CALL_SCREENING -> callScreeningRoleHeld()
        ProtectionComponent.LOCATION -> locationGranted()
        ProtectionComponent.BACKGROUND_LOCATION -> backgroundLocationGranted()
        ProtectionComponent.VPN_FILTER -> vpnPrepared() && ShieldVpnService.running
        ProtectionComponent.DEVICE_ADMIN -> deviceAdminActive()
        ProtectionComponent.OVERLAY -> overlayGranted()
        ProtectionComponent.BATTERY_OPTIMIZATION -> batteryUnrestricted()
        ProtectionComponent.CONTACTS -> isGranted(Manifest.permission.READ_CONTACTS)
        ProtectionComponent.PHONE_STATE -> isGranted(Manifest.permission.READ_PHONE_STATE)
    }

    /** Components the role needs, in the order the setup wizard asks for them. */
    fun required(role: MemberRole?, child: ChildPolicy, elder: ElderPolicy): List<ProtectionComponent> = when (role) {
        MemberRole.CHILD -> buildList {
            add(ProtectionComponent.NOTIFICATIONS)
            add(ProtectionComponent.USAGE_ACCESS)
            add(ProtectionComponent.ACCESSIBILITY)
            add(ProtectionComponent.OVERLAY)
            if (child.messageMonitoring.enabled) add(ProtectionComponent.NOTIFICATION_ACCESS)
            add(ProtectionComponent.LOCATION)
            add(ProtectionComponent.BACKGROUND_LOCATION)
            if (child.webFilter.enabled) add(ProtectionComponent.VPN_FILTER)
            if (child.uninstallProtection) add(ProtectionComponent.DEVICE_ADMIN)
            add(ProtectionComponent.BATTERY_OPTIMIZATION)
            add(ProtectionComponent.FOREGROUND_SERVICE)
        }
        MemberRole.ELDER -> buildList {
            add(ProtectionComponent.NOTIFICATIONS)
            if (Build.VERSION.SDK_INT >= 29) add(ProtectionComponent.CALL_SCREENING)
            add(ProtectionComponent.CONTACTS)
            add(ProtectionComponent.PHONE_STATE)
            add(ProtectionComponent.NOTIFICATION_ACCESS)
            add(ProtectionComponent.ACCESSIBILITY)
            add(ProtectionComponent.OVERLAY)
            add(ProtectionComponent.LOCATION)
            add(ProtectionComponent.BACKGROUND_LOCATION)
            if (elder.webFilter.enabled) add(ProtectionComponent.VPN_FILTER)
            add(ProtectionComponent.BATTERY_OPTIMIZATION)
            add(ProtectionComponent.FOREGROUND_SERVICE)
        }
        else -> listOf(ProtectionComponent.NOTIFICATIONS)
    }

    data class Snapshot(val active: Set<ProtectionComponent>, val missing: List<ProtectionComponent>) {
        val criticalMissing: List<ProtectionComponent> get() = missing.filter { it.critical }
        val score: Int get() = if (active.isEmpty() && missing.isEmpty()) 100 else 100 * active.size / (active.size + missing.size)
    }

    fun snapshot(role: MemberRole?, child: ChildPolicy, elder: ElderPolicy): Snapshot {
        val req = required(role, child, elder)
        val active = req.filter { isActive(it) }.toSet()
        return Snapshot(active, req.filter { it !in active })
    }
}

/** Process-wide runtime flags shared between services. */
object ShieldRuntime {
    @Volatile var serviceRunning: Boolean = false
    @Volatile var lastForegroundPackage: String? = null
    @Volatile var lastInteractionAt: Long = System.currentTimeMillis()
}
