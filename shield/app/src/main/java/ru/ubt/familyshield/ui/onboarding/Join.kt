package ru.ubt.familyshield.ui.onboarding

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.launch
import ru.ubt.familyshield.AppGraph
import ru.ubt.familyshield.shared.crypto.Crockford
import ru.ubt.familyshield.shared.crypto.PairingCode
import ru.ubt.familyshield.shared.model.MemberRole
import ru.ubt.familyshield.ui.QrScanActivity
import ru.ubt.familyshield.ui.components.Banner
import ru.ubt.familyshield.ui.components.PrimaryButton
import ru.ubt.familyshield.ui.components.SecondaryButton
import ru.ubt.familyshield.ui.components.SectionCard
import ru.ubt.familyshield.ui.theme.Brand

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

    Column(Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        TextButton(onClick = onBack) { Text("← Назад") }
        Text("Подключение к семье", style = MaterialTheme.typography.headlineMedium)
        Text("Отсканируйте QR-код из приложения взрослого или введите код вручную.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)

        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(40) },
            label = { Text("Как вас зовут (по желанию)") },
            singleLine = true,
            modifier = Modifier.fillMaxSize().height(64.dp),
        )

        SectionCard(title = "Отсканировать QR-код") {
            PrimaryButton("Открыть камеру") {
                scanner.launch(
                    ScanOptions().setOrientationLocked(true).setBeepEnabled(false)
                        .setCaptureActivity(QrScanActivity::class.java)
                        .setPrompt("Наведите камеру на QR-код").setDesiredBarcodeFormats(ScanOptions.QR_CODE),
                )
            }
        }

        SectionCard(title = "Или ввести код") {
            OutlinedTextField(
                value = code,
                onValueChange = { code = it.uppercase().take(40) },
                label = { Text("Код приглашения") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                modifier = Modifier.fillMaxSize().height(64.dp),
            )
            SecondaryButton("Подключиться") { join(code) }
        }

        if (loading) Text("Подключаемся…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        error?.let { Banner(it, Brand.DangerLight, textColor = Brand.Danger, emoji = "⚠️") }
        Spacer(Modifier.height(8.dp))
    }
}
