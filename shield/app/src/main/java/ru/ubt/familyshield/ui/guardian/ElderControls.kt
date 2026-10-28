package ru.ubt.familyshield.ui.guardian

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.ScreenShare
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.MedicalServices
import androidx.compose.material.icons.rounded.Password
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.PersonalInjury
import androidx.compose.material.icons.rounded.PhoneInTalk
import androidx.compose.material.icons.rounded.PhoneLocked
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.TextIncrease
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.ubt.familyshield.AppGraph
import ru.ubt.familyshield.core.db.MemberEntity
import ru.ubt.familyshield.core.family.elderPolicy
import ru.ubt.familyshield.shared.billing.Feature
import ru.ubt.familyshield.shared.model.ElderPolicy
import ru.ubt.familyshield.shared.policy.formatRub
import ru.ubt.familyshield.ui.components.AvatarImage
import ru.ubt.familyshield.ui.components.FeatureIcons
import ru.ubt.familyshield.ui.components.IconTile
import ru.ubt.familyshield.ui.components.TileRow
import ru.ubt.familyshield.ui.elder.MedicalCardEditor
import ru.ubt.familyshield.ui.paywall.PaywallScreen
import ru.ubt.familyshield.ui.paywall.UpsellReason
import ru.ubt.familyshield.ui.theme.Gradients

