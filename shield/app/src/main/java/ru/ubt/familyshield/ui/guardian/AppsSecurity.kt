package ru.ubt.familyshield.ui.guardian

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.ubt.familyshield.AppGraph
import ru.ubt.familyshield.core.db.MemberEntity
import ru.ubt.familyshield.core.family.childPolicy
import ru.ubt.familyshield.shared.billing.Feature
import ru.ubt.familyshield.shared.model.AppInventory
import ru.ubt.familyshield.shared.model.AppMode
import ru.ubt.familyshield.shared.model.Command
import ru.ubt.familyshield.shared.model.InstalledApp
import ru.ubt.familyshield.shared.model.SecurityReport
import ru.ubt.familyshield.shared.model.UsageReport
import ru.ubt.familyshield.shared.policy.formatMinutes
import ru.ubt.familyshield.ui.components.Banner
import ru.ubt.familyshield.ui.components.EmptyState
import ru.ubt.familyshield.ui.components.Pill
import ru.ubt.familyshield.ui.components.ProgressBar
import ru.ubt.familyshield.ui.components.ScreenScaffold
import ru.ubt.familyshield.ui.components.SecondaryButton
import ru.ubt.familyshield.ui.components.SectionCard
import ru.ubt.familyshield.ui.components.ToggleRow
import ru.ubt.familyshield.ui.components.relativeTime
import ru.ubt.familyshield.ui.paywall.PaywallScreen
import ru.ubt.familyshield.ui.paywall.UpsellReason
import ru.ubt.familyshield.ui.theme.Brand
import java.util.UUID

@Composable
fun AppsScreen(graph: AppGraph, member: MemberEntity, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var paywall by remember { mutableStateOf(false) }
    val inventory = remember(member.inventoryJson) {
        member.inventoryJson?.let { runCatching { ru.ubt.familyshield.shared.ShieldJson.decodeFromString(AppInventory.serializer(), it) }.getOrNull() }
    }
    val policy = remember(member.policyJson) { member.childPolicy() }
    val canLimit = graph.entitlements.has(Feature.APP_LIMITS)

    if (paywall) { PaywallScreen(graph, UpsellReason.KIDS) { paywall = false }; return }

    ScreenScaffold(title = "Приложения", onBack = onBack) { padding ->
        if (inventory == null) {
            EmptyState("📱", "Список обновляется", "Как только телефон ребёнка выйдет на связь, здесь появятся приложения и экранное время.")
            return@ScreenScaffold
        }
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            member.todayMinutes?.let {
                item { SectionCard { Text("Сегодня в телефоне: ${formatMinutes(it)}", style = MaterialTheme.typography.titleMedium) } }
            }
            val risky = inventory.apps.filter { it.riskFlags.isNotEmpty() }
            if (risky.isNotEmpty()) {
                item { Text("Требуют внимания", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
                items(risky, key = { "r" + it.packageName }) { app -> AppRow(graph, member, app, policy, canLimit) { paywall = true } }
            }
            item { Text("Все приложения (${inventory.apps.size})", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp)) }
            items(inventory.apps, key = { it.packageName }) { app -> AppRow(graph, member, app, policy, canLimit) { paywall = true } }
        }
    }
}

@Composable
private fun AppRow(graph: AppGraph, member: MemberEntity, app: InstalledApp, policy: ru.ubt.familyshield.shared.model.ChildPolicy, canLimit: Boolean, onUpsell: () -> Unit) {
    val scope = rememberCoroutineScope()
    val blocked = policy.appRules[app.packageName]?.mode == AppMode.BLOCKED
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(app.label, style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if ("sideloaded" in app.riskFlags) Pill("не из магазина", Brand.Warning)
                    if ("remote_access" in app.riskFlags) Pill("удалённый доступ", Brand.Danger)
                    if ("vpn_or_proxy" in app.riskFlags) Pill("VPN", Brand.Warning)
                }
            }
            ToggleRow("Заблокировать", checked = blocked, locked = !canLimit) { on ->
                if (!canLimit) onUpsell()
                else scope.launch {
                    graph.guardian.command(member.deviceId, if (on) Command.DenyApp(UUID.randomUUID().toString(), app.packageName) else Command.ApproveApp(UUID.randomUUID().toString(), app.packageName))
                }
            }
        }
    }
}

@Composable
fun SecurityScreen(graph: AppGraph, member: MemberEntity, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val report = remember(member.securityJson) {
        member.securityJson?.let { runCatching { ru.ubt.familyshield.shared.ShieldJson.decodeFromString(SecurityReport.serializer(), it) }.getOrNull() }
    }
    ScreenScaffold(title = "Безопасность телефона", onBack = onBack) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SecondaryButton("Проверить сейчас") { scope.launch { graph.guardian.command(member.deviceId, Command.RunSecurityScan(UUID.randomUUID().toString())) } }
            if (report == null) {
                EmptyState("🛡️", "Нет данных", "Запустите проверку — результат появится, когда телефон выйдет на связь.")
                return@Column
            }
            SectionCard {
                Text("Оценка защищённости: ${report.score}/100", style = MaterialTheme.typography.titleLarge)
                ProgressBar(report.score / 100f, if (report.score >= 70) Brand.Green else if (report.score >= 40) Brand.Warning else Brand.Danger)
                Text("Проверено ${relativeTime(report.scannedAt)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (report.findings.isEmpty()) {
                Banner("Угроз не найдено. Телефон защищён.", Brand.GreenLight, textColor = Brand.GreenDark, emoji = "✅")
            }
            report.findings.forEach { f ->
                SectionCard(container = ru.ubt.familyshield.ui.components.severityColor(f.severity).copy(alpha = 0.1f)) {
                    Text(f.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(f.description, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
