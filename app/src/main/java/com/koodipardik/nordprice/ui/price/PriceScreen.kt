package com.koodipardik.nordprice.ui.price

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.koodipardik.nordprice.R
import com.koodipardik.nordprice.core.ChartType
import com.koodipardik.nordprice.core.Day
import com.koodipardik.nordprice.core.Region
import com.koodipardik.nordprice.data.PriceRepository
import com.koodipardik.nordprice.settings.Settings
import com.koodipardik.nordprice.ui.TabletSideColumnWidth
import com.koodipardik.nordprice.ui.ads.AdPlacement
import com.koodipardik.nordprice.ui.ads.AnchorBanner
import com.koodipardik.nordprice.ui.components.CardHorizontalInset
import com.koodipardik.nordprice.ui.components.HeroPriceCard
import com.koodipardik.nordprice.ui.components.MinAvgMaxCard
import com.koodipardik.nordprice.ui.components.RegionFlagButton
import com.koodipardik.nordprice.ui.components.RegionPickerSheet
import com.koodipardik.nordprice.ui.components.ScreenTitle
import com.koodipardik.nordprice.ui.components.unitLabel
import com.koodipardik.nordprice.ui.chart.PriceChartCard
import com.koodipardik.nordprice.ui.dayTitle
import com.koodipardik.nordprice.ui.rememberContainer
import com.koodipardik.nordprice.ui.rememberSettings
import com.koodipardik.nordprice.ui.theme.AppTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/**
 * Today or Tomorrow on a phone: the day's cards stacked over its chart.
 *
 * Today leads with the current price, tomorrow leads with its min–max range and shows a
 * placeholder until the day-ahead auction publishes around 14:00–15:00.
 */
@Composable
fun PriceScreen(day: Day) {
    val container = rememberContainer()
    val settings = rememberSettings()
    val chartViewModel = rememberChartViewModel(day)
    val chartState by chartViewModel.state.collectAsStateWithLifecycle()
    val priceState = rememberCurrentPriceState(day)
    var showRegionPicker by remember { mutableStateOf(false) }

    DayLifecycleEffects(day, chartViewModel, priceState.viewModel)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = CardHorizontalInset),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ScreenTitle(dayTitleText(day))

        if (day == Day.TOMORROW && chartState.missingData) {
            TomorrowPendingCard(Modifier.weight(1f))
        } else {
            DayCards(day, chartState, priceState.value, onRegionClick = { showRegionPicker = true })
            Spacer(Modifier.height(12.dp))
            DayChart(day, chartState, settings, Modifier.fillMaxWidth().weight(1f))
        }

        AnchorBanner(priceBannerPlacement(day, chartState.missingData))
        Spacer(Modifier.height(8.dp))
    }

    RegionPicker(showRegionPicker) { showRegionPicker = false }
}

/**
 * Which banner unit this price screen asks for. iOS uses two different Tomorrow units depending on
 * whether the day-ahead auction has published yet (`TomorrowView.swift:57` and `:76`), and one for
 * Today; the tablet dashboard shares Today's.
 */
private fun priceBannerPlacement(day: Day, missingTomorrowData: Boolean): AdPlacement = when {
    day != Day.TOMORROW -> AdPlacement.TODAY
    missingTomorrowData -> AdPlacement.TOMORROW_PENDING
    else -> AdPlacement.TOMORROW_READY
}

/**
 * The tablet layout: today and tomorrow share one screen, one row per day, each with a narrow
 * column of cards beside its chart — the port of the iOS `DashboardView`, which appears when the
 * window is regular width *and* regular height.
 */
@Composable
fun PriceDashboard() {
    val container = rememberContainer()
    val settings = rememberSettings()
    val todayViewModel = rememberChartViewModel(Day.TODAY)
    val tomorrowViewModel = rememberChartViewModel(Day.TOMORROW)
    val todayState by todayViewModel.state.collectAsStateWithLifecycle()
    val tomorrowState by tomorrowViewModel.state.collectAsStateWithLifecycle()
    val priceState = rememberCurrentPriceState(Day.TODAY)
    var showRegionPicker by remember { mutableStateOf(false) }

    DayLifecycleEffects(Day.TODAY, todayViewModel, priceState.viewModel)
    DayLifecycleEffects(Day.TOMORROW, tomorrowViewModel, null)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = CardHorizontalInset, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        DayRow(
            title = dayTitleText(Day.TODAY),
            modifier = Modifier.weight(1f),
            cards = {
                DayCards(Day.TODAY, todayState, priceState.value, onRegionClick = { showRegionPicker = true })
            },
            chart = {
                DayChart(Day.TODAY, todayState, settings, Modifier.fillMaxSize())
            },
        )

        DayRow(
            title = dayTitleText(Day.TOMORROW),
            modifier = Modifier.weight(1f),
            cards = {
                if (!tomorrowState.missingData) {
                    DayCards(Day.TOMORROW, tomorrowState, null, onRegionClick = { showRegionPicker = true })
                }
            },
            chart = {
                if (tomorrowState.missingData) {
                    TomorrowPendingCard(Modifier.fillMaxSize())
                } else {
                    DayChart(Day.TOMORROW, tomorrowState, settings, Modifier.fillMaxSize())
                }
            },
        )

        // One banner serves the whole dashboard, as in iOS DashboardView.swift:48 — which is why it
        // asks for today's unit even though tomorrow shares the screen.
        AnchorBanner(AdPlacement.TODAY)
    }

    RegionPicker(showRegionPicker) { showRegionPicker = false }
}

