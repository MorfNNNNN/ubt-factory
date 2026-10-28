package ru.ubt.familyshield.ui.onboarding

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PhoneDisabled
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.ubt.familyshield.AppGraph
import ru.ubt.familyshield.R
import ru.ubt.familyshield.shared.model.MemberRole
import ru.ubt.familyshield.ui.components.Art3D
import ru.ubt.familyshield.ui.components.AvatarArt
import ru.ubt.familyshield.ui.components.AvatarImage
import ru.ubt.familyshield.ui.components.CroppedIllustration
import ru.ubt.familyshield.ui.components.IconTile
import ru.ubt.familyshield.ui.components.Illustration
import ru.ubt.familyshield.ui.components.LottieWithFallback
import ru.ubt.familyshield.ui.components.MintPanel
import ru.ubt.familyshield.ui.components.PageDots
import ru.ubt.familyshield.ui.components.PrimaryButton
import ru.ubt.familyshield.ui.components.visual
import ru.ubt.familyshield.ui.setup.PermissionWizard
import ru.ubt.familyshield.ui.theme.Brand
import ru.ubt.familyshield.ui.theme.Gradients
import kotlin.math.cos
import kotlin.math.sin

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

private data class Slide(val title: String, val text: String)

@Composable
private fun WelcomeSlides(onNext: () -> Unit) {
    val slides = listOf(
        Slide("Защита всей семьи в одном приложении", "Пожилым родителям — спокойствие при звонках незнакомцев. Детям — безопасный интернет и разумные правила."),
        Slide("Семья рядом в трудный момент", "Приложение подсказывает, когда звонок или сообщение похожи на обман, и помогает не потерять деньги."),
        Slide("Поддержка, а не слежка", "Родители видят главное: сколько времени в телефоне, где ребёнок, нет ли угроз. Личная переписка остаётся личной."),
        Slide("Данные видит только ваша семья", "Всё шифруется прямо на телефонах. Даже наш сервер не может прочитать ваши сообщения и данные."),
    )
    val pager = rememberPagerState { slides.size }
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == slides.lastIndex
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp).padding(top = 6.dp, bottom = 18.dp)) {
        Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
            Illustration(R.drawable.ill_mark_shield, size = 30.dp)
            Spacer(Modifier.width(10.dp))
            Text("Семейный щит", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (!last) TextButton(onClick = onNext) { Text("Пропустить", style = MaterialTheme.typography.labelLarge) }
        }
        HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { page ->
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                MintPanel(Modifier.fillMaxWidth().weight(1f).padding(top = 6.dp)) { SlideArt(page) }
                Spacer(Modifier.height(26.dp))
                Text(slides[page].title, style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center)
                Spacer(Modifier.height(10.dp))
                Text(
                    slides[page].text, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 6.dp),
                )
            }
        }
        Spacer(Modifier.height(22.dp))
        PageDots(slides.size, pager.currentPage, Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(22.dp))
        PrimaryButton(if (last) "Начать" else "Далее") {
            if (last) onNext() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
        }
    }
}

@Composable
private fun BoxScope.SlideArt(page: Int) {
    when (page) {
        0 -> FamilyArt()
        1 -> {
            PortraitArt(AvatarArt.GRANDMA, Art3D.CallRinging, propOnLeft = false)
            FloatingNote(Icons.Rounded.PhoneDisabled, Gradients.Coral, "Звонок мошенника", "заблокирован", Modifier.align(Alignment.BottomCenter).padding(16.dp))
        }
        2 -> {
            PortraitArt(AvatarArt.GIRL, Art3D.Stopwatch, propOnLeft = true)
            FloatingNote(Icons.Rounded.HourglassBottom, Gradients.Sky, "Экранное время", "1 ч 35 мин из 2 ч", Modifier.align(Alignment.BottomCenter).padding(16.dp))
        }
        else -> {
            BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val s = minOf(maxWidth, maxHeight)
                Box(Modifier.size(s * 0.66f).clip(CircleShape).background(Color.White.copy(alpha = 0.7f)))
                LottieWithFallback(R.raw.lottie_lock_secure, s * 0.72f, loop = false) {
                    Icon(Icons.Rounded.Lock, contentDescription = null, tint = Brand.Green, modifier = Modifier.size(s * 0.26f))
                }
            }
            FloatingNote(Icons.Rounded.Key, Gradients.Emerald, "Ключи шифрования", "только у вашей семьи", Modifier.align(Alignment.BottomCenter).padding(16.dp))
        }
    }
}

/** The brand mark with the family around it. */
@Composable
private fun BoxScope.FamilyArt() {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val s = minOf(maxWidth, maxHeight)
        Box(Modifier.size(s * 0.8f).clip(CircleShape).background(Color.White.copy(alpha = 0.45f)))
        Box(Modifier.size(s * 0.56f).clip(CircleShape).background(Color.White.copy(alpha = 0.6f)))
        Illustration(R.drawable.ill_mark_shield, size = s * 0.44f)
        val r = s * 0.4f
        listOf(
            Triple(AvatarArt.GRANDMA, -145.0, 0.25f),
            Triple(AvatarArt.GIRL, -35.0, 0.22f),
            Triple(AvatarArt.DAD, 145.0, 0.21f),
            Triple(AvatarArt.MOM, 35.0, 0.24f),
        ).forEach { (art, angle, scale) ->
            val rad = Math.toRadians(angle)
            AvatarImage(
                art.value, size = s * scale, ring = Color.White,
                modifier = Modifier.offset(x = r * cos(rad).toFloat(), y = r * sin(rad).toFloat()),
            )
        }
    }
}

