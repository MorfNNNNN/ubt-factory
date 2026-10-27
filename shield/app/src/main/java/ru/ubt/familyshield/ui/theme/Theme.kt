package ru.ubt.familyshield.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.ubt.familyshield.R

object Brand {
    val Green = Color(0xFF0F9D74)
    val GreenDark = Color(0xFF065F46)
    val GreenLight = Color(0xFFD1FAE5)
    val Mint = Color(0xFFECFDF5)
    val Teal = Color(0xFF14B8A6)
    val Blue = Color(0xFF2563EB)
    val Sky = Color(0xFF0EA5E9)
    val Navy = Color(0xFF0B1220)
    val NavyCard = Color(0xFF111A2E)
    val Ink = Color(0xFF0F172A)
    val Slate = Color(0xFF475569)
    val Danger = Color(0xFFDC2626)
    val DangerLight = Color(0xFFFEE2E2)
    val Coral = Color(0xFFFB7185)
    val Warning = Color(0xFFD97706)
    val WarningLight = Color(0xFFFEF3C7)
    val Info = Color(0xFF0369A1)
    val Gold = Color(0xFFF59E0B)
    val Purple = Color(0xFF7C3AED)
    val Canvas = Color(0xFFF3F7F5)
}

/** Brand gradients; every tile, hero card and primary button draws from these. */
object Gradients {
    val Emerald = Brush.linearGradient(listOf(Color(0xFF10B981), Color(0xFF047857)))
    val Mint = Brush.linearGradient(listOf(Color(0xFFD1FAE5), Color(0xFFA7F3D0)))
    val Ocean = Brush.linearGradient(listOf(Color(0xFF22D3EE), Color(0xFF0E7490)))
    val Sky = Brush.linearGradient(listOf(Color(0xFF60A5FA), Color(0xFF1D4ED8)))
    val Violet = Brush.linearGradient(listOf(Color(0xFFA78BFA), Color(0xFF6D28D9)))
    val Coral = Brush.linearGradient(listOf(Color(0xFFFB7185), Color(0xFFE11D48)))
    val Danger = Brush.linearGradient(listOf(Color(0xFFF87171), Color(0xFFB91C1C)))
    val Sunrise = Brush.linearGradient(listOf(Color(0xFFFBBF24), Color(0xFFEA580C)))
    val Gold = Brush.linearGradient(listOf(Color(0xFFFDE68A), Color(0xFFD97706)))
    val Slate = Brush.linearGradient(listOf(Color(0xFF94A3B8), Color(0xFF475569)))
    val Night = Brush.linearGradient(listOf(Color(0xFF1E293B), Color(0xFF0B1220)))
    val Hero = Brush.linearGradient(listOf(Color(0xFF0F9D74), Color(0xFF065F46), Color(0xFF064E3B)))
}

val Manrope = FontFamily(
    Font(R.font.manrope_regular, FontWeight.Normal),
    Font(R.font.manrope_medium, FontWeight.Medium),
    Font(R.font.manrope_semibold, FontWeight.SemiBold),
    Font(R.font.manrope_bold, FontWeight.Bold),
    Font(R.font.manrope_extrabold, FontWeight.ExtraBold),
)

private val Light: ColorScheme = lightColorScheme(
    primary = Brand.Green,
    onPrimary = Color.White,
    primaryContainer = Brand.GreenLight,
    onPrimaryContainer = Color(0xFF053B2C),
    secondary = Brand.Blue,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDBEAFE),
    onSecondaryContainer = Color(0xFF0B2A6B),
    tertiary = Brand.Purple,
    error = Brand.Danger,
    errorContainer = Brand.DangerLight,
    background = Brand.Canvas,
    onBackground = Brand.Ink,
    surface = Color.White,
    onSurface = Brand.Ink,
    surfaceVariant = Color(0xFFEAF1EE),
    onSurfaceVariant = Brand.Slate,
    outline = Color(0xFFCBD5E1),
    outlineVariant = Color(0xFFE2E8F0),
)

private val Dark: ColorScheme = darkColorScheme(
    primary = Color(0xFF34D399),
    onPrimary = Color(0xFF053B2C),
    primaryContainer = Color(0xFF065F46),
    onPrimaryContainer = Color(0xFFD1FAE5),
    secondary = Color(0xFF60A5FA),
    onSecondary = Color(0xFF0B2A6B),
    secondaryContainer = Color(0xFF1E3A8A),
    onSecondaryContainer = Color(0xFFDBEAFE),
    tertiary = Color(0xFFA78BFA),
    error = Color(0xFFF87171),
    errorContainer = Color(0xFF7F1D1D),
    background = Brand.Navy,
    onBackground = Color(0xFFE7EDF7),
    surface = Brand.NavyCard,
    onSurface = Color(0xFFE7EDF7),
    surfaceVariant = Color(0xFF1A2740),
    onSurfaceVariant = Color(0xFF93A4C3),
    outline = Color(0xFF2A3B5F),
    outlineVariant = Color(0xFF22324F),
)

/** Elder mode scales all text up and increases contrast. */
data class ShieldUiConfig(val fontScale: Float = 1f, val elderMode: Boolean = false)

val LocalShieldUi = staticCompositionLocalOf { ShieldUiConfig() }

private fun typography(scale: Float): Typography {
    fun s(size: Int, weight: FontWeight = FontWeight.Normal, line: Float = 1.35f, tracking: Float = 0f) =
        TextStyle(
            fontFamily = Manrope, fontSize = (size * scale).sp, lineHeight = (size * scale * line).sp,
            fontWeight = weight, letterSpacing = tracking.sp,
        )
    return Typography(
        displayLarge = s(44, FontWeight.ExtraBold, 1.1f, -0.8f),
        displayMedium = s(38, FontWeight.ExtraBold, 1.12f, -0.6f),
        displaySmall = s(32, FontWeight.ExtraBold, 1.15f, -0.4f),
        headlineLarge = s(29, FontWeight.ExtraBold, 1.18f, -0.3f),
        headlineMedium = s(25, FontWeight.Bold, 1.2f, -0.2f),
        headlineSmall = s(21, FontWeight.Bold, 1.25f),
        titleLarge = s(19, FontWeight.Bold),
        titleMedium = s(16, FontWeight.Bold),
        titleSmall = s(15, FontWeight.SemiBold),
        bodyLarge = s(16, FontWeight.Medium, 1.45f),
        bodyMedium = s(14, FontWeight.Medium, 1.45f),
        bodySmall = s(12, FontWeight.Medium, 1.4f),
        labelLarge = s(15, FontWeight.Bold),
        labelMedium = s(13, FontWeight.SemiBold),
        labelSmall = s(11, FontWeight.Bold, 1.3f, 0.3f),
    )
}

private val shapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(34.dp),
)

@Composable
fun ShieldTheme(config: ShieldUiConfig = ShieldUiConfig(), dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(LocalShieldUi provides config) {
        MaterialTheme(
            colorScheme = if (dark) Dark else Light,
            typography = typography(config.fontScale),
            shapes = shapes,
            content = content,
        )
    }
}
