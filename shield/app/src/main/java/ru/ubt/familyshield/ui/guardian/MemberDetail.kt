package ru.ubt.familyshield.ui.guardian

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Battery2Bar
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.GppGood
import androidx.compose.material.icons.rounded.GppMaybe
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MoreTime
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.PersonRemove
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.RingVolume
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Wifi
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.ubt.familyshield.AppGraph
import ru.ubt.familyshield.core.db.MemberEntity
import ru.ubt.familyshield.core.family.childPolicy
import ru.ubt.familyshield.core.family.memberRole
import ru.ubt.familyshield.shared.model.Command
import ru.ubt.familyshield.shared.model.DeviceStatus
import ru.ubt.familyshield.shared.model.MemberRole
import ru.ubt.familyshield.shared.model.SecurityReport
import ru.ubt.familyshield.shared.policy.Clock
import ru.ubt.familyshield.shared.policy.ScreenTimeEngine
import ru.ubt.familyshield.shared.policy.formatMinutes
import ru.ubt.familyshield.ui.FamilyUiState
import ru.ubt.familyshield.ui.ShieldViewModel
import ru.ubt.familyshield.ui.components.AvatarImage
import ru.ubt.familyshield.ui.components.HeroCard
import ru.ubt.familyshield.ui.components.IconTile
import ru.ubt.familyshield.ui.components.ProgressBar
import ru.ubt.familyshield.ui.components.SoftCard
import ru.ubt.familyshield.ui.components.TileRow
import ru.ubt.familyshield.ui.components.relativeTime
import ru.ubt.familyshield.ui.components.visual
import ru.ubt.familyshield.ui.location.LocationMapScreen
import ru.ubt.familyshield.ui.safety.MemberSafetyScreen
import ru.ubt.familyshield.ui.safety.safetySummary
import ru.ubt.familyshield.ui.tasks.GuardianTasksScreen
import ru.ubt.familyshield.ui.tasks.taskSummary
import ru.ubt.familyshield.ui.theme.Brand
import ru.ubt.familyshield.ui.theme.Gradients
import java.util.UUID

private enum class Detail { OVERVIEW, CONTROLS, LOCATION, APPS, SECURITY, TASKS, SAFETY }

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
        Detail.TASKS -> GuardianTasksScreen(graph, member) { screen = Detail.OVERVIEW }
        Detail.SAFETY -> MemberSafetyScreen(member) { screen = Detail.OVERVIEW }
    }
}

@Composable
private fun Overview(graph: AppGraph, vm: ShieldViewModel, state: FamilyUiState, member: MemberEntity, onBack: () -> Unit, open: (Detail) -> Unit) {
    val scope = rememberCoroutineScope()
    val isChild = member.memberRole == MemberRole.CHILD
    var confirmRemove by remember { mutableStateOf(false) }
    val status = remember(member.statusJson) { member.deviceStatus() }
    val score = remember(member.securityJson) {
        member.securityJson?.let { runCatching { ru.ubt.familyshield.shared.ShieldJson.decodeFromString(SecurityReport.serializer(), it) }.getOrNull() }?.score
    }

    fun cmd(c: Command) = scope.launch { graph.guardian.command(member.deviceId, c) }

    DetailScaffold(title = null, onBack = onBack) { padding ->
        Column(
            Modifier.padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            MemberHero(member, status)

            status?.let { HealthCard(it) }

            // Quick actions
            SoftCard(padding = 14.dp) {
                Row(Modifier.fillMaxWidth()) {
                    if (isChild) {
                        QuickAction("Пауза", Icons.Rounded.PauseCircle, Gradients.Sunrise) { scope.launch { graph.guardian.pause(member.deviceId, null) } }
                    } else {
                        QuickAction("Найти телефон", Icons.Rounded.RingVolume, Gradients.Coral) { cmd(Command.Ring(UUID.randomUUID().toString(), 30)) }
                    }
                    QuickAction("Где сейчас", Icons.Rounded.MyLocation, Gradients.Ocean) {
                        cmd(Command.RequestLocation(UUID.randomUUID().toString()))
                        open(Detail.LOCATION)
                    }
                    if (isChild) {
                        QuickAction("+15 мин", Icons.Rounded.MoreTime, Gradients.Sky) { scope.launch { graph.guardian.bonus(member.deviceId, 15, "Бонус") } }
                        QuickAction("Снять паузу", Icons.Rounded.PlayCircle, Gradients.Emerald) { scope.launch { graph.guardian.resume(member.deviceId) } }
                    }
                }
            }

            if (isChild) {
                member.todayMinutes?.let { ScreenTimeCard(member, it) }
            }

            SoftCard(padding = 12.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    TileRow(
                        if (isChild) "Правила и ограничения" else "Защита и помощь",
                        subtitle = if (isChild) "Лимиты, расписания, интернет" else "Звонки, банк, СМС, «Я в порядке»",
                        icon = Icons.Rounded.Tune, gradient = Gradients.Emerald,
                    ) { open(Detail.CONTROLS) }
                    TileRow(
                        "Местоположение и геозоны",
                        subtitle = member.lastAddress?.takeIf { it.isNotBlank() }?.let { "$it · ${relativeTime(member.lastLocationAt)}" } ?: "Узнать, где сейчас",
                        icon = Icons.Rounded.LocationOn, gradient = Gradients.Ocean,
                    ) { open(Detail.LOCATION) }
                    if (isChild) {
                        TileRow("Задания и награды", subtitle = taskSummary(graph, member.deviceId), icon = Icons.Rounded.EmojiEvents, gradient = Gradients.Sunrise) { open(Detail.TASKS) }
                        TileRow("Приложения и экранное время", subtitle = "Что установлено и что заблокировано", icon = Icons.Rounded.Apps, gradient = Gradients.Violet) { open(Detail.APPS) }
                    }
                    TileRow("Школа безопасности", subtitle = safetySummary(member), icon = Icons.Rounded.School, gradient = Gradients.Violet) { open(Detail.SAFETY) }
                    TileRow(
                        "Безопасность телефона",
                        subtitle = score?.let { "Оценка защищённости: $it/100" } ?: "Проверка угроз и настроек",
                        icon = Icons.Rounded.Security, gradient = Gradients.Sunrise,
                    ) { open(Detail.SECURITY) }
                }
            }

            DangerAction("Убрать из семьи", Icons.Rounded.PersonRemove, onClick = { confirmRemove = true }, modifier = Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(16.dp))
        }
    }

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            icon = { IconTile(Icons.Rounded.PersonRemove, Gradients.Danger, size = 52.dp) },
            title = { Text("Убрать «${member.displayName}»?", textAlign = TextAlign.Center) },
            text = { Text("Телефон перестанет быть под защитой, а его данные удалятся из приложения. Ключ семьи будет обновлён.") },
            shape = MaterialTheme.shapes.extraLarge,
            containerColor = MaterialTheme.colorScheme.surface,
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

