package com.koodipardik.nordprice.notifications

import com.koodipardik.nordprice.core.ChartResolution
import com.koodipardik.nordprice.core.Language
import com.koodipardik.nordprice.core.PriceUnit
import com.koodipardik.nordprice.core.Region
import com.koodipardik.nordprice.settings.Settings

/**
 * What this device tells the `syncNotificationDevice` Callable Function about itself — the Android
 * counterpart of iOS `RemoteNotificationSettingsSnapshot`.
 *
 * Field names and their values are a wire contract with `functions/src/index.ts` in the iOS repo,
 * which validates them one by one: `platform` must be `ios` or `android`, `region` one of
 * EE/LV/LT/FI, `chartResolution` one of `15min`/`1h`, and `unit` is read back as a display string
 * by `minimumFractionDigits`. That is why every enum here serialises through the same property the
 * iOS app sends — [Region.code], [Language.code], [PriceUnit.rawKey], [ChartResolution.key] — and
 * never through `enum.name`, which would be `ESTONIA` where the backend wants `EE`. A mismatch does
 * not crash anything: the call returns `invalid-argument`, the failure is logged, and the phone
 * simply never gets an alert. Hence the JVM test over [toPayload].
 *
 * [enabled] is derived rather than passed in, because the function rejects a registration whose
 * `enabled` is not exactly `notifyMaxEnabled || notifyMinEnabled`.
 */
data class DeviceRegistration(
    val installationId: String,
    val bundleId: String,
    val fcmToken: String,
    val settings: Settings,
    val appVersion: String? = null,
    val buildNumber: String? = null,
    val platform: String = PLATFORM_ANDROID,
) {
    val enabled: Boolean get() = settings.notifyMaxEnabled || settings.notifyMinEnabled

    /** Nulls are left out rather than sent as null — iOS omits the key, and so must we. */
    fun toPayload(): Map<String, Any> = buildMap<String, Any> {
        put("installationId", installationId)
        put("platform", platform)
        put("bundleId", bundleId)
        put("fcmToken", fcmToken)
        put("region", settings.region.code)
        put("language", settings.language.code)
        put("unit", settings.unit.rawKey)
        put("includeTax", settings.includeTax)
        put("chartResolution", settings.chartResolution.key)
        put("notifyMaxEnabled", settings.notifyMaxEnabled)
        put("notifyMaxRawMWh", settings.notifyMaxRawMWh)
        put("notifyMinEnabled", settings.notifyMinEnabled)
        put("notifyMinRawMWh", settings.notifyMinRawMWh)
        put("enabled", enabled)
        appVersion?.let { put("appVersion", it) }
        buildNumber?.let { put("buildNumber", it) }
    }

    companion object {
        /** The value the widened backend validator accepts for this platform. */
        const val PLATFORM_ANDROID = "android"
    }
}
