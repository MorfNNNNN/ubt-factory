package ru.ubt.familyshield.ui.location

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.content.Intent
import android.net.Uri
import ru.ubt.familyshield.AppGraph
import ru.ubt.familyshield.core.db.LocationEntity
import ru.ubt.familyshield.core.db.MemberEntity
import ru.ubt.familyshield.core.family.childPolicy
import ru.ubt.familyshield.core.family.elderPolicy
import ru.ubt.familyshield.core.family.memberRole
import ru.ubt.familyshield.shared.billing.Feature
import ru.ubt.familyshield.shared.model.GeoPoint
import ru.ubt.familyshield.shared.model.MemberRole
import ru.ubt.familyshield.ui.components.Banner
import ru.ubt.familyshield.ui.components.EmptyState
import ru.ubt.familyshield.ui.components.PrimaryButton
import ru.ubt.familyshield.ui.components.ScreenScaffold
import ru.ubt.familyshield.ui.components.SectionCard
import ru.ubt.familyshield.ui.components.relativeTime
import ru.ubt.familyshield.ui.paywall.PaywallScreen
import ru.ubt.familyshield.ui.paywall.UpsellReason
import ru.ubt.familyshield.ui.theme.Brand

/**
 * Location view. We don't bundle a map SDK (keeps the app light and avoids Google dependencies);
 * instead we show the latest address and history and open Яндекс/2ГИС/system maps on demand.
 */
@Composable
fun LocationMapScreen(graph: AppGraph, member: MemberEntity, onBack: () -> Unit) {
    val context = LocalContext.current
    val history by produceState(initialValue = emptyList<LocationEntity>(), member.deviceId) {
        val days = graph.entitlements.limits().historyDays.toLong()
        graph.db.locations().observeSince(member.deviceId, System.currentTimeMillis() - days * 86_400_000L).collect { value = it }
    }
    val hasLive = graph.entitlements.has(Feature.LIVE_LOCATION)
    val fences = if (member.memberRole == MemberRole.CHILD) member.childPolicy().geofences else member.elderPolicy().geofences

    ScreenScaffold(title = "Где ${member.displayName}", onBack = onBack) { padding ->
        Column(Modifier.padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val last = member.lastLat?.let { lat -> member.lastLon?.let { lon -> GeoPoint(lat, lon) } }
            if (last == null) {
                EmptyState("📍", "Местоположение недоступно", "Как только телефон поделится координатами, они появятся здесь. Нажмите «Обновить», чтобы запросить сейчас.")
            } else {
                SectionCard {
                    Text(member.lastAddress ?: "%.5f, %.5f".format(last.lat, last.lon), style = MaterialTheme.typography.titleMedium)
                    Text("Обновлено ${relativeTime(member.lastLocationAt)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    PrimaryButton("Открыть на карте") {
                        val uri = Uri.parse("geo:${last.lat},${last.lon}?q=${last.lat},${last.lon}(${member.displayName})")
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                            .onFailure { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://yandex.ru/maps/?pt=${last.lon},${last.lat}&z=17"))) } }
                    }
                }
            }
            if (!hasLive) {
                Banner("История перемещений, живая карта и геозоны (дом, школа, секция) доступны в Премиуме.", Brand.GreenLight, textColor = Brand.GreenDark, emoji = "🗺️")
            }
            if (fences.isNotEmpty()) {
                SectionCard(title = "Геозоны") { fences.forEach { Text("📍 ${it.name} · ${it.radiusMeters} м", style = MaterialTheme.typography.bodyLarge) } }
            }
            if (history.size > 1) {
                SectionCard(title = "История (${history.size})") {
                    history.take(20).forEach { h ->
                        Text("${relativeTime(h.time)} — ${h.address ?: "%.4f, %.4f".format(h.lat, h.lon)}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
