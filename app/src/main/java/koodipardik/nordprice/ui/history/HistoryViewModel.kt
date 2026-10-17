package koodipardik.nordprice.ui.history

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import koodipardik.nordprice.AppContainer
import koodipardik.nordprice.R
import koodipardik.nordprice.core.PriceLevel
import koodipardik.nordprice.core.Region
import koodipardik.nordprice.data.DailyAveragePrice
import koodipardik.nordprice.data.HistoryCalendar
import koodipardik.nordprice.ui.chart.ChartValue
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

enum class HistoryPeriod(@param:StringRes val labelRes: Int) {
    WEEK(R.string.period_week),
    MONTH(R.string.period_month),
    YEAR(R.string.period_year);

    companion object {
        val ordered = entries.toList()
    }
}

/** One bar of the history chart: a day (week / month view) or a month (year view). */
data class HistoryBar(
    val index: Int,
    val start: LocalDate,
    /** Null for slots with no data — days still ahead, or before Elering's record begins. */
    val rawPriceMWh: Double?,
) {
    val level: PriceLevel? get() = rawPriceMWh?.let { PriceLevel.of(it) }
}

data class HistoryUiState(
    val period: HistoryPeriod = HistoryPeriod.MONTH,
    val anchor: LocalDate = HistoryCalendar.today(),
    val bars: List<HistoryBar> = emptyList(),
    val loading: Boolean = true,
    val loadFailed: Boolean = false,
) {
    val priced: List<HistoryBar> get() = bars.filter { it.rawPriceMWh != null }
    val hasData: Boolean get() = priced.isNotEmpty()

    val averageRawMWh: Double?
        get() = priced.mapNotNull { it.rawPriceMWh }.takeIf { it.isNotEmpty() }?.average()

    val cheapest: HistoryBar?
        get() = priced.minByOrNull { it.rawPriceMWh ?: Double.MAX_VALUE }

    val mostExpensive: HistoryBar?
        get() = priced.maxByOrNull { it.rawPriceMWh ?: -Double.MAX_VALUE }

    /** First day of the visible period, and the day after its last one. */
    val intervalStart: LocalDate get() = periodStart(anchor, period)
    val intervalEndExclusive: LocalDate get() = periodEnd(anchor, period)

    /** There is an earlier period to step to. */
    val canGoBack: Boolean get() = intervalStart.isAfter(HistoryCalendar.earliestDate)

    /** The visible period has finished, so there is a later one to step to. */
    val canGoForward: Boolean get() = !intervalEndExclusive.isAfter(HistoryCalendar.today())
}

/**
 * Week / month / year price history.
 *
 * Steps one calendar period at a time. Changing the period keeps the anchor in place — going
 * from "September" to "Year" shows the year that contains September — and history always ends
 * today: tomorrow's day-ahead prices are already inside the current month's payload but are not
 * history yet.
 */
class HistoryViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(HistoryUiState())
    val state: StateFlow<HistoryUiState> = _state.asStateFlow()

    private var region: Region? = null
    private var loadJob: Job? = null
    private var configured = false

    fun configure(region: Region) {
        if (configured && this.region == region) return
        configured = true
        this.region = region
        load()
    }

    fun setPeriod(period: HistoryPeriod) {
        if (period == _state.value.period) return
        _state.update { it.copy(period = period) }
        load()
    }

    /** Public so the chart's "could not load" message can offer a retry. */
    fun load() = loadInternal()

    fun goBack() = move(-1)
    fun goForward() = move(1)

    /** Jump to the period containing [date], from the date picker. */
    fun select(date: LocalDate) {
        val clamped = date.coerceIn(HistoryCalendar.earliestDate, HistoryCalendar.today())
        val state = _state.value
        if (periodStart(clamped, state.period) == state.intervalStart) return
        _state.update { it.copy(anchor = clamped) }
        load()
    }

    /** Back on screen after a day rolled over: the current period gained a day. */
    fun refresh() = load()

    fun cancelInFlight() {
        loadJob?.cancel()
        loadJob = null
    }

    private fun move(by: Int) {
        val state = _state.value
        val shifted = addPeriod(state.anchor, state.period, by)
        val anchor = if (shifted.isAfter(HistoryCalendar.today())) HistoryCalendar.today() else shifted
        _state.update { it.copy(anchor = anchor) }
        load()
    }

    private fun loadInternal() {
        val state = _state.value
        val region = this.region ?: return
        val months = monthsIn(state.intervalStart, state.intervalEndExclusive)
        val slots = slots(state.intervalStart, state.intervalEndExclusive, state.period)

        _state.update { it.copy(loading = true, loadFailed = false) }
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            runCatching { container.historyRepository.dailyAverages(region, months) }
                .onSuccess { days ->
                    val bars = bars(slots, state.period, days)
                    _state.update { it.copy(bars = bars, loading = false) }
                }
                .onFailure { error ->
                    if (error is kotlinx.coroutines.CancellationException) return@onFailure
                    _state.update { it.copy(bars = emptyList(), loading = false, loadFailed = true) }
                }
        }
    }

    /** Month starts overlapping the period, capped at the current month. */
    private fun monthsIn(start: LocalDate, endExclusive: LocalDate): List<LocalDate> {
        val last = YearMonth.from(minOf(endExclusive.minusDays(1), HistoryCalendar.today()))
        var month = YearMonth.from(start)
        val months = mutableListOf<LocalDate>()
        while (!month.isAfter(last)) {
            months += month.atDay(1)
            month = month.plusMonths(1)
        }
        return months
    }

    /** Start of every bar: days for week / month, months for year. */
    private fun slots(start: LocalDate, endExclusive: LocalDate, period: HistoryPeriod): List<LocalDate> {
        val slots = mutableListOf<LocalDate>()
        if (period == HistoryPeriod.YEAR) {
            var month = YearMonth.from(start)
            val last = YearMonth.from(endExclusive.minusDays(1))
            while (!month.isAfter(last)) {
                slots += month.atDay(1)
                month = month.plusMonths(1)
            }
        } else {
            var day = start
            while (day.isBefore(endExclusive)) {
                slots += day
                day = day.plusDays(1)
            }
        }
        return slots
    }

    private fun bars(
        slots: List<LocalDate>,
        period: HistoryPeriod,
        days: List<DailyAveragePrice>,
    ): List<HistoryBar> {
        val today = HistoryCalendar.today()
        val pastDays = days.filter { HistoryCalendar.localDate(it.dayStart) <= today }

        if (period == HistoryPeriod.YEAR) {
            val byMonth = pastDays
                .groupBy { HistoryCalendar.monthKey(HistoryCalendar.localDate(it.dayStart)) }
                .mapValues { entry -> entry.value.sumOf { it.price } / entry.value.size }
            return slots.map { start -> HistoryBar(0, start, byMonth[HistoryCalendar.monthKey(start)]) }
                .mapIndexed { index, bar -> bar.copy(index = index) }
        }

        val byDay = pastDays.associateBy({ HistoryCalendar.localDate(it.dayStart) }, { it.price })
        return slots.mapIndexed { index, start -> HistoryBar(index, start, byDay[start]) }
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { HistoryViewModel(container) }
        }
    }
}

// MARK: - Period arithmetic

internal fun periodStart(anchor: LocalDate, period: HistoryPeriod): LocalDate = when (period) {
    HistoryPeriod.WEEK -> anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    HistoryPeriod.MONTH -> anchor.withDayOfMonth(1)
    HistoryPeriod.YEAR -> anchor.withDayOfMonth(1).withMonth(1)
}

internal fun periodEnd(anchor: LocalDate, period: HistoryPeriod): LocalDate =
    addPeriod(periodStart(anchor, period), period, 1)

internal fun addPeriod(date: LocalDate, period: HistoryPeriod, by: Int): LocalDate = when (period) {
    HistoryPeriod.WEEK -> date.plusWeeks(by.toLong())
    HistoryPeriod.MONTH -> date.plusMonths(by.toLong())
    HistoryPeriod.YEAR -> date.plusYears(by.toLong())
}

/** Bars of the visible period, in the shape the shared chart canvas draws. */
internal fun List<HistoryBar>.toChartValues(displayPrice: (Double) -> Double): List<ChartValue> =
    filter { it.rawPriceMWh != null }.map { bar ->
        ChartValue(
            index = bar.index,
            price = displayPrice(bar.rawPriceMWh!!),
            label = bar.start.toString(),
            level = bar.level,
        )
    }

