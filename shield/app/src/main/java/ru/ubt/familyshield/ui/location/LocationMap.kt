package ru.ubt.familyshield.ui.location

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.SportsSoccer
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.ubt.familyshield.AppGraph
import ru.ubt.familyshield.R
import ru.ubt.familyshield.core.db.LocationEntity
import ru.ubt.familyshield.core.db.MemberEntity
import ru.ubt.familyshield.core.family.childPolicy
import ru.ubt.familyshield.core.family.elderPolicy
import ru.ubt.familyshield.core.family.memberRole
import ru.ubt.familyshield.shared.billing.Feature
import ru.ubt.familyshield.shared.model.Command
import ru.ubt.familyshield.shared.model.Geofence
import ru.ubt.familyshield.shared.model.GeoPoint
import ru.ubt.familyshield.shared.model.MemberRole
import ru.ubt.familyshield.ui.components.Art3D
import ru.ubt.familyshield.ui.components.HeroCard
import ru.ubt.familyshield.ui.components.Illustration
import ru.ubt.familyshield.ui.components.LottieWithFallback
import ru.ubt.familyshield.ui.components.SectionHeader
import ru.ubt.familyshield.ui.components.SoftCard
import ru.ubt.familyshield.ui.components.TileRow
import ru.ubt.familyshield.ui.components.relativeTime
import ru.ubt.familyshield.ui.components.timeOf
import ru.ubt.familyshield.ui.guardian.DetailScaffold
import ru.ubt.familyshield.ui.guardian.PillButton
import ru.ubt.familyshield.ui.paywall.PaywallScreen
import ru.ubt.familyshield.ui.paywall.UpsellReason
import ru.ubt.familyshield.ui.theme.Brand
import ru.ubt.familyshield.ui.theme.Gradients
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * Location view. We don't bundle a map SDK (keeps the app light and avoids Google dependencies);
 * instead we show the latest place and the day's timeline and open Яндекс/2ГИС/system maps on demand.
 */