/** Gradient header with a big portrait: who, how they are doing, battery and network. */
@Composable
private fun MemberHero(member: MemberEntity, status: DeviceStatus?) {
    val role = member.memberRole
    val presence = presenceOf(member.lastSeenAt)
    HeroCard(
        title = member.displayName,
        subtitle = "${roleLabel(role)} · ${presence.text.replaceFirstChar { it.lowercase() }}",
        gradient = role.visual().gradient,
        art = {
            HeroHalo()
            AvatarImage(member.avatar, member.role, 112.dp, ring = Color.White)
        },
        footer = {
            if (status != null) {
                val network = networkLabel(status.networkType)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (status.batteryPercent >= 0) HeroChip("${status.batteryPercent}%", batteryIcon(status.batteryPercent, status.charging))
                    HeroChip(network, if (network == "Wi‑Fi") Icons.Rounded.Wifi else Icons.Rounded.SignalCellularAlt)
                }
            }
        },
    )
}

/** Protection health: complete, needs setup, or the battery is about to run out. */
@Composable
private fun HealthCard(status: DeviceStatus) {
    val criticalMissing = status.missingComponents.filter { it.critical }
    val total = status.activeComponents.size + status.missingComponents.size
    when {
        criticalMissing.isNotEmpty() -> HealthRow(
            Icons.Rounded.GppMaybe, Gradients.Sunrise, "Защита работает не полностью",
            "Не включено «${criticalMissing.first().titleRu}». Попросите близкого открыть приложение и завершить настройку.",
        )
        status.batteryPercent in 1..15 && !status.charging -> HealthRow(
            Icons.Rounded.Battery2Bar, Gradients.Sunrise, "Низкий заряд: ${status.batteryPercent}%",
            "Напомните поставить телефон на зарядку — без неё защита не работает.",
        )
        else -> {
            val active = status.activeComponents.size
            HealthRow(
                Icons.Rounded.GppGood, Gradients.Emerald, "Телефон под защитой",
                if (total > 0) "${plural(active, "Работает", "Работают", "Работают")} $active из $total ${plural(total, "модуля", "модулей", "модулей")} защиты" else "Все модули защиты работают",
                fraction = if (total > 0) active / total.toFloat() else null,
            )
        }
    }
}

@Composable
private fun HealthRow(icon: ImageVector, gradient: Brush, title: String, text: String, fraction: Float? = null) {
    SoftCard(padding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            IconTile(icon, gradient, size = 46.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (fraction != null) ProgressBar(fraction, Brand.Green)
    }
}

/** Today's screen time against today's limit (bonuses included). */
@Composable
private fun ScreenTimeCard(member: MemberEntity, minutes: Int) {
    val limit = remember(member.policyJson) { ScreenTimeEngine.dailyLimit(member.childPolicy(), Clock.of(System.currentTimeMillis())) }
    SoftCard(padding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            IconTile(Icons.Rounded.HourglassBottom, Gradients.Sky, size = 46.dp)
            Column(Modifier.weight(1f)) {
                Text("Сегодня в телефоне", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(formatMinutes(minutes), style = MaterialTheme.typography.headlineSmall)
            }
            if (limit != null) {
                Column(horizontalAlignment = Alignment.End) {
                    Text("Лимит", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatMinutes(limit), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        if (limit != null && limit > 0) {
            val fraction = minutes / limit.toFloat()
            ProgressBar(fraction, if (fraction >= 0.9f) Brand.Warning else Brand.Blue)
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.QuickAction(label: String, icon: ImageVector, gradient: Brush, onClick: () -> Unit) {
    Column(
        Modifier.weight(1f).clip(MaterialTheme.shapes.medium).clickable(role = Role.Button, onClick = onClick).padding(vertical = 6.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IconTile(icon, gradient, size = 50.dp)
        Text(label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 2)
    }
}
