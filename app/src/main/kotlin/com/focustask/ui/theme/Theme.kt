package com.focustask.ui.theme

import android.animation.ValueAnimator
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalContext
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
enum class Tone { FOCUS, BREAK, ENDING }

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

/** The size of every screen and page title (Day, Week, Lists, Settings and the pages inside them, Today's states). */
val TitleSize = 28.sp

/** Overdue, in light and dark: the red of the OVERDUE pill and of the outline round an overdue card, with white on it. */
val OverdueRed = Color(0xFFB42318)
val OnDanger = Color.White
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

/**
 * The palette with the wallpaper's colors ([wall], Android 12+) on the backgrounds and accents. The long break takes
 * the wallpaper's third color, so it still looks different from work. Ending keeps its amber, on the wallpaper's backgrounds.
 */
fun paletteFor(tone: Tone, dark: Boolean, wall: ColorScheme?): Palette {
    val base = paletteFor(tone, dark)
    if (wall == null) return base
    return when (tone) {
        // the long break keeps its own role: the wallpaper's third color instead of the blue
        Tone.BREAK -> base.copy(
            bg = wall.surface, surface = if (dark) wall.surfaceContainer else wall.surfaceContainerLowest,
            ink = wall.onSurface, muted = wall.onSurfaceVariant,
            accent = wall.tertiary, onAccent = wall.onTertiary,
            card = wall.tertiaryContainer, cardInk = wall.onTertiaryContainer,
            pill = wall.tertiaryContainer, pillInk = wall.onTertiaryContainer,
            bar = wall.tertiaryContainer, line = wall.outlineVariant, chip = wall.surfaceContainerHigh,
            bubble = wall.tertiary, navBg = wall.surfaceContainer,
        )
        Tone.FOCUS -> base.copy(
            bg = wall.surface, surface = if (dark) wall.surfaceContainer else wall.surfaceContainerLowest,
            ink = wall.onSurface, muted = wall.onSurfaceVariant,
            accent = wall.primary, onAccent = wall.onPrimary,
            card = wall.secondaryContainer, cardInk = wall.onSecondaryContainer,
            pill = wall.secondaryContainer, pillInk = wall.onSecondaryContainer,
            bar = wall.surfaceContainerHigh, line = wall.outlineVariant, chip = wall.surfaceContainerHigh,
            bubble = wall.primary, navBg = wall.surfaceContainer,
        )
        Tone.ENDING -> base.copy(
            bg = wall.surface, surface = if (dark) wall.surfaceContainer else wall.surfaceContainerLowest,
            line = wall.outlineVariant, chip = wall.surfaceContainerHigh, navBg = wall.surfaceContainer,
        )
    }
}

/**
 * The phone's "Remove animations" accessibility setting is on: the app then changes at once, without turning wheels,
 * dropping shades or spinning icons.
 */
val LocalReduceMotion = staticCompositionLocalOf { false }

fun animationsOff(): Boolean = !ValueAnimator.areAnimatorsEnabled()

/** The wallpaper's color scheme when "Match my wallpaper" is on, else null. */
val LocalWallpaper = staticCompositionLocalOf<ColorScheme?> { null }

/** [paletteFor] with the wallpaper's colors when they are switched on. */
@Composable
@ReadOnlyComposable
fun palette(tone: Tone, dark: Boolean): Palette = paletteFor(tone, dark, LocalWallpaper.current)

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
fun FocusTheme(wallpaper: Boolean = false, content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val wall = if (wallpaper && Build.VERSION.SDK_INT >= 31) {
        if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    } else null
    val p = paletteFor(Tone.FOCUS, dark, wall)
    // read again each time the app comes back: the setting is changed outside the app
    var still by remember { mutableStateOf(animationsOff()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { still = animationsOff() }
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
    CompositionLocalProvider(LocalPalette provides p, LocalWallpaper provides wall, LocalReduceMotion provides still) {
        MaterialTheme(colorScheme = scheme, typography = FocusTypography, content = content)
    }
}
