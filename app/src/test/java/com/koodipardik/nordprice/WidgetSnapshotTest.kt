package com.koodipardik.nordprice

import com.koodipardik.nordprice.core.ChartResolution
import com.koodipardik.nordprice.core.ChartType
import com.koodipardik.nordprice.core.PriceFormat
import com.koodipardik.nordprice.core.PriceLevel
import com.koodipardik.nordprice.core.PricePoint
import com.koodipardik.nordprice.core.PriceUnit
import com.koodipardik.nordprice.core.Region
import com.koodipardik.nordprice.data.PriceApi
import com.koodipardik.nordprice.settings.Settings
import com.koodipardik.nordprice.widget.WidgetSnapshotBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

/**
 * What the home-screen widget decides to show. These are the rules the iOS provider runs on, and
 * they are the reason the widget can be wrong while the app is right — so they get their own tests.
 */
class WidgetSnapshotTest {

    private val payload: String by lazy {
        checkNotNull(javaClass.getResourceAsStream("/elering_2026-10-17.json")).bufferedReader().readText()
    }

    private val api = PriceApi()

    private val dayStart = Instant.parse("2026-10-17T00:00:00Z").epochSecond

    private fun settings(
        unit: PriceUnit = PriceUnit.EUR_PER_KWH,
        includeTax: Boolean = false,
        resolution: ChartResolution = ChartResolution.FIFTEEN_MINUTES,
    ) = Settings(
        region = Region.ESTONIA,
        unit = unit,
        includeTax = includeTax,
        chartResolution = resolution,
        chartType = ChartType.BAR,
    )

    /**
     * 10:07 UTC. The interval in progress started at 10:00 UTC, which the widget prints in the
     * *region's* clock — Tallinn is on summer time (+3) on this date, so the label reads 13:00.
     * Labels follow the market, day boundaries follow the device; both are ported from iOS.
     */
    private val now = Instant.parse("2026-10-17T10:07:00Z")
    private val nowLabel = "13:00"

    @Test
    fun `picks the interval in progress and formats it in the region clock`() {
        val points = api.decode(payload, Region.ESTONIA)
        val snapshot = WidgetSnapshotBuilder.build(
            settings(), points, "€/kWh", now, ZoneOffset.UTC,
        )

        assertEquals(nowLabel, snapshot.timeText)
        // Raw €/MWh ÷1000 → €/kWh, no VAT: the same figure the app shows for this interval.
        val raw = points.first { it.timestamp == dayStart + 10 * 3600 }.price
        assertEquals(PriceFormat.format(raw / 1000.0, PriceUnit.EUR_PER_KWH), snapshot.priceText)
    }

    @Test
    fun `hourly mode averages the quarter-hours and aligns to the hour`() {
        val points = (0 until 96).map { PricePoint(dayStart + it * 900L, it.toDouble()) }
        val snapshot = WidgetSnapshotBuilder.build(
            settings(resolution = ChartResolution.ONE_HOUR), points, "€/MWh", now, ZoneOffset.UTC,
        )

        assertEquals(24, snapshot.values.size)
        assertEquals(nowLabel, snapshot.timeText)
        // The 10:00 bucket is the mean of the quarter-hours 40, 41, 42 and 43.
        assertEquals("0,0415", snapshot.priceText)
        assertEquals(10, snapshot.values.indexOfFirst { it.isCurrent })
    }

    @Test
    fun `vat is applied to the price but not to the colour`() {
        val points = listOf(
            PricePoint(dayStart, 60.0),      // cheap band by raw figure…
            PricePoint(dayStart + 900, 60.0),
        )
        val noTax = WidgetSnapshotBuilder.build(settings(), points, "€/kWh", now, ZoneOffset.UTC)
        val withTax = WidgetSnapshotBuilder.build(settings(includeTax = true), points, "€/kWh", now, ZoneOffset.UTC)

        assertEquals("0,0600", noTax.priceText)
        assertEquals("0,0744", withTax.priceText)
        // 60 €/MWh is "cheap" however it is displayed: VAT must not recolour a bar.
        assertEquals(PriceLevel.CHEAP, noTax.values.first().level)
        assertEquals(PriceLevel.CHEAP, withTax.values.first().level)
    }

    @Test
    fun `the unit decides the decimals, not the data`() {
        val points = listOf(PricePoint(dayStart, 106.0), PricePoint(dayStart + 900, 106.0))

        assertEquals("0,1060", WidgetSnapshotBuilder.build(settings(PriceUnit.EUR_PER_KWH), points, "", now, ZoneOffset.UTC).priceText)
        assertEquals("106,0", WidgetSnapshotBuilder.build(settings(PriceUnit.EUR_PER_MWH), points, "", now, ZoneOffset.UTC).priceText)
        assertEquals("10,6", WidgetSnapshotBuilder.build(settings(PriceUnit.CENT_PER_KWH), points, "", now, ZoneOffset.UTC).priceText)
    }

    @Test
    fun `no data still produces a renderable widget`() {
        val snapshot = WidgetSnapshotBuilder.build(settings(), emptyList(), "€/kWh", now, ZoneOffset.UTC)

        assertTrue(snapshot.isEmpty)
        assertEquals("---", snapshot.priceText)
        assertEquals("--:--", snapshot.timeText)
        assertTrue(snapshot.values.isEmpty())
    }

    @Test
    fun `a partial day is drawn with the hours it has`() {
        // 44 quarter-hours reaches 11:00; at 10:07 the interval in progress is 10:00.
        val points = (0 until 44).map { PricePoint(dayStart + it * 900L, 50.0) }
        val snapshot = WidgetSnapshotBuilder.build(settings(), points, "€/kWh", now, ZoneOffset.UTC)

        assertEquals(44, snapshot.values.size)
        assertEquals(40, snapshot.values.indexOfFirst { it.isCurrent })
    }
}
