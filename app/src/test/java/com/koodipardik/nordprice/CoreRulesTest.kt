package com.koodipardik.nordprice

import com.koodipardik.nordprice.ui.chart.ChartSceneBuilder
import com.koodipardik.nordprice.ui.chart.ChartStyles
import com.koodipardik.nordprice.ui.chart.ChartValue

import com.koodipardik.nordprice.core.ChartResolution
import com.koodipardik.nordprice.core.Day
import com.koodipardik.nordprice.core.DayCoverage
import com.koodipardik.nordprice.core.MarketDay
import com.koodipardik.nordprice.core.PriceFormat
import com.koodipardik.nordprice.core.PriceLevel
import com.koodipardik.nordprice.core.PriceMath
import com.koodipardik.nordprice.core.PricePoint
import com.koodipardik.nordprice.core.PriceUnit
import com.koodipardik.nordprice.core.Region
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/** The pure rules the port must keep identical to iOS. */
class CoreRulesTest {

    private val utc = ZoneId.of("UTC")

    private fun day(startEpochSecond: Long, count: Int, stepSeconds: Long) =
        (0 until count).map { PricePoint(startEpochSecond + it * stepSeconds, 100.0 + it) }

    @Test
    fun `a full quarter-hour day covers itself`() {
        // 2026-10-17T00:00:00Z, 96 × 15 min = exactly 24 h.
        assertTrue(DayCoverage.covers(1792195200L, 86_400L, day(1792195200L, 96, 900)))
    }

    @Test
    fun `a partial day does not cover itself`() {
        // This is the exact shape Elering returns while the day is still running.
        assertFalse(DayCoverage.covers(1792195200L, 86_400L, day(1792195200L, 88, 900)))
    }

    @Test
    fun `a payload that does not start at midnight is not a whole day`() {
        assertFalse(DayCoverage.covers(1792195200L, 86_400L, day(1792195500L, 95, 900)))
    }

    @Test
    fun `hourly data covers a 25 hour dst day`() {
        val tallinn = ZoneId.of("Europe/Tallinn")
        // Local Sunday 2026-10-25 in Tallinn is 25 hours long.
        val start = MarketDay.dayStartEpochSecond(Day.TODAY, tallinn, Instant.parse("2026-10-25T12:00:00Z"))
        assertEquals(25 * 3600L, MarketDay.daySeconds(Day.TODAY, tallinn, Instant.parse("2026-10-25T12:00:00Z")))
        assertTrue(DayCoverage.covers(start, 25 * 3600L, day(start, 25, 3600)))
        assertFalse(DayCoverage.covers(start, 25 * 3600L, day(start, 24, 3600)))
    }

    @Test
    fun `day interval is expressed in the device zone as UTC strings`() {
        val tallinn = ZoneId.of("Europe/Tallinn")
        val now = Instant.parse("2026-10-17T12:00:00Z")
        val interval = MarketDay.utcInterval(Day.TODAY, tallinn, now)
        // Tallinn is UTC+3 in October, so the local day starts at 21:00Z the day before.
        assertEquals("2026-10-16T21:00:00.000Z", MarketDay.formatUtc(interval.start))
        assertEquals("2026-10-17T20:59:59.999Z", MarketDay.formatUtc(interval.endInclusive))
    }

    @Test
    fun `fifteen minute points aggregate to hourly averages`() {
        val start = 1792195200L
        val points = (0 until 8).map { PricePoint(start + it * 900L, it.toDouble()) }
        val hourly = PriceMath.aggregateToHourly(points, utc)

        assertEquals(2, hourly.size)
        assertEquals((0 + 1 + 2 + 3) / 4.0, hourly[0].price, 1e-9)
        assertEquals((4 + 5 + 6 + 7) / 4.0, hourly[1].price, 1e-9)
        assertEquals(start, hourly[0].timestamp)
        assertEquals(start + 3600, hourly[1].timestamp)
    }

    @Test
    fun `units divide the raw MWh figure and fix the decimals`() {
        assertEquals("0,1060", PriceFormat.format(106.0 / PriceUnit.EUR_PER_KWH.divider, PriceUnit.EUR_PER_KWH))
        assertEquals("106,0", PriceFormat.format(106.0 / PriceUnit.EUR_PER_MWH.divider, PriceUnit.EUR_PER_MWH))
        assertEquals("10,6", PriceFormat.format(106.0 / PriceUnit.CENT_PER_KWH.divider, PriceUnit.CENT_PER_KWH))
    }

