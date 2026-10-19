package com.koodipardik.nordprice.ui.price

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.koodipardik.nordprice.AppContainer
import com.koodipardik.nordprice.core.ChartResolution
import com.koodipardik.nordprice.core.Day
import com.koodipardik.nordprice.core.MarketDay
import com.koodipardik.nordprice.core.PriceLevel
import com.koodipardik.nordprice.core.PriceMath
import com.koodipardik.nordprice.core.PriceFormat
import com.koodipardik.nordprice.core.PricePoint
import com.koodipardik.nordprice.core.Region
import com.koodipardik.nordprice.settings.Settings
import com.koodipardik.nordprice.ui.chart.ChartValue
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

data class ChartUiState(
    val loading: Boolean = true,
    val values: List<ChartValue> = emptyList(),
    val min: String = "---",
    val avg: String = "---",
    val max: String = "---",
    /** Tomorrow's prices are published around 14:00–15:00; until then there is nothing to draw. */
    val missingData: Boolean = false,
    val errorMessage: String? = null,
)

/**
 * One day of prices for the chart, the min/avg/max cards and the range card.
 *
 * The iOS `ChartViewModel` behaviour is preserved item for item, including when it decides to
 * hit the network again: a day that loaded today is not refetched, tomorrow retries whenever it
 * is still empty and otherwise once an hour, and a region or resolution change forces a load.
 */
