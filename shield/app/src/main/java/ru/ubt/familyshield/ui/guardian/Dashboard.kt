package ru.ubt.familyshield.ui.guardian

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MarkEmailUnread
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import ru.ubt.familyshield.AppGraph
import ru.ubt.familyshield.R
import ru.ubt.familyshield.core.db.AlertEntity
import ru.ubt.familyshield.core.db.MemberEntity
import ru.ubt.familyshield.core.family.memberRole
import ru.ubt.familyshield.shared.model.AlertSeverity
import ru.ubt.familyshield.shared.content.Campaign
import ru.ubt.familyshield.shared.content.CampaignAction
import ru.ubt.familyshield.shared.content.Campaigns
import ru.ubt.familyshield.shared.model.AlertType
import ru.ubt.familyshield.shared.model.MemberRole
import ru.ubt.familyshield.shared.policy.formatMinutes
import ru.ubt.familyshield.ui.FamilyUiState
import ru.ubt.familyshield.ui.ShieldViewModel
import ru.ubt.familyshield.ui.components.AvatarArt
import ru.ubt.familyshield.ui.components.AvatarImage
import ru.ubt.familyshield.ui.components.AvatarStack
import ru.ubt.familyshield.ui.components.FeatureIcons
import ru.ubt.familyshield.ui.components.HeroCard
import ru.ubt.familyshield.ui.components.IconTile
import ru.ubt.familyshield.ui.components.Illustration
import ru.ubt.familyshield.ui.components.LottieView
import ru.ubt.familyshield.ui.components.PrimaryButton
import ru.ubt.familyshield.ui.components.SectionHeader
import ru.ubt.familyshield.ui.components.SoftCard
import ru.ubt.familyshield.ui.components.StatTile
import ru.ubt.familyshield.ui.components.StatusDot
import ru.ubt.familyshield.ui.components.gradient
import ru.ubt.familyshield.ui.components.relativeTime
import ru.ubt.familyshield.ui.components.visual
import ru.ubt.familyshield.ui.invite.InviteScreen
import ru.ubt.familyshield.ui.safety.GosProtectionsScreen
import ru.ubt.familyshield.ui.safety.SafetySchoolScreen
import ru.ubt.familyshield.ui.paywall.PaywallScreen
import ru.ubt.familyshield.ui.paywall.UpsellReason
import ru.ubt.familyshield.ui.theme.Brand
import ru.ubt.familyshield.ui.theme.Gradients

private val URGENT = setOf("CRITICAL", "HIGH")
private const val WEEK_MILLIS = 7 * 86_400_000L

@Composable
fun GuardianDashboard(
    graph: AppGraph,
    vm: ShieldViewModel,
    state: FamilyUiState,
    onOpenMember: (String) -> Unit,
    onGoAlerts: () -> Unit,
    onOpenSettings: () -> Unit = {},
) {
    var showInvite by remember { mutableStateOf(false) }
    var paywall by remember { mutableStateOf<UpsellReason?>(null) }
    var school by remember { mutableStateOf<String?>(null) }
    var gos by remember { mutableStateOf(false) }

    paywall?.let { PaywallScreen(graph, it) { paywall = null }; return }
    if (showInvite) {
        InviteScreen(graph, state) { showInvite = false }
        return
    }
    school?.let { SafetySchoolScreen(graph, it.ifBlank { null }) { school = null }; return }
    if (gos) { GosProtectionsScreen(graph) { gos = false }; return }

    fun firstOf(role: MemberRole) = state.others.firstOrNull { it.memberRole == role }?.deviceId
    fun onCampaign(c: Campaign) = when (c.action) {
        CampaignAction.LESSON -> school = c.lessonId.orEmpty()
        CampaignAction.GOS -> gos = true
        CampaignAction.INVITE -> showInvite = true
        CampaignAction.PREMIUM -> paywall = UpsellReason.ELDER
        CampaignAction.CHILD_RULES -> firstOf(MemberRole.CHILD)?.let(onOpenMember) ?: run { showInvite = true }
    }
    fun onStep(step: SetupStep) = when (step) {
        SetupStep.ELDER, SetupStep.CHILD -> showInvite = true
        SetupStep.PHONE -> onOpenSettings()
        SetupStep.HOME_ZONE -> (firstOf(MemberRole.CHILD) ?: firstOf(MemberRole.ELDER))?.let(onOpenMember) ?: run { showInvite = true }
        SetupStep.LESSON -> school = ""
        SetupStep.CREDIT_BAN -> gos = true
    }

    val urgent = state.alerts.filter { !it.read && it.severity in URGENT }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        PageHeader(
            title = state.settings.familyName.ifBlank { "Моя семья" },
            overline = greeting(state.settings.displayName),
            trailing = { AvatarImage(state.self?.avatar ?: state.settings.avatar, MemberRole.GUARDIAN.name, 50.dp, ring = Color.White) },
        )

        when {
            urgent.isNotEmpty() -> UrgentHero(state, urgent, onGoAlerts)
            state.protectedMembers.isEmpty() -> InviteHero { showInvite = true }
            else -> CalmHero(state.protectedMembers)
        }

        if (state.protectedMembers.isNotEmpty()) StatsRow(state, onOpenMember, onGoAlerts)

        if (state.pendingRequests.isNotEmpty()) RequestsCard(state, onGoAlerts)

        Campaigns.active()?.let { CampaignCard(it, ::onCampaign) }

        val setup = setupProgress(state)
        if (setup.values.any { !it }) SetupChecklist(setup, ::onStep)

        if (state.others.isEmpty()) {
            RolePromo { showInvite = true }
        } else {
            SectionHeader("Под защитой")
            state.others.forEach { member ->
                MemberCard(state, member) { onOpenMember(member.deviceId) }
            }
            AddPhoneCard("Добавить ещё телефон", "Ребёнка, родителя или второго взрослого") { showInvite = true }
            WeekCard(state)
        }

        if (graph.entitlements.tier == ru.ubt.familyshield.shared.billing.Tier.FREE) {
            PremiumCard { paywall = UpsellReason.GENERAL }
        }
        Spacer(Modifier.height(10.dp))
    }
}

