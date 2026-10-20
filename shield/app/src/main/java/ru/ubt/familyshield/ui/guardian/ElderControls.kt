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
import ru.ubt.familyshield.core.family.elderPolicy
import ru.ubt.familyshield.shared.billing.Feature
import ru.ubt.familyshield.shared.model.ElderPolicy
import ru.ubt.familyshield.ui.components.Gap
import ru.ubt.familyshield.ui.components.ScreenScaffold
import ru.ubt.familyshield.ui.components.SectionCard
import ru.ubt.familyshield.ui.components.SliderRow
import ru.ubt.familyshield.ui.components.ToggleRow
import ru.ubt.familyshield.ui.paywall.PaywallScreen
import ru.ubt.familyshield.ui.paywall.UpsellReason

@Composable
fun ElderControls(graph: AppGraph, member: MemberEntity, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var paywall by remember { mutableStateOf<UpsellReason?>(null) }
    val policy = remember(member.policyJson) { member.elderPolicy() }

    paywall?.let { PaywallScreen(graph, it) { paywall = null }; return }

    fun update(block: (ElderPolicy) -> ElderPolicy) = scope.launch { graph.guardian.updateElder(member.deviceId) { block(it) } }
    fun gate(feature: Feature, apply: () -> Unit) {
        if (graph.entitlements.has(feature)) apply() else paywall = UpsellReason.ELDER
    }

    ScreenScaffold(title = "Защита: ${member.displayName}", onBack = onBack) { padding ->
        Column(Modifier.padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {

            SectionCard(title = "Защита от мошеннических звонков") {
                ToggleRow("Блокировать мошеннические номера", "По жалобам пользователей и чёрному списку", checked = policy.callShield.blockKnownSpam, locked = !graph.entitlements.has(Feature.CALL_BLOCKING)) { on ->
                    gate(Feature.CALL_BLOCKING) { update { it.copy(callShield = it.callShield.copy(blockKnownSpam = on)) } }
                }
                ToggleRow("Предупреждать о незнакомых номерах", "Показывать подсказку на экране звонка", checked = policy.callShield.warnUnknown) { on ->
                    update { it.copy(callShield = it.callShield.copy(warnUnknown = on)) }
                }
                ToggleRow("Блокировать звонки из-за рубежа", "Мошенники часто звонят из других стран", checked = policy.callShield.blockInternational, locked = !graph.entitlements.has(Feature.CALL_BLOCKING)) { on ->
                    gate(Feature.CALL_BLOCKING) { update { it.copy(callShield = it.callShield.copy(blockInternational = on)) } }
                }
                ToggleRow("Блокировать скрытые номера", checked = policy.callShield.blockHiddenNumbers, locked = !graph.entitlements.has(Feature.CALL_BLOCKING)) { on ->
                    gate(Feature.CALL_BLOCKING) { update { it.copy(callShield = it.callShield.copy(blockHiddenNumbers = on)) } }
                }
            }

            SectionCard(title = "Защита в опасный момент") {
                ToggleRow("Тревога: банк во время звонка", "Если открыть банк при разговоре с незнакомцем", checked = policy.inCallBankGuard, locked = !graph.entitlements.has(Feature.IN_CALL_GUARD)) { on ->
                    gate(Feature.IN_CALL_GUARD) { update { it.copy(inCallBankGuard = on) } }
                }
                ToggleRow("Защита кодов из СМС", "Предупреждать, если код пришёл во время звонка", checked = policy.otpGuard, locked = !graph.entitlements.has(Feature.OTP_GUARD)) { on ->
                    gate(Feature.OTP_GUARD) { update { it.copy(otpGuard = on) } }
                }
                ToggleRow("Защита от удалённого доступа", "Тревога при запуске AnyDesk, TeamViewer и т.п.", checked = policy.remoteAccessGuard, locked = !graph.entitlements.has(Feature.REMOTE_ACCESS_GUARD)) { on ->
                    gate(Feature.REMOTE_ACCESS_GUARD) { update { it.copy(remoteAccessGuard = on) } }
                }
                ToggleRow("Проверка сообщений на мошенничество", checked = policy.messageScan, locked = !graph.entitlements.has(Feature.SCAM_MESSAGE_SCAN)) { on ->
                    gate(Feature.SCAM_MESSAGE_SCAN) { update { it.copy(messageScan = on) } }
                }
                ToggleRow("Тревога при установке приложений не из магазина", checked = policy.sideloadGuard, locked = !graph.entitlements.has(Feature.SIDELOAD_GUARD)) { on ->
                    gate(Feature.SIDELOAD_GUARD) { update { it.copy(sideloadGuard = on) } }
                }
            }

            SectionCard(title = "Забота и помощь") {
                ToggleRow("Ежедневная отметка «Я в порядке»", checked = policy.checkIn.enabled, locked = !graph.entitlements.has(Feature.CHECK_IN)) { on ->
                    gate(Feature.CHECK_IN) { update { it.copy(checkIn = it.checkIn.copy(enabled = on)) } }
                }
                ToggleRow("Тревога при долгом бездействии", "Если телефоном не пользуются много часов", checked = policy.inactivityHours != null, locked = !graph.entitlements.has(Feature.INACTIVITY_ALERT)) { on ->
                    gate(Feature.INACTIVITY_ALERT) { update { it.copy(inactivityHours = if (on) 12 else null) } }
                }
                ToggleRow("Определение падения", "Сенсор телефона; тревога с возможностью отмены", checked = policy.fallDetection, locked = !graph.entitlements.has(Feature.FALL_DETECTION)) { on ->
                    gate(Feature.FALL_DETECTION) { update { it.copy(fallDetection = on) } }
                }
                if (policy.medications.isEmpty()) {
                    Text("Напоминания о лекарствах можно добавить на телефоне родителя в его приложении.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text("Лекарств настроено: ${policy.medications.size}", style = MaterialTheme.typography.bodyMedium)
                }
            }

            SectionCard(title = "Удобство") {
                ToggleRow("Крупные кнопки (простой режим)", "Большой шрифт и простой экран с главными функциями", checked = policy.simpleMode.enabled, locked = !graph.entitlements.has(Feature.REMOTE_SETUP)) { on ->
                    gate(Feature.REMOTE_SETUP) { update { it.copy(simpleMode = it.simpleMode.copy(enabled = on)) } }
                }
                ToggleRow("Безопасный интернет", "Блокировать мошеннические и вредоносные сайты", checked = policy.webFilter.enabled, locked = !graph.entitlements.has(Feature.LINK_SCAN)) { on ->
                    gate(Feature.LINK_SCAN) { update { it.copy(webFilter = it.webFilter.copy(enabled = on)) } }
                }
            }
            Gap(24.dp)
        }
    }
}
