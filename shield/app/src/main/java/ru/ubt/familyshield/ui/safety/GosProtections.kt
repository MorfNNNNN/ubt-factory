package ru.ubt.familyshield.ui.safety

import android.content.Intent
import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.ubt.familyshield.AppGraph
import ru.ubt.familyshield.core.db.MemberEntity
import ru.ubt.familyshield.core.family.memberRole
import ru.ubt.familyshield.shared.ShieldJson
import ru.ubt.familyshield.shared.content.GosProtections
import ru.ubt.familyshield.shared.content.ProtectionStep
import ru.ubt.familyshield.shared.model.SafetyProgress
import ru.ubt.familyshield.ui.components.Art3D
import ru.ubt.familyshield.ui.components.ArtTile
import ru.ubt.familyshield.ui.components.AvatarImage
import ru.ubt.familyshield.ui.components.HeroCard
import ru.ubt.familyshield.ui.components.Illustration
import ru.ubt.familyshield.ui.components.LabelChip
import ru.ubt.familyshield.ui.components.NoticeCard
import ru.ubt.familyshield.ui.components.ProgressBar
import ru.ubt.familyshield.ui.components.SectionHeader
import ru.ubt.familyshield.ui.components.SoftCard
import ru.ubt.familyshield.ui.components.relativeTime
import ru.ubt.familyshield.ui.guardian.DetailScaffold
import ru.ubt.familyshield.ui.guardian.HeroChip
import ru.ubt.familyshield.ui.guardian.PillButton
import ru.ubt.familyshield.ui.guardian.tintedFill
import ru.ubt.familyshield.ui.theme.Brand
import ru.ubt.familyshield.ui.theme.Gradients

/*
 * «Госуслуги и банк»: free official protections (credit self-ban, SIM ban, sign-in confirmation,
 * trusted person…) with step-by-step help. Ticks are shared with the family; the app never sees
 * the person's Gosuslugi or bank data.
 */

@DrawableRes
fun stepArt(id: String): Int = when (id) {
    "credit_ban" -> Art3D.Locker
    "sim_ban" -> Art3D.Mobile
    "gos_2fa" -> Art3D.Key
    "trusted_person" -> Art3D.Heart
    "bank_limits" -> Art3D.Wallet
    "credit_history" -> Art3D.FileText
    "code_word" -> Art3D.ChatText
    else -> Art3D.Shield
}

