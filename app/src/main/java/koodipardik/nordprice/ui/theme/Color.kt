package koodipardik.nordprice.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The palette of the iOS app, ported value-for-value from `Assets.xcassets/Colors`.
 *
 * Both variants are listed side by side rather than through Material roles, because the two
 * schemes are not recolors of each other: light mode is a system-blue-to-white gradient with
 * white display text and blue card text, dark mode is the website's ink-blue `#1A1E35` with
 * `#E8EAF6` text. Mapping that onto Material roles would lose the design.
 */
@Immutable
data class AppColors(
    val isDark: Boolean,
    /** Page gradient, top-leading to bottom-trailing. */
    val backgroundTop: Color,
    val backgroundBottom: Color,
    /** Card fill. */
    val card: Color,
    /** Thin ring around cards — invisible in light, `ink/8` in dark, as on the website. */
    val cardStroke: Color,
    /** Text placed directly on the page background. */
    val textOnBackground: Color,
    /** Primary text inside cards: system blue in light, near-white in dark. */
    val cardText: Color,
    /** Small labels inside cards. */
    val cardLabel: Color,
    /** Muted secondary text (axis labels, legend, dates). */
    val secondaryText: Color,
    /** Accent: system blue in light (original look), brand blue `#7B8FFF` in dark. */
    val brand: Color,
    /** Bottom bar fill. */
    val tabBar: Color,
    /** Current-interval highlight of the single-colour chart. */
    val currentHighlight: Color,
    /** Elapsed bars: neutral gray, so past reads apart from future at a glance. */
    val pastBar: Color,
    /** "Open Settings" pill on the connection-lost screen. */
    val pillBackground: Color,
    val pillText: Color,
)

val LightColors = AppColors(
    isDark = false,
    backgroundTop = Color(0xFF007AFF), // iOS systemBlue
    backgroundBottom = Color(0xFFFFFFFF),
    card = Color(0xFFFFFFFF),
    cardStroke = Color(0x00FFFFFF),
    textOnBackground = Color(0xFFFFFFFF),
    cardText = Color(0xFF007AFF),
    cardLabel = Color(0xFF007AFF),
    secondaryText = Color(0x8A090909),
    brand = Color(0xFF007AFF),
    tabBar = Color(0xFFFFFFFF),
    currentHighlight = Color(0xFFFF964F),
    pastBar = Color(0x4D808080), // gray @ 30 %
    pillBackground = Color(0xFFFFFFFF),
    pillText = Color(0xFF5C9BF8),
)

val DarkColors = AppColors(
    isDark = true,
    backgroundTop = Color(0xFF1A1E35),
    backgroundBottom = Color(0xFF252A42),
    card = Color(0xFF262B47),
    cardStroke = Color(0x14E8EAF6), // #E8EAF6 @ 8 %
    textOnBackground = Color(0xFFE8EAF6),
    cardText = Color(0xFFE8EAF6),
    cardLabel = Color(0x99E8EAF6), // @ 60 %
    secondaryText = Color(0x99E8EAF6),
    brand = Color(0xFF7B8FFF),
    tabBar = Color(0xFF262B47),
    currentHighlight = Color(0xFFFF964F),
    pastBar = Color(0x59808080), // gray @ 35 %
    pillBackground = Color(0xFF7B8FFF),
    pillText = Color(0xFFFFFFFF),
)

/** Chart constants that have no asset-catalog entry, kept together so both charts agree. */
object ChartPalette {
    val futureBarAlpha = 0.8f
    val unselectedBarAlpha = 0.45f
    val lineFutureAlpha = 1.0f
    val linePastAlpha = 0.4f
    val areaTopAlpha = 0.30f
    val areaBottomAlpha = 0.03f
    val gridAlpha = 0.30f
    val gridYAlpha = 0.20f
    val ruleAlpha = 0.50f
    val nowRuleAlpha = 0.35f
}

val LocalAppColors = staticCompositionLocalOf { LightColors }
