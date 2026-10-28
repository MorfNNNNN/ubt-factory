package ru.ubt.familyshield.ui.elder

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.FamilyRestroom
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.TextIncrease
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.ubt.familyshield.AppGraph
import ru.ubt.familyshield.shared.model.MemberRole
import ru.ubt.familyshield.ui.FamilyUiState
import ru.ubt.familyshield.ui.components.AvatarImage
import ru.ubt.familyshield.ui.components.Gap
import ru.ubt.familyshield.ui.components.GlyphLine
import ru.ubt.familyshield.ui.components.IconTile
import ru.ubt.familyshield.ui.components.PrimaryButton
import ru.ubt.familyshield.ui.components.ScreenScaffold
import ru.ubt.familyshield.ui.components.SectionHeader
import ru.ubt.familyshield.ui.components.SoftCard
import ru.ubt.familyshield.ui.components.TileRow
import ru.ubt.familyshield.ui.guardian.ChangeLogScreen
import ru.ubt.familyshield.ui.theme.Brand
import ru.ubt.familyshield.ui.theme.Gradients

@Composable
fun ElderSettings(graph: AppGraph, state: FamilyUiState, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var confirmOff by remember { mutableStateOf(false) }
    var changes by remember { mutableStateOf(false) }
    val s = state.settings
    if (changes) { ChangeLogScreen(graph, s.deviceId.orEmpty(), "Что меняли близкие", state.members) { changes = false }; return }

    ScreenScaffold(title = "Настройки", onBack = onBack) { padding ->
        Column(Modifier.padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SoftCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    AvatarImage(s.avatar, MemberRole.ELDER.name, size = 64.dp, ring = Color.White)
                    Column(Modifier.weight(1f)) {
                        Text(s.displayName.ifBlank { "Мой телефон" }, style = MaterialTheme.typography.titleLarge)
                        Text(
                            s.familyName.ifBlank { "Под защитой семьи" },
                            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            SectionHeader("Удобство")
            SoftCard(padding = 12.dp) {
                SwitchRow(Icons.Rounded.TextIncrease, Gradients.Sky, "Крупные кнопки", "Большой шрифт и простой экран", s.elderPolicy.simpleMode.enabled) { on ->
                    scope.launch { graph.settings.setElderPolicy(s.elderPolicy.copy(simpleMode = s.elderPolicy.simpleMode.copy(enabled = on), version = System.currentTimeMillis())) }
                }
            }

            SectionHeader("Защита настроек")
            SoftCard(padding = 12.dp) {
                SwitchRow(Icons.Rounded.Lock, Gradients.Emerald, "Защитить от отключения мошенниками", null, s.elderSelfProtect) { on ->
                    scope.launch { graph.settings.setElderSelfProtect(on) }
                }
                Text(
                    "Если включить, отключить защиту можно будет только через сутки или с разрешения близких. Это спасает, если мошенник уговаривает выключить защиту.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }

            if (s.protectionChange != null && !s.protectionChange!!.canApply(System.currentTimeMillis())) {
                Column(
                    Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(Brand.WarningLight).padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        IconTile(Icons.Rounded.HourglassTop, Gradients.Sunrise, size = 46.dp)
                        Text("Ожидание отключения", style = MaterialTheme.typography.titleLarge, color = Brand.Ink)
                    }
                    Text(
                        "Защита отключится через некоторое время или когда близкие подтвердят. Если это сделали вы не сами — нажмите «Оставить защиту».",
                        style = MaterialTheme.typography.bodyLarge, color = Brand.Ink,
                    )
                    PrimaryButton("Оставить защиту включённой") { scope.launch { graph.settings.setProtectionChange(null) } }
                }
            }

            SectionHeader("О защите")
            SoftCard {
                GlyphLine("Близкие помогают защитить вас от мошенников", Icons.Rounded.FamilyRestroom, Gradients.Emerald, tile = 38.dp)
                GlyphLine("Они видят предупреждения об опасности", Icons.Rounded.Visibility, Gradients.Sky, tile = 38.dp)
                GlyphLine("Но не читают вашу переписку", Icons.Rounded.VisibilityOff, Gradients.Slate, tile = 38.dp)
                TileRow("Что меняли близкие", subtitle = "Все изменения защиты видны вам", icon = Icons.Rounded.History, gradient = Gradients.Slate) { changes = true }
            }

            SoftCard(onClick = { confirmOff = true }, padding = 14.dp) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    IconTile(Icons.Rounded.PowerSettingsNew, Gradients.Danger, size = 42.dp)
                    Text("Отключить защиту", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(26.dp))
                }
            }
            Gap(24.dp)
        }
    }

    if (confirmOff) {
        AlertDialog(
            onDismissRequest = { confirmOff = false },
            icon = { IconTile(Icons.Rounded.PowerSettingsNew, Gradients.Danger, size = 56.dp) },
            title = { Text("Отключить защиту?", textAlign = TextAlign.Center) },
            text = {
                Text(
                    if (s.elderSelfProtect) "Защита отключится через сутки или когда близкие подтвердят. Так безопаснее." else "Вы останетесь без защиты от мошенников. Продолжить?",
                    style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmOff = false
                    scope.launch { graph.protection.requestProtectionOff() }
                }) { Text("Отключить", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmOff = false }) { Text("Не отключать", style = MaterialTheme.typography.labelLarge) } },
            shape = MaterialTheme.shapes.extraLarge,
            containerColor = MaterialTheme.colorScheme.surface,
        )
    }
}

/** A settings row: glyph tile, text and a switch; the whole row toggles. */
@Composable
private fun SwitchRow(icon: ImageVector, gradient: Brush, title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    TileRow(
        title = title, subtitle = subtitle, icon = icon, gradient = gradient,
        trailing = { Switch(checked = checked, onCheckedChange = onChange) },
        onClick = { onChange(!checked) },
    )
}
