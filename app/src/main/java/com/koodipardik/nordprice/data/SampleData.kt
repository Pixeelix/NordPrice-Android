package com.koodipardik.nordprice.data

import com.koodipardik.nordprice.core.Day
import com.koodipardik.nordprice.core.MarketDay
import com.koodipardik.nordprice.core.PriceMath
import com.koodipardik.nordprice.core.PricePoint
import com.koodipardik.nordprice.core.Region
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Deterministic made-up prices, ported from the iOS `ScreenshotPriceData` /
 * `SampleHistoryData`. On iOS this exists for App Store screenshot automation; here it doubles
 * as an offline development mode. Only debug builds can switch it on (Settings › Debug).
 *
 * A global rather than a constructor flag, so every data path — app and, later, widget — reads
 * the same switch, exactly like the iOS `AppRuntimeConfiguration.usesSamplePriceData`.
 */
object SampleData {

    /** Mirrors the iOS "Näidisandmed" debug toggle. */
    @Volatile
    var enabled: Boolean = false

    private val todayHourly = doubleArrayOf(
        54.0, 48.0, 43.0, 40.0, 46.0, 68.0, 112.0, 154.0,
        139.0, 104.0, 82.0, 71.0, 64.0, 58.0, 55.0, 63.0,
        91.0, 148.0, 183.0, 144.0, 101.0, 78.0, 65.0, 57.0,
    )

    private val tomorrowHourly = doubleArrayOf(
        38.0, 32.0, 27.0, 24.0, 21.0, 33.0, 58.0, 84.0,
        69.0, 36.0, 13.0, -9.0, -18.0, -24.0, -14.0, 6.0,
        45.0, 98.0, 129.0, 94.0, 59.0, 43.0, 35.0, 29.0,
    )

    private val quarterAdjustments = doubleArrayOf(-1.8, 0.7, 2.2, -0.6)

    fun fullDayData(day: Day, region: Region): List<PricePoint> {
        val dayStart = MarketDay.localDayStart(day)
        val nextDayStart = dayStart.plusDays(1)
        val intervalSeconds = PriceMath.quarterHourSeconds
        val intervalCount = max(1, java.time.Duration.between(dayStart, nextDayStart).seconds.toInt() / intervalSeconds.toInt())
        val base = if (day == Day.TODAY) todayHourly else tomorrowHourly
        val offset = when (region) {
            Region.ESTONIA -> 0.0
            Region.LATVIA -> 3.0
            Region.LITHUANIA -> 5.0
            Region.FINLAND -> -4.0
        }
        return (0 until intervalCount).map { index ->
            val timestamp = dayStart.plusSeconds(index.toLong() * intervalSeconds).toEpochSecond()
            val price = base[min(index / 4, base.size - 1)] +
                quarterAdjustments[index % quarterAdjustments.size] +
                offset
            PricePoint(timestamp, price)
        }
    }

    fun historyMonth(monthStart: LocalDate, region: Region): List<DailyAveragePrice> {
        val today = HistoryCalendar.today()
        if (monthStart.isAfter(today)) return emptyList()

        val daysInMonth = YearMonthLength(monthStart)
        val regionOffset = when (region) {
            Region.FINLAND -> -12.0
            Region.ESTONIA -> 0.0
            else -> 4.0
        }
        return (0 until daysInMonth).mapNotNull { offset ->
            val day = monthStart.plusDays(offset.toLong())
            if (day.isAfter(today)) return@mapNotNull null
            val dayOfYear = day.dayOfYear.toDouble()
            // Winter is expensive, summer cheap, weekends a little cheaper.
            val seasonal = 95 + 45 * cos((dayOfYear - 15) / 365 * 2 * Math.PI)
            val weekday = day.dayOfWeek.value
            val weekly = if (weekday == 6 || weekday == 7) -18.0 else 6.0
            val noise = 22 * sin(dayOfYear * 1.7) + 12 * sin(dayOfYear * 0.53)
            DailyAveragePrice(
                dayStart = HistoryCalendar.epochDayStart(day),
                price = max(4.0, seasonal + weekly + noise + regionOffset),
            )
        }
    }

    private fun YearMonthLength(date: LocalDate): Int =
        (ChronoUnit.DAYS.between(date.withDayOfMonth(1), date.withDayOfMonth(1).plusMonths(1))).toInt()
}