@Composable
fun GosProtectionsScreen(graph: AppGraph, onBack: () -> Unit) {
    val settings by graph.settings.state.collectAsState()
    val done = settings.learning.steps
    val steps = GosProtections.steps
    var expanded by remember { mutableStateOf(steps.firstOrNull { it.id !in done }?.id) }
    val context = LocalContext.current

    DetailScaffold(title = "Госуслуги и банк", onBack = onBack) { padding ->
        Column(
            Modifier.padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val count = steps.count { it.id in done }
            HeroCard(
                title = "Закройте лазейки мошенникам",
                subtitle = "${steps.size} бесплатных официальных мер. Каждая — несколько минут",
                gradient = Gradients.Hero,
                art = { Illustration(Art3D.Shield, size = 118.dp) },
                footer = {
                    LinearProgressIndicator(
                        progress = { count / steps.size.toFloat() },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                        color = Color.White, trackColor = Color.White.copy(alpha = 0.25f),
                    )
                    Spacer(Modifier.height(6.dp))
                    HeroChip("Сделано $count из ${steps.size}", Icons.Rounded.CheckCircle)
                },
            )

            steps.forEach { step ->
                StepCard(
                    step = step,
                    done = step.id in done,
                    expanded = expanded == step.id,
                    onToggleExpand = { expanded = if (expanded == step.id) null else step.id },
                    onOpenLink = { url -> runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } },
                    onMark = { value -> graph.appScope.launch { graph.actions.setProtectionStep(step.id, value) } },
                )
            }

            NoticeCard(
                "Всё делается в официальных сервисах. «Семейный щит» не видит ваши данные Госуслуг и банка — близкие видят только отметки.",
                Icons.Rounded.Lock, MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun StepCard(
    step: ProtectionStep,
    done: Boolean,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onOpenLink: (String) -> Unit,
    onMark: (Boolean) -> Unit,
) {
    SoftCard(Modifier.animateContentSize(), onClick = onToggleExpand, padding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            ArtTile(stepArt(step.id), size = 58.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(step.title, style = MaterialTheme.typography.titleMedium)
                if (done) LabelChip("Сделано", Brand.GreenLight, Brand.GreenDark, icon = Icons.Rounded.CheckCircle)
                else Text(step.why, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = if (expanded) 6 else 2)
            }
            Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (expanded) {
            if (done) Text(step.why, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                step.how.forEachIndexed { i, line ->
                    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(28.dp).clip(CircleShape).background(Gradients.Emerald), contentAlignment = Alignment.Center) {
                            Text("${i + 1}", style = MaterialTheme.typography.labelLarge, color = Color.White)
                        }
                        Text(line, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(top = 2.dp))
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                step.link?.let { url ->
                    PillButton("Госуслуги", modifier = Modifier.weight(1f), icon = Icons.AutoMirrored.Rounded.OpenInNew, fill = Gradients.Sky) { onOpenLink(url) }
                }
                if (done) {
                    PillButton(
                        "Отменить отметку", modifier = Modifier.weight(1f), fill = tintedFill(),
                        contentColor = MaterialTheme.colorScheme.onSurface, elevated = false,
                    ) { onMark(false) }
                } else {
                    PillButton("Я сделал(а)", modifier = Modifier.weight(1f), icon = Icons.Rounded.Check) { onMark(true) }
                }
            }
        }
    }
}

// ---- What a guardian sees about a family member ----------------------------------------------------

fun MemberEntity.learning(): SafetyProgress =
    learningJson?.let { runCatching { ShieldJson.decodeFromString(SafetyProgress.serializer(), it) }.getOrNull() } ?: SafetyProgress()

/** One-line summary for the member screen. */
fun safetySummary(member: MemberEntity): String {
    val p = member.learning()
    val lessons = lessonsFor(member.memberRole)
    val passed = lessons.count { p.passed(it.id) }
    val steps = GosProtections.steps.count { it.id in p.steps }
    return if (passed == 0 && steps == 0) "Пока не начато — предложите пройти уроки"
    else "Уроки: $passed из ${lessons.size} · защита: $steps из ${GosProtections.steps.size}"
}

/** A family member's lessons and official protections, read-only. */
@Composable
fun MemberSafetyScreen(member: MemberEntity, onBack: () -> Unit) {
    val progress = remember(member.learningJson) { member.learning() }
    val lessons = remember(member.role) { lessonsFor(member.memberRole) }
    DetailScaffold(title = "Безопасность: ${member.displayName}", onBack = onBack) { padding ->
        Column(
            Modifier.padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val passed = lessons.count { progress.passed(it.id) }
            val steps = GosProtections.steps.count { it.id in progress.steps }
            SoftCard(padding = 18.dp) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    AvatarImage(member.avatar, member.role, 56.dp)
                    Column(Modifier.weight(1f)) {
                        Text(member.displayName, style = MaterialTheme.typography.titleLarge)
                        Text(
                            if (progress.updatedAt > 0) "Обновлено ${relativeTime(progress.updatedAt)}" else "Пока нет данных",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                ProgressLine("Школа безопасности", passed, lessons.size, Brand.Purple)
                ProgressLine("Госуслуги и банк", steps, GosProtections.steps.size, Brand.Green)
            }

            SectionHeader("Уроки")
            SoftCard(padding = 12.dp) {
                lessons.forEach { lesson ->
                    val best = progress.lessons[lesson.id]
                    StatusRow(lessonArt(lesson.id), lesson.title, progress.passed(lesson.id), best?.let { "$it из ${lesson.quiz.size}" })
                }
            }
            SectionHeader("Защита в Госуслугах и банке")
            SoftCard(padding = 12.dp) {
                GosProtections.steps.forEach { step -> StatusRow(stepArt(step.id), step.title, step.id in progress.steps, null) }
            }
            NoticeCard(
                "Предложите пройти уроки вместе — по телефону или в гостях. Это лучшая защита от мошенников.",
                Icons.Rounded.CheckCircle, Brand.GreenLight, Brand.GreenDark,
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ProgressLine(title: String, value: Int, total: Int, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row {
            Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text("$value из $total", style = MaterialTheme.typography.titleSmall, color = color)
        }
        ProgressBar(if (total == 0) 0f else value / total.toFloat(), color)
    }
}

@Composable
private fun StatusRow(@DrawableRes art: Int, title: String, done: Boolean, extra: String?) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ArtTile(art, size = 44.dp)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (extra != null) Text(extra, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(
            if (done) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, contentDescription = if (done) "Сделано" else "Не сделано",
            tint = if (done) Brand.Green else MaterialTheme.colorScheme.outline, modifier = Modifier.size(26.dp),
        )
    }
}
