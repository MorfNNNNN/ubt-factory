package ru.ubt.familyshield.ui.setup

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import ru.ubt.familyshield.AppGraph
import ru.ubt.familyshield.protect.service.ShieldDeviceAdmin
import ru.ubt.familyshield.protect.service.ShieldVpnService
import ru.ubt.familyshield.shared.model.ProtectionComponent
import ru.ubt.familyshield.ui.components.Banner
import ru.ubt.familyshield.ui.components.PrimaryButton
import ru.ubt.familyshield.ui.components.ProgressBar
import ru.ubt.familyshield.ui.components.SecondaryButton
import ru.ubt.familyshield.ui.components.SectionCard
import ru.ubt.familyshield.ui.theme.Brand

private data class PermCopy(val title: String, val why: String)

private fun copyFor(c: ProtectionComponent): PermCopy = when (c) {
    ProtectionComponent.NOTIFICATIONS -> PermCopy("Разрешить уведомления", "Чтобы показывать предупреждения об опасности и напоминания")
    ProtectionComponent.USAGE_ACCESS -> PermCopy("Доступ к статистике приложений", "Чтобы считать экранное время по приложениям — как в «Цифровом благополучии»")
    ProtectionComponent.ACCESSIBILITY -> PermCopy("Включить спецвозможности", "Чтобы мгновенно применять лимиты и предупреждать в опасный момент. Содержимое экрана не сохраняется")
    ProtectionComponent.NOTIFICATION_ACCESS -> PermCopy("Доступ к уведомлениям", "Чтобы находить мошеннические и опасные сообщения прямо на телефоне")
    ProtectionComponent.CALL_SCREENING -> PermCopy("Сделать приложением для звонков", "Чтобы блокировать мошеннические номера и предупреждать о незнакомых")
    ProtectionComponent.CONTACTS -> PermCopy("Доступ к контактам", "Чтобы отличать звонок знакомого от незнакомца")
    ProtectionComponent.PHONE_STATE -> PermCopy("Доступ к состоянию звонков", "Чтобы знать, идёт ли разговор, в опасный момент")
    ProtectionComponent.LOCATION -> PermCopy("Доступ к геолокации", "Чтобы семья видела, где вы, и работали геозоны и SOS")
    ProtectionComponent.BACKGROUND_LOCATION -> PermCopy("Геолокация в фоне", "Чтобы местоположение обновлялось, даже когда приложение закрыто")
    ProtectionComponent.VPN_FILTER -> PermCopy("Включить веб-фильтр", "Локальный фильтр блокирует опасные сайты. Трафик никуда не передаётся")
    ProtectionComponent.OVERLAY -> PermCopy("Показ поверх окон", "Чтобы предупреждение появлялось прямо на экране звонка")
    ProtectionComponent.DEVICE_ADMIN -> PermCopy("Защита от удаления", "Чтобы приложение нельзя было удалить без ведома родителей")
    ProtectionComponent.BATTERY_OPTIMIZATION -> PermCopy("Работа без ограничений батареи", "Чтобы защита не отключалась в фоне")
    ProtectionComponent.FOREGROUND_SERVICE -> PermCopy("Запустить защиту", "Постоянное уведомление честно показывает, что телефон под защитой")
}

