package ru.ubt.familyshield.ui.guardian

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.GppMaybe
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PhoneDisabled
import androidx.compose.material.icons.rounded.PhonelinkLock
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.Timelapse
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.ubt.familyshield.AppGraph
import ru.ubt.familyshield.core.db.MemberEntity
import ru.ubt.familyshield.core.family.memberRole
import ru.ubt.familyshield.core.family.toDayStat
import ru.ubt.familyshield.shared.billing.Feature
import ru.ubt.familyshield.shared.content.DayStat
import ru.ubt.familyshield.shared.content.WeekNumbers
import ru.ubt.familyshield.shared.content.WeeklyDigest
import ru.ubt.familyshield.shared.model.MemberRole
import ru.ubt.familyshield.shared.policy.formatMinutes
import ru.ubt.familyshield.ui.FamilyUiState
import ru.ubt.familyshield.ui.components.Art3D
import ru.ubt.familyshield.ui.components.AvatarImage
import ru.ubt.familyshield.ui.components.AvatarStack
import ru.ubt.familyshield.ui.components.HeroCard
import ru.ubt.familyshield.ui.components.IconTile
import ru.ubt.familyshield.ui.components.Illustration
import ru.ubt.familyshield.ui.components.LabelChip
import ru.ubt.familyshield.ui.components.SectionHeader
import ru.ubt.familyshield.ui.components.SoftCard
import ru.ubt.familyshield.ui.theme.Brand
import ru.ubt.familyshield.ui.theme.Gradients
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.IsoFields
import java.util.Locale

/*
 * «Неделя семьи»: the seven days in numbers, what happened with every protected member and one thing to
 * do next week. Opened from the Sunday notification and from the card on the home screen.
 */

/** Daily counters of the last two weeks, newest values as they arrive. */
@Composable
internal fun rememberWeekStats(graph: AppGraph): List<DayStat> {
    val today = LocalDate.now()
    val rows by remember(today) {
        graph.db.stats().observeSince(WeeklyDigest.previousWeek(today).start)
    }.collectAsState(emptyList())
    return rows.map { it.toDayStat() }
}

internal fun List<DayStat>.within(range: ClosedRange<String>) = filter { it.date in range }

/** "22 – 28 октября" or "28 сентября – 4 октября". */
internal fun weekRangeLabel(today: LocalDate): String {
    val from = today.minusDays(6)
    val ru = Locale.forLanguageTag("ru")
    return if (from.month == today.month) "${from.dayOfMonth} – ${today.format(DateTimeFormatter.ofPattern("d MMMM", ru))}"
    else "${from.format(DateTimeFormatter.ofPattern("d MMMM", ru))} – ${today.format(DateTimeFormatter.ofPattern("d MMMM", ru))}"
}

