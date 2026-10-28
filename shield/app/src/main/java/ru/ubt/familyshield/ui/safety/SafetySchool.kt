package ru.ubt.familyshield.ui.safety

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Quiz
import androidx.compose.material.icons.rounded.Report
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.ubt.familyshield.AppGraph
import ru.ubt.familyshield.R
import ru.ubt.familyshield.shared.content.SafetyLesson
import ru.ubt.familyshield.shared.content.SafetySchool
import ru.ubt.familyshield.shared.model.MemberRole
import ru.ubt.familyshield.shared.model.SafetyProgress
import ru.ubt.familyshield.ui.components.Art3D
import ru.ubt.familyshield.ui.components.ArtTile
import ru.ubt.familyshield.ui.components.HeroCard
import ru.ubt.familyshield.ui.components.IconTile
import ru.ubt.familyshield.ui.components.Illustration
import ru.ubt.familyshield.ui.components.LabelChip
import ru.ubt.familyshield.ui.components.LottieView
import ru.ubt.familyshield.ui.components.PrimaryButton
import ru.ubt.familyshield.ui.components.SecondaryButton
import ru.ubt.familyshield.ui.components.SectionHeader
import ru.ubt.familyshield.ui.components.SoftCard
import ru.ubt.familyshield.ui.guardian.DetailScaffold
import ru.ubt.familyshield.ui.guardian.HeroChip
import ru.ubt.familyshield.ui.guardian.PillButton
import ru.ubt.familyshield.ui.guardian.plural
import ru.ubt.familyshield.ui.theme.Brand
import ru.ubt.familyshield.ui.theme.Gradients
import kotlinx.coroutines.launch

/*
 * «Школа безопасности»: short lessons on the scams that hit Russian families most, each with red flags,
 * what to do and a three-question check. Best scores are kept and shared with the family.
 */

@androidx.annotation.DrawableRes
fun lessonArt(id: String): Int = when (id) {
    "bank_security" -> Art3D.CreditCard
    "gosuslugi" -> Art3D.FileText
    "officials" -> Art3D.Locker
    "relative_trouble" -> Art3D.CallRinging
    "remote_access" -> Art3D.Computer
    "pension_benefits" -> Art3D.Wallet
    "home_visit" -> Art3D.Tools
    "messenger_boss" -> Art3D.ChatBubble
    "investments" -> Art3D.Chart
    "phishing_links" -> Art3D.Link
    "deepfake" -> Art3D.Mic
    "marketplace" -> Art3D.Bag
    else -> Art3D.Notebook
}

/** Lessons that suit [role]: elders get the phone and money schemes, teens the online ones, guardians all. */
fun lessonsFor(role: MemberRole?): List<SafetyLesson> = when (role) {
    MemberRole.ELDER -> SafetySchool.lessons.filter { it.forElders }
    MemberRole.CHILD -> SafetySchool.lessons.filter { it.forTeens }
    else -> SafetySchool.lessons
}

private sealed interface SchoolPage {
    data object List : SchoolPage
    data class Lesson(val id: String) : SchoolPage
    data class Quiz(val id: String) : SchoolPage
}

/** [initialLesson] opens one lesson directly (from an event or a seasonal banner); back then leaves the school. */
@Composable
fun SafetySchoolScreen(graph: AppGraph, initialLesson: String? = null, onBack: () -> Unit) {
    val settings by graph.settings.state.collectAsState()
    val lessons = remember(settings.role) { lessonsFor(settings.role) }
    var page by remember { mutableStateOf<SchoolPage>(initialLesson?.let { SchoolPage.Lesson(it) } ?: SchoolPage.List) }
    when (val p = page) {
        SchoolPage.List -> SchoolList(lessons, settings.learning, onBack) { page = SchoolPage.Lesson(it) }
        is SchoolPage.Lesson -> SafetySchool.byId(p.id)?.let { lesson ->
            LessonScreen(
                lesson, settings.learning.lessons[lesson.id],
                onBack = { if (p.id == initialLesson) onBack() else page = SchoolPage.List },
            ) { page = SchoolPage.Quiz(lesson.id) }
        }
        is SchoolPage.Quiz -> SafetySchool.byId(p.id)?.let { lesson ->
            QuizScreen(graph, lesson, onBack = { page = SchoolPage.Lesson(lesson.id) }, onDone = { page = SchoolPage.List })
        }
    }
}

