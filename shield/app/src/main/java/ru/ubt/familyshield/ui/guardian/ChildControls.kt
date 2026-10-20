package ru.ubt.familyshield.ui.guardian

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Modifier
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
import ru.ubt.familyshield.ui.components.Gap
import ru.ubt.familyshield.ui.components.PrimaryButton
import ru.ubt.familyshield.ui.components.ScreenScaffold
import ru.ubt.familyshield.ui.components.SectionCard
import ru.ubt.familyshield.ui.components.SliderRow
import ru.ubt.familyshield.ui.components.ToggleRow
import ru.ubt.familyshield.ui.paywall.PaywallScreen
import ru.ubt.familyshield.ui.paywall.UpsellReason

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

    ScreenScaffold(title = "Правила: ${member.displayName}", onBack = onBack) { padding ->
        Column(Modifier.padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {

            SectionCard(title = "Экранное время") {
                if (!premium) {
                    ToggleRow("Дневной лимит", "Ограничить время в развлекательных приложениях", checked = false, locked = true) { paywall = UpsellReason.KIDS }
                } else {
                    val weekday = policy.dailyLimitWeekdayMinutes ?: 0
                    ToggleRow("Дневной лимит по будням", checked = policy.dailyLimitWeekdayMinutes != null) { on ->
                        update { it.copy(dailyLimitWeekdayMinutes = if (on) 120 else null) }
                    }
                    if (policy.dailyLimitWeekdayMinutes != null) {
                        SliderRow("Будни", weekday, 30..360, 15, { ru.ubt.familyshield.shared.policy.formatMinutes(it) }) { v -> update { it.copy(dailyLimitWeekdayMinutes = v) } }
                    }
                    val weekend = policy.dailyLimitWeekendMinutes ?: 0
                    ToggleRow("Дневной лимит по выходным", checked = policy.dailyLimitWeekendMinutes != null) { on ->
                        update { it.copy(dailyLimitWeekendMinutes = if (on) 180 else null) }
                    }
                    if (policy.dailyLimitWeekendMinutes != null) {
                        SliderRow("Выходные", weekend, 30..480, 15, { ru.ubt.familyshield.shared.policy.formatMinutes(it) }) { v -> update { it.copy(dailyLimitWeekendMinutes = v) } }
                    }
                }
            }

            SectionCard(title = "Расписания", subtitle = "Когда доступны только звонки и важные приложения") {
                policy.schedules.forEach { s ->
                    ToggleRow(scheduleLabel(s), "${formatMinuteOfDay(s.window.startMinute)}–${formatMinuteOfDay(s.window.endMinute)}", checked = s.enabled, locked = !premium && s.id != "sleep") { on ->
                        if (!premium && s.id != "sleep") paywall = UpsellReason.KIDS
                        else update { p -> p.copy(schedules = p.schedules.map { if (it.id == s.id) it.copy(enabled = on) else it }) }
                    }
                }
                if (policy.schedules.none { it.id == "study" }) {
                    PrimaryButton("Добавить «Уроки»") {
                        gate(Feature.SCHEDULES, UpsellReason.KIDS) {
                            update { it.copy(schedules = it.schedules + ScheduleRule("study", "Уроки", TimeWindow(TimeWindow.hm(8, 30), TimeWindow.hm(14), ru.ubt.familyshield.shared.model.WEEKDAYS), ScheduleMode.BLOCK_CATEGORIES, setOf(AppCategory.GAMES, AppCategory.SOCIAL))) }
                        }
                    }
                }
            }

            SectionCard(title = "Интернет и приложения") {
                ToggleRow("Безопасный интернет", "Блокировать сайты 18+, мошенничество и вредоносные страницы", checked = policy.webFilter.enabled, locked = !graph.entitlements.has(Feature.WEB_FILTER)) { on ->
                    gate(Feature.WEB_FILTER, UpsellReason.KIDS) { update { it.copy(webFilter = it.webFilter.copy(enabled = on)) } }
                }
                if (policy.webFilter.enabled) {
                    ToggleRow("Безопасный поиск", "Яндекс, Google, YouTube в детском режиме", checked = policy.webFilter.safeSearch) { on -> update { it.copy(webFilter = it.webFilter.copy(safeSearch = on)) } }
                    ToggleRow("Блокировать соцсети и знакомства", checked = WebCategory.SOCIAL in policy.webFilter.categories) { on ->
                        update { p -> p.copy(webFilter = p.webFilter.copy(categories = if (on) p.webFilter.categories + WebCategory.SOCIAL + WebCategory.DATING else p.webFilter.categories - WebCategory.SOCIAL - WebCategory.DATING)) }
                    }
                }
                ToggleRow("Одобрять новые приложения", "Спрашивать разрешение перед запуском новых игр и приложений", checked = policy.requireApprovalForNewApps, locked = !graph.entitlements.has(Feature.APP_APPROVAL)) { on ->
                    gate(Feature.APP_APPROVAL, UpsellReason.KIDS) { update { it.copy(requireApprovalForNewApps = on) } }
                }
            }

            SectionCard(title = "Безопасность") {
                ToggleRow("Оповещать об опасных переписках", "Травля, вербовка, незнакомые взрослые, угрозы", checked = policy.messageMonitoring.enabled, locked = !graph.entitlements.has(Feature.RISK_ALERTS)) { on ->
                    gate(Feature.RISK_ALERTS, UpsellReason.KIDS) { update { it.copy(messageMonitoring = it.messageMonitoring.copy(enabled = on)) } }
                }
                if (policy.messageMonitoring.enabled && policy.ageBand.isTeen) {
                    ToggleRow("Щадящий режим для подростка", "Сообщать только о самом опасном (угроза жизни, вербовка)", checked = policy.messageMonitoring.criticalOnly) { on ->
                        update { it.copy(messageMonitoring = it.messageMonitoring.copy(criticalOnly = on)) }
                    }
                }
                ToggleRow("Защита от удаления", "Нельзя удалить приложение и отключить защиту без вас", checked = policy.uninstallProtection, locked = !graph.entitlements.has(Feature.UNINSTALL_PROTECTION)) { on ->
                    gate(Feature.UNINSTALL_PROTECTION, UpsellReason.KIDS) { update { it.copy(uninstallProtection = on) } }
                }
            }

            Gap(24.dp)
        }
    }
}

private fun scheduleLabel(s: ScheduleRule) = when (s.id) {
    "sleep" -> "🌙 Сон"
    "study", "school" -> "📚 Уроки"
    else -> s.name
}
