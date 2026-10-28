package com.koodipardik.nordprice.ui.ads

import android.util.Log
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.koodipardik.nordprice.ui.rememberSettings

/**
 * An anchor banner that behaves like iOS `AdaptiveBannerAd`: it occupies no vertical space until an
 * ad has actually loaded, so a no-fill — or the whole session before consent resolves, or a build with
 * no unit id for this placement — collapses instead of leaving a gap under the chart. iOS animates the
 * same reveal over 0.25 s.
 *
 * Three independent gates run before anything is asked for, and each of them is why the layout below
 * can legitimately draw nothing: sample data on (the offline mode, which is also iOS's screenshot
 * mode), consent unresolved, or [AdPlacement.resolveUnitId] returning null for a placement this build
 * has no id for.
 */
@Composable
fun AnchorBanner(placement: AdPlacement, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val settings = rememberSettings()

    // iOS hides banners in screenshot mode (`AdRuntimeConfiguration.hidesAdBanners`). The Android
    // switch that means the same thing is DEBUG › Sample data, which is also the offline mode.
    if (settings.useSampleData) return

    val canRequestAds by ConsentManager.canRequestAds.collectAsState()
    if (!canRequestAds) return

    val unitId = placement.resolveUnitId() ?: return

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val adWidthDp = rememberAdWidthDp(maxWidth)
        // Inline adaptive, not anchored: the anchored factories are deprecated in the 25.x SDK, and
        // iOS asks for an inline adaptive banner too — including the 50 pt cap, which is why the
        // height is a request rather than something cropped afterwards. Both measurements are dp,
        // so the SDK's own maths decides the shape and AdSize.getHeight() reads it back.
        val adSize = remember(adWidthDp) {
            AdSize.getInlineAdaptiveBannerAdSize(adWidthDp, MAX_AD_HEIGHT_DP)
        }

        var loaded by remember { mutableStateOf(false) }
        val animatedHeight by animateIntAsState(
            targetValue = if (loaded) adSize.height else 0,
            animationSpec = tween(250),
            label = "bannerHeight",
        )

        AndroidView(
            factory = { viewContext ->
                AdView(viewContext).apply {
                    setAdSize(adSize)
                    adUnitId = unitId
                    adListener = object : AdListener() {
                        override fun onAdLoaded() {
                            loaded = true
                        }

                        override fun onAdFailedToLoad(error: LoadAdError) {
                            // Stays collapsed. A no-fill is routine, not a fault: log it, show nothing.
                            Log.i(TAG, "${placement.name} banner: no fill (${error.code})")
                        }
                    }
                    loadAd(AdRequest.Builder().build())
                }
            },
            // A rotated or resized window changes the adaptive size, and an AdView cannot be
            // re-sized after construction without reloading into the new one.
            update = { view ->
                if (view.adSize != adSize) {
                    view.setAdSize(adSize)
                    loaded = false
                    view.loadAd(AdRequest.Builder().build())
                }
            },
            onRelease = { view -> view.destroy() },
            modifier = Modifier
                .fillMaxWidth()
                .height(animatedHeight.dp),
        )
    }
}

/** The cap iOS documents in `Banner.swift:22` — "inline adaptive ads are never taller than this". */
private const val MAX_AD_HEIGHT_DP = 50

private const val TAG = "NordPriceAds"

/**
 * Banner width in dp, clamped to a positive Int the SDK accepts. `AdSize` is specified in dp, so the
 * Compose constraint converts straight through with no density maths.
 */
@Composable
private fun rememberAdWidthDp(maxWidth: Dp): Int = remember(maxWidth) {
    maxWidth.value.toInt().coerceAtLeast(1)
}