class ChartViewModel(
    private val container: AppContainer,
    val day: Day,
) : ViewModel() {

    private val _state = MutableStateFlow(ChartUiState())
    val state: StateFlow<ChartUiState> = _state.asStateFlow()

    private val tag = "NordPriceChart" + day.name.lowercase()

    private var rawPoints: List<PricePoint> = emptyList()
    private var dataLastLoaded: Instant? = null
    private var forceReload = true
    private var loadJob: Job? = null
    private var observing = false

    init {
        if (!observing) {
            observing = true
            viewModelScope.launch {
                var previous = container.settings.current
                container.settings.state.collect { next ->
                    onSettingsChanged(previous, next)
                    previous = next
                }
            }
            load()
        }
    }

    /** Called when the screen becomes visible again, standing in for iOS `scenePhase == .active`. */
    fun onResume() = load()

    fun cancelInFlight() {
        loadJob?.cancel()
        loadJob = null
    }

    private fun onSettingsChanged(previous: Settings, next: Settings) {
        val mustReload = previous.region != next.region ||
            previous.chartResolution != next.chartResolution ||
            previous.useSampleData != next.useSampleData
        if (mustReload) {
            forceReload = true
            dataLastLoaded = null
            load()
            return
        }
        // Unit and VAT are pure display concerns: reformat what is already in memory.
        if (previous.unit != next.unit || previous.includeTax != next.includeTax) {
            publish(next)
        }
    }

    private fun load() {
        val settings = container.settings.current
        if (!shouldLoadData(settings)) {
            publish(settings)
            return
        }
        _state.update { it.copy(loading = true) }
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val region = settings.region
            runCatching { container.priceRepository.loadFullDayData(day, region) }
                .onSuccess { points ->
                    rawPoints = points
                    dataLastLoaded = Instant.now()
                    forceReload = false
                    if (day == Day.TOMORROW) {
                        _state.update { it.copy(missingData = points.size <= 4, loading = false) }
                    }
                    publish(container.settings.current)
                }
                .onFailure { error ->
                    if (error is kotlinx.coroutines.CancellationException) return@onFailure
                    Log.w(tag, "Failed to load ${day.name.lowercase()} prices for ${settings.region.code}", error)
                    _state.update { it.copy(loading = false, errorMessage = error.message) }
                }
        }
    }

    private fun publish(settings: Settings) {
        val values = buildValues(settings)
        val (minPrice, avgPrice, maxPrice) = statistics(settings)
        _state.update {
            it.copy(
                loading = false,
                values = values,
                min = minPrice,
                avg = avgPrice,
                max = maxPrice,
                missingData = day == Day.TOMORROW && rawPoints.size <= 4,
            )
        }
    }

    /** Chart points, converted to the user's unit and VAT, labelled in the region's clock. */
    private fun buildValues(settings: Settings): List<ChartValue> {
        val source = aggregated(settings)
        val label = MarketDay.labelsIn(settings.region, settings.chartResolution)
        val currentIndex = currentIndex(source.size, settings)
        return source.mapIndexed { index, point ->
            val display = point.price / settings.unit.divider *
                (if (settings.includeTax) settings.taxRate else 1.0)
            ChartValue(
                index = index,
                price = display,
                label = label(point.instant),
                level = PriceLevel.of(point.price),
                isCurrent = currentIndex == index,
            )
        }
    }

    private fun statistics(settings: Settings): Triple<String, String, String> {
        val source = aggregated(settings)
        if (source.isEmpty()) return Triple("---", "---", "---")
        val tax = if (settings.includeTax) settings.taxRate else 1.0
        val prices = source.map { it.price * tax / settings.unit.divider }
        return Triple(
            PriceFormat.format(prices.min(), settings.unit),
            PriceFormat.format(prices.average(), settings.unit),
            PriceFormat.format(prices.max(), settings.unit),
        )
    }

    private fun aggregated(settings: Settings): List<PricePoint> =
        if (settings.chartResolution == ChartResolution.ONE_HOUR) {
            PriceMath.aggregateToHourly(rawPoints)
        } else {
            rawPoints.sortedBy { it.timestamp }
        }

    /**
     * Index of the interval we are in right now.
     *
     * Deliberately the same proportional estimate the iOS view model uses
     * (`minutesIntoDay / 1440 × points`) rather than a timestamp lookup, so both platforms
     * highlight the same bar — including on the two days a year when the proportional shortcut
     * is one slot off on a 25-hour DST Sunday.
     */
    private fun currentIndex(pointCount: Int, settings: Settings): Int? {
        if (day != Day.TODAY || pointCount == 0) return null
        val now = Instant.now().atZone(ZoneId.systemDefault())
        val minutesIntoDay = now.hour * 60 + now.minute
        val proportion = minutesIntoDay.toDouble() / (24.0 * 60.0)
        return (pointCount * proportion).toInt().coerceIn(0, pointCount - 1)
    }

    /** Mirrors iOS `shouldLoadData()`. */
    private fun shouldLoadData(settings: Settings): Boolean {
        if (forceReload) return true
        val last = dataLastLoaded ?: return true
        val today = LocalDateNow.inDeviceZone()
        val lastDay = last.atZone(ZoneId.systemDefault()).toLocalDate()
        return when {
            day == Day.TODAY -> lastDay != today
            // Tomorrow: keep trying while it is empty, otherwise refresh hourly.
            _state.value.missingData -> true
            else -> Duration.between(last, Instant.now()).toHours() >= 1 || lastDay != today
        }
    }

    companion object {
        fun factory(container: AppContainer, day: Day) = viewModelFactory {
            initializer { ChartViewModel(container, day) }
        }
    }
}

internal object LocalDateNow {
    fun inDeviceZone(): java.time.LocalDate = Instant.now().atZone(ZoneId.systemDefault()).toLocalDate()
}

/**
 * The current-price card.
 *
 * Refreshes on the quarter-hour boundary when the market unit is 15 minutes — the first tick is
 * aligned to the next boundary, then every 15 minutes — and hourly otherwise, exactly like the
 * iOS timer task.
 */
class CurrentPriceViewModel(private val container: AppContainer) : ViewModel() {

