package com.koodipardik.nordprice.ui.ads

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.koodipardik.nordprice.BuildConfig

/**
 * Where a banner goes, and which AdMob unit it asks for — the port of iOS `AdUnit` in
 * `AdConstants.swift`.
 *
 * **One unit per placement, not one shared unit** — which is what iOS does too. AdMob reports fill rate
 * and revenue per ad unit, so a unit each is the only way to ask "is this slot earning its keep?", and
 * the only way to replace, floor or kill one placement in the console without touching the others.
 *
 * The ids below belong to the *Android* app record —
 * `ca-app-pub-5431783362632568~3036192953`, declared as `com.google.android.gms.ads.APPLICATION_ID` in
 * `app/src/main/AndroidManifest.xml`, which is the only place the SDK reads it from and so the one
 * reason this file cannot hold all the configuration alone. The iOS unit ids share that publisher
 * prefix but not that app record, and AdMob does not let a unit cross platforms.
 *
 * Every entry here is composed somewhere, and there are exactly four because that is how many screens
 * take a banner: three on the price screen (`PriceScreen`) and one on history (`HistoryScreen`).
 * Settings has no placement, as on iOS — `AdUnit` has none either, and the Android-only slot that had
 * been added there is gone.
 *
 * Debug builds ignore the ids above, which is the point: [resolveUnitId] substitutes Google's public
 * test unit exactly as iOS forces one inside `#if DEBUG` (`Banner.swift:47`), and
 * `app/src/debug/AndroidManifest.xml` substitutes Google's sample app id. So the layout, the consent
 * gate and the collapse behaviour are all testable on a phone without spending a real impression — and
 * without ever risking a test ad in production, since neither substitution is compiled into release.
 *
 * A blank unit is not a crash and not a test ad: [resolveUnitId] returns null and the banner stays
 * out, after a `Log.e` that R8 leaves in the release build. Serving test ads from production is an
 * AdMob policy violation, and an ad-free screen is a better failure than a suspended account.
 * [isConfigured] applies the same reasoning one level up, to the consent form and `MobileAds.initialize`.
 */
enum class AdPlacement(private val productionUnitId: String, private val label: String) {
    TODAY("ca-app-pub-5431783362632568/4073730424", "today"),
    TOMORROW_PENDING("ca-app-pub-5431783362632568/9534152697", "tomorrow-no-data"),
    TOMORROW_READY("ca-app-pub-5431783362632568/7186978928", "tomorrow-data"),

    /** The history screen's slot, composed by both of its arrangements, as on iOS. */
    HISTORY("ca-app-pub-5431783362632568/3850369144", "history"),
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

    companion object {
        private const val TAG = "NordPriceAds"

        /**
         * Google's public sample banner unit, paired with the sample app id that
         * `app/src/debug/AndroidManifest.xml` merges over `src/main` in debug builds only.
         */
        private const val TEST_BANNER = "ca-app-pub-3940256099942544/6300978111"

        /**
         * The shape of an AdMob app id (`ca-app-pub-<16 digits>~<digits>`). Matching a shape rather than
         * comparing against the value the manifest happens to hold today is what catches a half-pasted
         * or mistyped id, and an absent one — the configuration mistakes the SDK punishes hardest, since
         * `MobileAds.initialize` refuses a malformed app id outright.
         */
        private val APPLICATION_ID_SHAPE = Regex("ca-app-pub-\\d{16}~\\d+")

        /**
         * Whether this build has enough AdMob configuration to be worth asking consent for.
         *
         * Guards [ConsentManager.gatherConsent] rather than just [resolveUnitId]: a user who is shown a
         * GDPR form and answers it, in a build that then requests nothing, has been asked for something
         * the app did not need — and an app id that is not a real one makes `MobileAds.initialize`
         * throw. Both are worse than staying silent about ads entirely.
         *
         * One configured unit is enough. Which placements are still blank is [resolveUnitId]'s problem,
         * per banner, and a partially filled list should serve the ones that are filled.
         *
         * Debug is exempt from the unit check, and has to be: [resolveUnitId] answers with Google's test
         * unit for every placement, so `productionUnitId` is blank there by design. Requiring one would
         * gate consent off, keep `canRequestAds` false forever, and leave a banner that never appears in
         * the only build type anyone uses to develop it.
         *
         * The complaint is logged once per process: the caller is
         * [ConsentManager.gatherConsent], which runs on every `onResume`, and a line that repeats each
         * time the app is foregrounded is a line that stops being read.
         */
        fun isConfigured(context: Context): Boolean {
            val applicationId = applicationId(context)
            val hasUnits = BuildConfig.DEBUG || values().any { it.productionUnitId.isNotBlank() }
            val configured = applicationId?.matches(APPLICATION_ID_SHAPE) == true && hasUnits
            if (!configured && !complained) {
                complained = true
                Log.e(
                    TAG,
                    "AdMob not configured (app id ${applicationId ?: "absent"}, " +
                        "unit ids ${if (hasUnits) "present" else "all blank"}); no consent form, no banners",
                )
            }
            return configured
        }

        private var complained = false

        /**
         * The app id this build was installed with, read back out of the merged manifest — the only
         * place the SDK itself looks, and the reason a debug build sees Google's sample id while a
         * release build sees whatever `src/main` declares.
         */
        private fun applicationId(context: Context): String? = runCatching {
            context.packageManager
                .getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
                .metaData?.getString("com.google.android.gms.ads.APPLICATION_ID")
        }.getOrNull()
    }
}
