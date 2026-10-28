package com.koodipardik.nordprice.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp

/**
 * Whether the app has a tablet-sized window and should use the two-day dashboard instead of
 * separate Today and Tomorrow tabs.
 *
 * `smallestScreenWidthDp` rather than the current width: it does not change when the device
 * rotates, so a phone turned sideways keeps the phone layout. That mirrors how the iOS app decides,
 * where the dashboard needs a *regular* width and a *regular* height — an iPhone in landscape is
 * regular width but compact height, and stays on the tabbed layout.
 */
@Composable
fun rememberIsTablet(): Boolean = LocalConfiguration.current.smallestScreenWidthDp >= 600

/** Width of the card column beside a chart, on the two wide layouts that put them side by side. */
val TabletSideColumnWidth = 340.dp

/**
 * Widest a stacked column of cards and chart gets on a large window. Past it the content is centred
 * with margins instead of stretched edge to edge, which keeps a 10" tablet held upright reading as one
 * page rather than a blown-up phone screen. A phone, and a 7" tablet in portrait, never reach it.
 */
val WideContentMaxWidth = 720.dp
