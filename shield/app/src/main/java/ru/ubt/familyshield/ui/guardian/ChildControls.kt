package ru.ubt.familyshield.ui.guardian

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.MarkChatUnread
import androidx.compose.material.icons.rounded.PhonelinkLock
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Weekend
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.ubt.familyshield.AppGraph
import ru.ubt.familyshield.core.db.MemberEntity
import ru.ubt.familyshield.core.family.childPolicy
import ru.ubt.familyshield.shared.billing.Feature
import ru.ubt.familyshield.shared.model.AppCategory
import ru.ubt.familyshield.shared.model.ChildPolicy
import ru.ubt.familyshield.shared.model.ScheduleMode
import ru.ubt.familyshield.shared.model.ScheduleRule
import ru.ubt.familyshield.shared.model.TimeWindow
import ru.ubt.familyshield.shared.model.WebCategory
import ru.ubt.familyshield.shared.policy.formatMinuteOfDay
import ru.ubt.familyshield.ui.components.AvatarImage
import ru.ubt.familyshield.ui.components.FeatureIcons
import ru.ubt.familyshield.ui.components.Visual
import ru.ubt.familyshield.ui.paywall.PaywallScreen
import ru.ubt.familyshield.ui.paywall.UpsellReason
import ru.ubt.familyshield.ui.theme.Brand
import ru.ubt.familyshield.ui.theme.Gradients

