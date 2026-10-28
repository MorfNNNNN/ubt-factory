package ru.ubt.familyshield.ui.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.GppBad
import androidx.compose.material.icons.rounded.GppGood
import androidx.compose.material.icons.rounded.GppMaybe
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.ubt.familyshield.AppGraph
import ru.ubt.familyshield.R
import ru.ubt.familyshield.shared.detect.CallContext
import ru.ubt.familyshield.shared.detect.CallRiskAnalyzer
import ru.ubt.familyshield.shared.detect.NumberKind
import ru.ubt.familyshield.shared.detect.PhoneNumbers
import ru.ubt.familyshield.shared.detect.RiskLevel
import ru.ubt.familyshield.shared.detect.UrlAnalyzer
import ru.ubt.familyshield.ui.components.Art3D
import ru.ubt.familyshield.ui.components.HeroCard
import ru.ubt.familyshield.ui.components.IconTile
import ru.ubt.familyshield.ui.components.Illustration
import ru.ubt.familyshield.ui.components.LabelChip
import ru.ubt.familyshield.ui.components.LottieWithFallback
import ru.ubt.familyshield.ui.components.NoticeCard
import ru.ubt.familyshield.ui.components.PrimaryButton
import ru.ubt.familyshield.ui.components.SectionHeader
import ru.ubt.familyshield.ui.components.SoftCard
import ru.ubt.familyshield.ui.guardian.DetailScaffold
import ru.ubt.familyshield.ui.guardian.PillButton
import ru.ubt.familyshield.ui.paywall.PaywallScreen
import ru.ubt.familyshield.ui.paywall.UpsellReason
import ru.ubt.familyshield.ui.theme.Brand
import ru.ubt.familyshield.ui.theme.Gradients
import java.time.LocalDate

private enum class CheckKind(val label: String, val hint: String, val icon: ImageVector) {
    MESSAGE("Сообщение", "Вставьте текст подозрительного сообщения или письма", Icons.AutoMirrored.Rounded.Chat),
    LINK("Ссылка", "Вставьте ссылку из сообщения", Icons.Rounded.Link),
    NUMBER("Номер", "Введите номер телефона", Icons.Rounded.Phone),
}

private data class CheckResult(val level: RiskLevel, val headline: String, val reasons: List<String>, val advice: String?)

/** Typical tricks to try the checker on, one tap fills the field. */
private val examples = listOf(
    CheckKind.MESSAGE to "Мама, я разбил телефон, это мой новый номер. Срочно переведи 15 000 на карту друга, потом всё объясню",
    CheckKind.MESSAGE to "Ваш аккаунт Госуслуг взломан. Для отмены заявки сообщите код из СМС специалисту, который вам позвонит",
    CheckKind.LINK to "gosuslugi-pereraschet.ru/vyplata",
)

