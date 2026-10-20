package ru.ubt.familyshield.ui.elder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.ubt.familyshield.AppGraph
import ru.ubt.familyshield.ui.FamilyUiState
import ru.ubt.familyshield.ui.components.Gap
import ru.ubt.familyshield.ui.components.PrimaryButton
import ru.ubt.familyshield.ui.components.ScreenScaffold
import ru.ubt.familyshield.ui.components.SectionCard
import ru.ubt.familyshield.ui.components.ToggleRow
import ru.ubt.familyshield.ui.theme.Brand

@Composable
fun ElderSettings(graph: AppGraph, state: FamilyUiState, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var confirmOff by remember { mutableStateOf(false) }
    val s = state.settings

    ScreenScaffold(title = "Настройки", onBack = onBack) { padding ->
        Column(Modifier.padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionCard(title = "Удобство") {
                ToggleRow("Крупные кнопки", "Большой шрифт и простой экран", checked = s.elderPolicy.simpleMode.enabled) { on ->
                    scope.launch { graph.settings.setElderPolicy(s.elderPolicy.copy(simpleMode = s.elderPolicy.simpleMode.copy(enabled = on), version = System.currentTimeMillis())) }
                }
            }
            SectionCard(title = "Защита настроек", subtitle = "Если включить, отключить защиту можно будет только через сутки или с разрешения близких. Это спасает, если мошенник уговаривает выключить защиту.") {
                ToggleRow("Защитить от отключения мошенниками", checked = s.elderSelfProtect) { on -> scope.launch { graph.settings.setElderSelfProtect(on) } }
            }
            if (s.protectionChange != null && !s.protectionChange!!.canApply(System.currentTimeMillis())) {
                SectionCard(title = "Ожидание отключения", container = Brand.WarningLight) {
                    Text("Защита отключится через некоторое время или когда близкие подтвердят. Если это сделали вы не сами — нажмите «Оставить защиту».", color = Brand.Warning)
                    PrimaryButton("Оставить защиту включённой", color = Brand.Green) { scope.launch { graph.settings.setProtectionChange(null) } }
                }
            }
            SectionCard(title = "О защите") {
                Text("Близкие помогают защитить вас от мошенников. Они видят предупреждения об опасности, но не читают вашу переписку.", style = MaterialTheme.typography.bodyLarge)
            }
            TextButton(onClick = { confirmOff = true }, modifier = Modifier.align(androidx.compose.ui.Alignment.CenterHorizontally)) {
                Text("Отключить защиту", color = Brand.Danger)
            }
            Gap(24.dp)
        }
    }

    if (confirmOff) {
        AlertDialog(
            onDismissRequest = { confirmOff = false },
            title = { Text("Отключить защиту?") },
            text = { Text(if (s.elderSelfProtect) "Защита отключится через сутки или когда близкие подтвердят. Так безопаснее." else "Вы останетесь без защиты от мошенников. Продолжить?") },
            confirmButton = {
                TextButton(onClick = {
                    confirmOff = false
                    scope.launch { graph.protection.requestProtectionOff() }
                }) { Text("Отключить", color = Brand.Danger) }
            },
            dismissButton = { TextButton(onClick = { confirmOff = false }) { Text("Отмена") } },
        )
    }
}
