package ru.ubt.familyshield.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.ubt.familyshield.core.net.ApiException
import ru.ubt.familyshield.shared.crypto.CryptoException
import ru.ubt.familyshield.ui.components.BackButton
import ru.ubt.familyshield.ui.components.MintPanel

/** Turns low-level errors into calm, actionable Russian messages for onboarding screens. */
fun friendlyError(e: Throwable): String = when {
    e is ApiException && e.code == "invite_not_found" -> "Код не найден. Проверьте, что он введён без ошибок, или создайте новый."
    e is ApiException && e.code == "invite_used" -> "Этот код уже использован. Попросите близкого создать новый."
    e is ApiException && e.code == "invite_expired" -> "Срок действия кода истёк. Создайте новый в приложении взрослого."
    e is ApiException && e.code == "rate_limited" -> "Слишком много попыток. Подождите несколько минут."
    e is ApiException && e.code == "already_in_family" -> "Этот телефон уже состоит в семье."
    e is ApiException && e.code.startsWith("plan_limit") -> e.message ?: "Достигнут лимит тарифа"
    e is ApiException -> e.message ?: "Ошибка сервера"
    e is CryptoException -> "Код не подошёл. Проверьте, что все символы введены верно."
    e is java.io.IOException -> "Нет связи с сервером. Проверьте интернет и попробуйте ещё раз."
    else -> e.message ?: "Что-то пошло не так"
}

/** One onboarding step: back button, art on a mint panel, a big headline, text and the step's content. */
@Composable
internal fun OnboardingPage(
    title: String,
    text: String,
    onBack: (() -> Unit)?,
    art: @Composable BoxScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (onBack != null) BackButton(onBack)
        MintPanel(Modifier.fillMaxWidth().height(190.dp), content = art)
        Text(title, style = MaterialTheme.typography.headlineLarge)
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(2.dp))
        content()
    }
}

/** Rounded white text field used across onboarding. */
@Composable
internal fun ShieldTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    singleLine: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = singleLine,
        maxLines = if (singleLine) 1 else 4,
        keyboardOptions = keyboardOptions,
        shape = MaterialTheme.shapes.medium,
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}