@Composable
fun LocationMapScreen(graph: AppGraph, member: MemberEntity, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val history by produceState(initialValue = emptyList<LocationEntity>(), member.deviceId) {
        val days = graph.entitlements.limits().historyDays.toLong()
        graph.db.locations().observeSince(member.deviceId, System.currentTimeMillis() - days * 86_400_000L).collect { value = it }
    }
    val hasLive = graph.entitlements.has(Feature.LIVE_LOCATION)
    val fences = if (member.memberRole == MemberRole.CHILD) member.childPolicy().geofences else member.elderPolicy().geofences
    var paywall by remember { mutableStateOf(false) }
    var requested by remember { mutableStateOf(false) }
    if (paywall) { PaywallScreen(graph, UpsellReason.LOCATION) { paywall = false }; return }

    val last = member.lastLat?.let { lat -> member.lastLon?.let { lon -> GeoPoint(lat, lon) } }

    fun openMap(p: GeoPoint) {
        val uri = Uri.parse("geo:${p.lat},${p.lon}?q=${p.lat},${p.lon}(${member.displayName})")
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
            .onFailure { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://yandex.ru/maps/?pt=${p.lon},${p.lat}&z=17"))) } }
    }

    DetailScaffold(title = "Где ${member.displayName}", onBack = onBack) { padding ->
        Column(
            Modifier.padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            HeroCard(
                title = if (last == null) "Пока неизвестно" else member.lastAddress?.takeIf { it.isNotBlank() } ?: "%.5f, %.5f".format(last.lat, last.lon),
                subtitle = when {
                    requested -> "Запросили свежие координаты — обновим, как только телефон ответит"
                    last == null -> "Нажмите «Обновить», и телефон пришлёт координаты"
                    else -> "Обновлено ${relativeTime(member.lastLocationAt)}" + (member.lastAccuracy?.let { " · точность ${it.toInt()} м" } ?: "")
                },
                gradient = Gradients.Ocean,
                art = { LottieWithFallback(R.raw.lottie_location_ping, 140.dp) { Illustration(Art3D.MapPin, size = 104.dp) } },
                footer = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (last != null) PillButton("На карте", icon = Icons.Rounded.Map, fill = SolidColor(Color.White), contentColor = Brand.Info) { openMap(last) }
                        Box(
                            Modifier.size(48.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.22f))
                                .clickable(role = Role.Button, onClickLabel = "Обновить местоположение") {
                                    requested = true
                                    scope.launch { graph.guardian.command(member.deviceId, Command.RequestLocation(UUID.randomUUID().toString())) }
                                },
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Rounded.MyLocation, contentDescription = "Обновить местоположение", tint = Color.White, modifier = Modifier.size(22.dp)) }
                    }
                },
            )

            if (!hasLive) {
                SoftCard(onClick = { paywall = true }, padding = 16.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Illustration(Art3D.Crown, size = 52.dp)
                        Column(Modifier.weight(1f)) {
                            Text("История и геозоны", style = MaterialTheme.typography.titleMedium)
                            Text("Маршрут за день и уведомления «пришла в школу» — в Премиуме", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            if (fences.isNotEmpty()) {
                SectionHeader("Геозоны")
                SoftCard(padding = 12.dp) {
                    fences.forEach { f ->
                        val (icon, gradient) = fenceVisual(f)
                        TileRow(f.name, subtitle = "Радиус ${f.radiusMeters} м" + fenceNotify(f), icon = icon, gradient = gradient)
                    }
                }
            }

            val stops = remember(history) { timeline(history) }
            if (stops.isNotEmpty()) {
                SectionHeader("Хронология")
                SoftCard(padding = 16.dp) {
                    stops.forEachIndexed { i, stop ->
                        TimelineRow(stop, first = i == 0, last = i == stops.lastIndex, now = i == 0 && stop.until >= System.currentTimeMillis() - 30 * 60_000L)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

private fun fenceVisual(f: Geofence) = when {
    f.name.contains("дом", ignoreCase = true) -> Icons.Rounded.Home to Gradients.Emerald
    f.name.contains("школ", ignoreCase = true) || f.name.contains("сад", ignoreCase = true) -> Icons.Rounded.School to Gradients.Sky
    f.name.contains("секц", ignoreCase = true) || f.name.contains("спорт", ignoreCase = true) -> Icons.Rounded.SportsSoccer to Gradients.Sunrise
    else -> Icons.Rounded.Place to Gradients.Ocean
}

private fun fenceNotify(f: Geofence) = when {
    f.notifyOnEnter && f.notifyOnExit -> " · сообщим о приходе и уходе"
    f.notifyOnEnter -> " · сообщим о приходе"
    f.notifyOnExit -> " · сообщим об уходе"
    else -> ""
}

/** A place the person stayed at: consecutive points at the same address (or within 150 m) merge into one stop. */
private data class Stop(val place: String, val from: Long, val until: Long)

private fun timeline(points: List<LocationEntity>): List<Stop> {
    val stops = mutableListOf<Stop>()
    var current: Stop? = null
    var anchor: LocationEntity? = null
    // Newest first, as the DAO returns them.
    for (p in points.sortedByDescending { it.time }) {
        val place = p.address?.takeIf { it.isNotBlank() } ?: "%.4f, %.4f".format(p.lat, p.lon)
        val a = anchor
        val same = current != null && a != null && (place == current.place || distanceMeters(a, p) < 150)
        if (same) {
            current = current!!.copy(from = p.time)
        } else {
            current?.let { stops += it }
            current = Stop(place, p.time, p.time)
            anchor = p
        }
    }
    current?.let { stops += it }
    return stops.take(12)
}

private fun distanceMeters(a: LocationEntity, b: LocationEntity): Double {
    val dLat = (a.lat - b.lat) * 111_320.0
    val dLon = (a.lon - b.lon) * 111_320.0 * cos(Math.toRadians((a.lat + b.lat) / 2))
    return sqrt(dLat * dLat + dLon * dLon)
}

@Composable
private fun TimelineRow(stop: Stop, first: Boolean, last: Boolean, now: Boolean) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.width(30.dp).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
            Box(
                Modifier.width(3.dp).fillMaxHeight().padding(top = if (first) 14.dp else 0.dp, bottom = if (last) 0.dp else 0.dp)
                    .background(if (last && !first) Color.Transparent else MaterialTheme.colorScheme.outlineVariant),
            )
            Box(
                Modifier.padding(top = 4.dp).size(if (now) 22.dp else 16.dp).clip(CircleShape)
                    .background(if (now) Gradients.Ocean else SolidColor(MaterialTheme.colorScheme.outline)),
            )
        }
        Column(Modifier.weight(1f).padding(bottom = if (last) 0.dp else 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stop.place, style = MaterialTheme.typography.titleSmall)
            Text(
                (if (now) "Сейчас здесь · с ${timeOf(stop.from)}" else "${dayLabel(stop.from)}${timeOf(stop.from)}" + if (stop.until - stop.from > 5 * 60_000L) " – ${timeOf(stop.until)}" else ""),
                style = MaterialTheme.typography.bodySmall,
                color = if (now) Brand.Info else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun dayLabel(ts: Long): String {
    val day = Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).toLocalDate()
    val today = LocalDate.now()
    return when (day) {
        today -> ""
        today.minusDays(1) -> "Вчера, "
        else -> "${day.dayOfMonth}.${"%02d".format(day.monthValue)}, "
    }
}
