package com.koodipardik.nordprice.core

import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/** One market price: the start of its interval and the raw €/MWh Nord Pool published. */
@kotlinx.serialization.Serializable
data class PricePoint(val timestamp: Long, val price: Double) {
    val instant: Instant get() = Instant.ofEpochSecond(timestamp)
}

object PriceMath {

    /**
     * Collapse 15-minute points into one point per hour, averaging each group.
     *
     * Grouping is done in the *device* zone, as on iOS, so an hour bucket follows the wall
     * clock the user reads rather than UTC. Within a bucket every interval has the same
     * length, so a plain mean is the time-weighted average even on a 25-hour DST Sunday.
     */
    fun aggregateToHourly(
        data: List<PricePoint>,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): List<PricePoint> {
        if (data.isEmpty()) return emptyList()

        val buckets = mutableListOf<PricePoint>()
        var currentHourStart: Long? = null
        val currentValues = mutableListOf<Double>()

        for (point in data.sortedBy { it.timestamp }) {
            val hourStart = point.instant.atZone(zoneId)
                .truncatedTo(java.time.temporal.ChronoUnit.HOURS)
                .toEpochSecond()
            if (currentHourStart == null) currentHourStart = hourStart

            if (hourStart != currentHourStart) {
                if (!currentValues.isEmpty()) {
                    buckets += PricePoint(currentHourStart!!, average(currentValues))
                }
                currentHourStart = hourStart
                currentValues.clear()
            }
            currentValues += point.price
        }
        if (!currentValues.isEmpty() && currentHourStart != null) {
            buckets += PricePoint(currentHourStart!!, average(currentValues))
        }
        return buckets
    }

    private fun average(values: List<Double>): Double = values.sum() / values.size

    /** Hour length used by the sample-data generator. */
    val quarterHourSeconds: Long = TimeUnit.MINUTES.toSeconds(15)
}
