package ru.ubt.familyshield.ui.guardian

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GppMaybe
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.MoreTime
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.ubt.familyshield.AppGraph
import ru.ubt.familyshield.R
import ru.ubt.familyshield.core.db.AlertEntity
import ru.ubt.familyshield.core.db.MemberEntity
import ru.ubt.familyshield.core.db.RequestEntity
import ru.ubt.familyshield.shared.ShieldJson
import ru.ubt.familyshield.shared.model.AlertSeverity
import ru.ubt.familyshield.shared.model.AlertType
import ru.ubt.familyshield.shared.model.MemberRequest
import ru.ubt.familyshield.shared.model.MemberRole
import ru.ubt.familyshield.shared.model.RequestKind
import ru.ubt.familyshield.ui.FamilyUiState
import ru.ubt.familyshield.ui.ShieldViewModel
import ru.ubt.familyshield.ui.components.AvatarImage
import ru.ubt.familyshield.ui.components.FeatureIcons
import ru.ubt.familyshield.ui.components.IconTile
import ru.ubt.familyshield.ui.components.LottieView
import ru.ubt.familyshield.ui.components.SectionHeader
import ru.ubt.familyshield.ui.components.SoftCard
import ru.ubt.familyshield.ui.components.Visual
import ru.ubt.familyshield.ui.components.relativeTime
import ru.ubt.familyshield.ui.components.severityColor
import ru.ubt.familyshield.ui.components.visual
import ru.ubt.familyshield.ui.theme.Brand
import ru.ubt.familyshield.ui.theme.Gradients
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun AlertsScreen(graph: AppGraph, vm: ShieldViewModel, state: FamilyUiState, onOpenMember: (String) -> Unit) {
    LaunchedEffect(Unit) { vm.markAlertsRead() }
    fun member(deviceId: String) = state.members.firstOrNull { it.deviceId == deviceId }
    fun name(deviceId: String) = member(deviceId)?.displayName ?: "Близкий"

    val requests = state.pendingRequests.size
    val subtitle = if (requests > 0) "$requests ${plural(requests, "запрос ждёт", "запроса ждут", "запросов ждут")} ответа" else null

    if (state.alerts.isEmpty() && state.pendingRequests.isEmpty()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            PageHeader("События")
            ArtEmptyState(
                "Пока спокойно",
                "Здесь появятся предупреждения о мошенничестве, угрозах детям, геозонах и запросы от близких.",
                modifier = Modifier.padding(top = 36.dp),
            ) { LottieView(R.raw.lottie_shield_active, size = 180.dp) }
        }
        return
    }

    val days = remember(state.alerts) { state.alerts.groupBy { dayOf(it.createdAt) } }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "header") { PageHeader("События", subtitle = subtitle) }
        if (state.pendingRequests.isNotEmpty()) {
            item(key = "requests") { SectionHeader("Запросы") }
            items(state.pendingRequests, key = { it.id }) { req ->
                RequestCard(graph, req, member(req.deviceId), name(req.deviceId))
            }
        }
        days.forEach { (day, alerts) ->
            item(key = "day-$day") { SectionHeader(dayLabel(day)) }
            items(alerts, key = { it.id }) { alert ->
                AlertCard(alert, member(alert.deviceId), name(alert.deviceId)) { onOpenMember(alert.deviceId) }
            }
        }
    }
}

@Composable
private fun AlertCard(alert: AlertEntity, member: MemberEntity?, memberName: String, onClick: () -> Unit) {
    val severity = runCatching { AlertSeverity.valueOf(alert.severity) }.getOrDefault(AlertSeverity.INFO)
    val visual = runCatching { AlertType.valueOf(alert.type).visual() }.getOrNull() ?: FeatureIcons.Protection
    SoftCard(onClick = onClick, padding = 16.dp) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            IconTile(visual.icon, visual.gradient, size = 46.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(alert.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    if (severity >= AlertSeverity.WARNING) SeverityChip(severity)
                }
                Text(alert.body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        alert.excerpt?.takeIf { it.isNotBlank() }?.let {
            QuoteBlock("«${it.take(140)}»", accent = severityColor(severity))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AvatarImage(member?.avatar, member?.role, 24.dp)
            Text(
                listOfNotNull(memberName, relativeTime(alert.createdAt), alert.sourceAppLabel?.takeIf { it.isNotBlank() }).joinToString(" · "),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun RequestCard(graph: AppGraph, req: RequestEntity, member: MemberEntity?, memberName: String) {
    val scope = rememberCoroutineScope()
    val request = remember(req.json) { runCatching { ShieldJson.decodeFromString(MemberRequest.serializer(), req.json) }.getOrNull() }
    val kind = requestVisual(request?.kind)
    SoftCard(padding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box {
                AvatarImage(member?.avatar, member?.role ?: MemberRole.CHILD.name, 50.dp)
                Box(Modifier.align(Alignment.BottomEnd).offset(x = 5.dp, y = 5.dp)) { IconTile(kind.icon, kind.gradient, size = 24.dp) }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold)) { append(memberName) }
                        append(" ")
                        append(requestSummary(req))
                    },
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(relativeTime(req.createdAt), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        request?.reason?.takeIf { it.isNotBlank() }?.let { QuoteBlock("«$it»", accent = Brand.Blue) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton("Разрешить", modifier = Modifier.weight(1f), icon = Icons.Rounded.Check) {
                scope.launch { graph.guardian.decide(req, approved = true) }
            }
            PillButton(
                "Отклонить", modifier = Modifier.weight(1f), icon = Icons.Rounded.Close,
                fill = tintedFill(), contentColor = MaterialTheme.colorScheme.onSurface, elevated = false,
            ) {
                scope.launch { graph.guardian.decide(req, approved = false) }
            }
        }
    }
}

private fun requestVisual(kind: RequestKind?): Visual = when (kind) {
    RequestKind.MORE_TIME -> Visual(Icons.Rounded.MoreTime, Gradients.Sky)
    RequestKind.APP_APPROVAL -> Visual(Icons.Rounded.InstallMobile, Gradients.Violet)
    RequestKind.SITE_UNBLOCK -> Visual(Icons.Rounded.Language, Gradients.Violet)
    RequestKind.PROTECTION_CHANGE -> Visual(Icons.Rounded.GppMaybe, Gradients.Danger)
    null -> Visual(Icons.AutoMirrored.Rounded.HelpOutline, Gradients.Slate)
}

private fun dayOf(ts: Long): LocalDate = Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).toLocalDate()

private fun dayLabel(day: LocalDate, today: LocalDate = LocalDate.now()): String = when (day) {
    today -> "Сегодня"
    today.minusDays(1) -> "Вчера"
    else -> day.format(DateTimeFormatter.ofPattern(if (day.year == today.year) "d MMMM" else "d MMMM yyyy", Locale("ru")))
}
