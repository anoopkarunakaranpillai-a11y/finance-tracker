package com.anoop.financetracker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Colours that Material's scheme does not cover: money direction and chart series. */
@Immutable
data class FtColors(
    val income: Color,
    val expense: Color,
    val transfer: Color,
    val warn: Color,
    val muted: Color,
    val card: Color,
    val cardAlt: Color,
    val line: Color,
    val chart: List<Color>,
)

private val DarkFt = FtColors(
    income = Color(0xFF34D399), expense = Color(0xFFF87171), transfer = Color(0xFF93C5FD), warn = Color(0xFFFBBF24),
    muted = Color(0xFF94A3B8), card = Color(0xFF151A23), cardAlt = Color(0xFF1C2330), line = Color(0xFF263042),
    chart = listOf(Color(0xFF3B82F6), Color(0xFF34D399), Color(0xFFFBBF24), Color(0xFFF472B6), Color(0xFFA78BFA),
        Color(0xFF22D3EE), Color(0xFFFB923C), Color(0xFFA3E635), Color(0xFFF87171), Color(0xFF94A3B8)),
)
private val LightFt = FtColors(
    income = Color(0xFF059669), expense = Color(0xFFDC2626), transfer = Color(0xFF2563EB), warn = Color(0xFFD97706),
    muted = Color(0xFF64748B), card = Color(0xFFFFFFFF), cardAlt = Color(0xFFF1F5F9), line = Color(0xFFE2E8F0),
    chart = listOf(Color(0xFF2563EB), Color(0xFF059669), Color(0xFFD97706), Color(0xFFDB2777), Color(0xFF7C3AED),
        Color(0xFF0891B2), Color(0xFFEA580C), Color(0xFF65A30D), Color(0xFFDC2626), Color(0xFF64748B)),
)

val LocalFt = staticCompositionLocalOf { DarkFt }

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF3B82F6), onPrimary = Color.White,
    primaryContainer = Color(0xFF1E3A8A), onPrimaryContainer = Color(0xFFDBEAFE),
    secondary = Color(0xFF60A5FA), onSecondary = Color(0xFF0B0E14),
    background = Color(0xFF0B0E14), onBackground = Color(0xFFE5E7EB),
    surface = Color(0xFF0B0E14), onSurface = Color(0xFFE5E7EB),
    surfaceVariant = Color(0xFF151A23), onSurfaceVariant = Color(0xFF94A3B8),
    surfaceContainer = Color(0xFF151A23), surfaceContainerHigh = Color(0xFF1C2330),
    surfaceContainerHighest = Color(0xFF232B3A), surfaceContainerLow = Color(0xFF11151D),
    outline = Color(0xFF334155), outlineVariant = Color(0xFF263042),
    error = Color(0xFFF87171), onError = Color(0xFF0B0E14),
)
private val LightScheme = lightColorScheme(
    primary = Color(0xFF2563EB), onPrimary = Color.White,
    primaryContainer = Color(0xFFDBEAFE), onPrimaryContainer = Color(0xFF1E3A8A),
    secondary = Color(0xFF3B82F6),
    background = Color(0xFFF6F7FB), onBackground = Color(0xFF0F172A),
    surface = Color(0xFFF6F7FB), onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFFFFFFF), onSurfaceVariant = Color(0xFF64748B),
    surfaceContainer = Color(0xFFFFFFFF), surfaceContainerHigh = Color(0xFFF1F5F9),
    surfaceContainerHighest = Color(0xFFE2E8F0), surfaceContainerLow = Color(0xFFF8FAFC),
    outline = Color(0xFFCBD5E1), outlineVariant = Color(0xFFE2E8F0),
    error = Color(0xFFDC2626),
)

private val Type = Typography().let { t ->
    t.copy(
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.Bold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

val AmountStyle = TextStyle(fontWeight = FontWeight.Bold, fontSize = 30.sp, letterSpacing = (-0.5).sp)

@Composable
fun FinanceTheme(theme: String = "dark", content: @Composable () -> Unit) {
    val dark = when (theme) { "light" -> false; "system" -> isSystemInDarkTheme(); else -> true }
    CompositionLocalProvider(LocalFt provides if (dark) DarkFt else LightFt) {
        MaterialTheme(colorScheme = if (dark) DarkScheme else LightScheme, typography = Type, content = content)
    }
}

object Ft {
    val c: FtColors @Composable get() = LocalFt.current
}