/** A large family portrait on soft halos with a 3D object beside it. */
@Composable
private fun BoxScope.PortraitArt(portrait: AvatarArt, @DrawableRes prop: Int, propOnLeft: Boolean) {
    BoxWithConstraints(Modifier.fillMaxSize().padding(bottom = 40.dp), contentAlignment = Alignment.Center) {
        val s = minOf(maxWidth, maxHeight)
        Box(Modifier.size(s * 0.86f).clip(CircleShape).background(Color.White.copy(alpha = 0.45f)))
        Box(Modifier.size(s * 0.66f).clip(CircleShape).background(Color.White.copy(alpha = 0.6f)))
        AvatarImage(portrait.value, size = s * 0.56f, ring = Color.White)
        Illustration(
            prop,
            Modifier.offset(x = if (propOnLeft) -s * 0.3f else s * 0.3f, y = -s * 0.24f),
            size = s * 0.3f,
        )
    }
}

/** A small white card that floats over the art, like a notification from the app. */
@Composable
private fun FloatingNote(icon: ImageVector, gradient: Brush, title: String, text: String, modifier: Modifier) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier.shadow(12.dp, shape, ambientColor = Brand.GreenDark.copy(alpha = 0.25f), spotColor = Brand.GreenDark.copy(alpha = 0.25f))
            .clip(shape).background(MaterialTheme.colorScheme.surface).padding(start = 10.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        IconTile(icon, gradient, size = 38.dp)
        Column {
            Text(title, style = MaterialTheme.typography.labelLarge)
            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun RoleChoice(onChoose: (MemberRole?) -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 22.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Чей это телефон?", style = MaterialTheme.typography.headlineLarge)
        Text("От этого зависит, как будет работать приложение.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(2.dp))
        RoleCard(MemberRole.GUARDIAN, "Мой — я забочусь о семье", "Настрою защиту для близких и буду получать оповещения", { onChoose(MemberRole.GUARDIAN) }) {
            Illustration(R.drawable.ill_mark_shield, Modifier.align(Alignment.Center).padding(top = 8.dp), size = 92.dp)
        }
        RoleCard(MemberRole.ELDER, "Телефон пожилого родственника", "Защита от мошенников, крупные кнопки, SOS и напоминания", { onChoose(MemberRole.ELDER) }) {
            PortraitPair(AvatarArt.GRANDMA, AvatarArt.GRANDPA)
        }
        RoleCard(MemberRole.CHILD, "Телефон ребёнка или подростка", "Экранное время, безопасный интернет, геолокация и SOS", { onChoose(MemberRole.CHILD) }) {
            PortraitPair(AvatarArt.GIRL, AvatarArt.BOY)
        }
        Row(
            Modifier.fillMaxWidth()
                .shadow(8.dp, MaterialTheme.shapes.large, ambientColor = Brand.Green.copy(alpha = 0.12f), spotColor = Brand.Green.copy(alpha = 0.12f))
                .clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surface)
                .clickable(role = Role.Button) { onChoose(null) }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            IconTile(Icons.Rounded.QrCodeScanner, Gradients.Slate, size = 46.dp)
            Column(Modifier.weight(1f)) {
                Text("У меня есть код приглашения", style = MaterialTheme.typography.titleMedium)
                Text("Подключить этот телефон к уже созданной семье", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A large role card: text on the left, the role's art standing in a mint arch on the right. */
@Composable
private fun RoleCard(role: MemberRole, title: String, text: String, onClick: () -> Unit, art: @Composable BoxScope.() -> Unit) {
    val v = role.visual()
    val shape = MaterialTheme.shapes.large
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min)
            .shadow(10.dp, shape, ambientColor = Brand.Green.copy(alpha = 0.16f), spotColor = Brand.Green.copy(alpha = 0.16f))
            .clip(shape).background(MaterialTheme.colorScheme.surface)
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        Column(
            Modifier.weight(1f).heightIn(min = 148.dp).padding(start = 18.dp, top = 18.dp, bottom = 18.dp, end = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconTile(v.icon, v.gradient, size = 40.dp)
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(Modifier.width(RoleArtWidth).fillMaxHeight()) {
            Box(
                Modifier.align(Alignment.BottomCenter).offset(y = 26.dp).size(RoleArtWidth + 8.dp)
                    .clip(CircleShape).background(Gradients.Mint),
            )
            art()
        }
    }
}

private val RoleArtWidth: Dp = 122.dp

/** Two overlapping portraits standing in a role card's mint arch. */
@Composable
private fun BoxScope.PortraitPair(back: AvatarArt, front: AvatarArt) {
    AvatarImage(back.value, size = 62.dp, ring = Color.White, modifier = Modifier.align(Alignment.Center).offset(x = 22.dp, y = (-26).dp))
    AvatarImage(front.value, size = 70.dp, ring = Color.White, modifier = Modifier.align(Alignment.Center).offset(x = (-14).dp, y = 24.dp))
}
