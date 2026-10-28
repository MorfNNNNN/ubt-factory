package ru.ubt.familyshield.ui.child

import ru.ubt.familyshield.shared.model.withTrust
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AllInclusive
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.GppMaybe
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreTime
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.ubt.familyshield.AppGraph
import ru.ubt.familyshield.protect.ui.SosActivity
import ru.ubt.familyshield.shared.model.MemberRole
import ru.ubt.familyshield.shared.model.ScheduleRule
import ru.ubt.familyshield.shared.policy.Clock
import ru.ubt.familyshield.shared.policy.ScreenTimeEngine
import ru.ubt.familyshield.shared.policy.UsageSnapshot
import ru.ubt.familyshield.shared.policy.formatMinuteOfDay
import ru.ubt.familyshield.shared.policy.formatMinutes
import ru.ubt.familyshield.ui.FamilyUiState
import ru.ubt.familyshield.ui.ShieldViewModel
import ru.ubt.familyshield.ui.components.Art3D
import ru.ubt.familyshield.ui.components.ArtTile
import ru.ubt.familyshield.ui.components.AvatarImage
import ru.ubt.familyshield.ui.components.GlyphLine
import ru.ubt.familyshield.ui.components.HeroCard
import ru.ubt.familyshield.ui.components.IconTile
import ru.ubt.familyshield.ui.components.LabelChip
import ru.ubt.familyshield.ui.components.NoticeCard
import ru.ubt.familyshield.ui.components.ProgressRing
import ru.ubt.familyshield.ui.components.SoftCard
import ru.ubt.familyshield.ui.components.TileRow
import ru.ubt.familyshield.ui.guardian.ChangeLogScreen
import ru.ubt.familyshield.ui.components.SosPulse
import ru.ubt.familyshield.ui.safety.SafetySchoolScreen
import ru.ubt.familyshield.ui.setup.PermissionWizard
import ru.ubt.familyshield.ui.calendar.MyDayCard
import ru.ubt.familyshield.ui.tasks.ChildTasksCard
import ru.ubt.familyshield.ui.trust.ChildTrustScreen
import ru.ubt.familyshield.ui.trust.TrustCard
import ru.ubt.familyshield.ui.tasks.ChildTasksScreen
import ru.ubt.familyshield.ui.theme.Brand
import ru.ubt.familyshield.ui.theme.Gradients

private enum class ChildPage { HOME, TASKS, SCHOOL, CHANGES, TRUST }

/** The child's own screen: honest, friendly, shows their time left and lets them reach parents and SOS. */
@Composable
fun ChildHome(graph: AppGraph, vm: ShieldViewModel, state: FamilyUiState) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var showPermissions by remember { mutableStateOf(false) }
    var page by remember { mutableStateOf(ChildPage.HOME) }
    if (showPermissions) { PermissionWizard(graph, onboarding = false, onBack = { showPermissions = false }) { showPermissions = false }; return }
    when (page) {
        ChildPage.TASKS -> { ChildTasksScreen(graph) { page = ChildPage.HOME }; return }
        ChildPage.SCHOOL -> { SafetySchoolScreen(graph) { page = ChildPage.HOME }; return }
        ChildPage.CHANGES -> { ChangeLogScreen(graph, state.settings.deviceId.orEmpty(), "Что меняли родители", state.members) { page = ChildPage.HOME }; return }
        ChildPage.TRUST -> { ChildTrustScreen(graph, state, onBack = { page = ChildPage.HOME }, onTasks = { page = ChildPage.TASKS }); return }
        ChildPage.HOME -> Unit
    }

    val policy = state.settings.childPolicy.withTrust()
    val snapshot = remember(state.self?.todayMinutes) { UsageSnapshot(state.self?.todayMinutes ?: 0, emptyMap(), emptyMap()) }
    val clock = Clock.of(System.currentTimeMillis())
    val limit = ScreenTimeEngine.dailyLimit(policy, clock)
    val remaining = ScreenTimeEngine.remainingToday(policy, snapshot, clock)
    val health = remember { graph.protection.health.snapshot(MemberRole.CHILD, policy, state.settings.elderPolicy) }
    var askedForTime by remember { mutableStateOf(false) }
    var saidHello by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 14.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            AvatarImage(state.settings.avatar, MemberRole.CHILD.name, size = 60.dp, ring = Color.White)
            Column(Modifier.weight(1f)) {
                Text("Привет, ${state.settings.displayName.ifBlank { "друг" }}!", style = MaterialTheme.typography.headlineMedium)
                Text("Телефон под защитой семьи", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (health.criticalMissing.isNotEmpty()) {
            NoticeCard("Нужно завершить настройку, чтобы всё работало.", Icons.Rounded.Settings, Brand.WarningLight, Brand.Warning, textColor = Brand.Ink) { showPermissions = true }
        }

        TimeHero(
            limit = limit, remaining = remaining ?: 0, used = snapshot.totalMinutes,
            next = ScreenTimeEngine.nextScheduleStart(policy, clock), bonus = ScreenTimeEngine.bonusMinutes(policy, clock),
        )

        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ActionCard(
                Icons.Rounded.MoreTime, Gradients.Sky, "Попросить ещё времени",
                if (askedForTime) "Запрос отправлен — ждём ответа" else "+30 минут, если родители разрешат",
                done = askedForTime, modifier = Modifier.weight(1f).fillMaxHeight(),
            ) {
                scope.launch {
                    graph.sync.send(
                        ru.ubt.familyshield.shared.protocol.FamilyPayload.Request(
                            ru.ubt.familyshield.shared.model.MemberRequest(java.util.UUID.randomUUID().toString(), ru.ubt.familyshield.shared.model.RequestKind.MORE_TIME, System.currentTimeMillis(), minutes = 30),
                        ),
                        ru.ubt.familyshield.shared.protocol.Audience.GUARDIANS, emptyList(), true, null,
                    )
                }
                askedForTime = true
            }
            ActionCard(
                Icons.AutoMirrored.Rounded.Chat, Gradients.Emerald, "Написать родителям",
                if (saidHello) "Отправили «Привет!» в чат семьи" else "Отправим «Привет!» в чат семьи",
                done = saidHello, modifier = Modifier.weight(1f).fillMaxHeight(),
            ) {
                scope.launch { graph.guardian.note("Привет!", null) }
                saidHello = true
            }
        }

        MyDayCard(graph)

        ChildTasksCard(graph) { page = ChildPage.TASKS }

        TrustCard(graph, state) { page = ChildPage.TRUST }

        SosCard { context.startActivity(Intent(context, SosActivity::class.java)) }

        SoftCard(onClick = { page = ChildPage.SCHOOL }, padding = 16.dp) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                ArtTile(Art3D.Link, size = 58.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Как не попасться на обман", style = MaterialTheme.typography.titleMedium)
                    Text("Короткие уроки: ссылки, фейки, покупки онлайн", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        SoftCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconTile(Icons.Rounded.Visibility, Gradients.Sky, size = 40.dp)
                Text("Что видят родители", style = MaterialTheme.typography.titleMedium)
            }
            GlyphLine("Сколько времени ты в приложениях", Icons.Rounded.HourglassBottom, Gradients.Sky, tile = 30.dp, style = MaterialTheme.typography.bodyMedium)
            GlyphLine("Где находится телефон", Icons.Rounded.LocationOn, Gradients.Ocean, tile = 30.dp, style = MaterialTheme.typography.bodyMedium)
            GlyphLine("Предупреждения, если кто-то угрожает или обманывает", Icons.Rounded.GppMaybe, Gradients.Sunrise, tile = 30.dp, style = MaterialTheme.typography.bodyMedium)
            GlyphLine(
                "Твою личную переписку родители не читают.", Icons.Rounded.Lock, Gradients.Slate, tile = 30.dp,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            )
            TileRow("Что меняли родители", subtitle = "Все изменения правил видны тебе", icon = Icons.Rounded.History, gradient = Gradients.Slate) { page = ChildPage.CHANGES }
        }
    }
}

