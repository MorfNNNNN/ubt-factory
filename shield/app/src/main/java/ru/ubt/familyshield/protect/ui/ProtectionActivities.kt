package ru.ubt.familyshield.protect.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.PanTool
import androidx.compose.material.icons.rounded.Sos
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.ubt.familyshield.ShieldApp
import ru.ubt.familyshield.core.family.send
import ru.ubt.familyshield.shared.model.AlertType
import ru.ubt.familyshield.shared.model.MemberRequest
import ru.ubt.familyshield.shared.model.RequestKind
import ru.ubt.familyshield.shared.model.TrustedContact
import ru.ubt.familyshield.shared.policy.AccessDecision
import ru.ubt.familyshield.shared.policy.BlockReason
import ru.ubt.familyshield.shared.policy.formatMinuteOfDay
import ru.ubt.familyshield.shared.protocol.Audience
import ru.ubt.familyshield.shared.protocol.FamilyPayload
import ru.ubt.familyshield.ui.components.Art3D
import ru.ubt.familyshield.ui.components.GlassIcon
import ru.ubt.familyshield.ui.components.Illustration
import ru.ubt.familyshield.ui.components.MintPanel
import ru.ubt.familyshield.ui.components.NoticeCard
import ru.ubt.familyshield.ui.components.ProgressRing
import ru.ubt.familyshield.ui.components.SosPulse
import ru.ubt.familyshield.ui.theme.Brand
import ru.ubt.familyshield.ui.theme.Gradients
import ru.ubt.familyshield.ui.theme.ShieldTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

private val ComponentActivity.graph get() = (application as ShieldApp).graph

private fun ComponentActivity.goHome() {
    startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    finish()
}

private fun ComponentActivity.showOverLockScreen() {
    if (Build.VERSION.SDK_INT >= 27) {
        setShowWhenLocked(true)
        setTurnScreenOn(true)
    } else {
        @Suppress("DEPRECATION")
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
    }
}

