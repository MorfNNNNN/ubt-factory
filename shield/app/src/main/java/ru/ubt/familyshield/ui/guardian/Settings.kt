package ru.ubt.familyshield.ui.guardian

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.launch
import ru.ubt.familyshield.AppGraph
import ru.ubt.familyshield.BuildConfig
import ru.ubt.familyshield.shared.billing.Tier
import ru.ubt.familyshield.ui.FamilyUiState
import ru.ubt.familyshield.ui.ShieldViewModel
import ru.ubt.familyshield.ui.components.Gap
import ru.ubt.familyshield.ui.components.NavRow
import ru.ubt.familyshield.ui.components.ScreenScaffold
import ru.ubt.familyshield.ui.components.SectionCard
import ru.ubt.familyshield.ui.legal.LegalScreen
import ru.ubt.familyshield.ui.paywall.PaywallScreen
import ru.ubt.familyshield.ui.paywall.UpsellReason
import ru.ubt.familyshield.ui.theme.Brand
import ru.ubt.familyshield.ui.tools.CheckerScreen

private enum class SettingsScreen { MAIN, CHECKER, PRIVACY, TERMS, HELP }

@Composable
fun GuardianSettings(graph: AppGraph, vm: ShieldViewModel, state: FamilyUiState) {
    var screen by remember { mutableStateOf(SettingsScreen.MAIN) }
    var paywall by remember { mutableStateOf(false) }
    when (screen) {
        SettingsScreen.CHECKER -> { CheckerScreen(graph) { screen = SettingsScreen.MAIN }; return }
        SettingsScreen.PRIVACY -> { LegalScreen.Privacy { screen = SettingsScreen.MAIN }; return }
        SettingsScreen.TERMS -> { LegalScreen.Terms { screen = SettingsScreen.MAIN }; return }
        SettingsScreen.HELP -> { LegalScreen.Help { screen = SettingsScreen.MAIN }; return }
        SettingsScreen.MAIN -> Unit
    }
    if (paywall) { PaywallScreen(graph, UpsellReason.GENERAL) { paywall = false }; return }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var leaveDialog by remember { mutableStateOf(false) }
    val tier = graph.entitlements.tier

    ScreenScaffold(title = "Ещё") { padding ->
        Column(Modifier.padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionCard(
                title = when (tier) { Tier.FREE -> "Базовый тариф"; Tier.PREMIUM -> "Премиум активен"; Tier.FAMILY_MAX -> "Семья Макс активна" },
                subtitle = if (tier == Tier.FREE) "Откройте полную защиту для всей семьи" else "Спасибо, что поддерживаете проект!",
                container = if (tier == Tier.FREE) Brand.GreenLight else MaterialTheme.colorScheme.surface,
            ) {
                if (tier == Tier.FREE) ru.ubt.familyshield.ui.components.PrimaryButton("Открыть Премиум", color = Brand.Green) { paywall = true }
                else ru.ubt.familyshield.ui.components.SecondaryButton("Управление подпиской") {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.rustore.ru/catalog/app/ru.ubt.familyshield"))) }
                }
            }

            SectionCard(title = "Инструменты") {
                NavRow("Проверить номер, ссылку, сообщение", emoji = "🔍") { screen = SettingsScreen.CHECKER }
            }

            SectionCard(title = "Профиль") {
                NavRow("Имя и телефон", subtitle = state.settings.displayName.ifBlank { "Не указано" }, emoji = "👤") { }
            }

            SectionCard(title = "О приложении и помощь") {
                NavRow("Как это работает и частые вопросы", emoji = "❓") { screen = SettingsScreen.HELP }
                NavRow("Политика конфиденциальности", emoji = "🔒") { screen = SettingsScreen.PRIVACY }
                NavRow("Условия использования", emoji = "📄") { screen = SettingsScreen.TERMS }
                NavRow("Материалы о безопасности", emoji = "📚") {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://fincult.info"))) }
                }
            }

            if (BuildConfig.DEBUG) {
                SectionCard(title = "Отладка") {
                    ru.ubt.familyshield.ui.components.ToggleRow("Включить Премиум (debug)", checked = state.settings.devPremium) { on -> scope.launch { graph.settings.setDevPremium(on) } }
                }
            }

            TextButton(onClick = { leaveDialog = true }, modifier = Modifier.align(androidx.compose.ui.Alignment.CenterHorizontally)) {
                Text("Выйти из семьи и удалить данные", color = Brand.Danger)
            }
            Text("Версия ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            Gap(24.dp)
        }
    }

    if (leaveDialog) {
        AlertDialog(
            onDismissRequest = { leaveDialog = false },
            title = { Text("Выйти из семьи?") },
            text = { Text("Этот телефон перестанет управлять семьёй, а локальные данные будут удалены. Защищаемые телефоны останутся на связи с другими взрослыми.") },
            confirmButton = { TextButton(onClick = { leaveDialog = false; scope.launch { graph.family.leaveFamily() } }) { Text("Выйти", color = Brand.Danger) } },
            dismissButton = { TextButton(onClick = { leaveDialog = false }) { Text("Отмена") } },
        )
    }
}