@Composable
fun ElderControls(graph: AppGraph, member: MemberEntity, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var paywall by remember { mutableStateOf<UpsellReason?>(null) }
    val policy = remember(member.policyJson) { member.elderPolicy() }

    var medical by remember { mutableStateOf(false) }

    paywall?.let { PaywallScreen(graph, it) { paywall = null }; return }

    fun update(block: (ElderPolicy) -> ElderPolicy) = scope.launch { graph.guardian.updateElder(member.deviceId) { block(it) } }

    if (medical) {
        MedicalCardEditor(member.displayName, policy.medical, onBack = { medical = false }) { card ->
            update { it.copy(medical = card) }
            medical = false
        }
        return
    }
    fun gate(feature: Feature, apply: () -> Unit) {
        if (graph.entitlements.has(feature)) apply() else paywall = UpsellReason.ELDER
    }

    DetailScaffold(
        title = "Защита: ${member.displayName}",
        onBack = onBack,
        actions = { AvatarImage(member.avatar, member.role, 42.dp) },
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            GroupCard(title = "Защита от мошеннических звонков") {
                IconToggleRow(
                    "Блокировать мошеннические номера", FeatureIcons.Calls.icon, FeatureIcons.Calls.gradient, checked = policy.callShield.blockKnownSpam,
                    subtitle = "По жалобам пользователей и чёрному списку", locked = !graph.entitlements.has(Feature.CALL_BLOCKING),
                ) { on ->
                    gate(Feature.CALL_BLOCKING) { update { it.copy(callShield = it.callShield.copy(blockKnownSpam = on)) } }
                }
                IconToggleRow(
                    "Предупреждать о незнакомых номерах", Icons.Rounded.PhoneInTalk, Gradients.Sunrise, checked = policy.callShield.warnUnknown,
                    subtitle = "Показывать подсказку на экране звонка",
                ) { on ->
                    update { it.copy(callShield = it.callShield.copy(warnUnknown = on)) }
                }
                IconToggleRow(
                    "Блокировать звонки из-за рубежа", Icons.Rounded.Public, Gradients.Ocean, checked = policy.callShield.blockInternational,
                    subtitle = "Мошенники часто звонят из других стран", locked = !graph.entitlements.has(Feature.CALL_BLOCKING),
                ) { on ->
                    gate(Feature.CALL_BLOCKING) { update { it.copy(callShield = it.callShield.copy(blockInternational = on)) } }
                }
                IconToggleRow(
                    "Блокировать скрытые номера", Icons.Rounded.PhoneLocked, Gradients.Slate, checked = policy.callShield.blockHiddenNumbers,
                    locked = !graph.entitlements.has(Feature.CALL_BLOCKING),
                ) { on ->
                    gate(Feature.CALL_BLOCKING) { update { it.copy(callShield = it.callShield.copy(blockHiddenNumbers = on)) } }
                }
            }

            GroupCard(title = "Защита в опасный момент") {
                IconToggleRow(
                    "Тревога: банк во время звонка", Icons.Rounded.AccountBalance, Gradients.Danger, checked = policy.inCallBankGuard,
                    subtitle = "Если открыть банк при разговоре с незнакомцем", locked = !graph.entitlements.has(Feature.IN_CALL_GUARD),
                ) { on ->
                    gate(Feature.IN_CALL_GUARD) { update { it.copy(inCallBankGuard = on) } }
                }
                IconToggleRow(
                    "Защита кодов из СМС", Icons.Rounded.Password, Gradients.Danger, checked = policy.otpGuard,
                    subtitle = "Предупреждать, если код пришёл во время звонка", locked = !graph.entitlements.has(Feature.OTP_GUARD),
                ) { on ->
                    gate(Feature.OTP_GUARD) { update { it.copy(otpGuard = on) } }
                }
                IconToggleRow(
                    "Защита от удалённого доступа", Icons.AutoMirrored.Rounded.ScreenShare, Gradients.Danger, checked = policy.remoteAccessGuard,
                    subtitle = "Тревога при запуске AnyDesk, TeamViewer и т.п.", locked = !graph.entitlements.has(Feature.REMOTE_ACCESS_GUARD),
                ) { on ->
                    gate(Feature.REMOTE_ACCESS_GUARD) { update { it.copy(remoteAccessGuard = on) } }
                }
                IconToggleRow(
                    "Проверка сообщений на мошенничество", Icons.AutoMirrored.Rounded.Chat, Gradients.Coral, checked = policy.messageScan,
                    locked = !graph.entitlements.has(Feature.SCAM_MESSAGE_SCAN),
                ) { on ->
                    gate(Feature.SCAM_MESSAGE_SCAN) { update { it.copy(messageScan = on) } }
                }
                IconToggleRow(
                    "Тревога при установке приложений не из магазина", Icons.Rounded.InstallMobile, Gradients.Violet, checked = policy.sideloadGuard,
                    locked = !graph.entitlements.has(Feature.SIDELOAD_GUARD),
                ) { on ->
                    gate(Feature.SIDELOAD_GUARD) { update { it.copy(sideloadGuard = on) } }
                }
                IconToggleRow(
                    "Сообщать о крупных списаниях", Icons.Rounded.Payments, Gradients.Sunrise, checked = policy.bigDebitAlert,
                    subtitle = "По уведомлениям банка, только сумма. Включайте вместе с близким", locked = !graph.entitlements.has(Feature.BIG_DEBIT_ALERT),
                ) { on ->
                    gate(Feature.BIG_DEBIT_ALERT) { update { it.copy(bigDebitAlert = on) } }
                }
                if (policy.bigDebitAlert) {
                    DebitThreshold(policy.bigDebitThresholdRub) { value -> update { it.copy(bigDebitThresholdRub = value) } }
                }
            }

            GroupCard(title = "Забота и помощь") {
                TileRow(
                    "Медицинская карта", subtitle = if (policy.medical.isEmpty) "Группа крови, аллергии, лекарства — для скорой" else "Заполнена · появится рядом с SOS",
                    icon = Icons.Rounded.MedicalServices, gradient = Gradients.Danger,
                ) { medical = true }
                IconToggleRow(
                    "Ежедневная отметка «Я в порядке»", Icons.Rounded.Favorite, Gradients.Coral, checked = policy.checkIn.enabled,
                    locked = !graph.entitlements.has(Feature.CHECK_IN),
                ) { on ->
                    gate(Feature.CHECK_IN) { update { it.copy(checkIn = it.checkIn.copy(enabled = on)) } }
                }
                IconToggleRow(
                    "Тревога при долгом бездействии", Icons.Rounded.HourglassEmpty, Gradients.Sunrise, checked = policy.inactivityHours != null,
                    subtitle = "Если телефоном не пользуются много часов", locked = !graph.entitlements.has(Feature.INACTIVITY_ALERT),
                ) { on ->
                    gate(Feature.INACTIVITY_ALERT) { update { it.copy(inactivityHours = if (on) 12 else null) } }
                }
                IconToggleRow(
                    "Определение падения", Icons.Rounded.PersonalInjury, Gradients.Sunrise, checked = policy.fallDetection,
                    subtitle = "Сенсор телефона; тревога с возможностью отмены", locked = !graph.entitlements.has(Feature.FALL_DETECTION),
                ) { on ->
                    gate(Feature.FALL_DETECTION) { update { it.copy(fallDetection = on) } }
                }
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    IconTile(FeatureIcons.Pills.icon, FeatureIcons.Pills.gradient, size = 42.dp)
                    if (policy.medications.isEmpty()) {
                        Text(
                            "Напоминания о лекарствах можно добавить на телефоне родителя в его приложении.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f),
                        )
                    } else {
                        Text("Лекарств настроено: ${policy.medications.size}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    }
                }
            }

            GroupCard(title = "Удобство") {
                IconToggleRow(
                    "Крупные кнопки (простой режим)", Icons.Rounded.TextIncrease, Gradients.Sky, checked = policy.simpleMode.enabled,
                    subtitle = "Большой шрифт и простой экран с главными функциями", locked = !graph.entitlements.has(Feature.REMOTE_SETUP),
                ) { on ->
                    gate(Feature.REMOTE_SETUP) { update { it.copy(simpleMode = it.simpleMode.copy(enabled = on)) } }
                }
                IconToggleRow(
                    "Безопасный интернет", FeatureIcons.Web.icon, FeatureIcons.Web.gradient, checked = policy.webFilter.enabled,
                    subtitle = "Блокировать мошеннические и вредоносные сайты", locked = !graph.entitlements.has(Feature.LINK_SCAN),
                ) { on ->
                    gate(Feature.LINK_SCAN) { update { it.copy(webFilter = it.webFilter.copy(enabled = on)) } }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

private val debitThresholds = listOf(5_000, 10_000, 30_000, 50_000)

/** Amount chips under the large-debit switch. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DebitThreshold(current: Int, onPick: (Int) -> Unit) {
    FlowRow(
        Modifier.fillMaxWidth().padding(start = 58.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        debitThresholds.forEach { value ->
            val selected = value == current
            Box(
                Modifier.clip(CircleShape)
                    .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(role = Role.RadioButton) { onPick(value) }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
            ) {
                Text(
                    "от ${formatRub(value.toLong())}", style = MaterialTheme.typography.labelMedium,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
