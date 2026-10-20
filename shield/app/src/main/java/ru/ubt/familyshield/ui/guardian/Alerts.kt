package ru.ubt.familyshield.ui.guardian

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.ubt.familyshield.AppGraph
import ru.ubt.familyshield.core.db.AlertEntity
import ru.ubt.familyshield.core.db.RequestEntity
import ru.ubt.familyshield.shared.model.AlertSeverity
import ru.ubt.familyshield.ui.FamilyUiState
import ru.ubt.familyshield.ui.ShieldViewModel
import ru.ubt.familyshield.ui.components.Avatar
import ru.ubt.familyshield.ui.components.EmptyState
import ru.ubt.familyshield.ui.components.Pill
import ru.ubt.familyshield.ui.components.PrimaryButton
import ru.ubt.familyshield.ui.components.ScreenScaffold
import ru.ubt.familyshield.ui.components.SecondaryButton
import ru.ubt.familyshield.ui.components.SectionCard
import ru.ubt.familyshield.ui.components.relativeTime
import ru.ubt.familyshield.ui.components.severityColor
import ru.ubt.familyshield.ui.components.severityLabel
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

@Composable
fun AlertsScreen(graph: AppGraph, vm: ShieldViewModel, state: FamilyUiState, onOpenMember: (String) -> Unit) {
    LaunchedEffect(Unit) { vm.markAlertsRead() }
    val scope = rememberCoroutineScope()
    fun name(deviceId: String) = state.members.firstOrNull { it.deviceId == deviceId }?.displayName ?: "Близкий"

    ScreenScaffold(title = "События") { padding ->
        if (state.alerts.isEmpty() && state.pendingRequests.isEmpty()) {
            EmptyState("🕊️", "Пока спокойно", "Здесь появятся предупреждения о мошенничестве, угрозах детям, геозонах и запросы от близких.")
            return@ScreenScaffold
        }
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (state.pendingRequests.isNotEmpty()) {
                item { Text("Запросы", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
                items(state.pendingRequests, key = { it.id }) { req ->
                    RequestCard(graph, req, name(req.deviceId))
                }
            }
            item { Text("Лента", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp)) }
            items(state.alerts, key = { it.id }) { alert ->
                AlertCard(alert, name(alert.deviceId)) { onOpenMember(alert.deviceId) }
            }
        }
    }
}

@Composable
private fun AlertCard(alert: AlertEntity, memberName: String, onClick: () -> Unit) {
    val severity = runCatching { AlertSeverity.valueOf(alert.severity) }.getOrDefault(AlertSeverity.INFO)
    SectionCard(onClick = onClick) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Avatar(alertEmoji(alert.type), 40.dp, severityColor(severity).copy(alpha = 0.15f))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(alert.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    if (severity >= AlertSeverity.WARNING) Pill(severityLabel(severity), severityColor(severity))
                }
                Text(alert.body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                alert.excerpt?.takeIf { it.isNotBlank() }?.let {
                    Text("«${it.take(140)}»", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("$memberName · ${relativeTime(alert.createdAt)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun RequestCard(graph: AppGraph, req: RequestEntity, memberName: String) {
    val scope = rememberCoroutineScope()
    SectionCard(container = MaterialTheme.colorScheme.secondaryContainer) {
        Text("$memberName ${requestSummary(req)}", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryButton("Разрешить", modifier = Modifier.weight(1f)) { scope.launch { graph.guardian.decide(req, approved = true) } }
            SecondaryButton("Отклонить", modifier = Modifier.weight(1f)) { scope.launch { graph.guardian.decide(req, approved = false) } }
        }
    }
}

fun alertEmoji(type: String): String = when (type) {
    "SOS" -> "🆘"
    "FALL_DETECTED" -> "🧓"
    "BANK_APP_DURING_CALL", "OTP_DURING_CALL", "REMOTE_ACCESS_APP" -> "🚨"
    "SCAM_CALL_BLOCKED", "SUSPICIOUS_CALL", "LONG_CALL_UNKNOWN" -> "📞"
    "SCAM_MESSAGE", "PHISHING_LINK" -> "✉️"
    "CHILD_RISK_MESSAGE" -> "🛑"
    "RISKY_APP_INSTALLED", "NEW_APP_INSTALLED" -> "📦"
    "BLOCKED_SITE", "FILTER_BYPASS" -> "🌐"
    "LIMIT_REACHED" -> "⏳"
    "GEOFENCE_ENTER", "GEOFENCE_EXIT" -> "📍"
    "LOW_BATTERY" -> "🔋"
    "CHECK_IN_OK" -> "👍"
    "CHECK_IN_MISSED", "INACTIVITY" -> "⏰"
    "MEDICATION_MISSED", "MEDICATION_TAKEN" -> "💊"
    "TAMPER", "UNINSTALL_ATTEMPT", "PERMISSION_REVOKED" -> "🔧"
    else -> "🔔"
}