    @Test
    fun `axis labels drop the decimals only for MWh`() {
        assertEquals("0,11", PriceFormat.axisLabel(0.106, PriceUnit.EUR_PER_KWH))
        assertEquals("106", PriceFormat.axisLabel(106.0, PriceUnit.EUR_PER_MWH))
    }

    @Test
    fun `price levels are set by the raw figure not the displayed one`() {
        assertEquals(PriceLevel.VERY_CHEAP, PriceLevel.of(49.9))
        assertEquals(PriceLevel.CHEAP, PriceLevel.of(50.0))
        assertEquals(PriceLevel.NORMAL, PriceLevel.of(100.0))
        assertEquals(PriceLevel.EXPENSIVE, PriceLevel.of(200.0))
    }

    @Test
    fun `labels use the region clock while hours resolution snaps to the hour`() {
        val point = Instant.ofEpochSecond(1792195200L + 30 * 60) // 00:30 UTC = 03:30 in Tallinn
        assertEquals(
            "03:30",
            MarketDay.labelsIn(Region.ESTONIA, ChartResolution.FIFTEEN_MINUTES)(point),
        )
        assertEquals(
            "03:00",
            MarketDay.labelsIn(Region.ESTONIA, ChartResolution.ONE_HOUR)(point),
        )
        // Helsinki is on the same clock as Tallinn, so the region zone is not the device zone.
        assertEquals(
            "03:30",
            MarketDay.labelsIn(Region.FINLAND, ChartResolution.FIFTEEN_MINUTES)(point),
        )
    }

    @Test
    fun `tax rates match each market`() {
        assertEquals(1.24, Region.ESTONIA.taxRate, 1e-9)
        assertEquals(1.21, Region.LATVIA.taxRate, 1e-9)
        assertEquals(1.21, Region.LITHUANIA.taxRate, 1e-9)
        assertEquals(1.255, Region.FINLAND.taxRate, 1e-9)
        assertEquals("25,5%", Region.FINLAND.taxPercentage)
    }
}

/**
 * The history chart inherits most of its style from the day chart, and once did so without
 * overriding the tick positions — which left it labelling bars with hour-of-day indices that have
 * no label, i.e. drawing a single "1" on the axis. These two assertions are that bug.
 */
class ChartStyleTest {

    private val bars = (0 until 17).map {
        ChartValue(it, 50.0 + it, "d$it", com.koodipardik.nordprice.core.PriceLevel.CHEAP)
    }

    @Test
    fun `history uses the caller's tick positions`() {
        val style = ChartStyles.history(
            colors = com.koodipardik.nordprice.ui.theme.LightColors,
            chartType = com.koodipardik.nordprice.core.ChartType.BAR,
            colorful = true,
            barCount = bars.size,
            yLabel = { "%.0f".format(it) },
            xLabel = { index -> listOf(0, 7, 14).let { if (index in it) "day$index" else "" } },
            xTickIndices = { count -> (0 until count step 7).toList() },
        )
        val scene = ChartSceneBuilder.build(bars, style, selected = null, widthPx = 800f, heightPx = 300f, density = 2f)

        assertEquals(listOf(0, 7, 14), scene.xTicks.map { it.value.toInt() })
        assertEquals(listOf("day0", "day7", "day14"), scene.xTicks.map { it.label })
        assertTrue(scene.xTicks.map { it.position }.distinct().size == 3)
    }

    @Test
    fun `the day chart labels four hours of the day`() {
        val day = (0 until 24).map { ChartValue(it, 50.0, "%02d:00".format(it)) }
        val style = ChartStyles.day(
            colors = com.koodipardik.nordprice.ui.theme.LightColors,
            chartType = com.koodipardik.nordprice.core.ChartType.BAR,
            colorful = true,
            resolution = com.koodipardik.nordprice.core.ChartResolution.ONE_HOUR,
        ) { "%.2f".format(it) }
        val scene = ChartSceneBuilder.build(day, style, selected = null, widthPx = 800f, heightPx = 300f, density = 2f)

        assertEquals(listOf("00", "06", "12", "18"), scene.xTicks.map { it.label })
    }
}
