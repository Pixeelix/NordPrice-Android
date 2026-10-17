package koodipardik.nordprice.core

/**
 * Whether a set of price points covers a whole market day.
 *
 * Extracted from the cache so the rule can be tested without an Android context, and so the app
 * and the widget share one implementation. Ported from iOS `SharedPriceCache.covers(day:items:)`,
 * including the reason it exists: an earlier version compared the item count to the number of
 * *hours* in the day, so once Elering moved to 15-minute resolution nothing ever looked complete
 * and the cache could be written but never read back. The interval is therefore derived from the
 * data rather than assumed.
 */
object DayCoverage {

    /**
     * @param dayStartEpochSecond first second of the local day the data must cover.
     * @param daySeconds length of that day — 23 h, 24 h or 25 h across a DST change.
     */
    fun covers(dayStartEpochSecond: Long, daySeconds: Long, items: List<PricePoint>): Boolean {
        if (items.size < 2) return false
        val sorted = items.sortedBy { it.timestamp }

        // The payload must start exactly at the local day start.
        if (kotlin.math.abs(sorted.first().timestamp - dayStartEpochSecond) >= 1) return false

        val gaps = sorted.zipWithNext { a, b -> b.timestamp - a.timestamp }.sorted()
        val interval = gaps[gaps.size / 2]
        if (interval <= 0) return false

        return sorted.size.toDouble() * interval >= daySeconds.toDouble()
    }
}