/** Free tool: paste a message, link or number and get a verdict with advice. Analysis is on-device. */
@Composable
fun CheckerScreen(graph: AppGraph, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var kind by remember { mutableStateOf(CheckKind.MESSAGE) }
    var input by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<CheckResult?>(null) }
    var showPaywall by remember { mutableStateOf(false) }
    var limitReached by remember { mutableStateOf(false) }
    val left = graph.entitlements.manualChecksLeft(LocalDate.now().toString())

    if (showPaywall) {
        PaywallScreen(graph, UpsellReason.GENERAL) { showPaywall = false }
        return
    }

    fun analyze() {
        if (input.isBlank()) return
        scope.launch {
            if (!graph.entitlements.consumeManualCheck(LocalDate.now().toString())) {
                limitReached = true
                return@launch
            }
            result = when (kind) {
                CheckKind.MESSAGE -> {
                    val v = graph.scamAnalyzer.analyze(input)
                    CheckResult(v.level, v.level.titleRu, v.reasons, v.advice ?: defaultAdvice(v.level))
                }
                CheckKind.LINK -> {
                    val v = UrlAnalyzer.analyze(input.trim()) { graph.threats.isKnownBadDomain(it) }
                    CheckResult(
                        if (v.official) RiskLevel.SAFE else v.level,
                        if (v.official) "Официальный сайт" else v.level.titleRu,
                        v.reasons,
                        if (v.official) "Это настоящий сайт. Всё равно вводите данные, только если вы сами открыли приложение или набрали адрес." else linkAdvice(v.level),
                    )
                }
                CheckKind.NUMBER -> {
                    val info = PhoneNumbers.parse(input.trim())
                    if (info.kind == NumberKind.INVALID) {
                        CheckResult(RiskLevel.SAFE, "Не похоже на номер", listOf("Проверьте правильность ввода"), null)
                    } else {
                        val threat = graph.threats.lookupNumber(info)
                        val risk = CallRiskAnalyzer.assess(info, CallContext(inContacts = false, threatCategory = threat?.category, threatReports = threat?.reports ?: 0))
                        CheckResult(risk.level, "${info.display()} — ${risk.level.titleRu}", risk.reasons + listOfNotNull(info.countryRu?.let { "Страна: $it" }), numberAdvice(risk.level))
                    }
                }
            }
        }
    }

    DetailScaffold(title = "Проверка на обман", onBack = onBack) { padding ->
        Column(
            Modifier.padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            HeroCard(
                title = "Проверьте, прежде чем довериться",
                subtitle = "Сообщение, ссылка или номер. Проверка идёт прямо на телефоне",
                gradient = Gradients.Violet,
                art = { LottieWithFallback(R.raw.lottie_scanning, 132.dp) { Illustration(Art3D.Shield, size = 104.dp) } },
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CheckKind.entries.forEach { k ->
                    KindCard(k, selected = kind == k, modifier = Modifier.weight(1f)) {
                        kind = k
                        result = null
                    }
                }
            }

            OutlinedTextField(
                value = input,
                onValueChange = { input = it; result = null },
                placeholder = { Text(kind.hint) },
                modifier = Modifier.fillMaxWidth().heightIn(min = if (kind == CheckKind.MESSAGE) 150.dp else 60.dp),
                singleLine = kind != CheckKind.MESSAGE,
                shape = MaterialTheme.shapes.medium,
                keyboardOptions = KeyboardOptions(keyboardType = if (kind == CheckKind.NUMBER) KeyboardType.Phone else if (kind == CheckKind.LINK) KeyboardType.Uri else KeyboardType.Text),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                ),
            )
            if (left != Int.MAX_VALUE) {
                LabelChip("Бесплатных проверок сегодня: $left", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant, icon = Icons.Rounded.Search)
            }
            PrimaryButton("Проверить") { analyze() }

            if (limitReached) {
                SoftCard(padding = 16.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Illustration(Art3D.Crown, size = 52.dp)
                        Column(Modifier.weight(1f)) {
                            Text("Проверки на сегодня закончились", style = MaterialTheme.typography.titleMedium)
                            Text("В Премиуме — без ограничений, а звонки и сообщения проверяются сами.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    PillButton("Открыть Премиум", modifier = Modifier.fillMaxWidth(), fill = Gradients.Gold, contentColor = Brand.Ink) { showPaywall = true }
                }
            }

            result?.let { r ->
                ResultCard(r)
                if (kind == CheckKind.NUMBER && r.level >= RiskLevel.MEDIUM) {
                    PillButton("Пожаловаться на номер", modifier = Modifier.fillMaxWidth(), icon = Icons.Rounded.Flag, fill = Gradients.Danger) {
                        scope.launch { graph.threats.reportNumber(input.trim(), "мошенники") }
                        result = r.copy(advice = "Спасибо! Жалоба отправлена — она поможет защитить других людей.")
                    }
                }
            }

            if (result == null) {
                SectionHeader("Попробуйте на примерах")
                examples.forEach { (k, text) ->
                    ExampleRow(k, text) {
                        kind = k
                        input = text
                        result = null
                    }
                }
            }

            NoticeCard(
                "Текст не отправляется на сервер: проверка работает на телефоне, а номера сверяются с базой жалоб.",
                Icons.Rounded.Lock, MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun KindCard(kind: CheckKind, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = MaterialTheme.shapes.medium
    Column(
        modifier.clip(shape)
            .background(if (selected) Brand.Mint else MaterialTheme.colorScheme.surface)
            .border(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        IconTile(kind.icon, if (selected) Gradients.Emerald else Gradients.Slate, size = 36.dp)
        Text(
            kind.label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = if (selected) Brand.GreenDark else MaterialTheme.colorScheme.onSurface,
        )
    }
}

private class LevelLook(val icon: ImageVector, val gradient: Brush, val tint: Color, val container: Color)

private fun look(l: RiskLevel): LevelLook = when (l) {
    RiskLevel.SAFE -> LevelLook(Icons.Rounded.GppGood, Gradients.Emerald, Brand.GreenDark, Brand.GreenLight)
    RiskLevel.LOW -> LevelLook(Icons.Rounded.Info, Gradients.Sky, Brand.Info, Color(0xFFE0F2FE))
    RiskLevel.MEDIUM -> LevelLook(Icons.Rounded.GppMaybe, Gradients.Sunrise, Brand.Warning, Brand.WarningLight)
    RiskLevel.HIGH, RiskLevel.CRITICAL -> LevelLook(Icons.Rounded.GppBad, Gradients.Danger, Brand.Danger, Brand.DangerLight)
}

@Composable
private fun ResultCard(r: CheckResult) {
    val l = look(r.level)
    SoftCard(padding = 18.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            IconTile(l.icon, l.gradient, size = 54.dp)
            Text(r.headline, style = MaterialTheme.typography.titleLarge, color = l.tint, modifier = Modifier.weight(1f))
        }
        r.reasons.forEach { reason ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                androidx.compose.foundation.layout.Box(Modifier.padding(top = 8.dp).size(7.dp).clip(CircleShape).background(l.tint))
                Text(reason, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            }
        }
        r.advice?.let {
            Row(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(l.container).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Illustration(Art3D.Bulb, size = 40.dp)
                Text(it, style = MaterialTheme.typography.bodyMedium, color = Brand.Ink, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ExampleRow(kind: CheckKind, text: String, onClick: () -> Unit) {
    SoftCard(onClick = onClick, padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconTile(kind.icon, Gradients.Violet, size = 36.dp)
            Text(text, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Icon(Icons.Rounded.Search, contentDescription = "Проверить пример", tint = MaterialTheme.colorScheme.primary)
        }
    }
}

private fun defaultAdvice(l: RiskLevel) = when (l) {
    RiskLevel.SAFE, RiskLevel.LOW -> "Явных признаков обмана нет, но будьте внимательны: не сообщайте коды и не переводите деньги по просьбе из сообщения."
    else -> "Похоже на мошенничество. Не отвечайте, не переходите по ссылкам, не сообщайте коды. Свяжитесь с организацией по официальному номеру."
}

private fun linkAdvice(l: RiskLevel) = when (l) {
    RiskLevel.SAFE, RiskLevel.LOW -> "Будьте осторожны: не вводите данные карты и коды, если сомневаетесь в сайте."
    else -> "Не открывайте эту ссылку и не вводите на сайте данные карты, логины и коды. Зайдите на сайт организации сами."
}

private fun numberAdvice(l: RiskLevel) = when (l) {
    RiskLevel.SAFE, RiskLevel.LOW -> "Если вам звонили с этого номера и просили коды или деньги — это мошенники, несмотря на оценку."
    else -> "Не перезванивайте и не выполняйте просьбы звонившего. Банк и госорганы не звонят с таких номеров."
}
