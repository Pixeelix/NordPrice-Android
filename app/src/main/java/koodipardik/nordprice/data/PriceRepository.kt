package koodipardik.nordprice.data

import android.content.Context
import koodipardik.nordprice.core.Day
import koodipardik.nordprice.core.PricePoint
import koodipardik.nordprice.core.Region

/**
 * Orchestrates cache and API: cache first, then network, then back into the cache.
 *
 * [onCompleteDaySaved] is how the home-screen widget learns there is new data — on iOS the
 * equivalent call is `WidgetCenter.reloadAllTimelines()`. Cache *hits* deliberately do not
 * fire it.
 */
class PriceRepository(context: Context) {

    private val api = PriceApi()
    private val cache = PriceCache(context)

    var onCompleteDaySaved: (() -> Unit)? = null

    suspend fun loadFullDayData(day: Day, region: Region): List<PricePoint> {
        if (SampleData.enabled) return SampleData.fullDayData(day, region)

        cache.load(day, region)?.let { return it }

        val payload = api.fetchDay(day)
        val points = api.decode(payload, region)

        if (cache.save(day, region, points)) {
            onCompleteDaySaved?.invoke()
        }
        return points
    }

    /** Cached prices even when the day is incomplete — for when the network is down. */
    fun loadCached(day: Day, region: Region): List<PricePoint> =
        if (SampleData.enabled) SampleData.fullDayData(day, region) else cache.loadBestEffort(day, region).orEmpty()
}
