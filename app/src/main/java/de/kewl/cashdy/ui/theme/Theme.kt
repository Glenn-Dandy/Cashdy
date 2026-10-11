package de.kewl.cashdy.ui.theme

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
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import de.kewl.cashdy.R

@Immutable
data class CashdyColors(
    val bg: Color,
    val surface: Color,
    val fg: Color,
    val muted: Color,
    val hero: Color,
    val heroFg: Color,
    val heroChip: Color,
    val primary: Color,
    val onPrimary: Color,
    val coin: Color,
    val ink: Color,
    val soft1: Color,
    val soft2: Color,
    val soft3: Color,
    val warn: Color,
    val good: Color,
    val line: Color,
    val dark: Boolean,
)

val LightColors = CashdyColors(
    bg = Color(0xFFF6F4FB), surface = Color(0xFFFFFFFF), fg = Color(0xFF2A2150), muted = Color(0xFF8A84A6),
    hero = Color(0xFF6C4DF6), heroFg = Color.White, heroChip = Color(0x33FFFFFF),
    primary = Color(0xFF6C4DF6), onPrimary = Color.White,
    coin = Color(0xFFFFB37A), ink = Color(0xFF2A2150),
    // Zarte Flächen: nur als Hinterlegung für Ausgewähltes und Hinweise, nie großflächig bunt.
    soft1 = Color(0xFFFFF3EA), soft2 = Color(0xFFEFEBFE), soft3 = Color(0xFFFBF7E6),
    warn = Color(0xFFD9663B), good = Color(0xFF2E9E6E), line = Color(0xFFECE8F6), dark = false,
)

val DarkColors = CashdyColors(
    bg = Color(0xFF17142A), surface = Color(0xFF221D3B), fg = Color(0xFFF1EDFF), muted = Color(0xFFA9A1D1),
    hero = Color(0xFF7A5CFF), heroFg = Color.White, heroChip = Color(0x2EFFFFFF),
    primary = Color(0xFF8E76FF), onPrimary = Color.White,
    coin = Color(0xFFFFB37A), ink = Color(0xFF2A2150),
    soft1 = Color(0xFF33262C), soft2 = Color(0xFF2C2552), soft3 = Color(0xFF2F2B22),
    warn = Color(0xFFFF9A70), good = Color(0xFF6FD6A4), line = Color(0xFF2F2852), dark = true,
)

val LocalCashdy = staticCompositionLocalOf { LightColors }

object C {
    val colors: CashdyColors @Composable get() = LocalCashdy.current
}

// Feste Schnitte je Stärke statt einer variablen Datei: Android verwechselt bei variablen
// Schriften aus derselben Datei sonst die Stärken (SemiBold erschien dünn).
val Fredoka = FontFamily(
    Font(R.font.fredoka_400, FontWeight.Normal),
    Font(R.font.fredoka_500, FontWeight.Medium),
    Font(R.font.fredoka_600, FontWeight.SemiBold),
    Font(R.font.fredoka_700, FontWeight.Bold),
)
val Nunito = FontFamily(
    Font(R.font.nunito_500, FontWeight.Medium),
    Font(R.font.nunito_600, FontWeight.SemiBold),
    Font(R.font.nunito_700, FontWeight.Bold),
    Font(R.font.nunito_800, FontWeight.ExtraBold),
)

private val typography = Typography(
    displayLarge = TextStyle(fontFamily = Fredoka, fontWeight = FontWeight.Bold, fontSize = 46.sp, lineHeight = 48.sp),
    headlineLarge = TextStyle(fontFamily = Fredoka, fontWeight = FontWeight.SemiBold, fontSize = 28.sp),
    headlineMedium = TextStyle(fontFamily = Fredoka, fontWeight = FontWeight.SemiBold, fontSize = 24.sp),
    headlineSmall = TextStyle(fontFamily = Fredoka, fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
    titleLarge = TextStyle(fontFamily = Fredoka, fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
    titleMedium = TextStyle(fontFamily = Fredoka, fontWeight = FontWeight.Medium, fontSize = 17.sp),
    titleSmall = TextStyle(fontFamily = Fredoka, fontWeight = FontWeight.Medium, fontSize = 15.sp),
    bodyLarge = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = Fredoka, fontWeight = FontWeight.Medium, fontSize = 15.sp),
    labelMedium = TextStyle(fontFamily = Fredoka, fontWeight = FontWeight.Medium, fontSize = 13.sp),
    labelSmall = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 11.sp),
)

@Composable
fun CashdyTheme(content: @Composable () -> Unit) {
    val c = if (isSystemInDarkTheme()) DarkColors else LightColors
    val scheme = if (c.dark) darkColorScheme(
        primary = c.primary, onPrimary = c.onPrimary, secondary = c.coin, onSecondary = c.ink,
        background = c.bg, onBackground = c.fg, surface = c.surface, onSurface = c.fg,
        surfaceVariant = c.soft2, onSurfaceVariant = c.muted, surfaceContainerHigh = c.surface,
        surfaceContainer = c.surface, surfaceContainerLow = c.surface, surfaceContainerHighest = c.soft2,
        outline = c.line, error = c.warn,
    ) else lightColorScheme(
        primary = c.primary, onPrimary = c.onPrimary, secondary = c.coin, onSecondary = c.ink,
        background = c.bg, onBackground = c.fg, surface = c.surface, onSurface = c.fg,
        surfaceVariant = c.soft2, onSurfaceVariant = c.muted, surfaceContainerHigh = c.surface,
        surfaceContainer = c.surface, surfaceContainerLow = c.surface, surfaceContainerHighest = c.soft2,
        outline = c.line, error = c.warn,
    )
    CompositionLocalProvider(LocalCashdy provides c) {
        MaterialTheme(colorScheme = scheme, typography = typography, content = content)
    }
}
