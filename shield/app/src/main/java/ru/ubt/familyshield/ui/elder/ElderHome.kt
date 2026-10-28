package ru.ubt.familyshield.ui.elder

import androidx.annotation.DrawableRes
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.GppGood
import androidx.compose.material.icons.rounded.GppMaybe
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.MedicalServices
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Policy
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import ru.ubt.familyshield.AppGraph
import ru.ubt.familyshield.protect.ui.SosActivity
import ru.ubt.familyshield.shared.content.GosProtections
import ru.ubt.familyshield.shared.model.MedicationReminder
import ru.ubt.familyshield.shared.model.MemberRole
import ru.ubt.familyshield.shared.model.SafetyProgress
import ru.ubt.familyshield.shared.model.TrustedContact
import ru.ubt.familyshield.shared.policy.formatMinuteOfDay
import ru.ubt.familyshield.ui.FamilyUiState
import ru.ubt.familyshield.ui.ShieldViewModel
import ru.ubt.familyshield.ui.calendar.MyDayCard
import ru.ubt.familyshield.ui.components.Art3D
import ru.ubt.familyshield.ui.components.ArtTile
import ru.ubt.familyshield.ui.components.AvatarImage
import ru.ubt.familyshield.ui.components.ColumnScreen
import ru.ubt.familyshield.ui.components.GlassIcon
import ru.ubt.familyshield.ui.components.HeartBadge
import ru.ubt.familyshield.ui.components.IconTile
import ru.ubt.familyshield.ui.components.NoticeCard
import ru.ubt.familyshield.ui.components.ScreenScaffold
import ru.ubt.familyshield.ui.components.SoftCard
import ru.ubt.familyshield.ui.components.SosPulse
import ru.ubt.familyshield.ui.components.timeOf
import ru.ubt.familyshield.ui.safety.GosProtectionsScreen
import ru.ubt.familyshield.ui.safety.SafetySchoolScreen
import ru.ubt.familyshield.ui.safety.lessonsFor
import ru.ubt.familyshield.ui.setup.PermissionWizard
import ru.ubt.familyshield.ui.theme.Brand
import ru.ubt.familyshield.ui.theme.Gradients
import ru.ubt.familyshield.ui.tools.CheckerScreen
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

private class ElderApp(val title: String, val icon: ImageVector, val gradient: Brush, val subtitle: String? = null, val onClick: () -> Unit)

