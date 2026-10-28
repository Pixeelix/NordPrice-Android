package com.koodipardik.nordprice.ui.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.initialization.AdapterStatus
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Gathers UMP consent and starts the Mobile Ads SDK once ads may be requested — the port of
 * `ConsentManager.swift`, singleton and all.
 *
 * One copy, both build types: since ads ship, there is nothing left for a release stub to hide, and
 * the release build is proof enough that the path compiles. What still differs per build type is the
 * configuration behind it — Google's sample app id and test unit in debug, the real ones in release.
 *
 * Nothing initialises MobileAds at app start, deliberately: a request before consent is the exact
 * thing GDPR enforcement is about, so the SDK only starts once `canRequestAds` says so, which is also
 * what iOS does in `refreshState` (`ConsentManager.swift:78`). Making that true also required removing
 * the SDK's own auto-init provider — see the comment in `app/src/main/AndroidManifest.xml` — which in
 * turn makes [refreshState] responsible for the sequencing, not just the permission.
 *
 * UMP needs an `Activity` to present its form from, which is why the entry points take one instead
 * of being wired into [com.koodipardik.nordprice.AppContainer] like the other long-lived objects.
 */
object ConsentManager {

    private const val TAG = "NordPriceAds"

    private val _canRequestAds = MutableStateFlow(false)

    /** Banners stay out of the layout until this is true, as on iOS. */
    val canRequestAds: StateFlow<Boolean> = _canRequestAds.asStateFlow()

    private val _privacyOptionsRequired = MutableStateFlow(false)

    /** Drives whether Settings › About offers the "Privacy options" entry. */
    val privacyOptionsRequired: StateFlow<Boolean> = _privacyOptionsRequired.asStateFlow()

    private var isGathering = false
    private var hasStartedAds = false

    fun gatherConsent(activity: Activity) {
        // Before `isGathering`, and before any UMP call: an unconfigured build must not ask a user
        // for consent it has nothing to spend, and must not hand MobileAds a placeholder app id.
        if (!AdPlacement.isConfigured(activity)) return
        if (isGathering) return
        isGathering = true

        val info = UserMessagingPlatform.getConsentInformation(activity)
        // Consent from a previous session is valid right away; don't wait for the network.
        refreshState(activity, info)

        val parameters = ConsentRequestParameters.Builder()
            .setTagForUnderAgeOfConsent(false)
            .build()

        info.requestConsentInfoUpdate(
            activity,
            parameters,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                    formError?.let { Log.w(TAG, "Consent form failed: ${it.message}") }
                    finish(activity, info)
                }
            },
            { error ->
                // No update means no form this session, but any cached consent still stands —
                // which is what refreshState reads, so this is a log and not a dead end.
                Log.w(TAG, "Consent info update failed: ${error.message}")
                finish(activity, info)
            },
        )
    }

    /** Re-opens the form for a user who already answered, from Settings › About. */
    fun presentPrivacyOptions(activity: Activity) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { error ->
            error?.let { Log.w(TAG, "Privacy options form failed: ${it.message}") }
            refreshState(activity, UserMessagingPlatform.getConsentInformation(activity))
        }
    }

    private fun finish(context: Context, info: ConsentInformation) {
        isGathering = false
        refreshState(context, info)
    }

    private fun refreshState(context: Context, info: ConsentInformation) {
        _privacyOptionsRequired.value = info.privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

        if (info.canRequestAds() && !hasStartedAds) {
            hasStartedAds = true
            // canRequestAds is published by the callback rather than assigned here, because the SDK's
            // auto-init provider is removed (src/main/AndroidManifest.xml): nothing else starts
            // MobileAds, and an AdView that asks for an ad before initialisation has finished gets an
            // error rather than a fill. Consent is already settled at this point, so the one thing
            // banners are still waiting on is the SDK being ready.
            MobileAds.initialize(context.applicationContext) { status ->
                status.adapterStatusMap.forEach { (adapter, state) ->
                    if (state == null || state.initializationState != AdapterStatus.State.READY) {
                        Log.w(TAG, "MobileAds init: $adapter not ready (${state?.description})")
                    }
                }
                _canRequestAds.value = true
            }
        } else {
            _canRequestAds.value = info.canRequestAds()
        }
    }
}
