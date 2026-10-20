package ru.ubt.familyshield.ui.paywall

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import ru.ubt.familyshield.AppGraph
import ru.ubt.familyshield.core.billing.PurchaseOutcome
import ru.ubt.familyshield.core.billing.StoreProduct
import ru.ubt.familyshield.shared.billing.Feature
import ru.ubt.familyshield.shared.billing.FeatureGroup
import ru.ubt.familyshield.shared.billing.Products
import ru.ubt.familyshield.shared.billing.Tier
import ru.ubt.familyshield.ui.components.Banner
import ru.ubt.familyshield.ui.components.PrimaryButton
import ru.ubt.familyshield.ui.components.ScreenScaffold
import ru.ubt.familyshield.ui.components.SecondaryButton
import ru.ubt.familyshield.ui.components.SectionCard
import ru.ubt.familyshield.ui.theme.Brand

/** Why the paywall opened: tailors the headline and highlighted plan. */
enum class UpsellReason(val title: String, val subtitle: String, val highlight: Tier) {
    GENERAL("Семейный щит Премиум", "Полная защита для всей семьи", Tier.PREMIUM),
    ELDER("Защита родителей от мошенников", "Блокировка опасных звонков, контроль во время разговора, защита кодов из СМС", Tier.PREMIUM),
    KIDS("Родительский контроль", "Лимиты, расписания, веб-фильтр и оповещения об угрозах", Tier.PREMIUM),
    LOCATION("Местоположение семьи", "Live-карта, история и геозоны: дом, школа, секция", Tier.PREMIUM),
    FAMILY_MAX("Семья Макс", "До 10 устройств, 90 дней истории и приоритетная поддержка", Tier.FAMILY_MAX),
}

@Composable
fun PaywallScreen(graph: AppGraph, reason: UpsellReason, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val products by produceState(initialValue = emptyList<StoreProduct>()) { value = graph.billing.products() }
    var selected by remember { mutableStateOf(Products.PREMIUM_YEAR) }
    var status by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    fun priceOf(id: String) = products.firstOrNull { it.id == id }?.priceLabel ?: graph.billing.fallbackPrices[id] ?: ""

    fun buy(id: String) {
        loading = true
        status = null
        scope.launch {
            when (val r = graph.billing.purchase(id)) {
                PurchaseOutcome.Success -> {
                    status = null
                    onClose()
                }
                PurchaseOutcome.Cancelled -> status = null
                is PurchaseOutcome.Pending -> status = r.message
                is PurchaseOutcome.Failed -> status = r.message
            }
            loading = false
        }
    }

    ScreenScaffold(title = "Премиум", onBack = onClose) { padding ->
        Column(
            Modifier.padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(Brand.Green).padding(20.dp)) {
                Column {
                    Text("🛡️", fontSize = 40.sp)
                    Text(reason.title, color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(reason.subtitle, color = Color.White, style = MaterialTheme.typography.bodyLarge)
                }
            }

            FeatureList(reason)

            Text("Выберите тариф", style = MaterialTheme.typography.titleLarge)

            PlanOption("Премиум на год", priceOf(Products.PREMIUM_YEAR), "выгоднее на 40%", selected == Products.PREMIUM_YEAR, true) { selected = Products.PREMIUM_YEAR }
            PlanOption("Премиум на месяц", priceOf(Products.PREMIUM_MONTH), null, selected == Products.PREMIUM_MONTH, false) { selected = Products.PREMIUM_MONTH }
            PlanOption("Семья Макс на год", priceOf(Products.FAMILY_MAX_YEAR), "до 10 устройств", selected == Products.FAMILY_MAX_YEAR, false) { selected = Products.FAMILY_MAX_YEAR }
            PlanOption("Навсегда (разовый платёж)", priceOf(Products.PREMIUM_LIFETIME), "без подписки", selected == Products.PREMIUM_LIFETIME, false) { selected = Products.PREMIUM_LIFETIME }

            status?.let { Banner(it, Brand.WarningLight, textColor = Brand.Warning) }

            PrimaryButton(if (selected == Products.PREMIUM_LIFETIME) "Купить навсегда" else "Оформить подписку", loading = loading) { buy(selected) }

            if (reason == UpsellReason.ELDER || reason == UpsellReason.KIDS) {
                val packId = if (reason == UpsellReason.ELDER) Products.PACK_ELDER else Products.PACK_KIDS
                SecondaryButton("Только этот набор навсегда — ${priceOf(packId)}") { buy(packId) }
            }

            SecondaryButton("Восстановить покупки") {
                scope.launch {
                    loading = true
                    val n = graph.billing.restore()
                    status = if (n > 0) "Покупки восстановлены" else "Активных покупок не найдено"
                    loading = false
                    if (n > 0) onClose()
                }
            }
            Text(
                "Оплата через RuStore. Подписка продлевается автоматически, отменить можно в RuStore. Средства списываются с вашего счёта RuStore.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun FeatureList(reason: UpsellReason) {
    val group = when (reason) {
        UpsellReason.ELDER -> FeatureGroup.ELDER
        UpsellReason.KIDS -> FeatureGroup.KIDS
        else -> null
    }
    val features = Feature.entries.filter {
        it.minTier != Tier.FREE && (group == null || it.group == group || it.group == FeatureGroup.FAMILY)
    }.take(9)
    SectionCard(title = "Что входит") {
        features.forEach { Text("✓  ${it.titleRu}", style = MaterialTheme.typography.bodyLarge) }
    }
}

@Composable
private fun PlanOption(title: String, price: String, badge: String?, selected: Boolean, best: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface),
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    if (best) Box(Modifier.clip(RoundedCornerShape(50)).background(Brand.Gold).padding(horizontal = 8.dp, vertical = 2.dp)) {
                        Text("ХИТ", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
                if (badge != null) Text(badge, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
            Text(price, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

/** Small inline lock shown next to premium controls. */
@Composable
fun LockedHint(text: String, onUpgrade: () -> Unit) {
    Banner("$text Откройте в Премиуме.", Brand.WarningLight, textColor = Brand.Warning, emoji = "🔒", onClick = onUpgrade)
}