// ---- Hero ------------------------------------------------------------------------------------------

@Composable
private fun CalmHero(protectedMembers: List<MemberEntity>) {
    val n = protectedMembers.size
    HeroCard(
        title = "Вся семья под защитой",
        subtitle = "Срочных событий нет. Мы присматриваем за $n ${plural(n, "телефоном", "телефонами", "телефонами")}.",
        gradient = Gradients.Hero,
        art = {
            HeroHalo()
            LottieView(R.raw.lottie_shield_active, size = 128.dp)
        },
        footer = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val shown = protectedMembers.take(4)
                Box(Modifier.width(avatarStackWidth(shown.size, 30.dp))) {
                    AvatarStack(shown.map { it.avatar to it.role }, size = 30.dp)
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    shown.joinToString(", ") { it.displayName }, style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.9f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        },
    )
}

@Composable
private fun UrgentHero(state: FamilyUiState, urgent: List<AlertEntity>, onGoAlerts: () -> Unit) {
    val first = urgent.first()
    val member = state.members.firstOrNull { it.deviceId == first.deviceId }
    val more = urgent.size - 1
    HeroCard(
        title = "Нужно ваше внимание",
        subtitle = first.title,
        gradient = Gradients.Danger,
        onClick = onGoAlerts,
        art = {
            HeroHalo()
            LottieView(R.raw.lottie_alert_siren, size = 120.dp)
        },
        footer = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AvatarImage(member?.avatar, member?.role, 26.dp, ring = Color.White)
                Text(
                    "${member?.displayName ?: "Близкий"} · ${relativeTime(first.createdAt)}",
                    style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.92f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            if (more > 0) {
                Text(
                    "и ещё $more ${plural(more, "срочное событие", "срочных события", "срочных событий")}",
                    style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.8f),
                )
            }
            Spacer(Modifier.height(4.dp))
            PillButton("Открыть", fill = SolidColor(Color.White), contentColor = Brand.Danger, icon = Icons.AutoMirrored.Rounded.KeyboardArrowRight, onClick = onGoAlerts)
        },
    )
}

@Composable
private fun InviteHero(onAdd: () -> Unit) {
    HeroCard(
        title = "Добавьте близких",
        subtitle = "Подключите телефон ребёнка или пожилого родителя — им нужно всего отсканировать QR-код.",
        gradient = Gradients.Hero,
        art = {
            HeroHalo()
            LottieView(R.raw.lottie_devices_connect, size = 124.dp)
        },
        footer = {
            Spacer(Modifier.height(2.dp))
            PillButton("Добавить телефон", fill = SolidColor(Color.White), contentColor = Brand.GreenDark, icon = Icons.Rounded.Add, onClick = onAdd)
        },
    )
}

