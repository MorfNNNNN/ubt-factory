package ru.ubt.familyshield.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.ubt.familyshield.shared.model.MemberRole
import ru.ubt.familyshield.ui.components.PrimaryButton
import ru.ubt.familyshield.ui.components.SectionCard

/**
 * Plain-language consent shown on the protected device before it joins a family. Honest disclosure
 * is both an ethical requirement and a RuStore policy requirement for monitoring apps.
 */
@Composable
fun ConsentScreen(role: MemberRole, onBack: () -> Unit, onContinue: () -> Unit) {
    var agreed by remember { mutableStateOf(false) }
    val sees: List<String>
    val doesNot: List<String>
    if (role == MemberRole.CHILD) {
        sees = listOf(
            "сколько времени вы проводите в приложениях",
            "какие приложения установлены на телефоне",
            "где находится телефон",
            "предупреждения, если кто-то угрожает или пытается обмануть",
        )
        doesNot = listOf(
            "читать вашу личную переписку целиком — приложение само ищет только признаки опасности на телефоне",
            "слушать звонки и включать камеру",
            "видеть ваши пароли",
        )
    } else {
        sees = listOf(
            "предупреждения о мошеннических звонках и сообщениях",
            "заряд батареи и местоположение телефона",
            "отметку «я в порядке» и сигнал SOS",
        )
        doesNot = listOf(
            "читать вашу переписку целиком — на телефоне проверяются только признаки мошенничества",
            "слушать ваши звонки",
            "видеть ваши пароли и коды",
        )
    }
    Column(Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        TextButton(onClick = onBack) { Text("← Назад") }
        Text("Честно о том, как это работает", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Этот телефон будет подключён к семье. Важно, чтобы вы понимали, что увидят близкие, а что останется только вашим.",
            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SectionCard(title = "Близкие будут видеть", container = MaterialTheme.colorScheme.primaryContainer) {
            sees.forEach { Text("✓  $it", style = MaterialTheme.typography.bodyLarge) }
        }
        SectionCard(title = "Приложение НЕ будет") {
            doesNot.forEach { Text("✕  $it", style = MaterialTheme.typography.bodyLarge) }
        }
        Text(
            "Защиту можно отключить в любой момент. На телефоне всегда показано, что он под защитой семьи.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = agreed, onCheckedChange = { agreed = it })
            Text("Я понимаю и согласен(на) подключить этот телефон", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.height(4.dp))
        PrimaryButton("Продолжить", enabled = agreed, onClick = onContinue)
    }
}
