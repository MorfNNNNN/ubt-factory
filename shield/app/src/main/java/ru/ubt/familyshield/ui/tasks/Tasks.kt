package ru.ubt.familyshield.ui.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Bed
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.LocalDining
import androidx.compose.material.icons.rounded.MoreTime
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Stars
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.VolunteerActivism
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import ru.ubt.familyshield.AppGraph
import ru.ubt.familyshield.core.db.MemberEntity
import ru.ubt.familyshield.core.family.task
import ru.ubt.familyshield.shared.model.FamilyTask
import ru.ubt.familyshield.shared.model.HelperLevel
import ru.ubt.familyshield.shared.model.TaskRepeat
import ru.ubt.familyshield.shared.model.TaskState
import ru.ubt.familyshield.shared.model.TaskTemplate
import ru.ubt.familyshield.shared.model.TaskTemplates
import ru.ubt.familyshield.shared.policy.formatMinutes
import ru.ubt.familyshield.ui.components.Art3D
import ru.ubt.familyshield.ui.components.HeroCard
import ru.ubt.familyshield.ui.components.IconTile
import ru.ubt.familyshield.ui.components.Illustration
import ru.ubt.familyshield.ui.components.LabelChip
import ru.ubt.familyshield.ui.components.SectionHeader
import ru.ubt.familyshield.ui.components.SoftCard
import ru.ubt.familyshield.ui.components.Visual
import ru.ubt.familyshield.ui.guardian.ArtEmptyState
import ru.ubt.familyshield.ui.guardian.DetailScaffold
import ru.ubt.familyshield.ui.guardian.HeroChip
import ru.ubt.familyshield.ui.guardian.PillButton
import ru.ubt.familyshield.ui.guardian.plural
import ru.ubt.familyshield.ui.guardian.ChoiceChip
import ru.ubt.familyshield.ui.guardian.tintedFill
import ru.ubt.familyshield.ui.theme.Brand
import ru.ubt.familyshield.ui.theme.Gradients

/*
 * «Задания и награды»: a guardian sets chores and goals, the child marks them done, the guardian
 * accepts and the reward arrives as bonus screen time. Points from accepted tasks raise the child's level.
 */

fun taskVisual(kind: String): Visual = when (kind) {
    "homework" -> Visual(Icons.AutoMirrored.Rounded.MenuBook, Gradients.Sky)
    "reading" -> Visual(Icons.Rounded.AutoStories, Gradients.Violet)
    "room" -> Visual(Icons.Rounded.CleaningServices, Gradients.Ocean)
    "bed" -> Visual(Icons.Rounded.Bed, Gradients.Night)
    "dishes" -> Visual(Icons.Rounded.LocalDining, Gradients.Coral)
    "trash" -> Visual(Icons.Rounded.DeleteOutline, Gradients.Slate)
    "walk" -> Visual(Icons.AutoMirrored.Rounded.DirectionsWalk, Gradients.Emerald)
    "sport" -> Visual(Icons.Rounded.FitnessCenter, Gradients.Sunrise)
    "pet" -> Visual(Icons.Rounded.Pets, Gradients.Sunrise)
    "grandma" -> Visual(Icons.Rounded.Call, Gradients.Coral)
    "poem" -> Visual(Icons.Rounded.RecordVoiceOver, Gradients.Violet)
    "help" -> Visual(Icons.Rounded.VolunteerActivism, Gradients.Emerald)
    else -> Visual(Icons.Rounded.TaskAlt, Gradients.Emerald)
}

fun repeatLabel(repeat: TaskRepeat): String = when (repeat) {
    TaskRepeat.ONCE -> "Один раз"
    TaskRepeat.DAILY -> "Каждый день"
    TaskRepeat.WEEKLY -> "Каждую неделю"
}

/** What a child has earned with tasks so far, removed tasks included. */
data class TaskRewards(val points: Int, val minutes: Int, val accepted: Int) {
    val level: HelperLevel get() = HelperLevel.of(points)
    val next: HelperLevel? get() = HelperLevel.next(points)

