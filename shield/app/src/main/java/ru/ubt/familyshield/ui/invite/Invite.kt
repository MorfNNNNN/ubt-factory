package ru.ubt.familyshield.ui.invite

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.ubt.familyshield.AppGraph
import ru.ubt.familyshield.R
import ru.ubt.familyshield.core.family.Invitation
import ru.ubt.familyshield.shared.model.AgeBand
import ru.ubt.familyshield.shared.model.MemberRole
import ru.ubt.familyshield.ui.FamilyUiState
import ru.ubt.familyshield.ui.components.AvatarArt
import ru.ubt.familyshield.ui.components.AvatarImage
import ru.ubt.familyshield.ui.components.Illustration
import ru.ubt.familyshield.ui.components.LottieWithFallback
import ru.ubt.familyshield.ui.components.NoticeCard
import ru.ubt.familyshield.ui.components.PrimaryButton
import ru.ubt.familyshield.ui.components.SecondaryButton
import ru.ubt.familyshield.ui.components.SoftCard
import ru.ubt.familyshield.ui.components.visual
import ru.ubt.familyshield.ui.guardian.DetailScaffold
import ru.ubt.familyshield.ui.guardian.PillButton
import ru.ubt.familyshield.ui.onboarding.ShieldTextField
import ru.ubt.familyshield.ui.onboarding.friendlyError
import ru.ubt.familyshield.ui.theme.Brand
import ru.ubt.familyshield.ui.theme.Gradients

