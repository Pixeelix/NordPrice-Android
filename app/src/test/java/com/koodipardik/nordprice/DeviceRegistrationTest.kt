package com.koodipardik.nordprice

import com.koodipardik.nordprice.core.ChartResolution
import com.koodipardik.nordprice.core.Language
import com.koodipardik.nordprice.core.PriceUnit
import com.koodipardik.nordprice.core.Region
import com.koodipardik.nordprice.notifications.DeviceRegistration
import com.koodipardik.nordprice.settings.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The registration payload is a contract with `functions/src/index.ts` in the iOS repo, which
 * validates every field by name and rejects a value it does not recognise. Nothing at compile time
 * catches sending `ESTONIA` where the backend wants `EE` — the call fails, the failure is logged,
 * and the phone quietly never alerts. So the shape is pinned here instead.
 *
 * The allowed lists below are transcribed from that file deliberately: when the backend widens,
 * this test is where the Android side finds out.
 */
class DeviceRegistrationTest {

    private fun registration(
        settings: Settings = Settings(),
        appVersion: String? = "4.1",
        buildNumber: String? = "1",
    ) = DeviceRegistration(
        installationId = "installation-1",
        bundleId = "com.koodipardik.nordprice",
        fcmToken = "token-1",
        settings = settings,
        appVersion = appVersion,
        buildNumber = buildNumber,
    )

    @Test
    fun `the payload carries exactly the fields the function requires`() {
        val required = setOf(
            "installationId",
            "platform",
            "bundleId",
            "fcmToken",
            "region",
            "language",
            "unit",
            "includeTax",
            "chartResolution",
            "notifyMaxEnabled",
            "notifyMaxRawMWh",
            "notifyMinEnabled",
            "notifyMinRawMWh",
            "enabled",
            "appVersion",
            "buildNumber",
        )
        assertEquals(required, registration().toPayload().keys)
    }

    @Test
    fun `this device reports itself as android`() {
        assertEquals("android", registration().toPayload()["platform"])
    }

    @Test
    fun `enums travel as the wire strings the backend validates`() {
        val payload = registration(
            Settings(
                region = Region.FINLAND,
                language = Language.RUSSIAN,
                unit = PriceUnit.CENT_PER_KWH,
                chartResolution = ChartResolution.FIFTEEN_MINUTES,
            ),
        ).toPayload()

        // regions = ["EE", "LV", "LT", "FI"] and chartResolutions = ["15min", "1h"] in index.ts.
        assertEquals("FI", payload["region"])
        assertEquals("15min", payload["chartResolution"])
        // languageKey() and minimumFractionDigits() string-match these two as well.
        assertEquals("ru", payload["language"])
        assertEquals("cent/kWh", payload["unit"])
    }

    @Test
    fun `enabled is derived because the function rejects a mismatch`() {
        assertFalse(registration(Settings()).toPayload()["enabled"] as Boolean)
        assertTrue(registration(Settings(notifyMaxEnabled = true)).toPayload()["enabled"] as Boolean)
        assertTrue(registration(Settings(notifyMinEnabled = true)).toPayload()["enabled"] as Boolean)
        assertTrue(
            registration(
                Settings(notifyMaxEnabled = true, notifyMinEnabled = true),
            ).toPayload()["enabled"] as Boolean,
        )
    }

    @Test
    fun `thresholds travel raw eur per mwh whatever the display unit is`() {
        val settings = Settings(
            region = Region.ESTONIA,
            unit = PriceUnit.CENT_PER_KWH,
            includeTax = true,
            notifyMaxRawMWh = 200.0,
        )
        val payload = registration(settings).toPayload()

        // The server does its own formatting from unit/includeTax; converting here would round twice.
        assertEquals(200.0, payload["notifyMaxRawMWh"])
        assertEquals(24.8, settings.notifyMaxDisplay, 1e-9) // 200 * 1.24 / 10, shown in cent/kWh
    }

    @Test
    fun `missing version info is omitted rather than sent as null`() {
        val payload = registration(appVersion = null, buildNumber = null).toPayload()
        assertFalse(payload.containsKey("appVersion"))
        assertFalse(payload.containsKey("buildNumber"))
    }
}
