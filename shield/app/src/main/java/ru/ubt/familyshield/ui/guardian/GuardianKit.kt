package ru.ubt.familyshield.ui.guardian

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Battery2Bar
import androidx.compose.material.icons.rounded.Battery4Bar
import androidx.compose.material.icons.rounded.Battery6Bar
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ru.ubt.familyshield.core.db.MemberEntity
import ru.ubt.familyshield.shared.ShieldJson
import ru.ubt.familyshield.shared.model.AlertSeverity
import ru.ubt.familyshield.shared.model.DeviceStatus
import ru.ubt.familyshield.ui.components.IconTile
import ru.ubt.familyshield.ui.components.SoftCard
import ru.ubt.familyshield.ui.components.gradient
import ru.ubt.familyshield.ui.components.relativeTime
import ru.ubt.familyshield.ui.components.severityLabel
import ru.ubt.familyshield.ui.theme.Brand
import ru.ubt.familyshield.ui.theme.Gradients
import java.time.LocalTime
import kotlin.math.abs

/*
 * Premium building blocks shared by the guardian screens (and the chat, location and invite screens
 * they open). Everything is drawn from the design-system components and theme tokens.
 */

// ---- Page structure ------------------------------------------------------------------------------

/** Large ExtraBold title that opens every top-level guardian tab. */
@Composable
internal fun PageHeader(
    title: String,
    modifier: Modifier = Modifier,
    overline: String? = null,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 18.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (overline != null) {
                Text(overline, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(title, style = MaterialTheme.typography.headlineLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

/** Scaffold for nested screens: a round back button and a title instead of the stock Material app bar. */
@Composable
internal fun DetailScaffold(
    title: String?,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Row(
                Modifier.fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                RoundIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Назад", onBack)
                Text(
                    title.orEmpty(), style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                actions()
            }
        },
        content = content,
    )
}

/** White round button with a soft brand shadow (back, close). */
@Composable
internal fun RoundIconButton(icon: ImageVector, contentDescription: String?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.size(44.dp)
            .shadow(6.dp, CircleShape, ambientColor = Brand.Green.copy(alpha = 0.18f), spotColor = Brand.Green.copy(alpha = 0.18f))
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp))
    }
}

/** A white card section: optional heading, then rows. */
@Composable
internal fun GroupCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    SoftCard(modifier, padding = 16.dp) {
        if (title != null || subtitle != null) {
            Column(Modifier.padding(horizontal = 2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (title != null) Text(title, style = MaterialTheme.typography.titleMedium)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), content = content)
    }
}

/** Centered artwork (Lottie or illustration), title, text and an optional action. */
@Composable
internal fun ArtEmptyState(
    title: String,
    text: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
    art: @Composable () -> Unit,
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        art()
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(4.dp))
            action()
        }
    }
}

/** Soft concentric glow behind hero artwork; reads as a calm pulse around the animation. */
@Composable
internal fun BoxScope.HeroHalo() {
    Box(Modifier.align(Alignment.Center).size(136.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f)))
    Box(Modifier.align(Alignment.Center).size(104.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f)))
}

// ---- Rows and controls ---------------------------------------------------------------------------

/** Setting row: glyph tile, title, hint, premium chip and a switch. The whole row toggles. */
@Composable
internal fun IconToggleRow(
    title: String,
    icon: ImageVector,
    gradient: Brush,
    checked: Boolean,
    subtitle: String? = null,
    enabled: Boolean = true,
    locked: Boolean = false,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 8.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        IconTile(icon, gradient, size = 42.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (locked) PremiumChip(Modifier.padding(top = 4.dp))
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled, colors = shieldSwitchColors())
    }
}

@Composable
internal fun shieldSwitchColors() = SwitchDefaults.colors(
    checkedThumbColor = Color.White,
    checkedTrackColor = MaterialTheme.colorScheme.primary,
    checkedBorderColor = Color.Transparent,
    uncheckedThumbColor = Color.White,
    uncheckedTrackColor = MaterialTheme.colorScheme.outline,
    uncheckedBorderColor = Color.Transparent,
)

/** Limit slider aligned under its toggle row; snaps to [step] like the original slider row. */
@Composable
internal fun LimitSliderRow(title: String, value: Int, range: IntRange, step: Int, label: (Int) -> String, onChange: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = 58.dp, end = 4.dp, bottom = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text(label(value), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange((Math.round(it / step) * step).coerceIn(range.first, range.last)) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = ((range.last - range.first) / step - 1).coerceAtLeast(0),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
        )
    }
}

/** Compact action button. [fill] is a brand gradient or a solid brush for tinted variants. */
@Composable
internal fun PillButton(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    fill: Brush = Gradients.Emerald,
    contentColor: Color = Color.White,
    elevated: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        modifier.height(48.dp)
            .let { if (elevated) it.shadow(8.dp, CircleShape, ambientColor = Brand.GreenDark.copy(alpha = 0.3f), spotColor = Brand.GreenDark.copy(alpha = 0.3f)) else it }
            .clip(CircleShape)
            .background(fill)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = contentColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Neutral tinted pill: a quieter companion for [PillButton]. */
@Composable
internal fun tintedFill(): Brush = SolidColor(MaterialTheme.colorScheme.surfaceVariant)

/** Destructive text action (remove from family, leave). */
@Composable
internal fun DangerAction(text: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(CircleShape).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, contentDescription = null, tint = Brand.Danger, modifier = Modifier.size(20.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = Brand.Danger)
    }
}

