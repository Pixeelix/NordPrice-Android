package koodipardik.nordprice.data

import android.content.Context
import koodipardik.nordprice.core.Day
import koodipardik.nordprice.core.DayCoverage
import koodipardik.nordprice.core.MarketDay
import koodipardik.nordprice.core.PricePoint
import koodipardik.nordprice.core.Region
import kotlinx.serialization.Serializable
import java.io.File

/**
 * On-disk day cache.
 *
 * Storage format and the day-completeness rule are ported from the iOS `SharedPriceCache`,
 * which exists because the app and the widget once kept two copies of this logic and drifted:
 * both validated a day by comparing the item count to the number of *hours* in the day, so
 * after Elering moved to 15-minute resolution nothing ever counted as complete — the cache
 * could be written but never read back.
 *
 * iOS keeps blobs in App Group `UserDefaults` because a widget extension cannot read the app's
 * files; on Android the Glance widget runs in this same process, so plain files are enough.
 */
class PriceCache(context: Context) {

    @Serializable
    private data class CachedDay(
        val startUtc: String,
        val isComplete: Boolean,
        val points: List<PricePoint>,
        val fetchedAt: Long,
    )

    private val dir = File(context.filesDir, "price_cache").apply { mkdirs() }

    fun load(day: Day, region: Region): List<PricePoint>? {
        val cached = stored(day, region) ?: return null
        if (!cached.isComplete) return null
        return cached.points.takeIf { covers(day, it) }
    }

    /**
     * Whatever is cached, ignoring the completeness flag. Last resort when a live fetch fails:
     * stale-but-real prices beat an empty screen.
     */
    fun loadBestEffort(day: Day, region: Region): List<PricePoint>? =
        stored(day, region)?.points?.takeIf { it.isNotEmpty() }

    /**
     * @return true when a payload covering the whole day was stored, i.e. when there is
     * genuinely new complete data worth telling the widget about.
     */
    fun save(day: Day, region: Region, points: List<PricePoint>): Boolean {
        val isComplete = covers(day, points)
        val payload = CachedDay(
            startUtc = MarketDay.formatUtc(MarketDay.utcInterval(day).start),
            isComplete = isComplete,
            points = points,
            fetchedAt = System.currentTimeMillis(),
        )
        runCatching { file(day, region).writeText(NordPriceJson.encodeToString(CachedDay.serializer(), payload)) }
        pruneStaleKeys()
        return isComplete
    }

    private fun stored(day: Day, region: Region): CachedDay? {
        val text = runCatching { file(day, region).readText() }.getOrNull() ?: return null
        val cached = runCatching { NordPriceJson.decodeFromString(CachedDay.serializer(), text) }.getOrNull() ?: return null
        // The key already encodes the day start, but verify the blob agrees so a file written
        // under a different device time zone can never be trusted.
        if (cached.startUtc != MarketDay.formatUtc(MarketDay.utcInterval(day).start)) return null
        return cached
    }

    private fun file(day: Day, region: Region): File = File(dir, keyFor(day, region).sanitized())

    private fun keyFor(day: Day, region: Region): String {
        val start = MarketDay.formatUtc(MarketDay.utcInterval(day).start)
        return "prices_v1_${region.cacheKey}_$start"
    }

    /** Rule lives in [DayCoverage] so the widget can share it and tests can run it off-device. */
    private fun covers(day: Day, items: List<PricePoint>): Boolean =
        DayCoverage.covers(
            dayStartEpochSecond = MarketDay.dayStartEpochSecond(day),
            daySeconds = MarketDay.daySeconds(day),
            items = items,
        )

    /**
     * Drops every cached day that is not today or tomorrow. Each file holds a whole day of
     * 15-minute prices, so without this they accumulate one per (region × day) forever.
     */
    private fun pruneStaleKeys() {
        val keep = buildSet {
            for (region in Region.entries) {
                add(Day.TODAY to region)
                add(Day.TOMORROW to region)
            }
        }.mapTo(mutableSetOf()) { (day, region) -> keyFor(day, region).sanitized() }

        dir.listFiles()?.forEach { f ->
            if (f.name.startsWith("prices_v1_") && f.name !in keep) f.delete()
        }
    }

    /** Cache keys contain ':' from the ISO-8601 timestamp; filesystems are happier without. */
    private fun String.sanitized(): String = replace(':', '-').replace('/', '_')
}
