package ru.ubt.familyshield.ui.guardian

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.GppGood
import androidx.compose.material.icons.rounded.GppMaybe
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Work
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.ubt.familyshield.AppGraph
import ru.ubt.familyshield.R
import ru.ubt.familyshield.core.db.MemberEntity
import ru.ubt.familyshield.core.family.childPolicy
import ru.ubt.familyshield.shared.billing.Feature
import ru.ubt.familyshield.shared.model.AlertSeverity
import ru.ubt.familyshield.shared.model.AppCategory
import ru.ubt.familyshield.shared.model.AppInventory
import ru.ubt.familyshield.shared.model.AppMode
import ru.ubt.familyshield.shared.model.Command
import ru.ubt.familyshield.shared.model.InstalledApp
import ru.ubt.familyshield.shared.model.SecurityReport
import ru.ubt.familyshield.shared.policy.formatMinutes
import ru.ubt.familyshield.ui.components.Art3D
import ru.ubt.familyshield.ui.components.HeroCard
import ru.ubt.familyshield.ui.components.IconTile
import ru.ubt.familyshield.ui.components.Illustration
import ru.ubt.familyshield.ui.components.LottieView
import ru.ubt.familyshield.ui.components.SectionHeader
import ru.ubt.familyshield.ui.components.SoftCard
import ru.ubt.familyshield.ui.components.Visual
import ru.ubt.familyshield.ui.components.gradient
import ru.ubt.familyshield.ui.components.relativeTime
import ru.ubt.familyshield.ui.paywall.PaywallScreen
import ru.ubt.familyshield.ui.paywall.UpsellReason
import ru.ubt.familyshield.ui.theme.Brand
import ru.ubt.familyshield.ui.theme.Gradients
import java.util.UUID

@Composable
fun AppsScreen(graph: AppGraph, member: MemberEntity, onBack: () -> Unit) {
    var paywall by remember { mutableStateOf(false) }
    val inventory = remember(member.inventoryJson) {
        member.inventoryJson?.let { runCatching { ru.ubt.familyshield.shared.ShieldJson.decodeFromString(AppInventory.serializer(), it) }.getOrNull() }
    }
    val policy = remember(member.policyJson) { member.childPolicy() }
    val canLimit = graph.entitlements.has(Feature.APP_LIMITS)

    if (paywall) { PaywallScreen(graph, UpsellReason.KIDS) { paywall = false }; return }

    DetailScaffold(title = "Приложения", onBack = onBack) { padding ->
        if (inventory == null) {
            Column(
                Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                member.todayMinutes?.let { TodayCard(it) }
                ArtEmptyState(
                    "Список обновляется",
                    "Как только телефон ребёнка выйдет на связь, здесь появятся приложения и экранное время.",
                ) {
                    Box(Modifier.size(200.dp), contentAlignment = Alignment.Center) {
                        Illustration(Art3D.Mobile, size = 160.dp)
                        Illustration(Art3D.Stopwatch, Modifier.align(Alignment.BottomEnd), size = 86.dp)
                    }
                }
            }
            return@DetailScaffold
        }
        val risky = inventory.apps.filter { it.riskFlags.isNotEmpty() }
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            member.todayMinutes?.let { item(key = "today") { TodayCard(it) } }
            if (risky.isNotEmpty()) {
                item(key = "risky") { SectionHeader("Требуют внимания") }
                items(risky, key = { "r" + it.packageName }) { app -> AppRow(graph, member, app, policy, canLimit) { paywall = true } }
            }
            item(key = "all") { SectionHeader("Все приложения (${inventory.apps.size})") }
            items(inventory.apps, key = { it.packageName }) { app -> AppRow(graph, member, app, policy, canLimit) { paywall = true } }
        }
    }
}

