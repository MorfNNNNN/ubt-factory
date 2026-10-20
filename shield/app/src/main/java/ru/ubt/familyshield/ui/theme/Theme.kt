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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object Brand {
    val Green = Color(0xFF0F9D74)
    val GreenDark = Color(0xFF0B7A5A)
    val GreenLight = Color(0xFFD1FAE5)
    val Blue = Color(0xFF2563EB)
    val Navy = Color(0xFF0B1220)
    val NavyCard = Color(0xFF111A2E)
    val Danger = Color(0xFFDC2626)
    val DangerLight = Color(0xFFFEE2E2)
    val Warning = Color(0xFFD97706)
    val WarningLight = Color(0xFFFEF3C7)
    val Info = Color(0xFF0369A1)
    val Gold = Color(0xFFF59E0B)
    val Purple = Color(0xFF7C3AED)
}

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
    background = Color(0xFFF6F8FB),
    onBackground = Color(0xFF0F172A),
    surface = Color.White,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFEEF2F7),
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFFCBD5E1),
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
)

/** Elder mode scales all text up and increases contrast. */
data class ShieldUiConfig(val fontScale: Float = 1f, val elderMode: Boolean = false)

val LocalShieldUi = staticCompositionLocalOf { ShieldUiConfig() }

private fun typography(scale: Float): Typography {
    fun s(size: Int, weight: FontWeight = FontWeight.Normal, line: Float = 1.3f) =
        TextStyle(fontSize = (size * scale).sp, lineHeight = (size * scale * line).sp, fontWeight = weight)
    return Typography(
        displaySmall = s(34, FontWeight.Bold, 1.15f),
        headlineLarge = s(30, FontWeight.Bold, 1.2f),
        headlineMedium = s(26, FontWeight.Bold, 1.2f),
        headlineSmall = s(22, FontWeight.SemiBold, 1.25f),
        titleLarge = s(20, FontWeight.SemiBold),
        titleMedium = s(17, FontWeight.SemiBold),
        titleSmall = s(15, FontWeight.Medium),
        bodyLarge = s(16),
        bodyMedium = s(15),
        bodySmall = s(13),
        labelLarge = s(15, FontWeight.SemiBold),
        labelMedium = s(13, FontWeight.Medium),
        labelSmall = s(12, FontWeight.Medium),
    )
}

private val shapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
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
