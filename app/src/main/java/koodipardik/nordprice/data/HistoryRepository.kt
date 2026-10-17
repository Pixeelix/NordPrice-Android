package koodipardik.nordprice.data

import android.content.Context
import koodipardik.nordprice.core.PricePoint
import koodipardik.nordprice.core.Region
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** Average Nord Pool price of one calendar day, raw €/MWh before unit conversion and VAT. */
@Serializable
data class DailyAveragePrice(val dayStart: Long, val price: Double)

/**
 * All four markets sit on Eastern European Time, so one calendar serves every region. Weeks
 * start on Monday, as they do in Estonia, Latvia, Lithuania and Finland.
 */
object HistoryCalendar {
    val zone: ZoneId = ZoneId.of("Europe/Tallinn")

    /** Elering's price data starts in 2012 (Finland) and mid-2013 (Baltics). */
    val earliestDate: LocalDate = LocalDate.of(2013, 1, 1)

    fun monthKey(date: LocalDate): String = String.format("%04d-%02d", date.year, date.monthValue)

    fun epochDayStart(date: LocalDate): Long = date.atStartOfDay(zone).toEpochSecond()

    fun localDate(epochSecond: Long): LocalDate = Instant.ofEpochSecond(epochSecond).atZone(zone).toLocalDate()

    fun today(): LocalDate = LocalDate.now(zone)
}

/**
 * Daily average prices for the history tab.
 *
 * Data is fetched one calendar month at a time: a month is ~70 KB compressed, while a whole
 * year in one request is ~6 MB and slow. Months that have ended never change, so their daily
 * averages are kept on disk; the month in progress is refetched once a day. Ported from the
 * iOS `HistoryRepository`, including that shape.
 */
class HistoryRepository(context: Context) {

    @Serializable
    private data class DiskCache(val months: Map<String, Map<String, List<DailyAveragePrice>>> = emptyMap())

    private val api = PriceApi()
    private val file = File(context.cacheDir, "price_history_v1.json")
    private val mutex = Mutex()

    /** monthKey -> region code -> daily averages, for months that have ended. */
    private val completedMonths = mutableMapOf<String, Map<String, List<DailyAveragePrice>>>()

    /** The month still in progress: key, the day it was fetched, and its data. */
    private var currentMonth: Triple<String, LocalDate, Map<String, List<DailyAveragePrice>>>? = null
    private var didLoadFromDisk = false

    suspend fun dailyAverages(region: Region, months: List<LocalDate>): List<DailyAveragePrice> {
        if (SampleData.enabled) return months.flatMap { SampleData.historyMonth(it, region) }

        val results = mutableListOf<DailyAveragePrice>()
        for (month in months) {
            results += monthData(month)[region.code.lowercase()].orEmpty()
        }
        return results.sortedBy { it.dayStart }
    }

    private suspend fun monthData(monthStart: LocalDate): Map<String, List<DailyAveragePrice>> =
        mutex.withLock {
            loadFromDiskIfNeeded()

            val key = HistoryCalendar.monthKey(monthStart)
            completedMonths[key]?.let { return@withLock it }

            val today = HistoryCalendar.today()
            currentMonth?.takeIf { it.first == key && it.second == today }?.let { return@withLock it.third }

            val nextMonthStart = YearMonth.from(monthStart).plusMonths(1).atDay(1)
            val payload = api.fetchRange(
                monthStart.atStartOfDay(HistoryCalendar.zone).toInstant(),
                nextMonthStart.atStartOfDay(HistoryCalendar.zone).toInstant().minusMillis(1),
            )
            val data: Map<String, List<DailyAveragePrice>> = api.decodeAll(payload)
                .entries
                .associate { (region, points) -> region.code.lowercase() to dailyAverages(points) }

            if (!nextMonthStart.isAfter(today)) {
                completedMonths[key] = data
                saveToDisk()
            } else {
                currentMonth = Triple(key, today, data)
            }
            data
        }

    /**
     * Mean of every interval in a day. Older data is hourly and newer data is 15-minute, but
     * within one day all intervals have the same length, so a plain mean is the time-weighted
     * average either way.
     */
    private fun dailyAverages(points: List<PricePoint>): List<DailyAveragePrice> {
        val sums = linkedMapOf<Long, Pair<Double, Int>>()
        for (point in points) {
            val day = HistoryCalendar.epochDayStart(HistoryCalendar.localDate(point.timestamp))
            val (sum, count) = sums[day] ?: (0.0 to 0)
            sums[day] = (sum + point.price) to (count + 1)
        }
        return sums.map { (day, acc) -> DailyAveragePrice(day, acc.first / acc.second) }
            .sortedBy { it.dayStart }
    }

    private fun loadFromDiskIfNeeded() {
        if (didLoadFromDisk) return
        didLoadFromDisk = true
        runCatching {
            NordPriceJson.decodeFromString(DiskCache.serializer(), file.readText()).months
        }.getOrNull()?.let { loaded ->
            loaded.forEach { (key, value) -> completedMonths.putIfAbsent(key, value) }
        }
    }

    private fun saveToDisk() {
        runCatching {
            file.writeText(NordPriceJson.encodeToString(DiskCache.serializer(), DiskCache(completedMonths)))
        }
    }
}