private enum class InviteStage { CHOOSE, SHOW }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InviteScreen(graph: AppGraph, state: FamilyUiState, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var stage by remember { mutableStateOf(InviteStage.CHOOSE) }
    var role by remember { mutableStateOf(MemberRole.CHILD) }
    var name by remember { mutableStateOf("") }
    var age by remember { mutableStateOf(AgeBand.AGE_10_12) }
    var invitation by remember { mutableStateOf<Invitation?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    val limits = graph.entitlements.limits()
    val protectedCount = state.protectedMembers.size

    DetailScaffold(title = "Добавить телефон", onBack = onClose) { padding ->
        Column(
            Modifier.padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            when (stage) {
                InviteStage.CHOOSE -> {
                    Text("Кого добавляем?", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(horizontal = 4.dp))
                    RoleOption(MemberRole.CHILD, "Ребёнок или подросток", "Экранное время, безопасный интернет, геолокация", AvatarArt.GIRL.value, role == MemberRole.CHILD) { role = MemberRole.CHILD }
                    RoleOption(MemberRole.ELDER, "Пожилой родитель", "Защита от мошенников, крупные кнопки, SOS", AvatarArt.GRANDMA.value, role == MemberRole.ELDER) { role = MemberRole.ELDER }
                    RoleOption(MemberRole.GUARDIAN, "Ещё один взрослый", "Второй родитель или помощник: видит всё и помогает", AvatarArt.DAD.value, role == MemberRole.GUARDIAN) { role = MemberRole.GUARDIAN }

                    ShieldTextField(name, { name = it.take(40) }, "Имя (по желанию)")

                    if (role == MemberRole.CHILD) {
                        SoftCard(padding = 16.dp) {
                            Text("Возраст — подберём безопасные настройки", style = MaterialTheme.typography.titleSmall)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                AgeBand.entries.forEach { b -> AgeChip(ageLabel(b), age == b) { age = b } }
                            }
                        }
                    }
                    if (role != MemberRole.GUARDIAN && protectedCount >= limits.maxProtectedDevices) {
                        NoticeCard(
                            "На вашем тарифе — до ${limits.maxProtectedDevices} защищаемых устройств. Откройте Премиум, чтобы добавить больше.",
                            Icons.Rounded.Lock, Brand.WarningLight, Brand.Warning, textColor = Brand.Ink,
                        )
                    }
                    error?.let { NoticeCard(it, Icons.Rounded.ErrorOutline, Brand.DangerLight, Brand.Danger, textColor = Brand.Ink) }
                    PrimaryButton("Создать приглашение", loading = loading) {
                        loading = true
                        error = null
                        scope.launch {
                            try {
                                graph.family.ensureRegistered()
                                invitation = graph.family.createInvite(role, name, if (role == MemberRole.CHILD) age else null)
                                stage = InviteStage.SHOW
                            } catch (e: Throwable) {
                                error = friendlyError(e)
                            } finally {
                                loading = false
                            }
                        }
                    }
                }
                InviteStage.SHOW -> invitation?.let { inv ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        LottieWithFallback(R.raw.lottie_devices_connect, 72.dp) { Illustration(R.drawable.ic3d_mobile, size = 56.dp) }
                        Column(Modifier.weight(1f)) {
                            Text("Покажите этот код", style = MaterialTheme.typography.headlineSmall)
                            Text("на телефоне близкого", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    QrCard(inv.code.toUri())
                    SoftCard(padding = 16.dp) {
                        Step(1, "Установите «Семейный щит» из RuStore")
                        Step(2, "Выберите «У меня есть код приглашения»")
                        Step(3, "Наведите камеру на этот QR-код")
                    }
                    SoftCard(padding = 16.dp) {
                        Text("Или продиктуйте код", style = MaterialTheme.typography.titleSmall)
                        Text(inv.code.displayCode(), fontFamily = FontFamily.Monospace, fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurface)
                        Text("Код одноразовый и действует ограниченное время.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        PillButton("Отправить код", modifier = Modifier.fillMaxWidth(), icon = Icons.Rounded.Share, fill = Gradients.Sky) {
                            val text = "Подключись к нашей семье в «Семейном щите»: установи приложение из RuStore, выбери «У меня есть код» и введи ${inv.code.displayCode()}"
                            runCatching {
                                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Отправить код"))
                            }
                        }
                    }
                    PrimaryButton("Готово") { onClose() }
                    SecondaryButton("Создать другой код") { stage = InviteStage.CHOOSE }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun RoleOption(role: MemberRole, title: String, text: String, portrait: String, selected: Boolean, onClick: () -> Unit) {
    val shape = MaterialTheme.shapes.large
    Row(
        Modifier.fillMaxWidth()
            .shadow(if (selected) 10.dp else 6.dp, shape, ambientColor = Brand.Green.copy(alpha = 0.16f), spotColor = Brand.Green.copy(alpha = 0.16f))
            .clip(shape).background(if (selected) Brand.Mint else MaterialTheme.colorScheme.surface)
            .border(if (selected) 2.dp else 0.dp, if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, shape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AvatarImage(portrait, role.name, size = 60.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = if (selected) Brand.Ink else MaterialTheme.colorScheme.onSurface)
            Text(text, style = MaterialTheme.typography.bodySmall, color = if (selected) Brand.Slate else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(
            if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, modifier = Modifier.size(26.dp),
        )
    }
}

@Composable
private fun AgeChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(CircleShape)
            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun Step(n: Int, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(30.dp).clip(CircleShape).background(MemberRole.GUARDIAN.visual().gradient), contentAlignment = Alignment.Center) {
            Text("$n", style = MaterialTheme.typography.labelLarge, color = Color.White)
        }
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
    }
}

/** The QR code on a white card with a brand-green frame, so it scans well in any theme. */
@Composable
private fun QrCard(content: String) {
    val bitmap by produceState<Bitmap?>(null, content) {
        value = withContext(Dispatchers.Default) { runCatching { generateQr(content) }.getOrNull() }
    }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            Modifier.fillMaxWidth(0.82f).aspectRatio(1f)
                .shadow(14.dp, MaterialTheme.shapes.extraLarge, ambientColor = Brand.Green.copy(alpha = 0.3f), spotColor = Brand.Green.copy(alpha = 0.3f))
                .clip(MaterialTheme.shapes.extraLarge).background(Gradients.Emerald).padding(10.dp)
                .clip(MaterialTheme.shapes.large).background(Color.White).padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            bitmap?.let { Image(it.asImageBitmap(), contentDescription = "QR-код приглашения", modifier = Modifier.fillMaxWidth(), contentScale = ContentScale.Fit) }
        }
    }
}

private fun generateQr(content: String, size: Int = 640): Bitmap {
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size)
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
    for (x in 0 until size) for (y in 0 until size) {
        bmp.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
    }
    return bmp
}

fun ageLabel(b: AgeBand) = when (b) {
    AgeBand.UNDER_7 -> "До 7 лет"
    AgeBand.AGE_7_9 -> "7–9 лет"
    AgeBand.AGE_10_12 -> "10–12 лет"
    AgeBand.AGE_13_15 -> "13–15 лет"
    AgeBand.AGE_16_17 -> "16–17 лет"
}
