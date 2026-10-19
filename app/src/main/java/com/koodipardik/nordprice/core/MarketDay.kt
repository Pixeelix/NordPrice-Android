package com.koodipardik.nordprice.core

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Day geometry for API requests and cache keys.
 *
 * Days are bounded in the *device* time zone (the iOS app uses `Calendar.current`), while
 * price labels are rendered in the *selected region's* time zone — see [MarketDay.labelsIn].
 * Both choices are ported deliberately so the two platforms page through the same data.
 *
 * All formatting is in UTC with milliseconds, the shape Elering's API documents:
 * `2026-10-17T00:00:00.000Z`.
 */
object MarketDay {

    private val utcFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT).withZone(ZoneOffset.UTC)

    /** Start of the local day for [day] on the device clock. */
    fun localDayStart(day: Day, zone: ZoneId = ZoneId.systemDefault(), now: Instant = Instant.now()): ZonedDateTime =
        now.atZone(zone)
            .toLocalDate()
            .plusDays(if (day == Day.TOMORROW) 1 else 0)
            .atStartOfDay(zone)

    /** Seconds in the local day: 23 h, 24 h or 25 h across a DST change. */
    fun daySeconds(day: Day, zone: ZoneId = ZoneId.systemDefault(), now: Instant = Instant.now()): Long =
        Duration.between(localDayStart(day, zone, now), localDayStart(day, zone, now).plusDays(1)).seconds

    fun dayStartEpochSecond(day: Day, zone: ZoneId = ZoneId.systemDefault(), now: Instant = Instant.now()): Long =
        localDayStart(day, zone, now).toEpochSecond()

    /** UTC start/end strings for one market day, `-0.001s` on the end as iOS does. */
    fun utcInterval(day: Day, zone: ZoneId = ZoneId.systemDefault(), now: Instant = Instant.now()): ClosedRange<Instant> {
        val start = localDayStart(day, zone, now).toInstant()
        val end = localDayStart(day, zone, now).plusDays(1).toInstant().minusMillis(1)
        return start..end
    }

    fun formatUtc(instant: Instant): String = utcFormatter.format(instant)

    fun parseUtc(text: String): Instant = Instant.from(utcFormatter.parse(text))

    /** Today's UTC day start, the piece every cache key begins with. */
    fun todayStartUtc(now: Instant = Instant.now()): String =
        formatUtc(localDayStart(Day.TODAY, now = now).toInstant())

    /**
     * A label formatter for the chart axis and the current-price time.
     * Hours resolution renders "14:00", quarter-hour renders the real minute ("14:15").
     */
    fun labelsIn(region: Region, resolution: ChartResolution): (Instant) -> String {
        val pattern = if (resolution == ChartResolution.ONE_HOUR) "HH:00" else "HH:mm"
        val formatter = DateTimeFormatter.ofPattern(pattern, Locale.ROOT).withZone(region.zoneId)
        return { instant -> formatter.format(instant) }
    }
}
