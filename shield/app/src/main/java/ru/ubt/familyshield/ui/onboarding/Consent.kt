package ru.ubt.familyshield.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import ru.ubt.familyshield.R
import ru.ubt.familyshield.shared.model.MemberRole
import ru.ubt.familyshield.ui.components.Art3D
import ru.ubt.familyshield.ui.components.GlyphLine
import ru.ubt.familyshield.ui.components.Illustration
import ru.ubt.familyshield.ui.components.LottieWithFallback
import ru.ubt.familyshield.ui.components.NoticeCard
import ru.ubt.familyshield.ui.components.PrimaryButton
import ru.ubt.familyshield.ui.components.SoftCard
import ru.ubt.familyshield.ui.theme.Gradients

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
            "сумму крупных списаний по карте — только если вы разрешите",
        )
        doesNot = listOf(
            "читать вашу переписку целиком — на телефоне проверяются только признаки мошенничества",
            "слушать ваши звонки",
            "видеть ваши пароли и коды",
        )
    }
    OnboardingPage(
        title = "Честно о том, как это работает",
        text = "Этот телефон будет подключён к семье. Важно, чтобы вы понимали, что увидят близкие, а что останется только вашим.",
        onBack = onBack,
        art = { LottieWithFallback(R.raw.lottie_lock_secure, 170.dp, Modifier.align(Alignment.Center), loop = false) { Illustration(Art3D.Lock, size = 120.dp) } },
    ) {
        SoftCard(padding = 18.dp) {
            Text("Близкие будут видеть", style = MaterialTheme.typography.titleMedium)
            sees.forEach { GlyphLine(it, Icons.Rounded.Check, Gradients.Emerald, tile = 30.dp, style = MaterialTheme.typography.bodyLarge) }
        }
        SoftCard(padding = 18.dp) {
            Text("Приложение не будет", style = MaterialTheme.typography.titleMedium)
            doesNot.forEach { GlyphLine(it, Icons.Rounded.Close, Gradients.Slate, tile = 30.dp, style = MaterialTheme.typography.bodyLarge) }
        }
        NoticeCard(
            "Защиту можно отключить в любой момент. На телефоне всегда видно, что он под защитой семьи.",
            Icons.Rounded.PowerSettingsNew, MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AgreeRow(agreed) { agreed = it }
        PrimaryButton("Продолжить", enabled = agreed, onClick = onContinue)
    }
}

@Composable
private fun AgreeRow(checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onChange)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        val shape = RoundedCornerShape(9.dp)
        Box(
            Modifier.size(30.dp).clip(shape)
                .background(if (checked) Gradients.Emerald else androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.surface))
                .border(if (checked) 0.dp else 2.dp, MaterialTheme.colorScheme.outline, shape),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Text("Я понимаю и согласен(на) подключить этот телефон", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
    }
}