    /** Progress from the current level to the next one, 0..1. */
    val levelProgress: Float
        get() {
            val nextLevel = next ?: return 1f
            val span = (nextLevel.minPoints - level.minPoints).coerceAtLeast(1)
            return (points - level.minPoints) / span.toFloat()
        }

    companion object {
        fun of(tasks: List<FamilyTask>) = TaskRewards(tasks.sumOf { it.earnedPoints }, tasks.sumOf { it.earnedMinutes }, tasks.sumOf { it.approvals })
    }
}

private class TaskLists(val active: List<FamilyTask>, val rewards: TaskRewards)

@Composable
private fun rememberTasks(graph: AppGraph, deviceId: String): TaskLists {
    val history by remember(deviceId) {
        graph.db.tasks().observeHistoryFor(deviceId).map { list -> list.mapNotNull { runCatching { it.task() }.getOrNull() } }
    }.collectAsState(emptyList())
    val active = history.filter { !it.deleted }.sortedByDescending { it.createdAt }
    return TaskLists(active, TaskRewards.of(history))
}

// ---- Guardian ---------------------------------------------------------------------------------------

@Composable
fun GuardianTasksScreen(graph: AppGraph, member: MemberEntity, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val lists = rememberTasks(graph, member.deviceId)
    val now = System.currentTimeMillis()
    val toReview = lists.active.filter { it.effectiveState(now) == TaskState.DONE }
    var draft by remember { mutableStateOf<TaskTemplate?>(null) }

    DetailScaffold(title = "Задания: ${member.displayName}", onBack = onBack) { padding ->
        Column(
            Modifier.padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            RewardsHero(
                title = "Задания и награды",
                subtitle = "${member.displayName} выполняет, вы подтверждаете — минуты добавляются сами",
                rewards = lists.rewards,
            )

            if (toReview.isNotEmpty()) {
                SectionHeader("Ждут проверки")
                toReview.forEach { task ->
                    ReviewCard(
                        task,
                        onAccept = { scope.launch { graph.guardian.reviewTask(task.id, approved = true) } },
                        onReturn = { scope.launch { graph.guardian.reviewTask(task.id, approved = false) } },
                    )
                }
            }

            SectionHeader("Задания")
            if (lists.active.isEmpty()) {
                SoftCard {
                    ArtEmptyState(
                        title = "Пока нет заданий",
                        text = "Выберите готовое задание ниже — оно сразу появится на телефоне ребёнка.",
                    ) { Illustration(Art3D.Target, size = 96.dp) }
                }
            } else {
                SoftCard(padding = 12.dp) {
                    lists.active.forEach { task ->
                        TaskRow(task, task.effectiveState(now), trailing = {
                            IconButton(onClick = { scope.launch { graph.guardian.deleteTask(task.id) } }) {
                                Icon(Icons.Rounded.DeleteOutline, contentDescription = "Удалить задание", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        })
                    }
                }
            }

            SectionHeader("Добавить задание")
            TemplateGrid(onPick = { draft = it })
            Spacer(Modifier.height(16.dp))
        }
    }

    draft?.let { template ->
        NewTaskDialog(
            template = template,
            onDismiss = { draft = null },
            onCreate = { title, minutes, repeat ->
                draft = null
                scope.launch { graph.guardian.createTask(member.deviceId, title, template.kind, minutes, template.points, repeat) }
            },
        )
    }
}

@Composable
private fun ReviewCard(task: FamilyTask, onAccept: () -> Unit, onReturn: () -> Unit) {
    val visual = taskVisual(task.kind)
    SoftCard(padding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            IconTile(visual.icon, visual.gradient, size = 46.dp)
            Column(Modifier.weight(1f)) {
                Text(task.title, style = MaterialTheme.typography.titleMedium)
                Text("Отмечено как выполненное", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton(
                if (task.rewardMinutes > 0) "Принять +${task.rewardMinutes} мин" else "Принять",
                modifier = Modifier.weight(1.5f), onClick = onAccept,
            )
            PillButton(
                "Вернуть", modifier = Modifier.weight(1f), icon = Icons.Rounded.Close,
                fill = tintedFill(), contentColor = MaterialTheme.colorScheme.onSurface, elevated = false, onClick = onReturn,
            )
        }
    }
}

@Composable
private fun TemplateGrid(onPick: (TaskTemplate) -> Unit) {
    val custom = TaskTemplate("custom", "", 15, 10, TaskRepeat.ONCE)
    val items = TaskTemplates.all + custom
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { t -> TemplateCard(t, Modifier.weight(1f)) { onPick(t) } }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun TemplateCard(t: TaskTemplate, modifier: Modifier, onClick: () -> Unit) {
    val custom = t.kind == "custom"
    val visual = if (custom) Visual(Icons.Rounded.Add, Gradients.Slate) else taskVisual(t.kind)
    SoftCard(modifier, onClick = onClick, padding = 14.dp) {
        IconTile(visual.icon, visual.gradient, size = 40.dp)
        Text(
            if (custom) "Своё задание" else t.title, style = MaterialTheme.typography.titleSmall,
            maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.height(40.dp),
        )
        Text(
            if (custom) "Придумайте сами" else "+${t.rewardMinutes} мин · ${repeatLabel(t.repeat).lowercase()}",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

private val rewardOptions = listOf(5, 10, 15, 20, 30, 45, 60)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NewTaskDialog(template: TaskTemplate, onDismiss: () -> Unit, onCreate: (String, Int, TaskRepeat) -> Unit) {
    var title by remember { mutableStateOf(template.title) }
    var minutes by remember { mutableIntStateOf(template.rewardMinutes) }
    var repeat by remember { mutableStateOf(template.repeat) }
    val visual = if (template.kind == "custom") Visual(Icons.Rounded.TaskAlt, Gradients.Emerald) else taskVisual(template.kind)
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { IconTile(visual.icon, visual.gradient, size = 52.dp) },
        title = { Text("Новое задание", textAlign = TextAlign.Center) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(title, { title = it.take(80) }, label = { Text("Что нужно сделать") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("Награда", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    rewardOptions.forEach { m -> ChoiceChip("+$m мин", minutes == m) { minutes = m } }
                }
                Text("Как часто", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TaskRepeat.entries.forEach { r -> ChoiceChip(repeatLabel(r), repeat == r) { repeat = r } }
                }
            }
        },
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surface,
        confirmButton = {
            TextButton(onClick = { onCreate(title, minutes, repeat) }, enabled = title.isNotBlank()) { Text("Добавить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

// ---- Shared pieces ----------------------------------------------------------------------------------

@Composable
private fun RewardsHero(title: String, subtitle: String, rewards: TaskRewards) {
    HeroCard(
        title = title,
        subtitle = subtitle,
        gradient = Gradients.Sunrise,
        art = { Illustration(Art3D.Trophy, size = 124.dp) },
        footer = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                HeroChip(rewards.level.title, Icons.Rounded.Stars)
                HeroChip("${rewards.points} ${plural(rewards.points, "балл", "балла", "баллов")}")
            }
            if (rewards.minutes > 0) {
                Spacer(Modifier.height(6.dp))
                HeroChip("Заработано ${formatMinutes(rewards.minutes)}", Icons.Rounded.MoreTime)
            }
        },
    )
}

@Composable
private fun TaskRow(task: FamilyTask, state: TaskState, trailing: (@Composable () -> Unit)? = null) {
    val visual = taskVisual(task.kind)
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconTile(visual.icon, visual.gradient, size = 42.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(task.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                "${repeatLabel(task.repeat)} · +${task.rewardMinutes} мин · ${task.points} ${plural(task.points, "балл", "балла", "баллов")}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            StateChip(state)
        }
        trailing?.invoke()
    }
}

@Composable
private fun StateChip(state: TaskState) {
    when (state) {
        TaskState.OPEN -> LabelChip("Ждёт выполнения", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant, icon = Icons.Rounded.HourglassTop)
        TaskState.DONE -> LabelChip("Выполнено, ждёт проверки", Brand.WarningLight, Brand.Warning, icon = Icons.Rounded.TaskAlt)
        TaskState.APPROVED -> LabelChip("Принято", Brand.GreenLight, Brand.GreenDark, icon = Icons.Rounded.Check)
        TaskState.DECLINED -> LabelChip("Вернули — попробуй ещё", Brand.DangerLight, Brand.Danger, icon = Icons.Rounded.Repeat)
    }
}

// ---- Child ------------------------------------------------------------------------------------------

/** The child's tasks: mark done, see what is waiting for a check and what was earned. */
@Composable
fun ChildTasksScreen(graph: AppGraph, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val me = graph.settings.current.deviceId.orEmpty()
    val lists = rememberTasks(graph, me)
    val now = System.currentTimeMillis()
    val rewards = lists.rewards

    DetailScaffold(title = "Мои задания", onBack = onBack) { padding ->
        Column(
            Modifier.padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            RewardsHero(
                title = "Уровень: ${rewards.level.title}",
                subtitle = rewards.next?.let { "Ещё ${it.minPoints - rewards.points} ${plural(it.minPoints - rewards.points, "балл", "балла", "баллов")} до уровня «${it.title}»" }
                    ?: "Самый высокий уровень — ты супер!",
                rewards = rewards,
            )

            if (lists.active.isEmpty()) {
                SoftCard {
                    ArtEmptyState(
                        title = "Заданий пока нет",
                        text = "Попроси родителей добавить задание: за выполненное задание можно получить дополнительные минуты.",
                    ) { Illustration(Art3D.GiftBox, size = 104.dp) }
                }
            } else {
                SectionHeader("Мои задания")
                lists.active.forEach { task ->
                    val state = task.effectiveState(now)
                    SoftCard(padding = 14.dp) {
                        TaskRow(task, state)
                        if (state == TaskState.OPEN || state == TaskState.DECLINED) {
                            PillButton("Готово!", modifier = Modifier.fillMaxWidth(), icon = Icons.Rounded.Check) {
                                scope.launch { graph.actions.markTaskDone(task.id) }
                            }
                        }
                    }
                }
            }

            SoftCard(padding = 16.dp) {
                Text("Как это работает", style = MaterialTheme.typography.titleMedium)
                HowStep(1, "Выполни задание")
                HowStep(2, "Нажми «Готово!»")
                HowStep(3, "Родители проверят — и минуты добавятся к твоему времени")
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun HowStep(n: Int, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(30.dp).clip(CircleShape).background(Gradients.Sunrise), contentAlignment = Alignment.Center) {
            Text("$n", style = MaterialTheme.typography.labelLarge, color = Color.White)
        }
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

/** Compact entry card for the child's home screen. */
@Composable
fun ChildTasksCard(graph: AppGraph, onOpen: () -> Unit) {
    val me = graph.settings.current.deviceId.orEmpty()
    val lists = rememberTasks(graph, me)
    val now = System.currentTimeMillis()
    val open = lists.active.count { it.effectiveState(now).let { s -> s == TaskState.OPEN || s == TaskState.DECLINED } }
    SoftCard(onClick = onOpen, padding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Illustration(Art3D.Trophy, size = 58.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    if (open > 0) "$open ${plural(open, "задание", "задания", "заданий")} на сегодня" else "Задания и награды",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    "${lists.rewards.level.title} · ${lists.rewards.points} ${plural(lists.rewards.points, "балл", "балла", "баллов")}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** One-line summary for a guardian's member screen: what waits for review, or the child's level. */
@Composable
fun taskSummary(graph: AppGraph, deviceId: String): String {
    val lists = rememberTasks(graph, deviceId)
    val now = System.currentTimeMillis()
    val review = lists.active.count { it.effectiveState(now) == TaskState.DONE }
    return when {
        review > 0 -> "$review ${plural(review, "задание ждёт", "задания ждут", "заданий ждут")} проверки"
        lists.active.isEmpty() -> "Минуты за уборку, уроки и спорт"
        else -> "${lists.rewards.level.title} · ${lists.rewards.points} ${plural(lists.rewards.points, "балл", "балла", "баллов")}"
    }
}
