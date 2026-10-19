package com.focustask.ui.theme

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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** What the main screens are "about" right now. It decides the colours. */
enum class Tone { FOCUS, OVERDUE, BREAK, ENDING }

@Immutable
data class Palette(
    val dark: Boolean,
    val bg: Color,
    val surface: Color,
    val ink: Color,
    val muted: Color,
    val accent: Color,
    val onAccent: Color,
    val card: Color,
    val cardInk: Color,
    val pill: Color,
    val pillInk: Color,
    val bar: Color,
    val line: Color,
    val chip: Color,
    val danger: Color,
    val dangerSoft: Color,
    val bubble: Color,
    val navBg: Color,
)

private val SunFill = Color(0xFFFFF4DB)
private val SunRing = Color(0xFFD9930A)
val SunFillColor get() = SunFill
val SunRingColor get() = SunRing

private fun c(v: Long) = Color(v)

fun paletteFor(tone: Tone, dark: Boolean): Palette = when (tone) {
    Tone.FOCUS -> if (!dark) Palette(
        false, c(0xFFF4F5F2), c(0xFFFFFFFF), c(0xFF141A17), c(0xFF5F6B66), c(0xFF2F5D50), c(0xFFFFFFFF),
        c(0xFFDCE9E4), c(0xFF141A17), c(0xFFDCE9E4), c(0xFF2F5D50), c(0xFFE8F0EC), c(0xFFD9DED9),
        c(0xFFE8EAE6), c(0xFFB42318), c(0xFFFDE8E6), c(0xFF2F5D50), c(0xFFFFFFFF),
    ) else Palette(
        true, c(0xFF0F1311), c(0xFF1A201D), c(0xFFEEF2EF), c(0xFF9AA7A1), c(0xFF7CC4AE), c(0xFF0F1311),
        c(0xFF24312C), c(0xFFEEF2EF), c(0xFF24312C), c(0xFF7CC4AE), c(0xFF1A201D), c(0xFF2A332F),
        c(0xFF252C28), c(0xFFFF8A80), c(0xFF3A1C19), c(0xFF7CC4AE), c(0xFF151A18),
    )
    Tone.OVERDUE -> if (!dark) Palette(
        true, c(0xFFB42318), c(0xFF8F1B12), c(0xFFFFFFFF), c(0xFFF6CFCB), c(0xFFFFFFFF), c(0xFFB42318),
        c(0xFF7A1510), c(0xFFFFFFFF), c(0xFF7A1510), c(0xFFFFFFFF), c(0xFF7A1510), c(0xFFD35A50),
        c(0xFF8F1B12), c(0xFFFFE08A), c(0xFF7A1510), c(0xFFFFE08A), c(0xFFFFFFFF),
    ) else Palette(
        true, c(0xFF7A1510), c(0xFF5C0F0B), c(0xFFFFFFFF), c(0xFFE8B9B4), c(0xFFFFFFFF), c(0xFF7A1510),
        c(0xFF4F0E0A), c(0xFFFFFFFF), c(0xFF4F0E0A), c(0xFFFFFFFF), c(0xFF4F0E0A), c(0xFFA3463E),
        c(0xFF5C0F0B), c(0xFFFFE08A), c(0xFF4F0E0A), c(0xFFFFE08A), c(0xFF151A18),
    )
    Tone.BREAK -> if (!dark) Palette(
        false, c(0xFFECEFF8), c(0xFFFFFFFF), c(0xFF141A17), c(0xFF5A6280), c(0xFF3B4F9A), c(0xFFFFFFFF),
        c(0xFFDDE3F3), c(0xFF141A17), c(0xFFDDE3F3), c(0xFF3B4F9A), c(0xFFDDE3F3), c(0xFFCBD2E8),
        c(0xFFDDE3F3), c(0xFFB42318), c(0xFFFDE8E6), c(0xFF3B4F9A), c(0xFFFFFFFF),
    ) else Palette(
        true, c(0xFF12162A), c(0xFF1B2140), c(0xFFEEF0FA), c(0xFFA0A8C8), c(0xFF9DB0F0), c(0xFF12162A),
        c(0xFF1E2540), c(0xFFEEF0FA), c(0xFF1E2540), c(0xFF9DB0F0), c(0xFF1E2540), c(0xFF2C3458),
        c(0xFF252C4A), c(0xFFFF8A80), c(0xFF3A1C19), c(0xFF9DB0F0), c(0xFF151A18),
    )
    Tone.ENDING -> if (!dark) Palette(
        false, c(0xFFF4F5F2), c(0xFFFFFFFF), c(0xFF4A3200), c(0xFF7A5A1E), c(0xFF8A5A00), c(0xFFFFFFFF),
        c(0xFFFFF4DB), c(0xFF4A3200), c(0xFFFFE9B8), c(0xFF8A5A00), c(0xFFFFF4DB), c(0xFFEBD9A8),
        c(0xFFFFE9B8), c(0xFFB42318), c(0xFFFDE8E6), c(0xFF8A5A00), c(0xFFFFFFFF),
    ) else Palette(
        true, c(0xFF0F1311), c(0xFF1A201D), c(0xFFFFE9B8), c(0xFFD2B77A), c(0xFFE0A93A), c(0xFF0F1311),
        c(0xFF3A2C0A), c(0xFFFFE9B8), c(0xFF3A2C0A), c(0xFFE0A93A), c(0xFF3A2C0A), c(0xFF5A4410),
        c(0xFF3A2C0A), c(0xFFFF8A80), c(0xFF3A1C19), c(0xFFE0A93A), c(0xFF151A18),
    )
}

val LocalPalette = staticCompositionLocalOf { paletteFor(Tone.FOCUS, false) }

private val Sans = FontFamily.SansSerif

private val FocusTypography = Typography(
    bodyLarge = TextStyle(fontFamily = Sans, fontSize = 16.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontFamily = Sans, fontSize = 14.sp),
    bodySmall = TextStyle(fontFamily = Sans, fontSize = 12.sp),
    titleLarge = TextStyle(fontFamily = Sans, fontSize = 28.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontFamily = Sans, fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
    labelLarge = TextStyle(fontFamily = Sans, fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
    labelSmall = TextStyle(fontFamily = Sans, fontSize = 11.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun FocusTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val p = paletteFor(Tone.FOCUS, dark)
    val scheme = if (dark) darkColorScheme(
        primary = p.accent, onPrimary = p.onAccent, surface = p.surface, onSurface = p.ink,
        background = p.bg, onBackground = p.ink, surfaceVariant = p.card, onSurfaceVariant = p.muted,
        secondaryContainer = p.card, onSecondaryContainer = p.ink, outline = p.line,
        surfaceContainerHigh = p.surface, surfaceContainerHighest = p.card, surfaceContainer = p.surface,
        primaryContainer = p.card, onPrimaryContainer = p.ink,
    ) else lightColorScheme(
        primary = p.accent, onPrimary = p.onAccent, surface = p.surface, onSurface = p.ink,
        background = p.bg, onBackground = p.ink, surfaceVariant = p.card, onSurfaceVariant = p.muted,
        secondaryContainer = p.card, onSecondaryContainer = p.ink, outline = p.line,
        surfaceContainerHigh = p.surface, surfaceContainerHighest = p.card, surfaceContainer = p.surface,
        primaryContainer = p.card, onPrimaryContainer = p.ink,
    )
    CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(colorScheme = scheme, typography = FocusTypography, content = content)
    }
}
