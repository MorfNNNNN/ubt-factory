package ru.ubt.familyshield.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.ubt.familyshield.shared.model.AlertSeverity
import ru.ubt.familyshield.ui.theme.Brand
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.*
import ru.ubt.familyshield.ui.theme.Gradients
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.shadow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад") }
                },
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = bottomBar,
        containerColor = MaterialTheme.colorScheme.background,
        content = content,
    )
}

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    container: Color = MaterialTheme.colorScheme.surface,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth()
            .shadow(8.dp, MaterialTheme.shapes.large, ambientColor = Brand.Green.copy(alpha = 0.12f), spotColor = Brand.Green.copy(alpha = 0.12f))
            .let { if (onClick != null) it.clip(MaterialTheme.shapes.large).clickable(onClick = onClick) else it },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = container),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (title != null) Text(title, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}

@Composable
fun Pill(text: String, color: Color, textColor: Color = Color.White) {
    Box(Modifier.clip(RoundedCornerShape(50)).background(color).padding(horizontal = 10.dp, vertical = 4.dp)) {
        Text(text, color = textColor, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

/** Family member portrait; [avatar] is an "avatar:<id>" value (older emoji values are mapped). */
@Composable
fun Avatar(avatar: String, size: Dp = 48.dp, role: String? = null) = AvatarImage(avatar, role, size)

@Composable
fun NavRow(title: String, subtitle: String? = null, icon: ImageVector? = null, emoji: String? = null, trailing: String? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable(onClick = onClick).padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        when {
            icon != null -> IconTile(icon, Gradients.Emerald, size = 40.dp)
            emoji != null -> emojiVisual(emoji).let { IconTile(it.icon, it.gradient, size = 40.dp) }
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (trailing != null) Text(trailing, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ToggleRow(title: String, subtitle: String? = null, checked: Boolean, enabled: Boolean = true, locked: Boolean = false, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable(enabled = enabled) { onChange(!checked) }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                if (locked) {
                    Spacer(Modifier.width(6.dp))
                    Pill("Премиум", Brand.Gold)
                }
            }
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
fun SliderRow(title: String, value: Int, range: IntRange, step: Int, label: (Int) -> String, onChange: (Int) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth()) {
            Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(label(value), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange((Math.round(it / step) * step).coerceIn(range.first, range.last)) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = ((range.last - range.first) / step - 1).coerceAtLeast(0),
        )
    }
}

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, loading: Boolean = false, enabled: Boolean = true, color: Color? = null, onClick: () -> Unit) {
    val active = enabled && !loading
    val fill = when {
        !active -> Brush.linearGradient(listOf(Color(0xFFCBD5E1), Color(0xFF94A3B8)))
        color != null -> Brush.linearGradient(listOf(color, color))
        else -> Gradients.Emerald
    }
    Box(
        modifier.fillMaxWidth().height(58.dp)
            .shadow(if (active) 10.dp else 0.dp, MaterialTheme.shapes.medium, ambientColor = (color ?: Brand.Green).copy(alpha = 0.4f), spotColor = (color ?: Brand.Green).copy(alpha = 0.4f))
            .clip(MaterialTheme.shapes.medium).background(fill)
            .clickable(enabled = active, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = Color.White)
        else Text(text, style = MaterialTheme.typography.labelLarge, fontSize = 17.sp, color = Color.White)
    }
}

@Composable
fun SecondaryButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick, modifier = modifier.fillMaxWidth().height(54.dp), shape = MaterialTheme.shapes.medium,
        border = androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun EmptyState(emoji: String, title: String, text: String, action: (@Composable () -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        emojiVisual(emoji).let { IconTile(it.icon, it.gradient, size = 84.dp) }
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        action?.invoke()
    }
}

@Composable
fun ProgressBar(fraction: Float, color: Color = MaterialTheme.colorScheme.primary) {
    LinearProgressIndicator(
        progress = { fraction.coerceIn(0f, 1f) },
        modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)),
        color = color,
        trackColor = MaterialTheme.colorScheme.surfaceVariant,
    )
}

@Composable
fun Banner(text: String, color: Color, textColor: Color = Color.White, emoji: String = "⚠️", onClick: (() -> Unit)? = null) {
    Surface(
        color = color,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().let { if (onClick != null) it.clickable(onClick = onClick) else it },
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            emojiVisual(emoji).let { Icon(it.icon, null, tint = textColor, modifier = Modifier.size(22.dp)) }
            Text(text, color = textColor, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        }
    }
}

fun severityColor(s: AlertSeverity): Color = when (s) {
    AlertSeverity.CRITICAL -> Brand.Danger
    AlertSeverity.HIGH -> Color(0xFFEA580C)
    AlertSeverity.WARNING -> Brand.Warning
    AlertSeverity.INFO -> Brand.Info
}

fun severityLabel(s: AlertSeverity): String = when (s) {
    AlertSeverity.CRITICAL -> "Срочно"
    AlertSeverity.HIGH -> "Важно"
    AlertSeverity.WARNING -> "Внимание"
    AlertSeverity.INFO -> "Инфо"
}

fun relativeTime(ts: Long?, now: Long = System.currentTimeMillis()): String {
    if (ts == null || ts == 0L) return "нет данных"
    val diff = (now - ts).coerceAtLeast(0)
    val min = diff / 60_000
    return when {
        min < 1 -> "только что"
        min < 60 -> "$min мин назад"
        min < 24 * 60 -> "${min / 60} ч назад"
        min < 48 * 60 -> "вчера в " + SimpleDateFormat("HH:mm", Locale("ru")).format(Date(ts))
        else -> SimpleDateFormat("d MMM, HH:mm", Locale("ru")).format(Date(ts))
    }
}

fun timeOf(ts: Long): String = SimpleDateFormat("HH:mm", Locale("ru")).format(Date(ts))

fun dateTimeOf(ts: Long): String = SimpleDateFormat("d MMMM, HH:mm", Locale("ru")).format(Date(ts))

@Composable
fun Gap(h: Dp = 12.dp) = Spacer(Modifier.height(h))

@Composable
fun ColumnScreen(padding: PaddingValues, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        content()
        Spacer(Modifier.height(24.dp))
    }
}

/**
 * Maps the emoji earlier screens passed for decoration to a glyph tile, so no emoji is ever drawn.
 * New code passes icons directly.
 */
internal fun emojiVisual(e: String): Visual {
    val icons = androidx.compose.material.icons.Icons.Rounded
    return when {
        e.startsWith("⚠") || e.startsWith("🚨") || e.startsWith("❗") -> Visual(icons.Warning, Gradients.Sunrise)
        e.startsWith("✅") || e.startsWith("✓") || e.startsWith("👍") || e.startsWith("💚") -> Visual(icons.CheckCircle, Gradients.Emerald)
        e.startsWith("🛡") -> Visual(icons.Shield, Gradients.Emerald)
        e.startsWith("📍") || e.startsWith("🗺") -> Visual(icons.LocationOn, Gradients.Ocean)
        e.startsWith("🔒") || e.startsWith("🔐") -> Visual(icons.Lock, Gradients.Slate)
        e.startsWith("🆘") -> Visual(icons.Sos, Gradients.Danger)
        e.startsWith("📞") || e.startsWith("☎") -> Visual(icons.Call, Gradients.Emerald)
        e.startsWith("🔋") || e.startsWith("🔌") -> Visual(icons.Battery5Bar, Gradients.Sunrise)
        e.startsWith("📚") -> Visual(androidx.compose.material.icons.Icons.AutoMirrored.Rounded.MenuBook, Gradients.Sky)
        e.startsWith("⏳") || e.startsWith("⏰") || e.startsWith("🕐") -> Visual(icons.HourglassBottom, Gradients.Sky)
        e.startsWith("🔔") -> Visual(icons.Notifications, Gradients.Emerald)
        e.startsWith("⚙") || e.startsWith("🔧") -> Visual(icons.Settings, Gradients.Slate)
        e.startsWith("📱") -> Visual(icons.Smartphone, Gradients.Sky)
        e.startsWith("🌙") -> Visual(icons.Bedtime, Gradients.Night)
        e.startsWith("🔍") -> Visual(icons.ManageSearch, Gradients.Violet)
        e.startsWith("⛔") || e.startsWith("🛑") -> Visual(icons.Block, Gradients.Danger)
        e.startsWith("🌐") -> Visual(icons.Language, Gradients.Violet)
        e.startsWith("💊") -> Visual(icons.Medication, Gradients.Sunrise)
        e.startsWith("❓") -> Visual(icons.HelpOutline, Gradients.Slate)
        e.startsWith("📄") || e.startsWith("✉") -> Visual(icons.Description, Gradients.Slate)
        e.startsWith("💬") -> Visual(androidx.compose.material.icons.Icons.AutoMirrored.Rounded.Chat, Gradients.Sky)
        e.startsWith("📷") -> Visual(icons.PhotoCamera, Gradients.Night)
        e.startsWith("🖼") -> Visual(icons.Photo, Gradients.Ocean)
        e.startsWith("🔗") -> Visual(icons.Link, Gradients.Violet)
        e.startsWith("🎉") -> Visual(icons.Celebration, Gradients.Gold)
        e.startsWith("⏸") -> Visual(icons.Pause, Gradients.Sky)
        e.startsWith("📦") -> Visual(icons.Apps, Gradients.Violet)
        e.startsWith("💼") -> Visual(icons.Work, Gradients.Slate)
        e.startsWith("👵") || e.startsWith("🧓") -> Visual(icons.Elderly, Gradients.Violet)
        e.startsWith("👧") || e.startsWith("🧒") -> Visual(icons.ChildCare, Gradients.Sky)
        e.startsWith("🕊") -> Visual(icons.VolunteerActivism, Gradients.Emerald)
        else -> Visual(icons.Person, Gradients.Slate)
    }
}