/** Steps the person through each permission, explaining why, and verifies it was actually granted. */
@Composable
fun PermissionWizard(graph: AppGraph, onboarding: Boolean, onBack: (() -> Unit)?, onDone: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val s by graph.settings.state.collectAsState()
    var refresh by remember { mutableIntStateOf(0) }
    val snapshot = remember(refresh, s.role) { graph.protection.health.snapshot(s.role, s.childPolicy, s.elderPolicy) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh++ }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val missing = snapshot.missing
    val total = snapshot.active.size + missing.size

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }
    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refresh++ }
    val bgLocationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }
    val phonePermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refresh++ }
    val genericLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { refresh++ }
    val vpnLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == Activity.RESULT_OK) ShieldVpnService.start(context)
        refresh++
    }

    fun request(c: ProtectionComponent) {
        when (c) {
            ProtectionComponent.NOTIFICATIONS -> if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) else genericLauncher.launch(appSettings(context))
            ProtectionComponent.LOCATION -> locationLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            ProtectionComponent.BACKGROUND_LOCATION -> if (Build.VERSION.SDK_INT >= 29) bgLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION) else genericLauncher.launch(appSettings(context))
            ProtectionComponent.CONTACTS -> phonePermLauncher.launch(arrayOf(Manifest.permission.READ_CONTACTS))
            ProtectionComponent.PHONE_STATE -> phonePermLauncher.launch(arrayOf(Manifest.permission.READ_PHONE_STATE, Manifest.permission.CALL_PHONE, Manifest.permission.SEND_SMS))
            ProtectionComponent.USAGE_ACCESS -> genericLauncher.launch(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            ProtectionComponent.ACCESSIBILITY -> genericLauncher.launch(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            ProtectionComponent.NOTIFICATION_ACCESS -> genericLauncher.launch(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            ProtectionComponent.OVERLAY -> genericLauncher.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
            ProtectionComponent.CALL_SCREENING -> requestCallScreening(context, genericLauncher::launch)
            ProtectionComponent.DEVICE_ADMIN -> genericLauncher.launch(deviceAdminIntent(context))
            ProtectionComponent.BATTERY_OPTIMIZATION -> requestBattery(context, genericLauncher::launch)
            ProtectionComponent.VPN_FILTER -> VpnService.prepare(context)?.let { vpnLauncher.launch(it) } ?: ShieldVpnService.start(context).also { refresh++ }
            ProtectionComponent.FOREGROUND_SERVICE -> {
                graph.protection.apply()
                refresh++
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (onBack != null) TextButton(onClick = onBack) { Text("← Назад") }
        Text(if (onboarding) "Включим защиту" else "Проверка защиты", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Android просит выдать доступы по одному. Мы объясняем, зачем нужен каждый. Шаги можно пройти не все сразу.",
            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ProgressBar((snapshot.active.size.toFloat() / total.coerceAtLeast(1)))
        Text("${snapshot.active.size} из $total готово", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)

        if (missing.isEmpty()) {
            Banner("Всё готово! Телефон под защитой.", Brand.GreenLight, textColor = Brand.GreenDark, emoji = "✅")
        }

        missing.forEach { c ->
            val copy = copyFor(c)
            SectionCard(container = if (c.critical) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier) {
                        Text(copy.title, style = MaterialTheme.typography.titleMedium)
                        Text(copy.why, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (!c.critical) Text("Необязательно", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                PrimaryButton(if (c.critical) "Разрешить" else "Разрешить (по желанию)") { request(c) }
            }
        }

        snapshot.active.toList().forEach { c ->
            Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("✅", fontSize = 18.sp)
                Text(copyFor(c).title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(Modifier.height(8.dp))
        val criticalLeft = snapshot.criticalMissing.isNotEmpty()
        PrimaryButton(if (criticalLeft || !onboarding) "Готово" else "Завершить настройку") { onDone() }
        if (onboarding && criticalLeft) {
            SecondaryButton("Пропустить пока") { onDone() }
            Text(
                "Без основных доступов защита работает частично. Можно вернуться к настройке в любой момент.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
            )
        }
    }
}

private fun appSettings(context: Context) = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))

private fun deviceAdminIntent(context: Context): Intent = Intent(android.app.admin.DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
    .putExtra(android.app.admin.DevicePolicyManager.EXTRA_DEVICE_ADMIN, ComponentName(context, ShieldDeviceAdmin::class.java))
    .putExtra(android.app.admin.DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Защитит «Семейный щит» от удаления без ведома родителей.")

private fun requestCallScreening(context: Context, launch: (Intent) -> Unit) {
    if (Build.VERSION.SDK_INT < 29) {
        launch(appSettings(context))
        return
    }
    val rm = context.getSystemService(android.app.role.RoleManager::class.java)
    if (rm != null && rm.isRoleAvailable(android.app.role.RoleManager.ROLE_CALL_SCREENING)) {
        launch(rm.createRequestRoleIntent(android.app.role.RoleManager.ROLE_CALL_SCREENING))
    } else {
        launch(appSettings(context))
    }
}

private fun requestBattery(context: Context, launch: (Intent) -> Unit) {
    @android.annotation.SuppressLint("BatteryLife")
    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
    launch(intent)
}
