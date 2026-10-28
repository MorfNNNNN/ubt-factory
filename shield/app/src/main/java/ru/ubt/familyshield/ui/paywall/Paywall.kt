package ru.ubt.familyshield.ui.paywall

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AppBlocking
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.FamilyRestroom
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.NotificationImportant
import androidx.compose.material.icons.rounded.PhoneDisabled
import androidx.compose.material.icons.rounded.PhonelinkLock
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.RingVolume
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.SupportAgent
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.Icon
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.ubt.familyshield.AppGraph
import ru.ubt.familyshield.core.billing.PurchaseOutcome
import ru.ubt.familyshield.core.billing.StoreProduct
import ru.ubt.familyshield.shared.billing.Feature
import ru.ubt.familyshield.shared.billing.FeatureGroup
import ru.ubt.familyshield.shared.billing.Products
import ru.ubt.familyshield.shared.billing.Tier
import ru.ubt.familyshield.ui.components.Art3D
import ru.ubt.familyshield.ui.components.HeroCard
import ru.ubt.familyshield.ui.components.IconTile
import ru.ubt.familyshield.ui.components.Illustration
import ru.ubt.familyshield.ui.components.NoticeCard
import ru.ubt.familyshield.ui.components.PrimaryButton
import ru.ubt.familyshield.ui.components.SecondaryButton
import ru.ubt.familyshield.ui.components.SectionHeader
import ru.ubt.familyshield.ui.components.SoftCard
import ru.ubt.familyshield.ui.components.Visual
import ru.ubt.familyshield.ui.guardian.DetailScaffold
import ru.ubt.familyshield.ui.guardian.HeroChip
import ru.ubt.familyshield.ui.theme.Brand
import ru.ubt.familyshield.ui.theme.Gradients

/** Why the paywall opened: tailors the headline and highlighted plan. */
enum class UpsellReason(val title: String, val subtitle: String, val highlight: Tier) {
    GENERAL("Вся семья под защитой", "Одна подписка на детей, подростков и родителей", Tier.PREMIUM),
    ELDER("Защита родителей от мошенников", "Блокировка опасных звонков, контроль во время разговора, защита кодов из СМС", Tier.PREMIUM),
    KIDS("Родительский контроль", "Лимиты, расписания, веб-фильтр и оповещения об угрозах", Tier.PREMIUM),
    LOCATION("Местоположение семьи", "Живая карта, история и геозоны: дом, школа, секция", Tier.PREMIUM),
    FAMILY_MAX("Семья Макс", "До 10 устройств, 90 дней истории и приоритетная поддержка", Tier.FAMILY_MAX),
}

private data class Plan(val id: String, val title: String, val note: String?, val badge: String?)

