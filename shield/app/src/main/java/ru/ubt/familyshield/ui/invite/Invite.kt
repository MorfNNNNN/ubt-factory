package ru.ubt.familyshield.ui.invite

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.ubt.familyshield.AppGraph
import ru.ubt.familyshield.core.family.Invitation
import ru.ubt.familyshield.shared.model.AgeBand
import ru.ubt.familyshield.shared.model.MemberRole
import ru.ubt.familyshield.ui.FamilyUiState
import ru.ubt.familyshield.ui.components.Banner
import ru.ubt.familyshield.ui.components.PrimaryButton
import ru.ubt.familyshield.ui.components.ScreenScaffold
import ru.ubt.familyshield.ui.components.SecondaryButton
import ru.ubt.familyshield.ui.components.SectionCard
import ru.ubt.familyshield.ui.onboarding.friendlyError
import ru.ubt.familyshield.ui.theme.Brand

private enum class InviteStage { CHOOSE, SHOW }

@Composable
fun InviteScreen(graph: AppGraph, state: FamilyUiState, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var stage by remember { mutableStateOf(InviteStage.CHOOSE) }
    var role by remember { mutableStateOf(MemberRole.CHILD) }
    var name by remember { mutableStateOf("") }
    var age by remember { mutableStateOf(AgeBand.AGE_10_12) }
    var invitation by remember { mutableStateOf<Invitation?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    val limits = graph.entitlements.limits()
    val protectedCount = state.protectedMembers.size

    ScreenScaffold(title = "Добавить телефон", onBack = onClose) { padding ->
        Column(Modifier.padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            when (stage) {
                InviteStage.CHOOSE -> {
                    Text("Кого добавляем?", style = MaterialTheme.typography.titleLarge)
                    RolePick("🧒", "Ребёнок или подросток", role == MemberRole.CHILD) { role = MemberRole.CHILD }
                    RolePick("👵", "Пожилой родитель", role == MemberRole.ELDER) { role = MemberRole.ELDER }
                    RolePick("🧑‍💼", "Ещё один взрослый", role == MemberRole.GUARDIAN) { role = MemberRole.GUARDIAN }
                    OutlinedTextField(name, { name = it.take(40) }, label = { Text("Имя (по желанию)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    if (role == MemberRole.CHILD) {
                        Text("Возраст ребёнка — подберём безопасные настройки:", style = MaterialTheme.typography.bodyMedium)
                        AgeBand.entries.forEach { b ->
                            RolePick("", ageLabel(b), age == b) { age = b }
                        }
                    }
                    if (role != MemberRole.GUARDIAN && protectedCount >= limits.maxProtectedDevices) {
                        Banner("На вашем тарифе — до ${limits.maxProtectedDevices} защищаемых устройств. Откройте Премиум, чтобы добавить больше.", Brand.WarningLight, textColor = Brand.Warning, emoji = "🔒")
                    }
                    error?.let { Banner(it, Brand.DangerLight, textColor = Brand.Danger) }
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
                    Text("Покажите этот код на телефоне близкого", style = MaterialTheme.typography.titleLarge)
                    Text("Пусть он установит «Семейный щит», выберет «У меня есть код» и отсканирует.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    QrCode(inv.code.toUri())
                    SectionCard(title = "Или продиктуйте код") {
                        Text(inv.code.displayCode(), fontFamily = FontFamily.Monospace, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text("Код действует ограниченное время и только один раз.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    PrimaryButton("Готово") { onClose() }
                    SecondaryButton("Создать другой код") { stage = InviteStage.CHOOSE }
                }
            }
        }
    }
}

@Composable
private fun RolePick(emoji: String, label: String, selected: Boolean, onClick: () -> Unit) {
    SectionCard(onClick = onClick, container = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface) {
        Text((if (emoji.isNotEmpty()) "$emoji  " else "") + label, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun QrCode(content: String) {
    val color = MaterialTheme.colorScheme.onSurface
    val bitmap by produceState<Bitmap?>(null, content) {
        value = withContext(Dispatchers.Default) { runCatching { generateQr(content) }.getOrNull() }
    }
    Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxWidth(0.72f).aspectRatio(1f).background(Color.White).padding(12.dp), contentAlignment = Alignment.Center) {
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
