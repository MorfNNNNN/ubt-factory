package ru.ubt.familyshield.ui.guardian

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.ubt.familyshield.AppGraph
import ru.ubt.familyshield.core.db.MemberEntity
import ru.ubt.familyshield.core.family.childPolicy
import ru.ubt.familyshield.core.family.memberRole
import ru.ubt.familyshield.shared.model.MemberRole
import ru.ubt.familyshield.ui.FamilyUiState
import ru.ubt.familyshield.ui.ShieldViewModel
import ru.ubt.familyshield.ui.components.Avatar
import ru.ubt.familyshield.ui.components.Banner
import ru.ubt.familyshield.ui.components.Gap
import ru.ubt.familyshield.ui.components.Pill
import ru.ubt.familyshield.ui.components.PrimaryButton
import ru.ubt.familyshield.ui.components.ScreenScaffold
import ru.ubt.familyshield.ui.components.SectionCard
import ru.ubt.familyshield.ui.components.relativeTime
import ru.ubt.familyshield.ui.components.severityColor
import ru.ubt.familyshield.ui.invite.InviteScreen
import ru.ubt.familyshield.ui.paywall.PaywallScreen
import ru.ubt.familyshield.ui.paywall.UpsellReason
import ru.ubt.familyshield.ui.theme.Brand
import ru.ubt.familyshield.shared.policy.formatMinutes

@Composable
fun GuardianDashboard(graph: AppGraph, vm: ShieldViewModel, state: FamilyUiState, onOpenMember: (String) -> Unit, onGoAlerts: () -> Unit) {
    var showInvite by remember { mutableStateOf(false) }
    var paywall by remember { mutableStateOf<UpsellReason?>(null) }

    paywall?.let { PaywallScreen(graph, it) { paywall = null }; return }
    if (showInvite) {
        InviteScreen(graph, state) { showInvite = false }
        return
    }

    ScreenScaffold(title = state.settings.familyName.ifBlank { "Моя семья" }) { padding ->
        Column(Modifier.padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val critical = state.alerts.firstOrNull { it.severity == "CRITICAL" && !it.read }
            if (critical != null) {
                Banner("${state.members.firstOrNull { it.deviceId == critical.deviceId }?.displayName ?: "Близкий"}: ${critical.title}", Brand.Danger, emoji = "🚨", onClick = onGoAlerts)
            }

            if (state.pendingRequests.isNotEmpty()) {
                SectionCard(title = "Запросы ждут ответа (${state.pendingRequests.size})", container = MaterialTheme.colorScheme.secondaryContainer) {
                    state.pendingRequests.take(3).forEach { req ->
                        val name = state.members.firstOrNull { it.deviceId == req.deviceId }?.displayName ?: "Ребёнок"
                        Text("• $name — ${requestSummary(req)}", style = MaterialTheme.typography.bodyLarge)
                    }
                    PrimaryButton("Посмотреть запросы", color = Brand.Blue) { onGoAlerts() }
                }
            }

            if (state.others.isEmpty()) {
                SectionCard(title = "Добавьте близких", subtitle = "Подключите телефон ребёнка или пожилого родителя — им нужно всего отсканировать QR-код.") {
                    PrimaryButton("Добавить телефон") { showInvite = true }
                }
            } else {
                Text("Под защитой", style = MaterialTheme.typography.titleLarge)
                state.others.forEach { member ->
                    MemberCard(graph, state, member) { onOpenMember(member.deviceId) }
                }
                PrimaryButton("Добавить ещё телефон") { showInvite = true }
            }

            if (graph.entitlements.tier == ru.ubt.familyshield.shared.billing.Tier.FREE) {
                SectionCard(title = "Полная защита в Премиуме", subtitle = "Блокировка мошеннических звонков, лимиты и расписания, веб-фильтр, живая карта и оповещения об угрозах.", container = Brand.GreenLight) {
                    PrimaryButton("Открыть Премиум", color = Brand.Green) { paywall = UpsellReason.GENERAL }
                }
            }
            Gap(24.dp)
        }
    }
}

@Composable
private fun MemberCard(graph: AppGraph, state: FamilyUiState, member: MemberEntity, onClick: () -> Unit) {
    SectionCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Avatar(member.avatar, 52.dp)
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(member.displayName, style = MaterialTheme.typography.titleMedium)
                    Pill(roleLabel(member.memberRole), roleColor(member.memberRole))
                }
                val sub = buildList {
                    member.batteryText()?.let { add(it) }
                    if (member.memberRole == MemberRole.CHILD) member.todayMinutes?.let { add("сегодня ${formatMinutes(it)}") }
                    add(relativeTime(member.lastSeenAt))
                }.joinToString(" · ")
                Text(sub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val recent = state.alerts.firstOrNull { it.deviceId == member.deviceId && !it.read && it.severity in setOf("CRITICAL", "HIGH") }
            if (recent != null) {
                Avatar("⚠️", 32.dp, severityColor(ru.ubt.familyshield.shared.model.AlertSeverity.valueOf(recent.severity)).copy(alpha = 0.15f))
            }
        }
    }
}

private fun MemberEntity.batteryText(): String? {
    val status = statusJson ?: return null
    return runCatching {
        val st = ru.ubt.familyshield.shared.ShieldJson.decodeFromString(ru.ubt.familyshield.shared.model.DeviceStatus.serializer(), status)
        val icon = if (st.charging) "🔌" else "🔋"
        "$icon ${st.batteryPercent}%"
    }.getOrNull()
}

fun roleLabel(role: MemberRole) = when (role) {
    MemberRole.GUARDIAN -> "Взрослый"
    MemberRole.CHILD -> "Ребёнок"
    MemberRole.ELDER -> "Родитель"
}

fun roleColor(role: MemberRole) = when (role) {
    MemberRole.GUARDIAN -> Brand.Blue
    MemberRole.CHILD -> Brand.Purple
    MemberRole.ELDER -> Brand.Green
}

fun requestSummary(req: ru.ubt.familyshield.core.db.RequestEntity): String {
    val r = runCatching { ru.ubt.familyshield.shared.ShieldJson.decodeFromString(ru.ubt.familyshield.shared.model.MemberRequest.serializer(), req.json) }.getOrNull()
    return when (r?.kind) {
        ru.ubt.familyshield.shared.model.RequestKind.MORE_TIME -> "просит ещё ${r.minutes ?: 15} мин"
        ru.ubt.familyshield.shared.model.RequestKind.APP_APPROVAL -> "хочет «${r.appLabel ?: r.packageName}»"
        ru.ubt.familyshield.shared.model.RequestKind.SITE_UNBLOCK -> "просит сайт ${r.domain}"
        ru.ubt.familyshield.shared.model.RequestKind.PROTECTION_CHANGE -> "хочет отключить защиту"
        null -> "запрос"
    }
}
