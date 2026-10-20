package ru.ubt.familyshield.ui.guardian

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import ru.ubt.familyshield.AppGraph
import ru.ubt.familyshield.core.db.MemberEntity
import ru.ubt.familyshield.core.family.memberRole
import ru.ubt.familyshield.shared.model.AlertSeverity
import ru.ubt.familyshield.shared.model.Command
import ru.ubt.familyshield.shared.model.DeviceStatus
import ru.ubt.familyshield.shared.model.MemberRole
import ru.ubt.familyshield.shared.model.ProtectionComponent
import ru.ubt.familyshield.shared.policy.formatMinutes
import ru.ubt.familyshield.ui.FamilyUiState
import ru.ubt.familyshield.ui.ShieldViewModel
import ru.ubt.familyshield.ui.components.Avatar
import ru.ubt.familyshield.ui.components.Banner
import ru.ubt.familyshield.ui.components.Gap
import ru.ubt.familyshield.ui.components.PrimaryButton
import ru.ubt.familyshield.ui.components.ScreenScaffold
import ru.ubt.familyshield.ui.components.SecondaryButton
import ru.ubt.familyshield.ui.components.SectionCard
import ru.ubt.familyshield.ui.components.relativeTime
import ru.ubt.familyshield.ui.location.LocationMapScreen
import ru.ubt.familyshield.ui.theme.Brand
import java.util.UUID

private enum class Detail { OVERVIEW, CONTROLS, LOCATION, APPS, SECURITY }

@Composable
fun MemberDetailScreen(graph: AppGraph, vm: ShieldViewModel, state: FamilyUiState, member: MemberEntity, onBack: () -> Unit) {
    var screen by remember { mutableStateOf(Detail.OVERVIEW) }
    when (screen) {
        Detail.OVERVIEW -> Overview(graph, vm, state, member, onBack) { screen = it }
        Detail.CONTROLS -> if (member.memberRole == MemberRole.CHILD) {
            ChildControls(graph, member) { screen = Detail.OVERVIEW }
        } else {
            ElderControls(graph, member) { screen = Detail.OVERVIEW }
        }
        Detail.LOCATION -> LocationMapScreen(graph, member) { screen = Detail.OVERVIEW }
        Detail.APPS -> AppsScreen(graph, member) { screen = Detail.OVERVIEW }
        Detail.SECURITY -> SecurityScreen(graph, member) { screen = Detail.OVERVIEW }
    }
}

@Composable
private fun Overview(graph: AppGraph, vm: ShieldViewModel, state: FamilyUiState, member: MemberEntity, onBack: () -> Unit, open: (Detail) -> Unit) {
    val scope = rememberCoroutineScope()
    val isChild = member.memberRole == MemberRole.CHILD
    var confirmRemove by remember { mutableStateOf(false) }
    val status = remember(member.statusJson) {
        member.statusJson?.let { runCatching { ru.ubt.familyshield.shared.ShieldJson.decodeFromString(DeviceStatus.serializer(), it) }.getOrNull() }
    }

    fun cmd(c: Command) = scope.launch { graph.guardian.command(member.deviceId, c) }

    ScreenScaffold(title = member.displayName, onBack = onBack) { padding ->
        Column(Modifier.padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Avatar(member.avatar, 64.dp)
                Column {
                    Text(member.displayName, style = MaterialTheme.typography.headlineSmall)
                    Text("${roleLabel(member.memberRole)} · ${relativeTime(member.lastSeenAt)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            status?.let { HealthBanner(it) }

            // Quick actions
            SectionCard(title = "Быстрые действия") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecondaryButton(if (isChild) "Пауза" else "Найти", modifier = Modifier.weight(1f)) {
                        if (isChild) scope.launch { graph.guardian.pause(member.deviceId, null) } else cmd(Command.Ring(UUID.randomUUID().toString(), 30))
                    }
                    SecondaryButton("Где сейчас", modifier = Modifier.weight(1f)) {
                        cmd(Command.RequestLocation(UUID.randomUUID().toString()))
                        open(Detail.LOCATION)
                    }
                }
                if (isChild) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SecondaryButton("+15 мин", modifier = Modifier.weight(1f)) { scope.launch { graph.guardian.bonus(member.deviceId, 15, "Бонус") } }
                        SecondaryButton("Снять паузу", modifier = Modifier.weight(1f)) { scope.launch { graph.guardian.resume(member.deviceId) } }
                    }
                }
            }

            if (isChild) {
                member.todayMinutes?.let {
                    SectionCard { Text("Сегодня в телефоне: ${formatMinutes(it)}", style = MaterialTheme.typography.titleMedium) }
                }
            }

            SectionCard {
                NavRowSimple("⚙️", if (isChild) "Правила и ограничения" else "Защита и помощь") { open(Detail.CONTROLS) }
                NavRowSimple("📍", "Местоположение и геозоны") { open(Detail.LOCATION) }
                if (isChild) NavRowSimple("📱", "Приложения и экранное время") { open(Detail.APPS) }
                NavRowSimple("🛡️", "Безопасность телефона") { open(Detail.SECURITY) }
            }

            TextButton(onClick = { confirmRemove = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Убрать из семьи", color = Brand.Danger)
            }
            Gap(24.dp)
        }
    }

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("Убрать «${member.displayName}»?") },
            text = { Text("Телефон перестанет быть под защитой, а его данные удалятся из приложения. Ключ семьи будет обновлён.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemove = false
                    scope.launch { graph.family.removeMember(member.deviceId) }
                    onBack()
                }) { Text("Убрать", color = Brand.Danger) }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun HealthBanner(status: DeviceStatus) {
    val criticalMissing = status.missingComponents.filter { it.critical }
    when {
        criticalMissing.isNotEmpty() -> Banner(
            "Защита работает не полностью: не включено «${criticalMissing.first().titleRu}». Попросите близкого открыть приложение и завершить настройку.",
            Brand.WarningLight, textColor = Brand.Warning, emoji = "⚠️",
        )
        status.batteryPercent in 1..15 && !status.charging -> Banner("Низкий заряд: ${status.batteryPercent}%", Brand.WarningLight, textColor = Brand.Warning, emoji = "🔋")
        else -> Banner("Телефон под защитой · ${status.batteryPercent}%${if (status.charging) " 🔌" else ""} · ${status.networkType}", Brand.GreenLight, textColor = Brand.GreenDark, emoji = "✅")
    }
}

@Composable
fun NavRowSimple(emoji: String, title: String, onClick: () -> Unit) {
    ru.ubt.familyshield.ui.components.NavRow(title = title, emoji = emoji, onClick = onClick)
}
