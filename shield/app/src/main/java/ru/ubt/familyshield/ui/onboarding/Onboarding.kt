package ru.ubt.familyshield.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import ru.ubt.familyshield.AppGraph
import ru.ubt.familyshield.shared.model.MemberRole
import ru.ubt.familyshield.ui.components.PrimaryButton
import ru.ubt.familyshield.ui.components.SectionCard
import ru.ubt.familyshield.ui.setup.PermissionWizard

enum class OnboardingStep { WELCOME, ROLE, GUARDIAN, CONSENT, JOIN, PERMISSIONS }

/** Drives the first-run flow. The joining branches hand off to [PermissionWizard] for protected roles. */
@Composable
fun OnboardingFlow(graph: AppGraph, pairingLink: String?, onDone: () -> Unit) {
    var step by remember { mutableStateOf(if (pairingLink != null) OnboardingStep.JOIN else OnboardingStep.WELCOME) }
    var chosenRole by remember { mutableStateOf<MemberRole?>(null) }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding()) {
        AnimatedContent(targetState = step, label = "onboarding") { s ->
            when (s) {
                OnboardingStep.WELCOME -> WelcomeSlides { step = OnboardingStep.ROLE }
                OnboardingStep.ROLE -> RoleChoice { role ->
                    chosenRole = role
                    step = when (role) {
                        MemberRole.GUARDIAN -> OnboardingStep.GUARDIAN
                        null -> OnboardingStep.JOIN
                        else -> OnboardingStep.CONSENT
                    }
                }
                OnboardingStep.GUARDIAN -> GuardianSetup(graph, onBack = { step = OnboardingStep.ROLE }, onDone = onDone)
                OnboardingStep.CONSENT -> ConsentScreen(
                    chosenRole ?: MemberRole.ELDER,
                    onBack = { step = OnboardingStep.ROLE },
                    onContinue = { step = OnboardingStep.JOIN },
                )
                OnboardingStep.JOIN -> JoinFamily(graph, pairingLink, onBack = { step = OnboardingStep.ROLE }) { role ->
                    if (role == MemberRole.GUARDIAN) onDone() else step = OnboardingStep.PERMISSIONS
                }
                OnboardingStep.PERMISSIONS -> PermissionWizard(graph, onboarding = true, onBack = null, onDone = onDone)
            }
        }
    }
}

private data class Slide(val emoji: String, val title: String, val text: String)

@Composable
private fun WelcomeSlides(onNext: () -> Unit) {
    val slides = listOf(
        Slide("🛡️", "Защита всей семьи в одном приложении", "Пожилым родителям — спокойствие при звонках незнакомцев. Детям — безопасный интернет и разумные правила."),
        Slide("📞", "Семья рядом в трудный момент", "Приложение подсказывает, когда звонок или сообщение похожи на обман, и помогает не потерять деньги."),
        Slide("👧", "Поддержка, а не слежка", "Родители видят главное: сколько времени в телефоне, где ребёнок, нет ли угроз. Личная переписка остаётся личной."),
        Slide("🔐", "Данные видит только ваша семья", "Всё шифруется прямо на телефонах. Даже наш сервер не может прочитать ваши сообщения и данные."),
    )
    val pager = rememberPagerState { slides.size }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { page ->
            val s = slides[page]
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Box(Modifier.size(140.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                    Text(s.emoji, fontSize = 72.sp)
                }
                Spacer(Modifier.height(32.dp))
                Text(s.title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                Text(s.text, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.Center) {
            repeat(slides.size) { i ->
                Box(
                    Modifier.padding(4.dp).size(if (i == pager.currentPage) 12.dp else 8.dp).clip(CircleShape)
                        .background(if (i == pager.currentPage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        PrimaryButton(if (pager.currentPage == slides.lastIndex) "Начать" else "Далее") {
            if (pager.currentPage == slides.lastIndex) onNext() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
        }
        TextButton(onClick = onNext, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Пропустить") }
    }
}

@Composable
private fun RoleChoice(onChoose: (MemberRole?) -> Unit) {
    Column(Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Spacer(Modifier.height(12.dp))
        Text("Чей это телефон?", style = MaterialTheme.typography.headlineMedium)
        Text("От этого зависит, как будет работать приложение.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        roleCard("🧑‍💼", "Мой — я забочусь о семье", "Настрою защиту для близких и буду получать оповещения") { onChoose(MemberRole.GUARDIAN) }
        roleCard("👵", "Телефон пожилого родственника", "Защита от мошенников, крупные кнопки, SOS и напоминания") { onChoose(MemberRole.ELDER) }
        roleCard("🧒", "Телефон ребёнка или подростка", "Экранное время, безопасный интернет, геолокация и SOS") { onChoose(MemberRole.CHILD) }
        roleCard("🔗", "У меня есть код приглашения", "Подключить этот телефон к уже созданной семье") { onChoose(null) }
    }
}

@Composable
private fun roleCard(emoji: String, title: String, text: String, onClick: () -> Unit) {
    SectionCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(emoji, fontSize = 40.sp)
            Column(Modifier) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
