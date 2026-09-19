package com.syncro.desktop.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Immutable
data class Palette(
    val background: Color,
    val sidebar: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val outline: Color,
    val text: Color,
    val textMuted: Color,
    val accent: Color,
    val accentAlt: Color,
    val onAccent: Color,
    val success: Color,
    val danger: Color,
    val warning: Color,
    val isDark: Boolean,
) {
    val accentBrush: Brush get() = Brush.linearGradient(listOf(accent, accentAlt))
    val softAccentBrush: Brush get() = Brush.linearGradient(listOf(accent.copy(alpha = 0.18f), accentAlt.copy(alpha = 0.12f)))
}

private val DarkPalette = Palette(
    background = Color(0xFF0B0D12),
    sidebar = Color(0xFF0F1218),
    surface = Color(0xFF14171F),
    surfaceHigh = Color(0xFF1C212C),
    outline = Color(0xFF272D39),
    text = Color(0xFFF4F6FB),
    textMuted = Color(0xFF9AA3B2),
    accent = Color(0xFF8B6CFF),
    accentAlt = Color(0xFF19C6FF),
    onAccent = Color.White,
    success = Color(0xFF2DD4A7),
    danger = Color(0xFFFF6B6B),
    warning = Color(0xFFFFB547),
    isDark = true,
)

private val LightPalette = Palette(
    background = Color(0xFFF5F6FA),
    sidebar = Color(0xFFECEEF4),
    surface = Color(0xFFFFFFFF),
    surfaceHigh = Color(0xFFEEF0F6),
    outline = Color(0xFFDDE1EA),
    text = Color(0xFF0E1116),
    textMuted = Color(0xFF5C6573),
    accent = Color(0xFF6A4CF5),
    accentAlt = Color(0xFF0BA5E0),
    onAccent = Color.White,
    success = Color(0xFF0FA77F),
    danger = Color(0xFFE5484D),
    warning = Color(0xFFD98A00),
    isDark = false,
)

val LocalPalette = staticCompositionLocalOf { DarkPalette }

object Theme {
    val colors: Palette
        @Composable get() = LocalPalette.current
}

/** Roboto, the design system's typeface, bundled so Windows doesn't fall back to Segoe UI. */
private val Roboto = FontFamily(
    Font("font/roboto_regular.ttf", FontWeight.Normal),
    Font("font/roboto_medium.ttf", FontWeight.Medium),
    Font("font/roboto_semibold.ttf", FontWeight.SemiBold),
    Font("font/roboto_bold.ttf", FontWeight.Bold),
)

private val BaseTypography = Typography(
    displaySmall = TextStyle(fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.6).sp),
    headlineSmall = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp),
    titleLarge = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 13.5.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 13.5.sp, lineHeight = 19.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 10.5.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.6.sp),
)

private val AppTypography = BaseTypography.run {
    fun TextStyle.roboto() = copy(fontFamily = Roboto)
    Typography(
        displayLarge = displayLarge.roboto(), displayMedium = displayMedium.roboto(), displaySmall = displaySmall.roboto(),
        headlineLarge = headlineLarge.roboto(), headlineMedium = headlineMedium.roboto(), headlineSmall = headlineSmall.roboto(),
        titleLarge = titleLarge.roboto(), titleMedium = titleMedium.roboto(), titleSmall = titleSmall.roboto(),
        bodyLarge = bodyLarge.roboto(), bodyMedium = bodyMedium.roboto(), bodySmall = bodySmall.roboto(),
        labelLarge = labelLarge.roboto(), labelMedium = labelMedium.roboto(), labelSmall = labelSmall.roboto(),
    )
}

@Composable
fun SyncroDesktopTheme(mode: String, content: @Composable () -> Unit) {
    val dark = when (mode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    val p = if (dark) DarkPalette else LightPalette
    val scheme = if (dark) {
        darkColorScheme(
            primary = p.accent, onPrimary = p.onAccent, secondary = p.accentAlt, background = p.background, onBackground = p.text,
            surface = p.surface, onSurface = p.text, surfaceVariant = p.surfaceHigh, onSurfaceVariant = p.textMuted,
            surfaceContainer = p.surface, surfaceContainerHigh = p.surfaceHigh, outline = p.outline, outlineVariant = p.outline, error = p.danger,
        )
    } else {
        lightColorScheme(
            primary = p.accent, onPrimary = p.onAccent, secondary = p.accentAlt, background = p.background, onBackground = p.text,
            surface = p.surface, onSurface = p.text, surfaceVariant = p.surfaceHigh, onSurfaceVariant = p.textMuted,
            surfaceContainer = p.surface, surfaceContainerHigh = p.surfaceHigh, outline = p.outline, outlineVariant = p.outline, error = p.danger,
        )
    }
    // Mouse-driven UI: no 48dp touch padding around switches, checkboxes and icon buttons.
    CompositionLocalProvider(LocalPalette provides p, LocalMinimumInteractiveComponentSize provides 0.dp) {
        MaterialTheme(colorScheme = scheme, typography = AppTypography, content = content)
    }
}
