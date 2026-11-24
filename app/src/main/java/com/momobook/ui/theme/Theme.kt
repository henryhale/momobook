package com.momobook.ui.theme

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

object Brand {
    val Yellow = Color(0xFFFFC627)
    val YellowDeep = Color(0xFFE5A600)
    val Ink = Color(0xFF0E1525)
    val Navy = Color(0xFF22314F)
}

@Immutable
data class MoneyColors(
    val moneyIn: Color,
    val moneyOut: Color,
    val fee: Color,
    val inContainer: Color,
    val outContainer: Color,
)

val LocalMoneyColors = staticCompositionLocalOf {
    MoneyColors(Color.Green, Color.Red, Color.Yellow, Color.Green, Color.Red)
}

private val LightMoney = MoneyColors(
    moneyIn = Color(0xFF079455), moneyOut = Color(0xFFD92D20), fee = Color(0xFFDC6803),
    inContainer = Color(0xFFDCFAE6), outContainer = Color(0xFFFEE4E2),
)
private val DarkMoney = MoneyColors(
    moneyIn = Color(0xFF47CD89), moneyOut = Color(0xFFF97066), fee = Color(0xFFFDB022),
    inContainer = Color(0xFF053321), outContainer = Color(0xFF55160C),
)

private val LightScheme = lightColorScheme(
    primary = Brand.Ink, onPrimary = Color.White,
    primaryContainer = Color(0xFFFFF4CC), onPrimaryContainer = Color(0xFF3D2E00),
    secondary = Brand.YellowDeep, onSecondary = Brand.Ink,
    secondaryContainer = Color(0xFFFFE89A), onSecondaryContainer = Brand.Ink,
    background = Color(0xFFF5F6FA), onBackground = Brand.Ink,
    surface = Color(0xFFF5F6FA), onSurface = Brand.Ink,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFEEF0F5),
    surfaceContainerHighest = Color(0xFFE6E9F0),
    surfaceVariant = Color(0xFFEEF0F5), onSurfaceVariant = Color(0xFF5B6478),
    outline = Color(0xFFC6CBD6), outlineVariant = Color(0xFFE3E6ED),
)

private val DarkScheme = darkColorScheme(
    primary = Brand.Yellow, onPrimary = Brand.Ink,
    primaryContainer = Color(0xFF3D2E00), onPrimaryContainer = Color(0xFFFFE08A),
    secondary = Brand.Yellow, onSecondary = Brand.Ink,
    secondaryContainer = Color(0xFF2A3550), onSecondaryContainer = Color(0xFFFFE08A),
    background = Color(0xFF0A0E16), onBackground = Color(0xFFE9ECF3),
    surface = Color(0xFF0A0E16), onSurface = Color(0xFFE9ECF3),
    surfaceContainerLowest = Color(0xFF0D121C),
    surfaceContainerLow = Color(0xFF131926),
    surfaceContainer = Color(0xFF151C2A),
    surfaceContainerHigh = Color(0xFF1C2435),
    surfaceContainerHighest = Color(0xFF242E42),
    surfaceVariant = Color(0xFF1C2435), onSurfaceVariant = Color(0xFF9AA3B8),
    outline = Color(0xFF3A465F), outlineVariant = Color(0xFF232C3F),
)

private val base = Typography()
private val AppTypography = base.copy(
    displaySmall = base.displaySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
)

/** Tabular figures keep amounts aligned in lists and tables. */
val Tabular = TextStyle(fontFeatureSettings = "tnum")

@Composable
fun MomoTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalMoneyColors provides if (dark) DarkMoney else LightMoney) {
        MaterialTheme(
            colorScheme = if (dark) DarkScheme else LightScheme,
            typography = AppTypography,
            content = content,
        )
    }
}
