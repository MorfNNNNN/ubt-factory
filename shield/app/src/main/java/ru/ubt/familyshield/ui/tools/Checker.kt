package ru.ubt.familyshield.ui.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import ru.ubt.familyshield.AppGraph
import ru.ubt.familyshield.shared.detect.CallContext
import ru.ubt.familyshield.shared.detect.CallRiskAnalyzer
import ru.ubt.familyshield.shared.detect.NumberKind
import ru.ubt.familyshield.shared.detect.PhoneNumbers
import ru.ubt.familyshield.shared.detect.RiskLevel
import ru.ubt.familyshield.shared.detect.UrlAnalyzer
import ru.ubt.familyshield.ui.components.PrimaryButton
import ru.ubt.familyshield.ui.components.ScreenScaffold
import ru.ubt.familyshield.ui.components.SectionCard
import ru.ubt.familyshield.ui.paywall.PaywallScreen
import ru.ubt.familyshield.ui.paywall.UpsellReason
import ru.ubt.familyshield.ui.theme.Brand
import java.time.LocalDate

private enum class CheckKind(val label: String, val hint: String) {
    MESSAGE("Сообщение", "Вставьте текст подозрительного сообщения или письма"),
    LINK("Ссылка", "Вставьте ссылку из сообщения"),
    NUMBER("Номер", "Введите номер телефона"),
}

private data class CheckResult(val level: RiskLevel, val headline: String, val reasons: List<String>, val advice: String?)

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

    ScreenScaffold(title = "Проверка на мошенничество", onBack = onBack) { padding ->
        Column(Modifier.padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Проверьте, прежде чем довериться. Данные не покидают телефон.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                CheckKind.entries.forEachIndexed { i, k ->
                    SegmentedButton(
                        selected = kind == k,
                        onClick = { kind = k; result = null },
                        shape = SegmentedButtonDefaults.itemShape(i, CheckKind.entries.size),
                    ) { Text(k.label) }
                }
            }
            OutlinedTextField(
                value = input,
                onValueChange = { input = it; result = null },
                label = { Text(kind.hint) },
                modifier = Modifier.fillMaxWidth().height(if (kind == CheckKind.MESSAGE) 160.dp else 64.dp),
                singleLine = kind != CheckKind.MESSAGE,
            )
            if (left != Int.MAX_VALUE) Text("Бесплатных проверок сегодня: $left", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            PrimaryButton("Проверить") { analyze() }

            if (limitReached) {
                SectionCard(title = "Проверки на сегодня закончились", container = Brand.WarningLight) {
                    Text("В Премиуме проверки без ограничений, а сообщения и звонки анализируются автоматически.", color = Brand.Warning)
                    PrimaryButton("Открыть Премиум") { showPaywall = true }
                }
            }

            result?.let { r ->
                val color = levelColor(r.level)
                SectionCard(container = color.copy(alpha = 0.12f)) {
                    Text(levelEmoji(r.level) + "  " + r.headline, style = MaterialTheme.typography.titleLarge, color = color, fontWeight = FontWeight.Bold)
                    r.reasons.forEach { Text("• $it", style = MaterialTheme.typography.bodyLarge) }
                    r.advice?.let {
                        Spacer(Modifier.height(4.dp))
                        Text(it, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    }
                }
                if (kind == CheckKind.NUMBER && r.level >= RiskLevel.MEDIUM) {
                    PrimaryButton("Пожаловаться на номер", color = Brand.Danger) {
                        scope.launch { graph.threats.reportNumber(input.trim(), "мошенники") }
                        result = r.copy(advice = "Спасибо! Жалоба отправлена — она поможет защитить других людей.")
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

private fun levelColor(l: RiskLevel): Color = when (l) {
    RiskLevel.SAFE -> Brand.GreenDark
    RiskLevel.LOW -> Brand.Info
    RiskLevel.MEDIUM -> Brand.Warning
    RiskLevel.HIGH, RiskLevel.CRITICAL -> Brand.Danger
}

private fun levelEmoji(l: RiskLevel) = when (l) {
    RiskLevel.SAFE -> "✅"
    RiskLevel.LOW -> "🔵"
    RiskLevel.MEDIUM -> "⚠️"
    RiskLevel.HIGH, RiskLevel.CRITICAL -> "⛔"
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