// ---- Stats -----------------------------------------------------------------------------------------

private class Stat(val value: String, val label: String, val visual: ru.ubt.familyshield.ui.components.Visual, val onClick: () -> Unit)

@Composable
private fun StatsRow(state: FamilyUiState, onOpenMember: (String) -> Unit, onGoAlerts: () -> Unit) {
    val since = System.currentTimeMillis() - WEEK_MILLIS
    val child = state.protectedMembers.firstOrNull { it.memberRole == MemberRole.CHILD }
    val elder = state.protectedMembers.firstOrNull { it.memberRole == MemberRole.ELDER }
    val stats = buildList {
        if (elder != null) {
            val blocked = state.alerts.count { it.type == AlertType.SCAM_CALL_BLOCKED.name && it.createdAt >= since }
            add(Stat(blocked.toString(), "Звонков мошенников отбито за неделю", FeatureIcons.Calls, onGoAlerts))
        }
        if (child != null) {
            add(Stat(child.todayMinutes?.let { formatMinutes(it) } ?: "—", "${child.displayName} сегодня в телефоне", FeatureIcons.ScreenTime) { onOpenMember(child.deviceId) })
        }
        if (elder != null && child == null) {
            add(Stat(elder.lastCheckInAt?.let { relativeTime(it) } ?: "—", "Последнее «Я в порядке»", AlertType.CHECK_IN_OK.visual()) { onOpenMember(elder.deviceId) })
        }
        if (child != null && elder == null) {
            val sites = state.alerts.count { it.type == AlertType.BLOCKED_SITE.name && it.createdAt >= since }
            add(Stat(sites.toString(), "Опасных сайтов закрыто за неделю", FeatureIcons.Web, onGoAlerts))
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        stats.forEach { s ->
            StatTile(s.value, s.label, s.visual.icon, s.visual.gradient, modifier = Modifier.weight(1f), onClick = s.onClick)
        }
    }
}

// ---- Requests --------------------------------------------------------------------------------------

@Composable
private fun RequestsCard(state: FamilyUiState, onGoAlerts: () -> Unit) {
    val n = state.pendingRequests.size
    SoftCard(onClick = onGoAlerts) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconTile(Icons.Rounded.MarkEmailUnread, Gradients.Sky, size = 44.dp)
            Column(Modifier.weight(1f)) {
                Text("Запросы ждут ответа", style = MaterialTheme.typography.titleMedium)
                Text("$n ${plural(n, "запрос", "запроса", "запросов")} от близких", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            CountBadge(n, color = Brand.Blue)
        }
        state.pendingRequests.take(3).forEach { req ->
            val member = state.members.firstOrNull { it.deviceId == req.deviceId }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AvatarImage(member?.avatar, member?.role ?: MemberRole.CHILD.name, 36.dp)
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(member?.displayName ?: "Ребёнок") }
                        append(" ")
                        append(requestSummary(req))
                    },
                    style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                Text(relativeTime(req.createdAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        PillButton("Посмотреть запросы", modifier = Modifier.fillMaxWidth(), fill = Gradients.Sky, onClick = onGoAlerts)
    }
}

// ---- Members ---------------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MemberCard(state: FamilyUiState, member: MemberEntity, onClick: () -> Unit) {
    val role = member.memberRole
    val status = remember(member.statusJson) { member.deviceStatus() }
    val presence = presenceOf(member.lastSeenAt)
    val recent = state.alerts.firstOrNull { it.deviceId == member.deviceId && !it.read && it.severity in URGENT }
    val severity = recent?.let { runCatching { AlertSeverity.valueOf(it.severity) }.getOrNull() }

    SoftCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            AvatarImage(member.avatar, member.role, 64.dp, ring = severity?.let { ru.ubt.familyshield.ui.components.severityColor(it) })
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        member.displayName, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    TintChip(roleLabel(role), roleColor(role))
                }
                StatusDot(presence.text, presence.color)
            }
            if (severity != null) {
                IconTile(Icons.Rounded.Warning, severity.gradient(), size = 34.dp)
            } else {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        val battery = status?.takeIf { it.batteryPercent >= 0 }
        val place = member.lastAddress?.takeIf { it.isNotBlank() }
        val minutes = member.todayMinutes.takeIf { role == MemberRole.CHILD }
        val checkIn = member.lastCheckInAt.takeIf { role == MemberRole.ELDER }
        if (battery != null || place != null || minutes != null || checkIn != null) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                battery?.let { InfoChip(batteryIcon(it.batteryPercent, it.charging), "${it.batteryPercent}%", batteryTint(it.batteryPercent, it.charging)) }
                place?.let { InfoChip(Icons.Rounded.LocationOn, it, Brand.Info) }
                minutes?.let { InfoChip(Icons.Rounded.HourglassBottom, "${formatMinutes(it)} сегодня", Brand.Blue) }
                checkIn?.let { InfoChip(Icons.Rounded.Favorite, "«Я в порядке» ${relativeTime(it)}", Brand.Coral) }
            }
        }
    }
}