// ---- Chips and badges ----------------------------------------------------------------------------

/** Gold "Премиум" lock chip for features outside the current plan. */
@Composable
internal fun PremiumChip(modifier: Modifier = Modifier) {
    Row(
        modifier.clip(CircleShape).background(Gradients.Gold).padding(start = 7.dp, end = 9.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(Icons.Rounded.Lock, contentDescription = null, tint = Brand.Ink, modifier = Modifier.size(12.dp))
        Text("Премиум", style = MaterialTheme.typography.labelSmall, color = Brand.Ink)
    }
}

/** Soft chip tinted with a brand colour: roles, statuses, tags. */
@Composable
internal fun TintChip(text: String, color: Color, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Row(
        modifier.clip(CircleShape).background(color.copy(alpha = 0.12f)).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = color, maxLines = 1)
    }
}

/** Neutral fact chip with a coloured glyph: battery, place, screen time. */
@Composable
internal fun InfoChip(icon: ImageVector, text: String, tint: Color, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Translucent white chip for facts shown on a gradient hero. */
@Composable
internal fun HeroChip(text: String, icon: ImageVector? = null) {
    Row(
        Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.18f)).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = Color.White, maxLines = 1)
    }
}

/** Gradient severity badge; info-level events need none. */
@Composable
internal fun SeverityChip(severity: AlertSeverity) {
    Box(Modifier.clip(CircleShape).background(severity.gradient()).padding(horizontal = 10.dp, vertical = 4.dp)) {
        Text(severityLabel(severity), style = MaterialTheme.typography.labelSmall, color = Color.White, maxLines = 1)
    }
}

/** Round counter (unread events, pending requests). */
@Composable
internal fun CountBadge(count: Int, color: Color = Brand.Danger, modifier: Modifier = Modifier) {
    Box(
        modifier.heightIn(min = 20.dp).widthIn(min = 20.dp)
            .clip(CircleShape).background(color)
            .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
            .padding(horizontal = 6.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(count.coerceAtMost(99).toString(), style = MaterialTheme.typography.labelSmall, color = Color.White)
    }
}

/** A quoted fragment (message excerpt, a child's reason) on a soft block with an accent bar. */
@Composable
internal fun QuoteBlock(text: String, accent: Color, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().height(IntrinsicSize.Min)
            .clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(accent))
        Text(
            text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        )
    }
}

// ---- Facts about a family member -----------------------------------------------------------------

internal fun MemberEntity.deviceStatus(): DeviceStatus? =
    statusJson?.let { runCatching { ShieldJson.decodeFromString(DeviceStatus.serializer(), it) }.getOrNull() }

internal data class Presence(val text: String, val color: Color)

/** "На связи 3 мин назад", coloured by how fresh the last contact is. */
internal fun presenceOf(lastSeenAt: Long?, now: Long = System.currentTimeMillis()): Presence {
    if (lastSeenAt == null || lastSeenAt == 0L) return Presence("Нет данных", Brand.Slate)
    val minutes = (now - lastSeenAt).coerceAtLeast(0) / 60_000
    val color = when {
        minutes <= 20 -> Brand.Green
        minutes <= 24 * 60 -> Brand.Warning
        else -> Brand.Danger
    }
    return Presence("На связи ${relativeTime(lastSeenAt, now)}", color)
}

internal fun batteryIcon(percent: Int, charging: Boolean): ImageVector = when {
    charging -> Icons.Rounded.BatteryChargingFull
    percent >= 90 -> Icons.Rounded.BatteryFull
    percent >= 65 -> Icons.Rounded.Battery6Bar
    percent >= 35 -> Icons.Rounded.Battery4Bar
    percent > 15 -> Icons.Rounded.Battery2Bar
    else -> Icons.Rounded.BatteryAlert
}

internal fun batteryTint(percent: Int, charging: Boolean): Color = when {
    charging -> Brand.Green
    percent in 0..15 -> Brand.Danger
    percent <= 30 -> Brand.Warning
    else -> Brand.Green
}

/** Network names come from the device in Russian ("мобильная сеть"); older builds sent "wifi". */
internal fun networkLabel(raw: String): String = when (raw.trim().lowercase()) {
    "wifi", "wi-fi", "wi‑fi" -> "Wi‑Fi"
    "" -> "Сеть"
    else -> raw.trim().replaceFirstChar { it.titlecase() }
}

internal fun greeting(name: String, hour: Int = LocalTime.now().hour): String {
    val hello = when (hour) {
        in 5..11 -> "Доброе утро"
        in 12..17 -> "Добрый день"
        in 18..22 -> "Добрый вечер"
        else -> "Доброй ночи"
    }
    return if (name.isBlank()) hello else "$hello, $name"
}

/** Russian plural form for [n]: plural(5, "запрос", "запроса", "запросов") = "запросов". */
internal fun plural(n: Int, one: String, few: String, many: String): String {
    val mod100 = abs(n) % 100
    val mod10 = mod100 % 10
    return when {
        mod100 in 11..14 -> many
        mod10 == 1 -> one
        mod10 in 2..4 -> few
        else -> many
    }
}

/** Width of an [ru.ubt.familyshield.ui.components.AvatarStack] of [count] portraits, which overlap by 38 %. */
internal fun avatarStackWidth(count: Int, size: Dp): Dp = if (count <= 0) 0.dp else size + size * 0.62f * (count - 1)