    data class UiState(
        val price: String = "---",
        val timestamp: String = "--:--",
        val loading: Boolean = true,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val tag = "NordPriceCurrent"

    private var current: PricePoint? = null
    private var dataLastLoaded: Instant? = null
    private var forceReload = true
    private var loadJob: Job? = null
    private var ticker: Job? = null

    init {
        viewModelScope.launch {
            var previous = container.settings.current
            container.settings.state.collect { next ->
                if (previous.region != next.region || previous.chartResolution != next.chartResolution ||
                    previous.useSampleData != next.useSampleData
                ) {
                    forceReload = true
                    dataLastLoaded = null
                    restartTicker(next)
                    load()
                } else if (previous.unit != next.unit || previous.includeTax != next.includeTax) {
                    publish(next)
                }
                previous = next
            }
        }
        restartTicker(container.settings.current)
        load()
    }

    fun onResume() = load()

    fun cancelInFlight() {
        loadJob?.cancel()
        loadJob = null
        ticker?.cancel()
        ticker = null
    }

    private fun restartTicker(settings: Settings) {
        ticker?.cancel()
        if (settings.chartResolution != ChartResolution.FIFTEEN_MINUTES) return
        ticker = viewModelScope.launch {
            val now = Instant.now().atZone(ZoneId.systemDefault())
            val minutesIntoQuarter = now.minute % 15
            val secondsToNext = Duration.ofMinutes((15 - minutesIntoQuarter).toLong()).seconds - now.second
            delay(maxOf(1L, secondsToNext) * 1_000)
            load()
            while (true) {
                delay(15 * 60 * 1_000)
                load()
            }
        }
    }

    private fun load() {
        val settings = container.settings.current
        if (!shouldLoadData(settings)) {
            publish(settings)
            return
        }
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            runCatching { container.priceRepository.loadFullDayData(Day.TODAY, settings.region) }
                .onSuccess { points ->
                    current = pickCurrent(points, settings)
                    dataLastLoaded = Instant.now()
                    forceReload = false
                    publish(container.settings.current)
                }
                .onFailure { error ->
                    if (error !is kotlinx.coroutines.CancellationException) {
                        Log.w(tag, "Failed to load the current price", error)
                    }
                }
        }
    }

    private fun pickCurrent(points: List<PricePoint>, settings: Settings): PricePoint? {
        if (points.isEmpty()) return null
        val now = Instant.now()
        return if (settings.chartResolution == ChartResolution.ONE_HOUR) {
            val hourStart = now.atZone(ZoneId.systemDefault()).truncatedTo(java.time.temporal.ChronoUnit.HOURS).toInstant()
            val hourly = PriceMath.aggregateToHourly(points)
            hourly.lastOrNull { it.instant <= hourStart } ?: hourly.lastOrNull()
        } else {
            points.sortedBy { it.timestamp }.lastOrNull { it.instant <= now } ?: points.minByOrNull { it.timestamp }
        }
    }

    private fun publish(settings: Settings) {
        val point = current ?: return
        val label = MarketDay.labelsIn(settings.region, settings.chartResolution)
        val tax = if (settings.includeTax) settings.taxRate else 1.0
        _state.update {
            it.copy(
                loading = false,
                price = PriceFormat.format(point.price * tax / settings.unit.divider, settings.unit),
                timestamp = label(point.instant),
            )
        }
    }

    /** Same quarter-hour / hour bucketing rule the iOS view model applies. */
    private fun shouldLoadData(settings: Settings): Boolean {
        if (forceReload) return true
        val last = dataLastLoaded ?: return true
        val now = Instant.now()
        return if (settings.chartResolution == ChartResolution.ONE_HOUR) {
            last.atZone(ZoneId.systemDefault()).truncatedTo(java.time.temporal.ChronoUnit.HOURS) !=
                now.atZone(ZoneId.systemDefault()).truncatedTo(java.time.temporal.ChronoUnit.HOURS)
        } else {
            quarterBucket(last) != quarterBucket(now)
        }
    }

    private fun quarterBucket(instant: Instant): Instant =
        instant.atZone(ZoneId.systemDefault())
            .run { withMinute((minute / 15) * 15).withSecond(0).withNano(0) }
            .toInstant()

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { CurrentPriceViewModel(container) }
        }
    }
}