/** Large rounded button: white on coloured screens, brand gradient on light ones. */
@Composable
private fun BigButton(
    text: String,
    icon: ImageVector? = null,
    fill: Brush = Gradients.Emerald,
    content: Color = Color.White,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().height(64.dp)
            .shadow(10.dp, MaterialTheme.shapes.medium, ambientColor = Color.Black.copy(alpha = 0.2f), spotColor = Color.Black.copy(alpha = 0.2f))
            .clip(MaterialTheme.shapes.medium).background(fill)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(text, color = content, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private val White = SolidColor(Color.White)

/** Shown to a child when an app is not available right now. */
class BlockActivity : ComponentActivity() {
    companion object {
        private const val EXTRA_PKG = "pkg"
        private const val EXTRA_LABEL = "label"
        private const val EXTRA_REASON = "reason"
        private const val EXTRA_DETAIL = "detail"
        private const val EXTRA_UNTIL_MIN = "until_min"
        private const val EXTRA_UNTIL_TS = "until_ts"
        private const val EXTRA_TAMPER = "tamper"

        fun intent(context: Context, pkg: String, label: String, d: AccessDecision.Blocked): Intent =
            Intent(context, BlockActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(EXTRA_PKG, pkg).putExtra(EXTRA_LABEL, label).putExtra(EXTRA_REASON, d.reason.name).putExtra(EXTRA_DETAIL, d.detail)
                .putExtra(EXTRA_UNTIL_MIN, d.untilMinuteOfDay ?: -1).putExtra(EXTRA_UNTIL_TS, d.untilEpochMillis ?: -1L)

        fun tamperIntent(context: Context): Intent =
            Intent(context, BlockActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP).putExtra(EXTRA_TAMPER, true)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = goHome()
        })
        val tamper = intent.getBooleanExtra(EXTRA_TAMPER, false)
        val reason = intent.getStringExtra(EXTRA_REASON)?.let { runCatching { BlockReason.valueOf(it) }.getOrNull() }
        val label = intent.getStringExtra(EXTRA_LABEL).orEmpty()
        val pkg = intent.getStringExtra(EXTRA_PKG).orEmpty()
        val untilMin = intent.getIntExtra(EXTRA_UNTIL_MIN, -1)
        val untilTs = intent.getLongExtra(EXTRA_UNTIL_TS, -1L)
        setContent { ShieldTheme { BlockScreen(tamper, reason, label, pkg, intent.getStringExtra(EXTRA_DETAIL).orEmpty(), untilMin, untilTs) } }
    }

    @Composable
    private fun BlockScreen(tamper: Boolean, reason: BlockReason?, label: String, pkg: String, detail: String, untilMin: Int, untilTs: Long) {
        val scope = rememberCoroutineScope()
        var sent by remember { mutableStateOf<String?>(null) }
        var pin by remember { mutableStateOf("") }
        var pinError by remember { mutableStateOf(false) }
        val art = when {
            tamper -> Art3D.Lock
            reason == BlockReason.SCHEDULE -> if (detail.contains("Сон")) Art3D.Moon else Art3D.Notebook
            reason == BlockReason.PAUSED -> Art3D.Clock
            reason == BlockReason.PENDING_APPROVAL -> Art3D.Bell
            reason == BlockReason.BYPASS_TOOL -> Art3D.Shield
            else -> Art3D.Stopwatch
        }
        Column(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding().padding(24.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(12.dp))
            MintPanel(Modifier.fillMaxWidth().height(220.dp)) {
                Illustration(art, Modifier.align(Alignment.Center), size = 168.dp)
            }
            Text(
                if (tamper) "Эти настройки защищены" else reason?.titleRu ?: "Недоступно",
                style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center,
            )
            val sub = when {
                tamper -> "Изменить защиту может только родитель. Попроси его ввести PIN-код."
                reason == BlockReason.SCHEDULE -> "«$detail» до ${formatMinuteOfDay(untilMin.coerceAtLeast(0))}. «$label» снова заработает после этого."
                reason == BlockReason.PAUSED && untilTs > 0 && untilTs < System.currentTimeMillis() + 300L * 86_400_000L ->
                    "Родители поставили паузу до ${SimpleDateFormat("HH:mm", Locale("ru")).format(Date(untilTs))}."
                reason == BlockReason.PENDING_APPROVAL -> "Мы отправили родителям запрос на «$label». Как только они разрешат, приложение откроется."
                reason == BlockReason.BYPASS_TOOL -> "VPN и анонимайзеры отключают защиту от опасных сайтов, поэтому они недоступны."
                else -> "«$label» сейчас недоступно. Звонки и сообщения работают как обычно."
            }
            Text(sub, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            if (tamper) {
                OutlinedTextField(
                    value = pin, onValueChange = { pin = it.filter(Char::isDigit).take(8); pinError = false },
                    label = { Text("PIN родителя") }, singleLine = true, isError = pinError,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth(),
                )
                BigButton("Разблокировать на 10 минут", Icons.Rounded.LockOpen) {
                    val hash = graph.settings.current.childPolicy.parentPin
                    if (hash != null && hash.matches(pin)) {
                        graph.protection.child.tamperUnlockedUntil = System.currentTimeMillis() + 10 * 60_000L
                        finish()
                    } else {
                        pinError = true
                    }
                }
            } else if (reason in setOf(BlockReason.DAILY_LIMIT, BlockReason.APP_LIMIT, BlockReason.CATEGORY_LIMIT, BlockReason.SCHEDULE)) {
                if (sent == null) {
                    Text("Попросить ещё времени", style = MaterialTheme.typography.titleMedium)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        for (m in listOf(15, 30, 60)) {
                            Box(
                                Modifier.weight(1f).height(58.dp).clip(MaterialTheme.shapes.medium)
                                    .background(MaterialTheme.colorScheme.surface)
                                    .border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), MaterialTheme.shapes.medium)
                                    .clickable(role = Role.Button) {
                                        scope.launch {
                                            graph.sync.send(
                                                FamilyPayload.Request(MemberRequest(UUID.randomUUID().toString(), RequestKind.MORE_TIME, System.currentTimeMillis(), minutes = m, packageName = pkg, appLabel = label)),
                                                Audience.GUARDIANS, urgent = true,
                                            )
                                            sent = "Запрос на $m минут отправлен родителям"
                                        }
                                    },
                                contentAlignment = Alignment.Center,
                            ) { Text("+$m мин", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary) }
                        }
                    }
                } else {
                    NoticeCard(sent!!, Icons.Rounded.CheckCircle, Brand.GreenLight, Brand.GreenDark)
                }
            }
            BigButton("На главный экран", Icons.Rounded.Home) { goHome() }
        }
    }
}

/** Elder: full-screen "STOP" warning at the critical moment of a scam. */
class WarningActivity : ComponentActivity() {
    enum class Kind { BANK_DURING_CALL, OTP_DURING_CALL, REMOTE_ACCESS, INSTALL_DURING_CALL }