/** The elderly relative's own home: large, calm, few choices. Doubles as a launcher in Simple Mode. */
@Composable
fun ElderHome(graph: AppGraph, vm: ShieldViewModel, state: FamilyUiState, activity: ComponentActivity) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var screen by remember { mutableStateOf("home") }
    var contacts by remember { mutableStateOf<List<TrustedContact>>(emptyList()) }
    LaunchedEffect(Unit) { contacts = graph.protection.sos.contacts() }

    when (screen) {
        "checker" -> { CheckerScreen(graph) { screen = "home" }; return }
        "permissions" -> { PermissionWizard(graph, onboarding = false, onBack = { screen = "home" }) { screen = "home" }; return }
        "settings" -> { ElderSettings(graph, state) { screen = "home" }; return }
        "medications" -> { ElderMedications(state.settings.elderPolicy.medications) { screen = "home" }; return }
        "school" -> { SafetySchoolScreen(graph) { screen = "home" }; return }
        "gos" -> { GosProtectionsScreen(graph) { screen = "home" }; return }
        "medical" -> { MedicalCardScreen(state.settings.displayName, state.settings.elderPolicy.medical) { screen = "home" }; return }
    }

    val health = remember(state.settings) { graph.protection.health.snapshot(MemberRole.ELDER, state.settings.childPolicy, state.settings.elderPolicy) }
    val family = contacts.firstOrNull()
    val medications = state.settings.elderPolicy.medications

    fun dial(phone: String?) = phone?.let { graph.protection.sos.call(it) }
    fun start(intent: Intent) = runCatching { context.startActivity(intent) }

    val apps = buildList {
        add(ElderApp("Телефон", Icons.Rounded.Call, Gradients.Sky) { start(Intent(Intent.ACTION_DIAL)) })
        add(ElderApp("Сообщения", Icons.AutoMirrored.Rounded.Chat, Gradients.Violet) { start(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MESSAGING)) })
        add(ElderApp("Камера", Icons.Rounded.PhotoCamera, Gradients.Night) { start(Intent("android.media.action.STILL_IMAGE_CAMERA")) })
        add(ElderApp("Фотографии", Icons.Rounded.PhotoLibrary, Gradients.Ocean) { start(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_GALLERY)) })
        add(ElderApp("Карты", Icons.Rounded.Map, Gradients.Emerald) { start(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("geo:0,0?q="))) })
        if (medications.isNotEmpty()) add(ElderApp("Лекарства", Icons.Rounded.Medication, Gradients.Sunrise, nextDose(medications)) { screen = "medications" })
        add(ElderApp("Настройки", Icons.Rounded.Settings, Gradients.Slate) { screen = "settings" })
    }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 14.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Greeting(state.settings.displayName, state.settings.avatar)
        ProtectionStatus(ok = health.criticalMissing.isEmpty()) { screen = "permissions" }
        SosTile { context.startActivity(Intent(context, SosActivity::class.java)) }
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CheckInTile(state.settings.lastCheckInAt, Modifier.weight(1f).fillMaxHeight()) { scope.launch { graph.checkIn() } }
            CallFamilyTile(family, Modifier.weight(1f).fillMaxHeight()) { dial(family?.phone) ?: run { screen = "settings" } }
        }
        MyDayCard(graph, large = true)
        AppRow(apps[0], apps[1])
        CheckerTile { screen = "checker" }
        LearnTile(Art3D.Notebook, "Уроки безопасности", schoolHint(state.settings.learning)) { screen = "school" }
        LearnTile(Art3D.Shield, "Госуслуги и банк", gosHint(state.settings.learning)) { screen = "gos" }
        if (!state.settings.elderPolicy.medical.isEmpty) MedicalTile { screen = "medical" }
        apps.drop(2).chunked(2).forEach { AppRow(it[0], it.getOrNull(1)) }
    }
}

private fun schoolHint(p: SafetyProgress): String {
    val lessons = lessonsFor(MemberRole.ELDER)
    val passed = lessons.count { p.passed(it.id) }
    return if (passed == 0) "Как распознать мошенника — по 3 минуты" else "Пройдено $passed из ${lessons.size}"
}

private fun gosHint(p: SafetyProgress): String {
    val done = GosProtections.steps.count { it.id in p.steps }
    return if (done == 0) "Запрет на кредиты и другие меры" else "Сделано $done из ${GosProtections.steps.size}"
}

/** «Медкарта для врачей»: opens the medical card in large type. */
@Composable
private fun MedicalTile(onClick: () -> Unit) {
    SoftCard(onClick = onClick, padding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            IconTile(Icons.Rounded.MedicalServices, Gradients.Danger, size = 68.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Медкарта для врачей", style = MaterialTheme.typography.titleLarge)
                Text("Группа крови, аллергии, лекарства", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(28.dp))
        }
    }
}

/** A wide card with 3D art: lessons and the official-protection checklist. */
@Composable
private fun LearnTile(@DrawableRes art: Int, title: String, subtitle: String, onClick: () -> Unit) {
    SoftCard(onClick = onClick, padding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            ArtTile(art, size = 68.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(28.dp))
        }
    }
}

private fun greetingFor(hour: Int) = when (hour) {
    in 5..11 -> "Доброе утро"
    in 12..16 -> "Добрый день"
    in 17..22 -> "Добрый вечер"
    else -> "Доброй ночи"
}

