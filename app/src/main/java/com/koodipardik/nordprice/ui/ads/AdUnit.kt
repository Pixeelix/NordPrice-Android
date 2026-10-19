package com.koodipardik.nordprice.ui.ads

import android.util.Log
import com.koodipardik.nordprice.BuildConfig

/**
 * Ad unit ids — the port of iOS `AdUnit` in `AdConstants.swift`.
 *
 * **The production values are blank on purpose.** AdMob units belong to one app record on one
 * platform, so the iOS ids (`ca-app-pub-5431783362632568/…`) cannot be reused here: each entry below
 * has to become the unit created for the *Android* app in the AdMob console, and the
 * `com.google.android.gms.ads.APPLICATION_ID` meta-data in the manifest has to become that app's id
 * (it currently holds Google's sample app id, without which the SDK refuses to initialise).
 *
 * Debug builds do not care, which is the point: [AdPlacement.resolveUnitId] substitutes Google's
 * public test unit exactly as iOS forces one inside `#if DEBUG` (`Banner.swift:47`), so the layout,
 * the consent gate and the collapse behaviour are all testable on a phone before a single real id
 * exists.
 *
 * A release build with a blank id shows no banner and logs which placement is unconfigured. It does
 * **not** fall back to the test unit — serving test ads from production is an AdMob policy
 * violation, and an ad-free screen is a better failure than a suspended account.
 */
enum class AdPlacement(private val productionUnitId: String, private val label: String) {
    TODAY("", "today"),
    TOMORROW_PENDING("", "tomorrow-no-data"),
    TOMORROW_READY("", "tomorrow-data"),
    HISTORY("", "history"),
    SETTINGS("", "settings"),
    ;

    /** The unit to request, or null when this build has none configured. */
    fun resolveUnitId(): String? = when {
        BuildConfig.DEBUG -> TEST_BANNER
        productionUnitId.isBlank() -> {
            Log.e(TAG, "No AdMob unit id configured for the $label banner; showing nothing")
            null
        }

        else -> productionUnitId
    }

    private companion object {
        const val TAG = "NordPriceAds"

        /** Google's public sample banner unit, paired with [AdUnit.TEST_APP_ID]. */
        const val TEST_BANNER = "ca-app-pub-3940256099942544/6300978111"
    }
}

object AdUnit {
    /** Google's sample app id. Replace in the manifest with the Android app's own id. */
    const val TEST_APP_ID = "ca-app-pub-3940256099942544~3347511713"
}