/** One row of the dashboard: title and cards in a fixed-width column, chart taking the rest. */
@Composable
private fun DayRow(
    title: String,
    modifier: Modifier = Modifier,
    cards: @Composable () -> Unit,
    chart: @Composable () -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier.width(TabletSideColumnWidth),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = title,
                fontSize = 26.sp,
                fontWeight = FontWeight.Medium,
                color = AppTheme.textOnBackground,
                modifier = Modifier.padding(start = 4.dp),
            )
            cards()
        }
        Box(modifier = Modifier.weight(1f)) { chart() }
    }
}

/** Current price (today) or min–max range (tomorrow), over the min/avg/max card. */
@Composable
private fun DayCards(
    day: Day,
    chartState: ChartUiState,
    priceState: CurrentPriceViewModel.UiState?,
    onRegionClick: () -> Unit,
) {
    val settings = rememberSettings()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (day == Day.TODAY) {
            HeroPriceCard(
                leading = { RegionFlagButton(region = settings.region, onClick = onRegionClick) },
                trailing = {
                    Text(text = priceState?.timestamp.orEmpty(), fontSize = 15.sp, color = AppTheme.cardText)
                },
                value = priceState?.price ?: "---",
                unit = unitLabel(settings.unit),
                valueAccessibilityLabel = stringResource(R.string.text_current_price),
            )
        } else {
            HeroPriceCard(
                leading = { RegionFlagButton(region = settings.region, onClick = onRegionClick) },
                value = "${chartState.min} - ${chartState.max}",
                unit = unitLabel(settings.unit),
                valueAccessibilityLabel = stringResource(R.string.a11y_price_range),
            )
        }
        MinAvgMaxCard(min = chartState.min, avg = chartState.avg, max = chartState.max)
    }
}

@Composable
private fun DayChart(
    day: Day,
    chartState: ChartUiState,
    settings: Settings,
    modifier: Modifier = Modifier,
) {
    val container = rememberContainer()
    PriceChartCard(
        values = chartState.values,
        chartType = settings.chartType,
        colorful = settings.colorfulChart,
        unit = settings.unit,
        resolution = settings.chartResolution,
        loading = chartState.loading,
        title = dayTitle(
            LocalDate.now(ZoneId.systemDefault())
                .plusDays(if (day == Day.TOMORROW) 1L else 0L),
        ),
        onToggleChartType = {
            container.settings.setChartType(
                if (settings.chartType == ChartType.BAR) ChartType.LINE else ChartType.BAR,
            )
        },
        modifier = modifier,
    )
}

/** Tomorrow before the auction: the "available from 15:00" note. */
@Composable
private fun TomorrowPendingCard(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(R.string.text_tomorrows_price_will_apear),
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            color = AppTheme.textOnBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .widthIn(max = 320.dp)
                .padding(24.dp),
        )
    }
}

// MARK: - Shared plumbing

@Composable
private fun dayTitleText(day: Day): String = stringResource(
    if (day == Day.TODAY) R.string.title_todays_price else R.string.title_tomorrows_price,
)

@Composable
private fun rememberChartViewModel(day: Day): ChartViewModel {
    val container = rememberContainer()
    return viewModel(key = "chart-$day", factory = ChartViewModel.factory(container, day))
}

/** Holder so the dashboard can drive the current-price model without duplicating its lifecycle. */
private class CurrentPriceHolder(
    val viewModel: CurrentPriceViewModel?,
    val value: CurrentPriceViewModel.UiState,
)

@Composable
private fun rememberCurrentPriceState(day: Day): CurrentPriceHolder {
    if (day != Day.TODAY) return CurrentPriceHolder(null, CurrentPriceViewModel.UiState())
    val container = rememberContainer()
    val model: CurrentPriceViewModel = viewModel(factory = CurrentPriceViewModel.factory(container))
    return CurrentPriceHolder(model, model.state.collectAsStateWithLifecycle().value)
}

/**
 * Load on resume, cancel on pause — the Android shape of iOS's `scenePhase` handling — plus the
 * tomorrow prefetch that only today's screen does.
 */
@Composable
private fun DayLifecycleEffects(
    day: Day,
    chartViewModel: ChartViewModel,
    currentPriceViewModel: CurrentPriceViewModel?,
) {
    val container = rememberContainer()
    val settings = rememberSettings()
    val scope = rememberCoroutineScope()

    LifecycleResumeEffect(day) {
        chartViewModel.onResume()
        currentPriceViewModel?.onResume()
        // Tomorrow's prices appear mid-afternoon; warm the cache even if the user never opens the
        // tab, so the widget has something to show after midnight without a network call.
        if (day == Day.TODAY) {
            scope.launchPrefetchTomorrow(container.priceRepository, settings.region)
        }
        onPauseOrDispose {
            chartViewModel.cancelInFlight()
            currentPriceViewModel?.cancelInFlight()
        }
    }
}

@Composable
private fun RegionPicker(shown: Boolean, onDismiss: () -> Unit) {
    val container = rememberContainer()
    if (!shown) return
    RegionPickerSheet(
        onDismiss = onDismiss,
        onSelect = {
            container.settings.setRegion(it)
            onDismiss()
        },
    )
}

/** Fire-and-forget cache warm for tomorrow's day, mirroring iOS `prefetchTomorrow()`. */
internal fun CoroutineScope.launchPrefetchTomorrow(repository: PriceRepository, region: Region) {
    launch(Dispatchers.IO) {
        runCatching { repository.loadFullDayData(Day.TOMORROW, region) }
    }
}
