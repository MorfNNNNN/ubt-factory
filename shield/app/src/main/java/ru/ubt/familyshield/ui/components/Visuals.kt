package ru.ubt.familyshield.ui.components

import androidx.annotation.DrawableRes
import androidx.annotation.RawRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.AppBlocking
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Battery5Bar
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChildCare
import androidx.compose.material.icons.rounded.CrisisAlert
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Elderly
import androidx.compose.material.icons.rounded.FamilyRestroom
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.GppGood
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.NotificationImportant
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PhoneDisabled
import androidx.compose.material.icons.rounded.PhonelinkLock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.Sos
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import ru.ubt.familyshield.shared.model.AlertSeverity
import ru.ubt.familyshield.shared.model.AlertType
import ru.ubt.familyshield.shared.model.MemberRole
import ru.ubt.familyshield.ui.theme.Brand
import ru.ubt.familyshield.ui.theme.Gradients

/** An icon glyph on a rounded gradient square: the app's replacement for emoji everywhere. */
@Composable
fun IconTile(icon: ImageVector, gradient: Brush = Gradients.Emerald, size: Dp = 44.dp, iconSize: Dp = size * 0.52f, tint: Color = Color.White) {
    Box(
        Modifier.size(size).shadow(6.dp, RoundedCornerShape(size * 0.32f), ambientColor = Brand.Green.copy(alpha = 0.25f), spotColor = Brand.Green.copy(alpha = 0.25f))
            .clip(RoundedCornerShape(size * 0.32f)).background(gradient),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(iconSize)) }
}

/** A generated 3D illustration (res/drawable-nodpi/ill_*, avatar_*). */
@Composable
fun Illustration(@DrawableRes res: Int, modifier: Modifier = Modifier, size: Dp? = null, contentScale: ContentScale = ContentScale.Fit) {
    Image(painterResource(res), contentDescription = null, contentScale = contentScale, modifier = if (size != null) modifier.size(size) else modifier)
}

/** A brand Lottie animation from res/raw. Loops by default; [iterations] = 1 plays once. */
@Composable
fun LottieView(@RawRes raw: Int, modifier: Modifier = Modifier, size: Dp? = null, loop: Boolean = true, speed: Float = 1f) {
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(raw))
    LottieAnimation(
        composition = composition,
        iterations = if (loop) LottieConstants.IterateForever else 1,
        speed = speed,
        modifier = if (size != null) modifier.size(size) else modifier,
    )
}

/** Large gradient card with an illustration or animation on the right — the top of most home screens. */
@Composable
fun HeroCard(
    title: String,
    subtitle: String? = null,
    gradient: Brush = Gradients.Hero,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    art: (@Composable BoxScope.() -> Unit)? = null,
    footer: (@Composable ColumnScope.() -> Unit)? = null,
) {
    Box(
        modifier.fillMaxWidth()
            .shadow(14.dp, MaterialTheme.shapes.extraLarge, ambientColor = Brand.GreenDark.copy(alpha = 0.35f), spotColor = Brand.GreenDark.copy(alpha = 0.35f))
            .clip(MaterialTheme.shapes.extraLarge).background(gradient)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it },
    ) {
        if (art != null) Box(Modifier.align(Alignment.CenterEnd).padding(end = 6.dp).size(140.dp), contentAlignment = Alignment.Center, content = art)
        Column(Modifier.padding(22.dp).padding(end = if (art != null) 118.dp else 0.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = Color.White)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.86f))
            if (footer != null) {
                Spacer(Modifier.height(6.dp))
                footer()
            }
        }
    }
}

/** A white surface with the brand radius and a soft green-tinted shadow. */
@Composable
fun SoftCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, padding: Dp = 18.dp, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth()
            .shadow(8.dp, MaterialTheme.shapes.large, ambientColor = Brand.Green.copy(alpha = 0.12f), spotColor = Brand.Green.copy(alpha = 0.12f))
            .clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surface)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

/** Compact metric: big number, caption, glyph tile. */
@Composable
fun StatTile(value: String, label: String, icon: ImageVector, gradient: Brush, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    SoftCard(modifier, onClick, padding = 14.dp) {
        IconTile(icon, gradient, size = 36.dp)
        Text(value, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
    }
}

@Composable
fun SectionHeader(title: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp, start = 4.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) {
            Text(action, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onAction).padding(6.dp))
        }
    }
}

/** A list row with a glyph tile, used in settings and menus. */
@Composable
fun TileRow(
    title: String,
    subtitle: String? = null,
    icon: ImageVector,
    gradient: Brush = Gradients.Emerald,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).let { if (onClick != null) it.clickable(onClick = onClick) else it }.padding(vertical = 8.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        IconTile(icon, gradient, size = 42.dp)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (trailing != null) trailing()
        else if (onClick != null) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Small coloured status dot + label. */
@Composable
fun StatusDot(text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.Bold)
    }
}

// ---- semantic visuals ------------------------------------------------------------------------------

data class Visual(val icon: ImageVector, val gradient: Brush)