@Composable
fun ChildControls(graph: AppGraph, member: MemberEntity, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var paywall by remember { mutableStateOf<UpsellReason?>(null) }
    val policy = remember(member.policyJson) { member.childPolicy() }
    val premium = graph.entitlements.has(Feature.APP_LIMITS)

    paywall?.let { PaywallScreen(graph, it) { paywall = null }; return }

    fun update(block: (ChildPolicy) -> ChildPolicy) = scope.launch { graph.guardian.updateChild(member.deviceId) { block(it) } }
    fun gate(feature: Feature, reason: UpsellReason, apply: () -> Unit) {
        if (graph.entitlements.has(feature)) apply() else paywall = reason
    }

    DetailScaffold(
        title = "Правила: ${member.displayName}",
        onBack = onBack,
        actions = { AvatarImage(member.avatar, member.role, 42.dp) },
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            GroupCard(title = "Экранное время") {
                if (!premium) {
                    IconToggleRow(
                        "Дневной лимит", Icons.Rounded.Today, Gradients.Sky, checked = false,
                        subtitle = "Ограничить время в развлекательных приложениях", locked = true,
                    ) { paywall = UpsellReason.KIDS }
                } else {
                    val weekday = policy.dailyLimitWeekdayMinutes ?: 0
                    IconToggleRow("Дневной лимит по будням", Icons.Rounded.Today, Gradients.Sky, checked = policy.dailyLimitWeekdayMinutes != null) { on ->
                        update { it.copy(dailyLimitWeekdayMinutes = if (on) 120 else null) }
                    }
                    if (policy.dailyLimitWeekdayMinutes != null) {
                        LimitSliderRow("Будни", weekday, 30..360, 15, { ru.ubt.familyshield.shared.policy.formatMinutes(it) }) { v -> update { it.copy(dailyLimitWeekdayMinutes = v) } }
                    }
                    val weekend = policy.dailyLimitWeekendMinutes ?: 0
                    IconToggleRow("Дневной лимит по выходным", Icons.Rounded.Weekend, Gradients.Sunrise, checked = policy.dailyLimitWeekendMinutes != null) { on ->
                        update { it.copy(dailyLimitWeekendMinutes = if (on) 180 else null) }
                    }
                    if (policy.dailyLimitWeekendMinutes != null) {
                        LimitSliderRow("Выходные", weekend, 30..480, 15, { ru.ubt.familyshield.shared.policy.formatMinutes(it) }) { v -> update { it.copy(dailyLimitWeekendMinutes = v) } }
                    }
                }
            }

            GroupCard(title = "Расписания", subtitle = "Когда доступны только звонки и важные приложения") {
                policy.schedules.forEach { s ->
                    val v = scheduleVisual(s)
                    IconToggleRow(
                        scheduleLabel(s), v.icon, v.gradient, checked = s.enabled,
                        subtitle = "${formatMinuteOfDay(s.window.startMinute)}–${formatMinuteOfDay(s.window.endMinute)}",
                        locked = !premium && s.id != "sleep",
                    ) { on ->
                        if (!premium && s.id != "sleep") paywall = UpsellReason.KIDS
                        else update { p -> p.copy(schedules = p.schedules.map { if (it.id == s.id) it.copy(enabled = on) else it }) }
                    }
                }
                if (policy.schedules.none { it.id == "study" }) {
                    Spacer(Modifier.height(6.dp))
                    PillButton(
                        "Добавить «Уроки»", modifier = Modifier.fillMaxWidth(), icon = Icons.Rounded.Add,
                        fill = SolidColor(MaterialTheme.colorScheme.primaryContainer), contentColor = Brand.GreenDark, elevated = false,
                    ) {
                        gate(Feature.SCHEDULES, UpsellReason.KIDS) {
                            update { it.copy(schedules = it.schedules + ScheduleRule("study", "Уроки", TimeWindow(TimeWindow.hm(8, 30), TimeWindow.hm(14), ru.ubt.familyshield.shared.model.WEEKDAYS), ScheduleMode.BLOCK_CATEGORIES, setOf(AppCategory.GAMES, AppCategory.SOCIAL))) }
                        }
                    }
                }
            }

            GroupCard(title = "Интернет и приложения") {
                IconToggleRow(
                    "Безопасный интернет", FeatureIcons.Web.icon, FeatureIcons.Web.gradient, checked = policy.webFilter.enabled,
                    subtitle = "Блокировать сайты 18+, мошенничество и вредоносные страницы", locked = !graph.entitlements.has(Feature.WEB_FILTER),
                ) { on ->
                    gate(Feature.WEB_FILTER, UpsellReason.KIDS) { update { it.copy(webFilter = it.webFilter.copy(enabled = on)) } }
                }
                if (policy.webFilter.enabled) {
                    IconToggleRow(
                        "Безопасный поиск", Icons.Rounded.TravelExplore, Gradients.Ocean, checked = policy.webFilter.safeSearch,
                        subtitle = "Яндекс, Google, YouTube в детском режиме",
                    ) { on -> update { it.copy(webFilter = it.webFilter.copy(safeSearch = on)) } }
                    IconToggleRow("Блокировать соцсети и знакомства", Icons.Rounded.Groups, Gradients.Coral, checked = WebCategory.SOCIAL in policy.webFilter.categories) { on ->
                        update { p -> p.copy(webFilter = p.webFilter.copy(categories = if (on) p.webFilter.categories + WebCategory.SOCIAL + WebCategory.DATING else p.webFilter.categories - WebCategory.SOCIAL - WebCategory.DATING)) }
                    }
                }
                IconToggleRow(
                    "Одобрять новые приложения", Icons.Rounded.InstallMobile, Gradients.Violet, checked = policy.requireApprovalForNewApps,
                    subtitle = "Спрашивать разрешение перед запуском новых игр и приложений", locked = !graph.entitlements.has(Feature.APP_APPROVAL),
                ) { on ->
                    gate(Feature.APP_APPROVAL, UpsellReason.KIDS) { update { it.copy(requireApprovalForNewApps = on) } }
                }
            }

            GroupCard(title = "Безопасность") {
                IconToggleRow(
                    "Оповещать об опасных переписках", Icons.Rounded.MarkChatUnread, Gradients.Danger, checked = policy.messageMonitoring.enabled,
                    subtitle = "Травля, вербовка, незнакомые взрослые, угрозы", locked = !graph.entitlements.has(Feature.RISK_ALERTS),
                ) { on ->
                    gate(Feature.RISK_ALERTS, UpsellReason.KIDS) { update { it.copy(messageMonitoring = it.messageMonitoring.copy(enabled = on)) } }
                }
                if (policy.messageMonitoring.enabled && policy.ageBand.isTeen) {
                    IconToggleRow(
                        "Щадящий режим для подростка", Icons.Rounded.VisibilityOff, Gradients.Slate, checked = policy.messageMonitoring.criticalOnly,
                        subtitle = "Сообщать только о самом опасном (угроза жизни, вербовка)",
                    ) { on ->
                        update { it.copy(messageMonitoring = it.messageMonitoring.copy(criticalOnly = on)) }
                    }
                }
                IconToggleRow(
                    "Защита от удаления", Icons.Rounded.PhonelinkLock, Gradients.Emerald, checked = policy.uninstallProtection,
                    subtitle = "Нельзя удалить приложение и отключить защиту без вас", locked = !graph.entitlements.has(Feature.UNINSTALL_PROTECTION),
                ) { on ->
                    gate(Feature.UNINSTALL_PROTECTION, UpsellReason.KIDS) { update { it.copy(uninstallProtection = on) } }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

private fun scheduleLabel(s: ScheduleRule) = when (s.id) {
    "sleep" -> "Сон"
    "study", "school" -> "Уроки"
    else -> s.name
}

private fun scheduleVisual(s: ScheduleRule): Visual = when (s.id) {
    "sleep" -> FeatureIcons.Sleep
    "study", "school" -> FeatureIcons.Study
    else -> Visual(Icons.Rounded.Schedule, Gradients.Slate)
}
