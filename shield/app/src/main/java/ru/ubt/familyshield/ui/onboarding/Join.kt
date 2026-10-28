package ru.ubt.familyshield.ui.onboarding

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.launch
import ru.ubt.familyshield.AppGraph
import ru.ubt.familyshield.R
import ru.ubt.familyshield.shared.crypto.PairingCode
import ru.ubt.familyshield.shared.model.MemberRole
import ru.ubt.familyshield.ui.QrScanActivity
import ru.ubt.familyshield.ui.components.Art3D
import ru.ubt.familyshield.ui.components.IconTile
import ru.ubt.familyshield.ui.components.Illustration
import ru.ubt.familyshield.ui.components.LottieWithFallback
import ru.ubt.familyshield.ui.components.NoticeCard
import ru.ubt.familyshield.ui.components.PrimaryButton
import ru.ubt.familyshield.ui.components.SecondaryButton
import ru.ubt.familyshield.ui.components.SoftCard
import ru.ubt.familyshield.ui.theme.Brand
import ru.ubt.familyshield.ui.theme.Gradients

/** Joins an existing family by scanning the QR code or typing the pairing code. */
@Composable
fun JoinFamily(graph: AppGraph, pairingLink: String?, onBack: () -> Unit, onJoined: (MemberRole) -> Unit) {
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    fun join(raw: String) {
        val parsed = PairingCode.parse(raw)
        if (parsed == null) {
            error = "Не удалось распознать код. Проверьте и попробуйте ещё раз."
            return
        }
        loading = true
        error = null
        scope.launch {
            try {
                graph.family.joinFamily(parsed, name.trim())
                val role = graph.settings.current.role ?: MemberRole.CHILD
                graph.protection.apply()
                graph.sync.syncNow()
                onJoined(role)
            } catch (e: Throwable) {
                error = friendlyError(e)
            } finally {
                loading = false
            }
        }
    }

    val scanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let { join(it) }
    }

    LaunchedEffect(pairingLink) { if (pairingLink != null) join(pairingLink) }

    OnboardingPage(
        title = "Подключение к семье",
        text = "Отсканируйте QR-код из приложения близкого или введите код вручную.",
        onBack = onBack,
        art = { LottieWithFallback(R.raw.lottie_devices_connect, 180.dp, Modifier.align(Alignment.Center)) { Illustration(Art3D.Mobile, size = 120.dp) } },
    ) {
        ShieldTextField(name, { name = it.take(40) }, "Как вас зовут (по желанию)")

        SoftCard(padding = 18.dp) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                IconTile(Icons.Rounded.QrCodeScanner, Gradients.Emerald, size = 48.dp)
                Column(Modifier.weight(1f)) {
                    Text("Отсканировать QR-код", style = MaterialTheme.typography.titleMedium)
                    Text("Быстрее всего: наведите камеру на экран близкого", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            PrimaryButton("Открыть камеру") {
                scanner.launch(
                    ScanOptions().setOrientationLocked(true).setBeepEnabled(false)
                        .setCaptureActivity(QrScanActivity::class.java)
                        .setPrompt("Наведите камеру на QR-код").setDesiredBarcodeFormats(ScanOptions.QR_CODE),
                )
            }
        }

        SoftCard(padding = 18.dp) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                IconTile(Icons.Rounded.Keyboard, Gradients.Slate, size = 48.dp)
                Column(Modifier.weight(1f)) {
                    Text("Ввести код", style = MaterialTheme.typography.titleMedium)
                    Text("Код из приложения близкого, буквы и цифры", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            ShieldTextField(
                code, { code = it.uppercase().take(40) }, "Код приглашения",
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            )
            SecondaryButton("Подключиться") { join(code) }
        }

        if (loading) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Text("Подключаемся…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
        error?.let { NoticeCard(it, Icons.Rounded.ErrorOutline, Brand.DangerLight, Brand.Danger, textColor = Brand.Ink) }
    }
}