@Composable
fun PaywallScreen(graph: AppGraph, reason: UpsellReason, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val products by produceState(initialValue = emptyList<StoreProduct>()) { value = graph.billing.products() }
    var selected by remember { mutableStateOf(if (reason.highlight == Tier.FAMILY_MAX) Products.FAMILY_MAX_YEAR else Products.PREMIUM_YEAR) }
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

    val plans = listOf(
        Plan(Products.PREMIUM_YEAR, "Премиум на год", "Выгоднее на 40% — меньше 7 ₽ в день", "Хит"),
        Plan(Products.PREMIUM_MONTH, "Премиум на месяц", "Можно отменить в любой момент", null),
        Plan(Products.FAMILY_MAX_YEAR, "Семья Макс на год", "До 10 устройств и 90 дней истории", null),
        Plan(Products.PREMIUM_LIFETIME, "Навсегда", "Один платёж, без подписки", null),
    )

    DetailScaffold(title = null, onBack = onClose) { padding ->
        Column(
            Modifier.padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            HeroCard(
                title = reason.title,
                subtitle = reason.subtitle,
                gradient = Gradients.Night,
                art = {
                    Box(Modifier.size(132.dp)) {
                        Illustration(Art3D.Shield, Modifier.align(Alignment.Center), size = 112.dp)
                        Illustration(Art3D.Crown, Modifier.align(Alignment.TopEnd).offset(x = (-6).dp, y = (-4).dp), size = 56.dp)
                    }
                },
                footer = { HeroChip("Премиум", Icons.Rounded.WorkspacePremium) },
            )

            SoftCard(padding = 16.dp) {
                TrustLine(Icons.Rounded.FamilyRestroom, Gradients.Emerald, "Одна подписка на всю семью", "Дети, подростки и родители — на всех телефонах")
                TrustLine(Icons.Rounded.Lock, Gradients.Slate, "Данные видит только семья", "Всё шифруется прямо на телефонах")
                TrustLine(Icons.Rounded.Verified, Gradients.Sky, "Оплата через RuStore", "Отменить можно в любой момент")
            }

            FeatureList(reason)

            SectionHeader("Выберите тариф")
            plans.forEach { plan ->
                PlanCard(plan, priceOf(plan.id), selected == plan.id) { selected = plan.id }
            }

            status?.let { NoticeCard(it, Icons.Rounded.NotificationImportant, Brand.WarningLight, Brand.Warning, textColor = Brand.Ink) }

            PrimaryButton(if (selected == Products.PREMIUM_LIFETIME) "Купить навсегда — ${priceOf(selected)}" else "Подключить — ${priceOf(selected)}", loading = loading) { buy(selected) }

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
                "Оплата через RuStore. Подписка продлевается автоматически, отменить можно в RuStore в любой момент. " +
                    "SOS, чат семьи и проверка номеров остаются бесплатными всегда.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun TrustLine(icon: androidx.compose.ui.graphics.vector.ImageVector, gradient: androidx.compose.ui.graphics.Brush, title: String, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        IconTile(icon, gradient, size = 40.dp)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun Feature.visual(): Visual = when (this) {
    Feature.CALL_BLOCKING -> Visual(Icons.Rounded.PhoneDisabled, Gradients.Coral)
    Feature.IN_CALL_GUARD -> Visual(Icons.Rounded.AccountBalance, Gradients.Danger)
    Feature.OTP_GUARD -> Visual(Icons.Rounded.Sms, Gradients.Danger)
    Feature.REMOTE_ACCESS_GUARD -> Visual(Icons.Rounded.Devices, Gradients.Danger)
    Feature.SCAM_MESSAGE_SCAN -> Visual(Icons.AutoMirrored.Rounded.Chat, Gradients.Coral)
    Feature.LINK_SCAN -> Visual(Icons.Rounded.Link, Gradients.Violet)
    Feature.SIDELOAD_GUARD -> Visual(Icons.Rounded.Security, Gradients.Sunrise)
    Feature.COOLING_OFF -> Visual(Icons.Rounded.PhonelinkLock, Gradients.Slate)
    Feature.CHECK_IN -> Visual(Icons.Rounded.Favorite, Gradients.Coral)
    Feature.INACTIVITY_ALERT -> Visual(Icons.Rounded.NotificationImportant, Gradients.Sunrise)
    Feature.FALL_DETECTION -> Visual(Icons.Rounded.NotificationImportant, Gradients.Danger)
    Feature.MEDICATIONS -> Visual(Icons.Rounded.Medication, Gradients.Sunrise)
    Feature.REMOTE_SETUP -> Visual(Icons.Rounded.SupportAgent, Gradients.Emerald)
    Feature.APP_LIMITS -> Visual(Icons.Rounded.HourglassBottom, Gradients.Sky)
    Feature.SCHEDULES -> Visual(Icons.Rounded.Schedule, Gradients.Sky)
    Feature.APP_BLOCKING -> Visual(Icons.Rounded.AppBlocking, Gradients.Violet)
    Feature.APP_APPROVAL -> Visual(Icons.Rounded.Apps, Gradients.Violet)
    Feature.WEB_FILTER -> Visual(Icons.Rounded.Language, Gradients.Violet)
    Feature.RISK_ALERTS -> Visual(Icons.Rounded.NotificationImportant, Gradients.Danger)
    Feature.GEOFENCES, Feature.LIVE_LOCATION -> Visual(Icons.Rounded.LocationOn, Gradients.Ocean)
    Feature.LOCATION_HISTORY -> Visual(Icons.Rounded.Route, Gradients.Ocean)
    Feature.REWARDS -> Visual(Icons.Rounded.EmojiEvents, Gradients.Sunrise)
    Feature.UNINSTALL_PROTECTION -> Visual(Icons.Rounded.Block, Gradients.Danger)
    Feature.INSTANT_LOCK -> Visual(Icons.Rounded.Bedtime, Gradients.Night)
    Feature.WEEKLY_REPORTS, Feature.REPORT_EXPORT -> Visual(Icons.Rounded.Insights, Gradients.Emerald)
    Feature.FIND_PHONE -> Visual(Icons.Rounded.RingVolume, Gradients.Coral)
    Feature.PRIORITY_SUPPORT -> Visual(Icons.Rounded.SupportAgent, Gradients.Gold)
    else -> Visual(Icons.Rounded.CheckCircle, Gradients.Emerald)
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
    }.take(10)
    SoftCard(padding = 16.dp) {
        Text("Что входит", style = MaterialTheme.typography.titleMedium)
        features.forEach { f ->
            val v = f.visual()
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconTile(v.icon, v.gradient, size = 34.dp)
                Text(f.titleRu, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun PlanCard(plan: Plan, price: String, selected: Boolean, onClick: () -> Unit) {
    val shape = MaterialTheme.shapes.large
    Box {
        Row(
            Modifier.fillMaxWidth().padding(top = if (plan.badge != null) 10.dp else 0.dp)
                .clip(shape).background(if (selected) Brand.Mint else MaterialTheme.colorScheme.surface)
                .border(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, shape)
                .clickable(role = Role.RadioButton, onClick = onClick)
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(
                if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, modifier = Modifier.size(26.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(plan.title, style = MaterialTheme.typography.titleMedium, color = if (selected) Brand.Ink else MaterialTheme.colorScheme.onSurface)
                if (plan.note != null) Text(plan.note, style = MaterialTheme.typography.bodySmall, color = if (selected) Brand.GreenDark else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(price, style = MaterialTheme.typography.titleMedium, color = if (selected) Brand.Ink else MaterialTheme.colorScheme.onSurface)
        }
        if (plan.badge != null) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(end = 18.dp).clip(CircleShape).background(Gradients.Sunrise)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            ) { Text(plan.badge, style = MaterialTheme.typography.labelSmall, color = Color.White) }
        }
    }
}

/** Small inline lock shown next to premium controls. */
@Composable
fun LockedHint(text: String, onUpgrade: () -> Unit) {
    NoticeCard("$text Откройте в Премиуме.", Icons.Rounded.Lock, Brand.WarningLight, Brand.Warning, textColor = Brand.Ink, onClick = onUpgrade)
}