/** Sky hero with the time left today as a ring, the next schedule and today's bonus. */
@Composable
private fun TimeHero(limit: Int?, remaining: Int, used: Int, next: Pair<ScheduleRule, Int>?, bonus: Int) {
    val (title, subtitle) = when {
        limit == null -> "Сегодня без лимита" to "Родители не ограничили время на сегодня"
        remaining == 0 -> "Время на сегодня вышло" to "Использовано ${formatMinutes(used)} из ${formatMinutes(limit)}. Можно попросить ещё."
        else -> "Осталось сегодня" to "Использовано ${formatMinutes(used)} из ${formatMinutes(limit)}"
    }
    HeroCard(
        title = title,
        subtitle = subtitle,
        gradient = Gradients.Sky,
        art = {
            if (limit == null) {
                ProgressRing(1f, 118.dp, stroke = 11.dp) { Icon(Icons.Rounded.AllInclusive, contentDescription = null, tint = Color.White, modifier = Modifier.size(52.dp)) }
            } else {
                ProgressRing(remaining.toFloat() / limit.coerceAtLeast(1), 118.dp, stroke = 11.dp) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val big = if (remaining >= 60) "${remaining / 60} ч" else "$remaining"
                        val small = when {
                            remaining < 60 -> "мин"
                            remaining % 60 > 0 -> "${remaining % 60} мин"
                            else -> "осталось"
                        }
                        Text(big, style = MaterialTheme.typography.headlineLarge, color = Color.White, maxLines = 1)
                        Text(small, style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.88f), maxLines = 1)
                    }
                }
            }
        },
        footer = if (next == null && bonus == 0) null else {
            {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    next?.let { (rule, start) ->
                        LabelChip("«${rule.name}» в ${formatMinuteOfDay(start)}", Color.White.copy(alpha = 0.2f), Color.White, icon = Icons.Rounded.Schedule)
                    }
                    if (bonus > 0) LabelChip("+${formatMinutes(bonus)} в подарок", Color.White.copy(alpha = 0.2f), Color.White, icon = Icons.Rounded.Celebration)
                }
            }
        },
    )
}

@Composable
private fun ActionCard(icon: ImageVector, gradient: Brush, title: String, subtitle: String, done: Boolean, modifier: Modifier, onClick: () -> Unit) {
    SoftCard(modifier, onClick = onClick, padding = 16.dp) {
        IconTile(if (done) Icons.Rounded.TaskAlt else icon, if (done) Gradients.Emerald else gradient, size = 46.dp)
        Spacer(Modifier.weight(1f))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle, style = MaterialTheme.typography.bodySmall,
                color = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SosCard(onClick: () -> Unit) {
    val shape = MaterialTheme.shapes.large
    Row(
        Modifier.fillMaxWidth()
            .shadow(14.dp, shape, ambientColor = Brand.Danger.copy(alpha = 0.45f), spotColor = Brand.Danger.copy(alpha = 0.45f))
            .clip(shape).background(Gradients.Danger)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(104.dp), contentAlignment = Alignment.Center) { SosPulse(124.dp) }
        Column(Modifier.weight(1f).padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Мне нужна помощь", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = Color.White)
            Text("Родители сразу узнают, где ты", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.92f))
        }
    }
}