@Composable
private fun TodayCard(minutes: Int) {
    SoftCard(padding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            IconTile(Icons.Rounded.HourglassBottom, Gradients.Sky, size = 46.dp)
            Column {
                Text("Сегодня в телефоне", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(formatMinutes(minutes), style = MaterialTheme.typography.headlineSmall)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppRow(graph: AppGraph, member: MemberEntity, app: InstalledApp, policy: ru.ubt.familyshield.shared.model.ChildPolicy, canLimit: Boolean, onUpsell: () -> Unit) {
    val scope = rememberCoroutineScope()
    val blocked = policy.appRules[app.packageName]?.mode == AppMode.BLOCKED
    val visual = categoryVisual(app.category)
    fun setBlocked(on: Boolean) {
        if (!canLimit) onUpsell()
        else scope.launch {
            graph.guardian.command(member.deviceId, if (on) Command.DenyApp(UUID.randomUUID().toString(), app.packageName) else Command.ApproveApp(UUID.randomUUID().toString(), app.packageName))
        }
    }
    SoftCard(padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconTile(visual.icon, visual.gradient, size = 42.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(app.label, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (app.riskFlags.isEmpty()) {
                    Text(
                        if (blocked) "Заблокировано" else categoryLabel(app.category),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (blocked) Brand.Danger else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if ("sideloaded" in app.riskFlags) TintChip("не из магазина", Brand.Warning)
                        if ("remote_access" in app.riskFlags) TintChip("удалённый доступ", Brand.Danger)
                        if ("vpn_or_proxy" in app.riskFlags) TintChip("VPN", Brand.Warning)
                    }
                }
            }
            if (blocked) {
                PillButton("Разрешить", icon = if (canLimit) null else Icons.Rounded.Lock, fill = tintedFill(), contentColor = MaterialTheme.colorScheme.onSurface, elevated = false) { setBlocked(false) }
            } else {
                PillButton(
                    "Заблокировать", icon = if (canLimit) Icons.Rounded.Block else Icons.Rounded.Lock,
                    fill = SolidColor(Brand.DangerLight), contentColor = Brand.Danger, elevated = false,
                ) { setBlocked(true) }
            }
        }
    }
}

private fun categoryVisual(c: AppCategory): Visual = when (c) {
    AppCategory.SOCIAL -> Visual(Icons.Rounded.Groups, Gradients.Coral)
    AppCategory.MESSAGING -> Visual(Icons.AutoMirrored.Rounded.Chat, Gradients.Sky)
    AppCategory.GAMES -> Visual(Icons.Rounded.SportsEsports, Gradients.Violet)
    AppCategory.VIDEO -> Visual(Icons.Rounded.SmartDisplay, Gradients.Danger)
    AppCategory.BROWSER -> Visual(Icons.Rounded.Language, Gradients.Ocean)
    AppCategory.EDUCATION -> Visual(Icons.Rounded.School, Gradients.Emerald)
    AppCategory.MUSIC -> Visual(Icons.Rounded.MusicNote, Gradients.Violet)
    AppCategory.SHOPPING -> Visual(Icons.Rounded.ShoppingBag, Gradients.Sunrise)
    AppCategory.FINANCE -> Visual(Icons.Rounded.AccountBalance, Gradients.Emerald)
    AppCategory.PRODUCTIVITY -> Visual(Icons.Rounded.Work, Gradients.Slate)
    AppCategory.NAVIGATION -> Visual(Icons.Rounded.Map, Gradients.Ocean)
    AppCategory.PHOTO -> Visual(Icons.Rounded.PhotoCamera, Gradients.Night)
    AppCategory.OTHER -> Visual(Icons.Rounded.Apps, Gradients.Slate)
}

private fun categoryLabel(c: AppCategory): String = when (c) {
    AppCategory.SOCIAL -> "Соцсети"
    AppCategory.MESSAGING -> "Мессенджер"
    AppCategory.GAMES -> "Игра"
    AppCategory.VIDEO -> "Видео"
    AppCategory.BROWSER -> "Браузер"
    AppCategory.EDUCATION -> "Учёба"
    AppCategory.MUSIC -> "Музыка"
    AppCategory.SHOPPING -> "Покупки"
    AppCategory.FINANCE -> "Финансы"
    AppCategory.PRODUCTIVITY -> "Работа и учёба"
    AppCategory.NAVIGATION -> "Карты"
    AppCategory.PHOTO -> "Фото"
    AppCategory.OTHER -> "Приложение"
}

@Composable
fun SecurityScreen(graph: AppGraph, member: MemberEntity, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val report = remember(member.securityJson) {
        member.securityJson?.let { runCatching { ru.ubt.familyshield.shared.ShieldJson.decodeFromString(SecurityReport.serializer(), it) }.getOrNull() }
    }
    fun scan() = scope.launch { graph.guardian.command(member.deviceId, Command.RunSecurityScan(UUID.randomUUID().toString())) }

    DetailScaffold(title = "Безопасность телефона", onBack = onBack) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (report == null) {
                HeroCard(
                    title = "Проверка безопасности",
                    subtitle = "Найдём опасные приложения и уязвимые настройки на телефоне близкого.",
                    gradient = Gradients.Hero,
                    art = {
                        HeroHalo()
                        LottieView(R.raw.lottie_scanning, size = 128.dp)
                    },
                    footer = {
                        PillButton("Проверить", fill = SolidColor(Color.White), contentColor = Brand.GreenDark, icon = Icons.Rounded.Radar) { scan() }
                    },
                )
                ArtEmptyState(
                    "Нет данных",
                    "Запустите проверку — результат появится, когда телефон выйдет на связь.",
                    modifier = Modifier.padding(top = 8.dp),
                ) { IconTile(Icons.Rounded.GppMaybe, Gradients.Slate, size = 64.dp) }
                return@Column
            }

            ScoreCard(report) { scan() }

            if (report.findings.isEmpty()) {
                SoftCard(padding = 16.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        IconTile(Icons.Rounded.GppGood, Gradients.Emerald, size = 46.dp)
                        Text("Угроз не найдено. Телефон защищён.", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    }
                }
            } else {
                SectionHeader("Что нашли")
            }
            report.findings.forEach { f ->
                SoftCard(padding = 16.dp) {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        IconTile(Icons.Rounded.GppMaybe, f.severity.gradient(), size = 44.dp)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(f.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                if (f.severity >= AlertSeverity.WARNING) SeverityChip(f.severity)
                            }
                            Text(f.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** Score gauge with the verdict and when the phone was last checked. */
@Composable
private fun ScoreCard(report: SecurityReport, onScan: () -> Unit) {
    val color = if (report.score >= 70) Brand.Green else if (report.score >= 40) Brand.Warning else Brand.Danger
    val track = MaterialTheme.colorScheme.surfaceVariant
    SoftCard(padding = 18.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Box(Modifier.size(112.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val w = 11.dp.toPx()
                    val arc = Size(size.width - w, size.height - w)
                    drawArc(track, startAngle = 135f, sweepAngle = 270f, useCenter = false, topLeft = Offset(w / 2, w / 2), size = arc, style = Stroke(w, cap = StrokeCap.Round))
                    drawArc(color, startAngle = 135f, sweepAngle = 270f * report.score.coerceIn(0, 100) / 100f, useCenter = false, topLeft = Offset(w / 2, w / 2), size = arc, style = Stroke(w, cap = StrokeCap.Round))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${report.score}", style = MaterialTheme.typography.displaySmall, color = color)
                    Text("из 100", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Оценка защищённости", style = MaterialTheme.typography.titleMedium)
                Text(
                    when {
                        report.score >= 70 -> "Хорошая защита"
                        report.score >= 40 -> "Есть что улучшить"
                        else -> "Телефон в опасности"
                    },
                    style = MaterialTheme.typography.labelLarge, color = color,
                )
                Text("Проверено ${relativeTime(report.scannedAt)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        PillButton("Проверить сейчас", modifier = Modifier.fillMaxWidth(), icon = Icons.Rounded.Radar, onClick = onScan)
    }
}
