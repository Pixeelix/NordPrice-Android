package com.koodipardik.nordprice.ui.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.MobileAds
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
 * Nothing initialises MobileAds at app start, deliberately: a request before consent is the exact
 * thing GDPR enforcement is about, so the SDK only starts once `canRequestAds` says so, which is
 * also what iOS does in `refreshState` (`ConsentManager.swift:78`).
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
        _canRequestAds.value = info.canRequestAds()

        if (info.canRequestAds() && !hasStartedAds) {
            hasStartedAds = true
            MobileAds.initialize(context.applicationContext)
        }
    }
}