    companion object {
        fun intent(context: Context, kind: Kind, detail: String): Intent =
            Intent(context, WarningActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra("kind", kind.name).putExtra("detail", detail)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        val kind = runCatching { Kind.valueOf(intent.getStringExtra("kind").orEmpty()) }.getOrDefault(Kind.BANK_DURING_CALL)
        val detail = intent.getStringExtra("detail").orEmpty()
        vibrate()
        setContent { ShieldTheme(dark = false) { WarningScreen(kind, detail) } }
    }

    private fun vibrate() {
        val v = getSystemService(Vibrator::class.java) ?: return
        runCatching { v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 400, 200, 400, 200, 400), -1)) }
    }

    @Composable
    private fun WarningScreen(kind: Kind, detail: String) {
        val contacts by produceState(initialValue = emptyList<TrustedContact>()) { value = graph.protection.sos.contacts() }
        val (title, lines) = when (kind) {
            Kind.BANK_DURING_CALL -> "СТОП! Вы открыли банк во время звонка" to listOf(
                "Вы разговариваете с незнакомым номером $detail.",
                "Мошенники просят открыть банк, чтобы вы сами перевели деньги или назвали коды.",
                "Положите трубку. Банк, полиция и ЦБ никогда не просят переводить деньги.",
            )
            Kind.OTP_DURING_CALL -> "Не называйте этот код!" to listOf(
                "Вам пришёл код ${if (detail.isNotBlank()) "от $detail " else ""}во время разговора с незнакомцем.",
                "Код из СМС — это ваша подпись. Кто его узнает, тот получит доступ к деньгам или Госуслугам.",
                "Положите трубку и позвоните близким.",
            )
            Kind.REMOTE_ACCESS -> "Осторожно: удалённый доступ" to listOf(
                "Открыта программа «$detail». Через неё чужой человек видит ваш экран и коды.",
                "Если её попросили установить по телефону — это мошенники. Закройте и удалите её.",
            )
            Kind.INSTALL_DURING_CALL -> "Не устанавливайте приложения по просьбе звонящего" to listOf(
                "Мошенники просят установить «приложение банка» или «защиту», а на самом деле это вирус.",
                "Нажмите «Отмена» и положите трубку.",
            )
        }
        Box(Modifier.fillMaxSize().background(Gradients.Danger)) {
            Column(
                Modifier.fillMaxSize().systemBarsPadding().padding(24.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                GlassIcon(Icons.Rounded.PanTool, size = 84.dp)
                Text(title, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 36.sp)
                lines.forEach { line ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.padding(top = 10.dp).size(9.dp).clip(CircleShape).background(Color.White))
                        Text(line, color = Color.White, fontSize = 21.sp, lineHeight = 28.sp, modifier = Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(8.dp))
                contacts.take(3).forEach { c ->
                    BigButton("Позвонить: ${c.name}", Icons.Rounded.Call, fill = Gradients.Emerald) {
                        graph.protection.sos.call(c.phone)
                        finish()
                    }
                }
                BigButton("Я понял(а)", fill = White, content = Brand.Danger) { finish() }
            }
        }
    }
}

/** Fall confirmation: 30 seconds to say "I'm fine" before the family is alerted. */
class FallActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        graph.protection.ringer.ring(30)
        setContent {
            ShieldTheme(dark = false) {
                var left by remember { mutableIntStateOf(30) }
                LaunchedEffect(Unit) {
                    while (left > 0) {
                        delay(1000)
                        left--
                    }
                    graph.protection.ringer.stop()
                    graph.protection.sos.trigger(AlertType.FALL_DETECTED)
                    finish()
                }
                Column(
                    Modifier.fillMaxSize().background(Gradients.Sunrise).systemBarsPadding().padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    ProgressRing(left / 30f, 168.dp, stroke = 12.dp) {
                        Text("$left", color = Color.White, fontSize = 56.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    Text("Похоже, вы упали", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
                    Text("Через $left сек мы сообщим близким", color = Color.White, fontSize = 22.sp, textAlign = TextAlign.Center)
                    BigButton("Я в порядке", Icons.Rounded.Favorite, fill = Gradients.Emerald) {
                        graph.protection.ringer.stop()
                        finish()
                    }
                    BigButton("Нужна помощь", Icons.Rounded.Sos, fill = White, content = Brand.Danger) {
                        graph.protection.ringer.stop()
                        graph.protection.sos.trigger(AlertType.FALL_DETECTED)
                        finish()
                    }
                }
            }
        }
    }
}

/** "Найти телефон" screen while the alarm rings. */
class RingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        setContent {
            ShieldTheme {
                Column(
                    Modifier.fillMaxSize().background(Gradients.Hero).systemBarsPadding().padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Illustration(Art3D.Bell, size = 180.dp)
                    Text("Вас ищут близкие", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
                    Text("Телефон звонит, чтобы его было легко найти", color = Color.White.copy(alpha = 0.9f), fontSize = 20.sp, textAlign = TextAlign.Center)
                    BigButton("Выключить звук", Icons.AutoMirrored.Rounded.VolumeOff, fill = White, content = Brand.GreenDark) {
                        graph.protection.ringer.stop()
                        finish()
                    }
                }
            }
        }
    }
}

/** SOS with a short countdown so an accidental tap can be cancelled. */
class SosActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        setContent {
            ShieldTheme(dark = false) {
                var left by remember { mutableIntStateOf(5) }
                var sent by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    while (left > 0) {
                        delay(1000)
                        left--
                    }
                    graph.protection.sos.trigger()
                    sent = true
                }
                Column(
                    Modifier.fillMaxSize().background(Gradients.Danger).systemBarsPadding().padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    SosPulse(300.dp)
                    if (!sent) {
                        Text("Вызов помощи через $left сек", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        BigButton("Отменить", Icons.Rounded.Close, fill = White, content = Brand.Danger) { finish() }
                    } else {
                        Text("Близкие получили тревогу и ваше местоположение. Звоним…", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        BigButton("Закрыть", Icons.Rounded.Check, fill = White, content = Brand.Danger) { finish() }
                    }
                }
            }
        }
    }
}