@Composable
private fun Greeting(name: String, avatar: String) {
    val hello = remember { greetingFor(LocalDateTime.now().hour) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        AvatarImage(avatar, MemberRole.ELDER.name, size = 68.dp, ring = Color.White)
        Column(Modifier.weight(1f)) {
            if (name.isBlank()) {
                Text("$hello!", style = MaterialTheme.typography.headlineLarge)
            } else {
                Text("$hello,", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(name, style = MaterialTheme.typography.headlineLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Calm when everything works; a tappable reminder when setup is unfinished. */
@Composable
private fun ProtectionStatus(ok: Boolean, onFix: () -> Unit) {
    val shape = MaterialTheme.shapes.large
    Row(
        Modifier.fillMaxWidth()
            .shadow(8.dp, shape, ambientColor = Brand.Green.copy(alpha = 0.12f), spotColor = Brand.Green.copy(alpha = 0.12f))
            .clip(shape)
            .background(if (ok) MaterialTheme.colorScheme.surface else Brand.WarningLight)
            .clickable(enabled = !ok, role = Role.Button, onClick = onFix)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (ok) IconTile(Icons.Rounded.GppGood, Gradients.Emerald, size = 50.dp) else IconTile(Icons.Rounded.GppMaybe, Gradients.Sunrise, size = 50.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                if (ok) "Телефон под защитой" else "Защита включена не полностью",
                style = MaterialTheme.typography.titleMedium,
                color = if (ok) MaterialTheme.colorScheme.onPrimaryContainer else Brand.Ink,
            )
            Text(
                if (ok) "Мошенники не пройдут, близкие рядом" else "Нажмите, чтобы закончить настройку",
                style = MaterialTheme.typography.bodyMedium,
                color = if (ok) MaterialTheme.colorScheme.onSurfaceVariant else Brand.Slate,
            )
        }
        if (!ok) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = Brand.Warning, modifier = Modifier.size(28.dp))
    }
}

@Composable
private fun SosTile(onClick: () -> Unit) {
    val shape = MaterialTheme.shapes.extraLarge
    Row(
        Modifier.fillMaxWidth().heightIn(min = 140.dp)
            .shadow(16.dp, shape, ambientColor = Brand.Danger.copy(alpha = 0.5f), spotColor = Brand.Danger.copy(alpha = 0.5f))
            .clip(shape).background(Gradients.Danger)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(end = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(132.dp), contentAlignment = Alignment.Center) { SosPulse(156.dp) }
        Column(Modifier.weight(1f).padding(vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Позвать на помощь", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = Color.White)
            Text("Близкие сразу узнают, где вы", style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.92f))
        }
    }
}

/** A big gradient button-tile; content is laid out top to bottom with white type. */
@Composable
private fun ElderTile(gradient: Brush, onClick: () -> Unit, modifier: Modifier = Modifier, watermark: ImageVector? = null, content: @Composable ColumnScope.() -> Unit) {
    val shape = MaterialTheme.shapes.large
    Box(
        modifier
            .shadow(10.dp, shape, ambientColor = Brand.Ink.copy(alpha = 0.2f), spotColor = Brand.Ink.copy(alpha = 0.2f))
            .clip(shape).background(gradient)
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        if (watermark != null) {
            Icon(
                watermark, contentDescription = null, tint = Color.White.copy(alpha = 0.12f),
                modifier = Modifier.align(Alignment.BottomEnd).offset(x = 22.dp, y = 22.dp).size(108.dp),
            )
        }
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

/** Tile caption that shrinks instead of breaking a word, so large font settings never mangle it. */
@Composable
private fun TileTitle(text: String, lines: Int = 1) {
    val style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold)
    Text(
        text, style = style, color = Color.White, maxLines = lines, softWrap = false,
        autoSize = TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = style.fontSize, stepSize = 0.5.sp),
    )
}

@Composable
private fun TileSubtitle(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.92f), maxLines = 2, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun CheckInTile(lastCheckInAt: Long, modifier: Modifier, onClick: () -> Unit) {
    val today = lastCheckInAt > 0 && Instant.ofEpochMilli(lastCheckInAt).atZone(ZoneId.systemDefault()).toLocalDate() == LocalDate.now()
    ElderTile(Gradients.Coral, onClick, modifier) {
        HeartBadge(62.dp)
        Spacer(Modifier.weight(1f))
        Column {
            TileTitle("Я в порядке")
            TileSubtitle(if (today) "Отмечено в ${timeOf(lastCheckInAt)}" else "Сообщить близким")
        }
    }
}

private val DEFAULT_PHOTO = TrustedContact("", "").photoEmoji

@Composable
private fun CallFamilyTile(contact: TrustedContact?, modifier: Modifier, onClick: () -> Unit) {
    ElderTile(Gradients.Emerald, onClick, modifier) {
        if (contact != null && contact.photoEmoji != DEFAULT_PHOTO) AvatarImage(contact.photoEmoji, size = 62.dp, ring = Color.White)
        else GlassIcon(Icons.Rounded.Call, size = 62.dp)
        Spacer(Modifier.weight(1f))
        Column {
            TileTitle("Позвонить\nродным", lines = 2)
            TileSubtitle(contact?.name ?: "Номер не добавлен")
        }
    }
}

@Composable
private fun AppRow(first: ElderApp, second: ElderApp?) {
    if (second == null) {
        WideTile(first.title, first.subtitle, first.icon, first.gradient, first.onClick)
        return
    }
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        AppTile(first, Modifier.weight(1f).fillMaxHeight())
        AppTile(second, Modifier.weight(1f).fillMaxHeight())
    }
}

@Composable
private fun AppTile(app: ElderApp, modifier: Modifier) {
    ElderTile(app.gradient, app.onClick, modifier.heightIn(min = 124.dp), watermark = app.icon) {
        GlassIcon(app.icon, size = 50.dp)
        Spacer(Modifier.weight(1f))
        Column {
            TileTitle(app.title)
            if (app.subtitle != null) TileSubtitle(app.subtitle)
        }
    }
}

@Composable
private fun WideTile(title: String, subtitle: String?, icon: ImageVector, gradient: Brush, onClick: () -> Unit) {
    ElderTile(gradient, onClick, Modifier.fillMaxWidth(), watermark = icon) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            GlassIcon(icon, size = 56.dp)
            Column(Modifier.weight(1f)) {
                TileTitle(title)
                if (subtitle != null) TileSubtitle(subtitle)
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = Color.White, modifier = Modifier.size(30.dp))
        }
    }
}

@Composable
private fun CheckerTile(onClick: () -> Unit) =
    WideTile("Проверить мошенников", "Номер, сообщение или ссылку", Icons.Rounded.Policy, Gradients.Violet, onClick)

/** "Сегодня в 14:00" for the next dose still ahead today. */
private fun nextDose(meds: List<MedicationReminder>): String {
    val now = LocalDateTime.now()
    val minute = now.hour * 60 + now.minute
    val next = meds.filter { now.dayOfWeek.value in it.days }.flatMap { it.minutesOfDay }.filter { it > minute }.minOrNull()
    return if (next != null) "Сегодня в ${formatMinuteOfDay(next)}" else "На сегодня всё"
}

/** Read-only schedule of the reminders the family set up. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ElderMedications(meds: List<MedicationReminder>, onBack: () -> Unit) {
    ScreenScaffold(title = "Лекарства", onBack = onBack) { padding ->
        ColumnScreen(padding) {
            NoticeCard(
                "Телефон напомнит, когда придёт время. Нажмите «Принял(а)» в напоминании — близкие увидят, что всё хорошо.",
                Icons.Rounded.Alarm, Brand.WarningLight, Brand.Warning, textColor = Brand.Ink,
            )
            meds.forEach { m ->
                SoftCard {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        IconTile(Icons.Rounded.Medication, Gradients.Sunrise, size = 52.dp)
                        Column(Modifier.weight(1f)) {
                            Text(m.name, style = MaterialTheme.typography.titleLarge)
                            if (m.dose.isNotBlank()) Text(m.dose, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        m.minutesOfDay.sorted().forEach { minute ->
                            Row(
                                Modifier.clip(RoundedCornerShape(50)).background(Brand.WarningLight).padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(Icons.Rounded.Alarm, contentDescription = null, tint = Brand.Warning, modifier = Modifier.size(20.dp))
                                Text(formatMinuteOfDay(minute), style = MaterialTheme.typography.titleMedium, color = Brand.Ink)
                            }
                        }
                    }
                }
            }
        }
    }
}