fun AlertType.visual(): Visual = when (this) {
    AlertType.SOS, AlertType.FALL_DETECTED -> Visual(Icons.Rounded.Sos, Gradients.Danger)
    AlertType.CHECK_IN_MISSED, AlertType.INACTIVITY -> Visual(Icons.Rounded.NotificationImportant, Gradients.Sunrise)
    AlertType.CHECK_IN_OK -> Visual(Icons.Rounded.Favorite, Gradients.Coral)
    AlertType.LOW_BATTERY -> Visual(Icons.Rounded.Battery5Bar, Gradients.Sunrise)
    AlertType.SCAM_CALL_BLOCKED, AlertType.SUSPICIOUS_CALL, AlertType.LONG_CALL_UNKNOWN -> Visual(Icons.Rounded.PhoneDisabled, Gradients.Coral)
    AlertType.BANK_APP_DURING_CALL -> Visual(Icons.Rounded.AccountBalance, Gradients.Danger)
    AlertType.REMOTE_ACCESS_APP -> Visual(Icons.Rounded.Devices, Gradients.Danger)
    AlertType.OTP_DURING_CALL -> Visual(Icons.Rounded.Sms, Gradients.Danger)
    AlertType.SCAM_MESSAGE -> Visual(Icons.AutoMirrored.Rounded.Chat, Gradients.Coral)
    AlertType.PHISHING_LINK -> Visual(Icons.Rounded.Link, Gradients.Coral)
    AlertType.RISKY_APP_INSTALLED -> Visual(Icons.Rounded.Warning, Gradients.Danger)
    AlertType.NEW_APP_INSTALLED -> Visual(Icons.Rounded.Apps, Gradients.Sky)
    AlertType.DEVICE_SECURITY_ISSUE -> Visual(Icons.Rounded.Security, Gradients.Sunrise)
    AlertType.PROTECTION_CHANGE_REQUESTED, AlertType.TAMPER, AlertType.PERMISSION_REVOKED, AlertType.UNINSTALL_ATTEMPT -> Visual(Icons.Rounded.PhonelinkLock, Gradients.Danger)
    AlertType.CHILD_RISK_MESSAGE -> Visual(Icons.Rounded.CrisisAlert, Gradients.Danger)
    AlertType.BLOCKED_SITE, AlertType.FILTER_BYPASS -> Visual(Icons.Rounded.Language, Gradients.Violet)
    AlertType.LIMIT_REACHED -> Visual(Icons.Rounded.HourglassBottom, Gradients.Sky)
    AlertType.GEOFENCE_ENTER, AlertType.GEOFENCE_EXIT -> Visual(Icons.Rounded.LocationOn, Gradients.Ocean)
    AlertType.MEDICATION_MISSED -> Visual(Icons.Rounded.Medication, Gradients.Sunrise)
    AlertType.MEDICATION_TAKEN -> Visual(Icons.Rounded.Medication, Gradients.Emerald)
    else -> Visual(Icons.Rounded.Notifications, Gradients.Emerald)
}

fun AlertSeverity.gradient(): Brush = when (this) {
    AlertSeverity.CRITICAL -> Gradients.Danger
    AlertSeverity.HIGH -> Gradients.Coral
    AlertSeverity.WARNING -> Gradients.Sunrise
    AlertSeverity.INFO -> Gradients.Sky
}

fun MemberRole.visual(): Visual = when (this) {
    MemberRole.GUARDIAN -> Visual(Icons.Rounded.Shield, Gradients.Emerald)
    MemberRole.CHILD -> Visual(Icons.Rounded.ChildCare, Gradients.Sky)
    MemberRole.ELDER -> Visual(Icons.Rounded.Elderly, Gradients.Violet)
}

/** Ready-made glyphs for the most common feature rows. */
object FeatureIcons {
    val Protection = Visual(Icons.Rounded.GppGood, Gradients.Emerald)
    val Calls = Visual(Icons.Rounded.PhoneDisabled, Gradients.Coral)
    val Location = Visual(Icons.Rounded.LocationOn, Gradients.Ocean)
    val ScreenTime = Visual(Icons.Rounded.HourglassBottom, Gradients.Sky)
    val Apps = Visual(Icons.Rounded.AppBlocking, Gradients.Violet)
    val Web = Visual(Icons.Rounded.Language, Gradients.Violet)
    val Sleep = Visual(Icons.Rounded.Bedtime, Gradients.Night)
    val Study = Visual(Icons.AutoMirrored.Rounded.MenuBook, Gradients.Sky)
    val Pills = Visual(Icons.Rounded.Medication, Gradients.Sunrise)
    val Alarm = Visual(Icons.Rounded.Alarm, Gradients.Sunrise)
    val Sos = Visual(Icons.Rounded.Sos, Gradients.Danger)
    val Lock = Visual(Icons.Rounded.Lock, Gradients.Slate)
    val Family = Visual(Icons.Rounded.FamilyRestroom, Gradients.Emerald)
    val Person = Visual(Icons.Rounded.Person, Gradients.Slate)
    val Block = Visual(Icons.Rounded.Block, Gradients.Danger)
    val Done = Visual(Icons.Rounded.TaskAlt, Gradients.Emerald)
    val Ok = Visual(Icons.Rounded.CheckCircle, Gradients.Emerald)
    val Driving = Visual(Icons.Rounded.DirectionsCar, Gradients.Ocean)
    val Battery = Visual(Icons.Rounded.Battery5Bar, Gradients.Sunrise)
}