@Composable
fun WeekReportScreen(
    graph: AppGraph,
    state: FamilyUiState,
    onBack: () -> Unit,
    onOpenMember: (String) -> Unit,
    onLesson: (String) -> Unit,
    onPremium: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val today = LocalDate.now()
    val rows = rememberWeekStats(graph)
    val thisWeek = rows.within(WeeklyDigest.thisWeek(today))
    val week = WeekNumbers.of(thisWeek)
    val before = WeekNumbers.of(rows.within(WeeklyDigest.previousWeek(today)))
    val detailed = graph.entitlements.has(Feature.WEEKLY_REPORTS)
    val protectedMembers = state.protectedMembers
    fun trend(now: Int, was: Int) = if (detailed) WeeklyDigest.trend(now, was) else null

    DetailScaffold(title = "Неделя семьи", onBack = onBack) { padding ->
        Column(
            Modifier.padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val n = protectedMembers.size
            HeroCard(
                title = when {
                    week.sos > 0 -> "Был сигнал SOS"
                    week.dangerous > 0 -> "${week.dangerous} ${plural(week.dangerous, "опасный момент", "опасных момента", "опасных моментов")}"
                    else -> "Всё спокойно"
                },
                subtitle = weekRangeLabel(today),
                gradient = when {
                    week.sos > 0 -> Gradients.Danger
                    week.dangerous > 0 -> Gradients.Sunrise
                    else -> Gradients.Hero
                },
                art = { Illustration(Art3D.Calendar, size = 116.dp) },
                footer = {
                    if (n > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val shown = protectedMembers.take(4)
                            Box(Modifier.width(avatarStackWidth(shown.size, 28.dp))) { AvatarStack(shown.map { it.avatar to it.role }, size = 28.dp) }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                shown.joinToString(", ") { it.displayName }, style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(alpha = 0.9f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    } else {
                        HeroChip("Итоги за 7 дней", Icons.Rounded.DateRange)
                    }
                },
            )

            // Attacks are coloured neutrally: more blocked calls is not anyone's failure. Good habits are green when they grow.
            TileRowOf {
                WeekTile(week.scamCalls, "Мошенники", "звонки не прошли", Icons.Rounded.PhoneDisabled, Gradients.Danger, trend(week.scamCalls, before.scamCalls), null)
                WeekTile(week.scamMessages, "Сообщения", "с признаками обмана", Icons.Rounded.Sms, Gradients.Coral, trend(week.scamMessages, before.scamMessages), null)
            }
            TileRowOf {
                WeekTile(week.sitesBlocked, "Сайты", "закрыты фильтром", Icons.Rounded.PhonelinkLock, Gradients.Ocean, trend(week.sitesBlocked, before.sitesBlocked), null)
                WeekTile(week.checkIns, "Отметки", "«Я в порядке»", Icons.Rounded.Favorite, Gradients.Coral, trend(week.checkIns, before.checkIns), week.checkIns > before.checkIns)
            }
            TileRowOf {
                WeekTile(week.tasksDone, "Задания", "выполнены детьми", Icons.Rounded.TaskAlt, Gradients.Emerald, trend(week.tasksDone, before.tasksDone), week.tasksDone > before.tasksDone)
                WeekTile(week.lessons, "Уроки", "школы безопасности", Icons.Rounded.School, Gradients.Violet, trend(week.lessons, before.lessons), week.lessons > before.lessons)
            }
            week.avgScreenMinutes?.let { avg ->
                SoftCard(padding = 16.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        IconTile(Icons.Rounded.Timelapse, Gradients.Sky, size = 44.dp)
                        Column(Modifier.weight(1f)) {
                            Text("Экран у детей", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${formatMinutes(avg)} в день", style = MaterialTheme.typography.titleLarge)
                            val was = before.avgScreenMinutes
                            if (detailed && was != null && was != avg) {
                                val diff = avg - was
                                Text(
                                    "${if (diff > 0) "+" else "−"}${formatMinutes(kotlin.math.abs(diff))} к прошлой неделе",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (diff > 0) Brand.Warning else MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }

            if (protectedMembers.isNotEmpty()) {
                SectionHeader("По близким")
                if (detailed) {
                    protectedMembers.forEach { member ->
                        MemberWeekCard(member, WeekNumbers.of(thisWeek.filter { it.deviceId == member.deviceId })) { onOpenMember(member.deviceId) }
                    }
                } else {
                    LockedDetails(onPremium)
                }
            }

            SectionHeader("Совет на неделю")
            val tip = WeeklyDigest.tip(week, today.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR))
            SoftCard(padding = 16.dp) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Illustration(Art3D.Bulb, size = 56.dp)
                    Text(tip.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                }
                tip.lessonId?.let { id ->
                    PillButton("Открыть урок", modifier = Modifier.fillMaxWidth(), icon = Icons.Rounded.School) { onLesson(id) }
                }
            }

            GroupCard {
                IconToggleRow(
                    "Итоги по воскресеньям", Icons.Rounded.NotificationsActive, Gradients.Emerald,
                    checked = state.settings.weeklyDigest,
                    subtitle = "Вечером в воскресенье пришлём главное за неделю",
                ) { on -> scope.launch { graph.settings.setWeeklyDigest(on) } }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun TileRowOf(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp), content = content)
}

/** [good]: true colours the trend green, null keeps it neutral. */
@Composable
private fun RowScope.WeekTile(value: Int, title: String, caption: String, icon: ImageVector, gradient: Brush, trend: String?, good: Boolean?) {
    SoftCard(Modifier.weight(1f).fillMaxHeight(), padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IconTile(icon, gradient, size = 36.dp)
            Text("$value", style = MaterialTheme.typography.headlineSmall)
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (trend != null) {
                Text(
                    trend, style = MaterialTheme.typography.labelSmall, maxLines = 1,
                    color = if (good == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Short facts about one member's week, the alarming ones first. */
internal fun memberWeekFacts(n: WeekNumbers): List<String> = buildList {
    if (n.scamCalls > 0) add("${n.scamCalls} ${plural(n.scamCalls, "звонок мошенников", "звонка мошенников", "звонков мошенников")}")
    if (n.scamMessages > 0) add("${n.scamMessages} ${plural(n.scamMessages, "опасное сообщение", "опасных сообщения", "опасных сообщений")}")
    if (n.sitesBlocked > 0) add("${n.sitesBlocked} ${plural(n.sitesBlocked, "закрытый сайт", "закрытых сайта", "закрытых сайтов")}")
    if (n.checkIns > 0) add("${n.checkIns} ${plural(n.checkIns, "отметка", "отметки", "отметок")} «Я в порядке»")
    if (n.tasksDone > 0) add("${n.tasksDone} ${plural(n.tasksDone, "задание", "задания", "заданий")}")
    if (n.lessons > 0) add("${n.lessons} ${plural(n.lessons, "урок", "урока", "уроков")}")
    n.avgScreenMinutes?.let { add("экран ${formatMinutes(it)} в день") }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MemberWeekCard(member: MemberEntity, numbers: WeekNumbers, onClick: () -> Unit) {
    val facts = memberWeekFacts(numbers)
    SoftCard(onClick = onClick, padding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AvatarImage(member.avatar, member.role, 48.dp)
            Column(Modifier.weight(1f)) {
                Text(member.displayName.ifBlank { "Близкий" }, style = MaterialTheme.typography.titleMedium)
                Text(
                    if (member.memberRole == MemberRole.ELDER) "Защита от мошенников" else "Детский режим",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            when {
                numbers.sos > 0 -> LabelChip("Сигнал SOS", Brand.DangerLight, Brand.Danger, icon = Icons.Rounded.GppMaybe)
                numbers.dangerous > 0 -> LabelChip(
                    "${numbers.dangerous} ${plural(numbers.dangerous, "тревога", "тревоги", "тревог")}",
                    Brand.WarningLight, Brand.Warning, icon = Icons.Rounded.GppMaybe,
                )
                else -> LabelChip("Спокойная неделя", Brand.GreenLight, Brand.GreenDark, icon = Icons.Rounded.Shield)
            }
            facts.forEach { LabelChip(it, MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurface) }
        }
    }
}

@Composable
private fun LockedDetails(onPremium: () -> Unit) {
    SoftCard(padding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            IconTile(Icons.Rounded.Lock, Gradients.Gold, size = 44.dp, tint = Brand.Ink)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Неделя каждого близкого", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Что было у мамы и у детей по отдельности и как неделя отличается от прошлой — в Премиуме.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        PillButton("Подробнее о Премиуме", modifier = Modifier.fillMaxWidth(), fill = Gradients.Gold, contentColor = Brand.Ink, onClick = onPremium)
    }
}