/** Dashed "add" card: the quiet, always-available way to connect another phone. */
@Composable
private fun AddPhoneCard(title: String, subtitle: String, onClick: () -> Unit) {
    val stroke = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
    Row(
        Modifier.fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .drawBehind {
                val w = 1.5.dp.toPx()
                drawRoundRect(
                    color = stroke,
                    topLeft = Offset(w / 2, w / 2),
                    size = Size(size.width - w, size.height - w),
                    cornerRadius = CornerRadius(26.dp.toPx()),
                    style = Stroke(width = w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(9.dp.toPx(), 7.dp.toPx()))),
                )
            }
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        IconTile(Icons.Rounded.Add, Gradients.Emerald, size = 44.dp)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
    }
}

/** Empty family: two illustrated doors into the invite flow. */
@Composable
private fun RolePromo(onAdd: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf(
            Triple(AvatarArt.GIRL, AvatarArt.BOY, "Ребёнок или подросток"),
            Triple(AvatarArt.GRANDMA, AvatarArt.GRANDPA, "Пожилой родитель"),
        ).forEach { (back, front, label) ->
            SoftCard(Modifier.weight(1f), onClick = onAdd, padding = 14.dp) {
                Box(Modifier.fillMaxWidth().height(120.dp).clip(MaterialTheme.shapes.medium).background(Gradients.Mint), contentAlignment = Alignment.Center) {
                    AvatarImage(back.value, size = 64.dp, ring = Color.White, modifier = Modifier.offset(x = 20.dp, y = (-10).dp))
                    AvatarImage(front.value, size = 72.dp, ring = Color.White, modifier = Modifier.offset(x = (-18).dp, y = 14.dp))
                }
                Text(label, style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

@Composable
private fun PremiumCard(onOpen: () -> Unit) {
    SoftCard(onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            IconTile(Icons.Rounded.WorkspacePremium, Gradients.Gold, size = 48.dp)
            Column(Modifier.weight(1f)) {
                Text("Полная защита в Премиуме", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Блокировка мошеннических звонков, лимиты и расписания, веб-фильтр, живая карта и оповещения об угрозах.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        PrimaryButton("Открыть Премиум") { onOpen() }
    }
}

fun roleLabel(role: MemberRole) = when (role) {
    MemberRole.GUARDIAN -> "Взрослый"
    MemberRole.CHILD -> "Ребёнок"
    MemberRole.ELDER -> "Родитель"
}

/** Matches [ru.ubt.familyshield.ui.components.visual] for roles: children sky, parents violet, adults emerald. */
fun roleColor(role: MemberRole) = when (role) {
    MemberRole.GUARDIAN -> Brand.Green
    MemberRole.CHILD -> Brand.Blue
    MemberRole.ELDER -> Brand.Purple
}

fun requestSummary(req: ru.ubt.familyshield.core.db.RequestEntity): String {
    val r = runCatching { ru.ubt.familyshield.shared.ShieldJson.decodeFromString(ru.ubt.familyshield.shared.model.MemberRequest.serializer(), req.json) }.getOrNull()
    return when (r?.kind) {
        ru.ubt.familyshield.shared.model.RequestKind.MORE_TIME -> "просит ещё ${r.minutes ?: 15} мин"
        ru.ubt.familyshield.shared.model.RequestKind.APP_APPROVAL -> "хочет «${r.appLabel ?: r.packageName}»"
        ru.ubt.familyshield.shared.model.RequestKind.SITE_UNBLOCK -> "просит сайт ${r.domain}"
        ru.ubt.familyshield.shared.model.RequestKind.PROTECTION_CHANGE -> "хочет отключить защиту"
        ru.ubt.familyshield.shared.model.RequestKind.REMOTE_ACCESS -> "хочет открыть «${r.appLabel ?: r.packageName}» (удалённый доступ)"
        null -> "запрос"
    }
}
