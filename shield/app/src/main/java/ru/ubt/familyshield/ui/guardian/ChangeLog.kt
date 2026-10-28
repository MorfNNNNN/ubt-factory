package ru.ubt.familyshield.ui.guardian

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.ubt.familyshield.AppGraph
import ru.ubt.familyshield.core.db.ChangeEntity
import ru.ubt.familyshield.core.db.MemberEntity
import ru.ubt.familyshield.ui.components.Art3D
import ru.ubt.familyshield.ui.components.AvatarImage
import ru.ubt.familyshield.ui.components.Illustration
import ru.ubt.familyshield.ui.components.SectionHeader
import ru.ubt.familyshield.ui.components.SoftCard
import ru.ubt.familyshield.ui.components.timeOf
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * «Журнал изменений»: who changed which rule and when. The same list is shown to the guardians and on
 * the protected phone itself, so rules never change silently.
 */
@Composable
fun ChangeLogScreen(graph: AppGraph, memberDeviceId: String, title: String, members: List<MemberEntity>, onBack: () -> Unit) {
    val changes by remember(memberDeviceId) { graph.db.changes().observeFor(memberDeviceId) }.collectAsState(emptyList())
    val me = graph.settings.current.deviceId
    DetailScaffold(title = title, onBack = onBack) { padding ->
        Column(
            Modifier.padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (changes.isEmpty()) {
                SoftCard {
                    ArtEmptyState(
                        "Изменений пока не было",
                        "Когда кто-то из семьи поменяет правила или защиту, здесь появится запись: кто, что и когда.",
                    ) { Illustration(Art3D.FileText, size = 96.dp) }
                }
            } else {
                changes.groupBy { dayOf(it.createdAt) }.forEach { (day, items) ->
                    SectionHeader(dayTitle(day))
                    SoftCard(padding = 14.dp) {
                        items.groupBy { it.actorDeviceId to it.createdAt }.forEach { (key, batch) ->
                            ChangeBatch(key.first, key.second, batch, members, me)
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** Changes saved together by one person: their portrait, the time and one line per rule. */
@Composable
private fun ChangeBatch(actor: String, at: Long, batch: List<ChangeEntity>, members: List<MemberEntity>, me: String?) {
    val who = members.firstOrNull { it.deviceId == actor }
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        AvatarImage(who?.avatar, who?.role, 36.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                (if (actor == me) "Вы" else who?.displayName ?: "Близкий") + " · " + timeOf(at),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            batch.forEach { Text(it.text, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

private fun dayOf(ts: Long): LocalDate = Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).toLocalDate()

private fun dayTitle(day: LocalDate, today: LocalDate = LocalDate.now()): String = when (day) {
    today -> "Сегодня"
    today.minusDays(1) -> "Вчера"
    else -> day.format(DateTimeFormatter.ofPattern("d MMMM", Locale("ru")))
}