@Composable
private fun SchoolList(lessons: List<SafetyLesson>, progress: SafetyProgress, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val passed = lessons.count { progress.passed(it.id) }
    val next = lessons.firstOrNull { !progress.passed(it.id) }
    DetailScaffold(title = "Школа безопасности", onBack = onBack) { padding ->
        Column(
            Modifier.padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            HeroCard(
                title = "Как не попасться мошенникам",
                subtitle = "Короткие уроки по 3 минуты и проверка из трёх вопросов",
                gradient = Gradients.Violet,
                art = { Illustration(Art3D.Notebook, size = 120.dp) },
                footer = {
                    LinearProgressIndicator(
                        progress = { if (lessons.isEmpty()) 0f else passed / lessons.size.toFloat() },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                        color = Color.White, trackColor = Color.White.copy(alpha = 0.25f),
                    )
                    Spacer(Modifier.height(6.dp))
                    HeroChip("Пройдено $passed из ${lessons.size}", Icons.Rounded.CheckCircle)
                },
            )

            if (next != null) {
                SectionHeader(if (passed == 0) "Начните с главного" else "Следующий урок")
                SoftCard(onClick = { onOpen(next.id) }, padding = 16.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        ArtTile(lessonArt(next.id), size = 84.dp)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(next.title, style = MaterialTheme.typography.titleMedium)
                            Text(next.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    PillButton("Начать урок", modifier = Modifier.fillMaxWidth(), icon = Icons.AutoMirrored.Rounded.ArrowForward, fill = Gradients.Violet) { onOpen(next.id) }
                }
            }

            SectionHeader("Все уроки")
            lessons.forEach { lesson -> LessonRow(lesson, progress.lessons[lesson.id]) { onOpen(lesson.id) } }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun LessonRow(lesson: SafetyLesson, best: Int?, onClick: () -> Unit) {
    SoftCard(onClick = onClick, padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            ArtTile(lessonArt(lesson.id), size = 60.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(lesson.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(lesson.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                ScoreChip(lesson, best)
            }
        }
    }
}

@Composable
private fun ScoreChip(lesson: SafetyLesson, best: Int?) {
    when {
        best != null && best >= 2 -> LabelChip("Пройден · $best из ${lesson.quiz.size}", Brand.GreenLight, Brand.GreenDark, icon = Icons.Rounded.CheckCircle)
        best != null -> LabelChip("Попробуйте ещё раз · $best из ${lesson.quiz.size}", Brand.WarningLight, Brand.Warning, icon = Icons.Rounded.Quiz)
        else -> LabelChip("${lesson.minutes} мин", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant, icon = Icons.Rounded.Schedule)
    }
}

@Composable
private fun LessonScreen(lesson: SafetyLesson, best: Int?, onBack: () -> Unit, onQuiz: () -> Unit) {
    DetailScaffold(title = null, onBack = onBack) { padding ->
        Column(
            Modifier.padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.extraLarge).background(Gradients.Mint).padding(vertical = 20.dp),
                contentAlignment = Alignment.Center,
            ) { Illustration(lessonArt(lesson.id), size = 150.dp) }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = 4.dp)) {
                Text(lesson.title, style = MaterialTheme.typography.headlineMedium)
                ScoreChip(lesson, best)
            }
            SoftCard(padding = 18.dp) {
                Text("Как это происходит", style = MaterialTheme.typography.titleMedium)
                Text(lesson.story, style = MaterialTheme.typography.bodyLarge)
            }
            SoftCard(padding = 18.dp) {
                Text("Признаки обмана", style = MaterialTheme.typography.titleMedium)
                lesson.redFlags.forEach { BulletLine(it, Icons.Rounded.Report, Gradients.Danger) }
            }
            SoftCard(padding = 18.dp) {
                Text("Что делать", style = MaterialTheme.typography.titleMedium)
                lesson.whatToDo.forEach { BulletLine(it, Icons.Rounded.Check, Gradients.Emerald) }
            }
            PrimaryButton("Проверить себя — ${lesson.quiz.size} ${plural(lesson.quiz.size, "вопрос", "вопроса", "вопросов")}", onClick = onQuiz)
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun BulletLine(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, gradient: androidx.compose.ui.graphics.Brush) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        IconTile(icon, gradient, size = 30.dp)
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(top = 3.dp))
    }
}

@Composable
private fun QuizScreen(graph: AppGraph, lesson: SafetyLesson, onBack: () -> Unit, onDone: () -> Unit) {
    var index by remember { mutableIntStateOf(0) }
    var answer by remember { mutableStateOf<Int?>(null) }
    var score by remember { mutableIntStateOf(0) }
    var finished by remember { mutableStateOf(false) }

    if (finished) {
        QuizResult(lesson, score, onDone = onDone, onRetry = {
            index = 0; answer = null; score = 0; finished = false
        })
        return
    }

    val q = lesson.quiz[index]
    DetailScaffold(title = lesson.title, onBack = onBack) { padding ->
        Column(
            Modifier.padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                lesson.quiz.indices.forEach { i ->
                    Box(
                        Modifier.weight(1f).height(6.dp).clip(CircleShape)
                            .background(if (i <= index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                    )
                }
            }
            Text("Вопрос ${index + 1} из ${lesson.quiz.size}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(q.question, style = MaterialTheme.typography.titleLarge)

            q.options.forEachIndexed { i, option ->
                OptionCard(
                    letter = "АБВГ".getOrElse(i) { 'А' + i }.toString(),
                    text = option,
                    state = when {
                        answer == null -> OptionState.IDLE
                        i == q.correct -> OptionState.CORRECT
                        i == answer -> OptionState.WRONG
                        else -> OptionState.DIMMED
                    },
                ) {
                    if (answer == null) {
                        answer = i
                        if (i == q.correct) score++
                    }
                }
            }

            answer?.let { a ->
                Row(
                    Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large)
                        .background(if (a == q.correct) Brand.GreenLight else Brand.WarningLight).padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Illustration(Art3D.Bulb, size = 44.dp)
                    Column(Modifier.weight(1f)) {
                        Text(if (a == q.correct) "Верно!" else "Не совсем", style = MaterialTheme.typography.titleSmall, color = Brand.Ink)
                        Text(q.explanation, style = MaterialTheme.typography.bodyMedium, color = Brand.Ink)
                    }
                }
                PrimaryButton(if (index + 1 < lesson.quiz.size) "Дальше" else "Узнать результат") {
                    if (index + 1 < lesson.quiz.size) {
                        index++
                        answer = null
                    } else {
                        finished = true
                        val result = score
                        // App scope: the score must be saved and shared even if the screen is closed right away.
                        graph.appScope.launch { graph.actions.recordLesson(lesson.id, result) }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

private enum class OptionState { IDLE, CORRECT, WRONG, DIMMED }

@Composable
private fun OptionCard(letter: String, text: String, state: OptionState, onClick: () -> Unit) {
    val border by animateColorAsState(
        when (state) {
            OptionState.CORRECT -> Brand.Green
            OptionState.WRONG -> Brand.Danger
            else -> MaterialTheme.colorScheme.outlineVariant
        },
        label = "optionBorder",
    )
    val shape = MaterialTheme.shapes.large
    Row(
        Modifier.fillMaxWidth().clip(shape).background(MaterialTheme.colorScheme.surface)
            .border(if (state == OptionState.CORRECT || state == OptionState.WRONG) 2.dp else 1.dp, border, shape)
            .clickable(role = Role.Button, enabled = state == OptionState.IDLE, onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val (bg, glyph) = when (state) {
            OptionState.CORRECT -> Gradients.Emerald to Icons.Rounded.Check
            OptionState.WRONG -> Gradients.Danger to Icons.Rounded.Close
            else -> null to null
        }
        Box(
            Modifier.size(34.dp).clip(RoundedCornerShape(11.dp))
                .background(bg ?: androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.surfaceVariant)),
            contentAlignment = Alignment.Center,
        ) {
            if (glyph != null) Icon(glyph, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            else Text(letter, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f),
            color = if (state == OptionState.DIMMED) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun QuizResult(lesson: SafetyLesson, score: Int, onDone: () -> Unit, onRetry: () -> Unit) {
    val passed = score >= 2
    DetailScaffold(title = null, onBack = onDone) { padding ->
        Column(
            Modifier.padding(padding).padding(horizontal = 24.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(Modifier.size(240.dp), contentAlignment = Alignment.Center) {
                Illustration(if (passed) Art3D.Medal else Art3D.Bulb, size = 170.dp)
                if (passed) LottieView(R.raw.lottie_confetti, size = 240.dp, loop = false)
            }
            Text(
                if (passed) "Отлично! $score из ${lesson.quiz.size}" else "Почти! $score из ${lesson.quiz.size}",
                style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center,
            )
            Text(
                if (passed) "Урок «${lesson.title}» пройден. Близкие увидят ваш прогресс — и будут спокойнее."
                else "Перечитайте признаки обмана и попробуйте ещё раз — это займёт минуту.",
                style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            PrimaryButton("К урокам", onClick = onDone)
            SecondaryButton("Пройти ещё раз", onClick = onRetry)
        }
    }
}
