package ru.ubt.familyshield.ui.onboarding

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.ubt.familyshield.AppGraph
import ru.ubt.familyshield.ui.components.Banner
import ru.ubt.familyshield.ui.components.PrimaryButton
import ru.ubt.familyshield.ui.components.SectionCard
import ru.ubt.familyshield.ui.theme.Brand

/** The guardian names the family and themselves; the phone becomes the first (admin) member. */
@Composable
fun GuardianSetup(graph: AppGraph, onBack: () -> Unit, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(graph.settings.current.displayName) }
    var phone by remember { mutableStateOf(graph.settings.current.phone) }
    var family by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        TextButton(onClick = onBack) { Text("← Назад") }
        Text("Создаём вашу семью", style = MaterialTheme.typography.headlineMedium)
        Text("Потом вы добавите телефоны близких по QR-коду.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SectionCard {
            OutlinedTextField(name, { name = it.take(40) }, label = { Text("Ваше имя") }, singleLine = true, modifier = Modifier.fillMaxSize().height(64.dp))
            OutlinedTextField(
                phone, { phone = it.take(20) },
                label = { Text("Ваш телефон (для кнопки SOS у близких)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxSize().height(64.dp),
            )
            OutlinedTextField(family, { family = it.take(40) }, label = { Text("Название семьи, напр. «Наша семья»") }, singleLine = true, modifier = Modifier.fillMaxSize().height(64.dp))
        }
        error?.let { Banner(it, Brand.DangerLight, textColor = Brand.Danger) }
        Spacer(Modifier.height(4.dp))
        PrimaryButton("Создать семью", loading = loading, enabled = name.isNotBlank()) {
            loading = true
            error = null
            scope.launch {
                try {
                    graph.settings.setProfile(name, graph.settings.current.avatar)
                    graph.settings.setPhone(phone)
                    graph.family.createFamily(family)
                    graph.scheduleWork()
                    onDone()
                } catch (e: Throwable) {
                    error = friendlyError(e)
                } finally {
                    loading = false
                }
            }
        }
    }
}
