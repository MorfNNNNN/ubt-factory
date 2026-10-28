package ru.ubt.familyshield.ui.guardian

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.ManageSearch
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.ubt.familyshield.AppGraph
import ru.ubt.familyshield.BuildConfig
import ru.ubt.familyshield.R
import ru.ubt.familyshield.shared.billing.Tier
import ru.ubt.familyshield.shared.model.MemberRole
import ru.ubt.familyshield.ui.FamilyUiState
import ru.ubt.familyshield.ui.ShieldViewModel
import ru.ubt.familyshield.ui.components.AvatarImage
import ru.ubt.familyshield.ui.components.HeroCard
import ru.ubt.familyshield.ui.components.IconTile
import ru.ubt.familyshield.ui.components.Illustration
import ru.ubt.familyshield.ui.components.SoftCard
import ru.ubt.familyshield.ui.components.TileRow
import ru.ubt.familyshield.ui.legal.LegalScreen
import ru.ubt.familyshield.ui.paywall.PaywallScreen
import ru.ubt.familyshield.ui.paywall.UpsellReason
import ru.ubt.familyshield.ui.theme.Brand
import ru.ubt.familyshield.ui.theme.Gradients
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
    var profileDialog by remember { mutableStateOf(false) }
    val tier = graph.entitlements.tier

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        PageHeader("Ещё")

        // Profile
        SoftCard(onClick = { profileDialog = true }) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                AvatarImage(state.self?.avatar ?: state.settings.avatar, MemberRole.GUARDIAN.name, 60.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(state.settings.displayName.ifBlank { "Имя и телефон" }, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        state.settings.phone.ifBlank { if (state.settings.displayName.isBlank()) "Не указано" else "Телефон для SOS не указан" },
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(Icons.Rounded.Edit, contentDescription = "Изменить имя и телефон", tint = MaterialTheme.colorScheme.primary)
            }
        }

        // Plan
        if (tier == Tier.FREE) {
            HeroCard(
                title = "Базовый тариф",
                subtitle = "Откройте полную защиту для всей семьи",
                gradient = Gradients.Hero,
                onClick = { paywall = true },
                art = {
                    HeroHalo()
                    Illustration(R.drawable.ill_mark_shield, size = 104.dp)
                },
                footer = {
                    PillButton("Открыть Премиум", fill = Gradients.Gold, contentColor = Brand.Ink, icon = Icons.Rounded.WorkspacePremium) { paywall = true }
                },
            )
        } else {
            HeroCard(
                title = if (tier == Tier.PREMIUM) "Премиум активен" else "Семья Макс активна",
                subtitle = "Спасибо, что поддерживаете проект!",
                gradient = Gradients.Night,
                art = {
                    HeroHalo()
                    Illustration(R.drawable.ill_mark_shield, size = 104.dp)
                },
                footer = {
                    PillButton("Подписка", fill = SolidColor(Color.White), contentColor = Brand.Ink, elevated = false) {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.rustore.ru/catalog/app/ru.ubt.familyshield"))) }
                    }
                },
            )
        }

        GroupCard(title = "Инструменты") {
            TileRow(
                "Проверить номер, ссылку, сообщение", subtitle = "Подскажем, не мошенники ли это",
                icon = Icons.AutoMirrored.Rounded.ManageSearch, gradient = Gradients.Violet,
            ) { screen = SettingsScreen.CHECKER }
        }

        GroupCard(title = "О приложении и помощь") {
            TileRow("Как это работает и частые вопросы", icon = Icons.AutoMirrored.Rounded.HelpOutline, gradient = Gradients.Sky) { screen = SettingsScreen.HELP }
            TileRow("Политика конфиденциальности", icon = Icons.Rounded.PrivacyTip, gradient = Gradients.Emerald) { screen = SettingsScreen.PRIVACY }
            TileRow("Условия использования", icon = Icons.Rounded.Description, gradient = Gradients.Slate) { screen = SettingsScreen.TERMS }
            TileRow("Материалы о безопасности", subtitle = "Финансовая культура — сайт Банка России", icon = Icons.AutoMirrored.Rounded.MenuBook, gradient = Gradients.Sunrise) {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://fincult.info"))) }
            }
        }

        if (BuildConfig.DEBUG) {
            GroupCard(title = "Отладка") {
                IconToggleRow("Включить Премиум (debug)", Icons.Rounded.BugReport, Gradients.Slate, checked = state.settings.devPremium) { on ->
                    scope.launch { graph.settings.setDevPremium(on) }
                }
            }
        }

        DangerAction(
            "Выйти из семьи и удалить данные", Icons.AutoMirrored.Rounded.Logout, onClick = { leaveDialog = true },
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Text(
            "Версия ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
    }

    if (profileDialog) {
        var name by remember { mutableStateOf(state.settings.displayName) }
        var phone by remember { mutableStateOf(state.settings.phone) }
        AlertDialog(
            onDismissRequest = { profileDialog = false },
            icon = { AvatarImage(state.self?.avatar ?: state.settings.avatar, MemberRole.GUARDIAN.name, 64.dp) },
            title = { Text("Профиль") },
            shape = MaterialTheme.shapes.extraLarge,
            containerColor = MaterialTheme.colorScheme.surface,
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        name, { name = it.take(40) }, label = { Text("Имя") }, singleLine = true, shape = MaterialTheme.shapes.medium,
                        leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null) },
                    )
                    OutlinedTextField(
                        phone, { phone = it.take(20) }, label = { Text("Телефон для SOS") }, singleLine = true, shape = MaterialTheme.shapes.medium,
                        leadingIcon = { Icon(Icons.Rounded.Phone, contentDescription = null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    )
                    Text(
                        "Телефон видят только члены вашей семьи — для кнопок SOS и «Позвонить родным».",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    profileDialog = false
                    scope.launch {
                        graph.settings.setProfile(name, state.settings.avatar)
                        graph.settings.setPhone(phone)
                        graph.sync.send(ru.ubt.familyshield.shared.protocol.FamilyPayload.Profile(graph.family.myProfile()), ru.ubt.familyshield.shared.protocol.Audience.ALL, emptyList(), false, null)
                    }
                }) { Text("Сохранить") }
            },
            dismissButton = { TextButton(onClick = { profileDialog = false }) { Text("Отмена") } },
        )
    }

    if (leaveDialog) {
        AlertDialog(
            onDismissRequest = { leaveDialog = false },
            icon = { IconTile(Icons.AutoMirrored.Rounded.Logout, Gradients.Danger, size = 52.dp) },
            title = { Text("Выйти из семьи?", textAlign = TextAlign.Center) },
            text = { Text("Этот телефон перестанет управлять семьёй, а локальные данные будут удалены. Защищаемые телефоны останутся на связи с другими взрослыми.") },
            shape = MaterialTheme.shapes.extraLarge,
            containerColor = MaterialTheme.colorScheme.surface,
            confirmButton = { TextButton(onClick = { leaveDialog = false; scope.launch { graph.family.leaveFamily() } }) { Text("Выйти", color = Brand.Danger) } },
            dismissButton = { TextButton(onClick = { leaveDialog = false }) { Text("Отмена") } },
        )
    }
}
