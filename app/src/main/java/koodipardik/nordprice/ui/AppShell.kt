package koodipardik.nordprice.ui

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import koodipardik.nordprice.R
import koodipardik.nordprice.core.Day
import koodipardik.nordprice.data.SampleData
import koodipardik.nordprice.ui.components.ConnectionLostScreen
import koodipardik.nordprice.ui.components.GradientBackground
import koodipardik.nordprice.ui.rememberIsTablet
import koodipardik.nordprice.ui.history.HistoryScreen
import koodipardik.nordprice.ui.price.PriceDashboard
import koodipardik.nordprice.ui.price.PriceScreen
import koodipardik.nordprice.ui.settings.SettingsScreen
import koodipardik.nordprice.ui.theme.AppTheme

/**
 * One bottom-bar destination. The list is built per window size: a phone gets Today and Tomorrow,
 * a tablet merges them into a single "Prices" dashboard, exactly as the iOS tab bar does when both
 * size classes are regular.
 */
private class TabSpec(
    val labelRes: Int,
    val icon: ImageVector,
    val content: @Composable () -> Unit,
)

@Composable
private fun tabs(tablet: Boolean): List<TabSpec> = buildList {
    if (tablet) {
        add(TabSpec(R.string.label_prices, Icons.Filled.Bolt) { PriceDashboard() })
    } else {
        add(TabSpec(R.string.label_today, Icons.Filled.Bolt) { PriceScreen(Day.TODAY) })
        add(TabSpec(R.string.label_tomorrow, Icons.Filled.Schedule) { PriceScreen(Day.TOMORROW) })
    }
    add(TabSpec(R.string.label_history, Icons.Filled.BarChart) { HistoryScreen() })
    add(TabSpec(R.string.label_settings, Icons.Filled.Settings) { SettingsScreen() })
}

/**
 * Root of the UI: connection gate, tab bar, and the two app-wide behaviours the iOS app sets
 * outside its views — the keep-screen-on flag and the per-app language.
 */
@Composable
fun NordPriceRoot() {
    val container = rememberContainer()
    val settings = rememberSettings()
    val online by container.networkMonitor.isOnline.collectAsStateWithLifecycle(initialValue = true)

    // iOS defaults `isConnected` to true and only swaps the UI when the monitor reports
    // otherwise; sample-data mode never blocks on the network.
    if (!online && !SampleData.enabled) {
        ConnectionLostScreen()
        return
    }

    AppShell(settings.alwaysOnDisplay, settings.language.code)
    val tablet = rememberIsTablet()

    Box(modifier = Modifier.fillMaxSize()) {
        GradientBackground {
            var selected by remember(tablet) { mutableIntStateOf(0) }
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = Color.Transparent,
                contentColor = AppTheme.textOnBackground,
                bottomBar = {
                    NavigationBar(
                        containerColor = Color.Transparent,
                        tonalElevation = 0.dp,
                    ) {
                        tabs(tablet).forEachIndexed { index, tab ->
                            NavigationBarItem(
                                selected = selected == index,
                                onClick = { selected = index },
                                icon = { Icon(tab.icon, contentDescription = null) },
                                label = {
                                    Text(
                                        text = stringResource(tab.labelRes),
                                        fontSize = 12.sp,
                                        fontWeight = if (selected == index) {
                                            FontWeight.SemiBold
                                        } else {
                                            FontWeight.Medium
                                        },
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = AppTheme.brand,
                                    selectedTextColor = AppTheme.brand,
                                    indicatorColor = AppTheme.brand.copy(alpha = 0.14f),
                                    unselectedIconColor = AppTheme.secondaryText,
                                    unselectedTextColor = AppTheme.secondaryText,
                                ),
                            )
                        }
                    }
                },
            ) { contentPadding ->
                Box(
                    // The Scaffold's contentPadding already clears the status bar and the
                    // bottom bar; padding the system bars on top of it pushed everything down.
                    modifier = Modifier
                        .padding(contentPadding)
                        .fillMaxSize(),
                ) {
                    val destinations = tabs(tablet)
                    destinations.getOrNull(selected)?.content?.invoke()
                }
            }
        }
    }
}

/**
 * Side effects that belong to the application rather than to a screen: the keep-screen-on flag
 * (iOS `UIApplication.isIdleTimerDisabled`) and the in-app language, which on Android is the
 * per-app locales API and therefore needs the activity to be re-created with the new config.
 */
@Composable
private fun AppShell(alwaysOnDisplay: Boolean, languageCode: String) {
    val context = LocalContext.current
    val activity = context as? Activity

    DisposableEffect(alwaysOnDisplay) {
        val window = activity?.window
        if (window != null) {
            if (alwaysOnDisplay) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
        onDispose { }
    }

    LaunchedEffect(languageCode) {
        val current = AppCompatDelegate.getApplicationLocales()
        val wanted = LocaleListCompat.forLanguageTags(languageCode)
        if (current.toLanguageTags() != wanted.toLanguageTags()) {
            AppCompatDelegate.setApplicationLocales(wanted)
        }
    }
}
