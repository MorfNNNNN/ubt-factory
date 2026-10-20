package ru.ubt.familyshield.ui.child

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import ru.ubt.familyshield.AppGraph
import ru.ubt.familyshield.shared.model.MemberRole
import ru.ubt.familyshield.shared.policy.Clock
import ru.ubt.familyshield.shared.policy.ScreenTimeEngine
import ru.ubt.familyshield.shared.policy.UsageSnapshot
import ru.ubt.familyshield.shared.policy.formatMinutes
import ru.ubt.familyshield.ui.FamilyUiState
import ru.ubt.familyshield.ui.ShieldViewModel
import ru.ubt.familyshield.ui.components.Avatar
import ru.ubt.familyshield.ui.components.Banner
import ru.ubt.familyshield.ui.components.Gap
import ru.ubt.familyshield.ui.components.PrimaryButton
import ru.ubt.familyshield.ui.components.ProgressBar
import ru.ubt.familyshield.ui.components.ScreenScaffold
import ru.ubt.familyshield.ui.components.SecondaryButton
import ru.ubt.familyshield.ui.components.SectionCard
import ru.ubt.familyshield.ui.components.timeOf
import ru.ubt.familyshield.ui.setup.PermissionWizard
import ru.ubt.familyshield.ui.theme.Brand
import ru.ubt.familyshield.protect.ui.SosActivity
import android.content.Intent
import androidx.compose.ui.platform.LocalContext

/** The child's own screen: honest, friendly, shows their time left and lets them reach parents and SOS. */
@Composable
fun ChildHome(graph: AppGraph, vm: ShieldViewModel, state: FamilyUiState) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var showPermissions by remember { mutableStateOf(false) }
    if (showPermissions) { PermissionWizard(graph, onboarding = false, onBack = { showPermissions = false }) { showPermissions = false }; return }

    val policy = state.settings.childPolicy
    val snapshot = remember(state.self?.todayMinutes) { UsageSnapshot(state.self?.todayMinutes ?: 0, emptyMap(), emptyMap()) }
    val clock = Clock.of(System.currentTimeMillis())
    val limit = ScreenTimeEngine.dailyLimit(policy, clock)
    val remaining = ScreenTimeEngine.remainingToday(policy, snapshot, clock)
    val health = remember { graph.protection.health.snapshot(MemberRole.CHILD, policy, state.settings.elderPolicy) }

    ScreenScaffold(title = "Мой щит") { padding ->
        Column(Modifier.padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Avatar(state.settings.avatar, 56.dp)
                Column {
                    Text("Привет, ${state.settings.displayName.ifBlank { "друг" }}!", style = MaterialTheme.typography.headlineSmall)
                    Text("Телефон под защитой семьи", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            if (health.criticalMissing.isNotEmpty()) {
                Banner("Нужно завершить настройку, чтобы всё работало.", Brand.Warning, emoji = "⚙️") { showPermissions = true }
            }

            SectionCard(title = "Время сегодня") {
                if (limit == null) {
                    Text("Сегодня без лимита 🎉", style = MaterialTheme.typography.titleMedium)
                } else {
                    val used = snapshot.totalMinutes
                    Text("${formatMinutes(remaining ?: 0)} осталось", style = MaterialTheme.typography.headlineSmall, color = if ((remaining ?: 0) > 15) Brand.Green else Brand.Warning)
                    ProgressBar(used.toFloat() / limit, if (used < limit) Brand.Green else Brand.Danger)
                    Text("Использовано ${formatMinutes(used)} из ${formatMinutes(limit)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                ScreenTimeEngine.nextScheduleStart(policy, clock)?.let { (rule, start) ->
                    Text("Далее: «${rule.name}» в ${ru.ubt.familyshield.shared.policy.formatMinuteOfDay(start)}", style = MaterialTheme.typography.bodyMedium)
                }
            }

            SectionCard(title = "Связь с семьёй") {
                PrimaryButton("Попросить ещё времени") {
                    scope.launch {
                        graph.sync.send(
                            ru.ubt.familyshield.shared.protocol.FamilyPayload.Request(
                                ru.ubt.familyshield.shared.model.MemberRequest(java.util.UUID.randomUUID().toString(), ru.ubt.familyshield.shared.model.RequestKind.MORE_TIME, System.currentTimeMillis(), minutes = 30),
                            ),
                            ru.ubt.familyshield.shared.protocol.Audience.GUARDIANS, emptyList(), true, null,
                        )
                    }
                }
                SecondaryButton("Написать родителям") {
                    scope.launch { graph.guardian.note("Привет!", null) }
                }
            }

            Box(
                Modifier.fillMaxWidth().size(0.dp),
            ) {}
            PrimaryButton("🆘  Мне нужна помощь (SOS)", color = Brand.Danger) {
                context.startActivity(Intent(context, SosActivity::class.java))
            }

            SectionCard(title = "Что видят родители", container = MaterialTheme.colorScheme.surfaceVariant) {
                Text("• Сколько времени ты в приложениях\n• Где находится телефон\n• Предупреждения, если кто-то угрожает или обманывает", style = MaterialTheme.typography.bodyMedium)
                Text("Твою личную переписку родители не читают.", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            }
            Gap(24.dp)
        }
    }
}
