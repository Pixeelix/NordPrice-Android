package com.koodipardik.nordprice

import com.koodipardik.nordprice.notifications.PriceAlertNotifier
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * `PriceAlertNotifier` posts with id `type.hashCode()` so one alert per direction replaces the
 * other, and the debug rows in Settings reuse the ids of real pushes. Both halves only line up
 * while the type strings match what the backend puts in `data.type`
 * (`functions/src/index.ts:378`: `` `price_threshold_${direction}` ``). A typo here compiles,
 * passes every build, and quietly lets a debug alert and a real push stack on top of each other
 * instead of replacing — so the spelling is pinned, as DeviceRegistrationTest pins the payload.
 */
class PriceAlertNotifierTest {

    @Test
    fun `the direction types are spelled exactly as the backend sends them`() {
        assertEquals("price_threshold_max", PriceAlertNotifier.TYPE_MAX)
        assertEquals("price_threshold_min", PriceAlertNotifier.TYPE_MIN)
    }
}
