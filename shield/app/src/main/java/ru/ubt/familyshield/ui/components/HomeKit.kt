package ru.ubt.familyshield.ui.components

import androidx.annotation.DrawableRes
import androidx.annotation.RawRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Favorite
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import ru.ubt.familyshield.R
import ru.ubt.familyshield.ui.theme.Brand
import ru.ubt.familyshield.ui.theme.Gradients

// Building blocks shared by the home, onboarding and setup screens, on top of Visuals.kt.

/** A white glyph on a translucent white square: how icons sit on gradient surfaces. */
@Composable
fun GlassIcon(icon: ImageVector, size: Dp = 48.dp, iconSize: Dp = size * 0.56f, alpha: Float = 0.22f) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.32f)).background(Color.White.copy(alpha = alpha)),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(iconSize)) }
}

/**
 * A generated illustration with its bottom strip cut away. The figures end in a hard edge (and some
 * files carry a mark in that corner), so they are drawn standing on the bottom edge of their panel.
 */
@Composable
fun CroppedIllustration(@DrawableRes res: Int, width: Dp, modifier: Modifier = Modifier, cropBottom: Float = 0.1f) {
    Image(
        painterResource(res), contentDescription = null,
        contentScale = ContentScale.Crop, alignment = Alignment.TopCenter,
        modifier = modifier.size(width, width * (1f - cropBottom)),
    )
}

/** Round white button with a glyph, for back/close actions on screens without a top bar. */
@Composable
fun CircleIconButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 48.dp) {
    Box(
        modifier.size(size)
            .shadow(6.dp, CircleShape, ambientColor = Brand.Green.copy(alpha = 0.22f), spotColor = Brand.Green.copy(alpha = 0.22f))
            .clip(CircleShape).background(MaterialTheme.colorScheme.surface)
            .clickable(role = Role.Button, onClickLabel = contentDescription, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(size * 0.5f)) }
}

@Composable
fun BackButton(onClick: () -> Unit, modifier: Modifier = Modifier) =
    CircleIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Назад", onClick, modifier)

/** Small rounded label: the state of a step, a hint on a hero card. */
@Composable
fun LabelChip(text: String, container: Color, content: Color, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Row(
        modifier.clip(RoundedCornerShape(50)).background(container).padding(start = if (icon != null) 8.dp else 11.dp, end = 11.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(16.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = content, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/** Pager indicator: the current page is a wide pill, the rest are dots. */
@Composable
fun PageDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { i ->
            val active = i == current
            val width by animateDpAsState(if (active) 28.dp else 8.dp, label = "dot")
            val color by animateColorAsState(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, label = "dotColor")
            Box(Modifier.height(8.dp).width(width).clip(RoundedCornerShape(50)).background(color))
        }
    }
}

/** A calm one-line notice with a glyph: errors, hints, pending states. */
@Composable
fun NoticeCard(text: String, icon: ImageVector, container: Color, content: Color, modifier: Modifier = Modifier, textColor: Color = content, onClick: (() -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(container)
            .let { if (onClick != null) it.clickable(role = Role.Button, onClick = onClick) else it }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(24.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = textColor, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
    }
}

/** A glyph tile followed by text: one line of a "what happens" list. */
@Composable
fun GlyphLine(text: String, icon: ImageVector, gradient: Brush, modifier: Modifier = Modifier, tile: Dp = 32.dp, style: TextStyle = MaterialTheme.typography.bodyLarge, color: Color = MaterialTheme.colorScheme.onSurface) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        IconTile(icon, gradient, size = tile)
        Text(text, style = style, color = color, modifier = Modifier.weight(1f))
    }
}

/** Circular progress with rounded ends and anything in the middle. */
@Composable
fun ProgressRing(
    fraction: Float,
    size: Dp,
    modifier: Modifier = Modifier,
    stroke: Dp = 10.dp,
    color: Color = Color.White,
    track: Color = Color.White.copy(alpha = 0.24f),
    content: @Composable BoxScope.() -> Unit = {},
) {
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = stroke.toPx()
            val inset = w / 2
            val arcSize = androidx.compose.ui.geometry.Size(this.size.width - w, this.size.height - w)
            val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
            drawArc(track, 0f, 360f, useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(w))
            val sweep = 360f * fraction.coerceIn(0f, 1f)
            if (sweep > 0f) drawArc(color, -90f, sweep, useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(w, cap = StrokeCap.Round))
        }
        content()
    }
}

/**
 * A brand Lottie that shows [fallback] until the animation has loaded, so the spot is never empty
 * (on first frames, and in environments that do not load animations at all).
 */
@Composable
fun LottieWithFallback(@RawRes raw: Int, size: Dp, modifier: Modifier = Modifier, loop: Boolean = true, fallback: @Composable BoxScope.() -> Unit) {
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(raw))
    Box(modifier.requiredSize(size), contentAlignment = Alignment.Center) {
        if (composition == null) fallback()
        LottieAnimation(composition, iterations = if (loop) LottieConstants.IterateForever else 1, modifier = Modifier.requiredSize(size))
    }
}

/** The pulsing SOS button (lottie_sos_pulse) with its label; [size] is the animation canvas. */
@Composable
fun SosPulse(size: Dp, modifier: Modifier = Modifier) {
    Box(modifier.requiredSize(size), contentAlignment = Alignment.Center) {
        LottieWithFallback(R.raw.lottie_sos_pulse, size) {
            // The animation's resting frame: a white ring around a red core.
            Box(Modifier.size(size * (178f / 512f)).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                Box(Modifier.size(size * (150f / 512f)).clip(CircleShape).background(Gradients.Danger))
            }
        }
        // Sized in dp so it always fits the button, whatever the font scale.
        val label = with(LocalDensity.current) { (size * 0.1f).toSp() }
        Text("SOS", color = Color.White, fontSize = label, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.5.sp, maxLines = 1)
    }
}

/** The beating heart (lottie_heart_beat) on a white disc, for «Я в порядке». */
@Composable
fun HeartBadge(size: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
        val canvas = size * 1.3f
        LottieWithFallback(R.raw.lottie_heart_beat, canvas) {
            // Same footprint as the animated heart (230 px wide on a 512 px canvas, 20 px below centre).
            Icon(
                Icons.Rounded.Favorite, contentDescription = null, tint = Brand.Coral,
                modifier = Modifier.padding(top = canvas * 0.07f).size(canvas * 0.5f),
            )
        }
    }
}

/** A soft mint panel that holds onboarding art. */
@Composable
fun MintPanel(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.clip(MaterialTheme.shapes.extraLarge).background(Gradients.Mint), content = content)
}
