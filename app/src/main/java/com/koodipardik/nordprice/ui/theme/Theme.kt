package com.koodipardik.nordprice.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * The app's own color system, plus a Material scheme derived from it so that the widgets we
 * do take from Material — switches, bottom sheets, text fields — pick up the same brand blue
 * instead of Material defaults.
 */
@Composable
fun NordPriceTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val scheme = if (darkTheme) darkScheme(colors) else lightScheme(colors)
    val view = LocalView.current

    SideEffect {
        val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
        // The gradient reaches the top edge, so the status bar icons stay light in both modes.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
    }

    CompositionLocalProvider(LocalAppColors provides colors) {
        MaterialTheme(colorScheme = scheme, typography = AppTypography, content = content)
    }
}

private fun lightScheme(c: AppColors) = lightColorScheme(
    primary = c.brand,
    onPrimary = Color.White,
    secondary = c.brand,
    surface = c.card,
    onSurface = c.cardText,
    onSurfaceVariant = c.secondaryText,
    background = c.card,
    onBackground = c.cardText,
    outlineVariant = c.cardStroke,
    scrim = Color.Black,
)

private fun darkScheme(c: AppColors) = darkColorScheme(
    primary = c.brand,
    onPrimary = Color.White,
    secondary = c.brand,
    surface = c.card,
    onSurface = c.cardText,
    onSurfaceVariant = c.secondaryText,
    background = c.card,
    onBackground = c.cardText,
    outlineVariant = c.cardStroke,
)

/**
 * Type scale mirroring the iOS `Font.system` sizes the screens actually use.
 * iOS sizes are points and Android sp, and both scale with the user's text-size setting.
 */
val AppTypography = Typography()

/** The palette the running screen should draw with. */
val AppTheme: AppColors
    @Composable @ReadOnlyComposable get() = LocalAppColors.current
