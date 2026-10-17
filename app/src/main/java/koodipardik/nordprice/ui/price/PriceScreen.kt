package koodipardik.nordprice.ui.price

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import koodipardik.nordprice.R
import koodipardik.nordprice.core.ChartType
import koodipardik.nordprice.core.Day
import koodipardik.nordprice.ui.dayTitle
import koodipardik.nordprice.ui.components.HeroPriceCard
import koodipardik.nordprice.ui.components.ScreenTitle
import koodipardik.nordprice.ui.components.MinAvgMaxCard
import koodipardik.nordprice.ui.components.RegionFlagButton
import koodipardik.nordprice.ui.components.RegionPickerSheet
import koodipardik.nordprice.ui.components.unitLabel
import koodipardik.nordprice.ui.chart.PriceChartCard
import koodipardik.nordprice.ui.rememberContainer
import koodipardik.nordprice.ui.rememberSettings
import koodipardik.nordprice.ui.theme.AppTheme
import koodipardik.nordprice.ui.components.CardHorizontalInset
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import koodipardik.nordprice.core.Region
import koodipardik.nordprice.data.PriceRepository

/**
 * Today and Tomorrow. They are one screen because they are one screen on iOS too, minus a few
 * cards: today leads with the current price, tomorrow leads with its min–max range and shows a
 * placeholder until the day-ahead auction publishes around 14:00–15:00.
 */
@Composable
fun PriceScreen(day: Day) {
    val container = rememberContainer()
    val settings = rememberSettings()
    val chartViewModel: ChartViewModel = viewModel(
        key = "chart-$day",
        factory = ChartViewModel.factory(container, day),
    )
    val chartState by chartViewModel.state.collectAsStateWithLifecycle()

    val currentPriceViewModel: CurrentPriceViewModel? = if (day == Day.TODAY) {
        viewModel(factory = CurrentPriceViewModel.factory(container))
    } else {
        null
    }
    val priceState = currentPriceViewModel?.state?.collectAsStateWithLifecycle()

    var showRegionPicker by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LifecycleResumeEffect(day) {
        chartViewModel.onResume()
        currentPriceViewModel?.onResume()
        // Tomorrow's prices appear mid-afternoon; warm the cache even if the user never opens
        // the tab, so the widget has something to show after midnight without a network call.
        if (day == Day.TODAY) {
            scope.launchPrefetchTomorrow(container.priceRepository, settings.region)
        }
        onPauseOrDispose {
            chartViewModel.cancelInFlight()
            currentPriceViewModel?.cancelInFlight()
        }
    }

    val title = stringResource(
        if (day == Day.TODAY) R.string.title_todays_price else R.string.title_tomorrows_price,
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = CardHorizontalInset),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ScreenTitle(title)

        when {
            day == Day.TODAY -> {
                CurrentPriceCard(
                    state = priceState?.value ?: CurrentPriceViewModel.UiState(),
                    settings = settings,
                    onRegionClick = { showRegionPicker = true },
                )
                Spacer(Modifier.height(12.dp))
                MinAvgMaxCard(
                    min = chartState.min,
                    avg = chartState.avg,
                    max = chartState.max,
                )
                Spacer(Modifier.height(12.dp))
                DayChart(
                    day = day,
                    chartState = chartState,
                    settings = settings,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
            }

            chartState.missingData -> TomorrowPendingCard(Modifier.weight(1f))

            else -> {
                HeroPriceCard(
                    leading = {
                        RegionFlagButton(region = settings.region, onClick = { showRegionPicker = true })
                    },
                    value = "${chartState.min} - ${chartState.max}",
                    unit = unitLabel(settings.unit),
                    valueAccessibilityLabel = stringResource(R.string.a11y_price_range),
                )
                Spacer(Modifier.height(12.dp))
                MinAvgMaxCard(
                    min = chartState.min,
                    avg = chartState.avg,
                    max = chartState.max,
                )
                Spacer(Modifier.height(12.dp))
                DayChart(
                    day = day,
                    chartState = chartState,
                    settings = settings,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
            }
        }

        // Phase 2: the AdMob anchor banner slot goes here.
        Spacer(Modifier.height(8.dp))
    }

    if (showRegionPicker) {
        RegionPickerSheet(
            onDismiss = { showRegionPicker = false },
            onSelect = {
                container.settings.setRegion(it)
                showRegionPicker = false
            },
        )
    }
}

@Composable
private fun CurrentPriceCard(
    state: CurrentPriceViewModel.UiState,
    settings: koodipardik.nordprice.settings.Settings,
    onRegionClick: () -> Unit,
) {
    HeroPriceCard(
        leading = { RegionFlagButton(region = settings.region, onClick = onRegionClick) },
        trailing = {
            Text(text = state.timestamp, fontSize = 15.sp, color = AppTheme.cardText)
        },
        value = state.price,
        unit = unitLabel(settings.unit),
        valueAccessibilityLabel = stringResource(R.string.text_current_price),
    )
}

@Composable
private fun DayChart(
    day: Day,
    chartState: ChartUiState,
    settings: koodipardik.nordprice.settings.Settings,
    modifier: Modifier = Modifier,
) {
    val container = rememberContainer()
    val dayOffset = if (day == Day.TOMORROW) 1L else 0L
    PriceChartCard(
        values = chartState.values,
        chartType = settings.chartType,
        colorful = settings.colorfulChart,
        unit = settings.unit,
        resolution = settings.chartResolution,
        loading = chartState.loading,
        title = dayTitle(LocalDate.now(ZoneId.systemDefault()).plusDays(dayOffset)),
        onToggleChartType = {
            container.settings.setChartType(
                if (settings.chartType == ChartType.BAR) ChartType.LINE else ChartType.BAR,
            )
        },
        modifier = modifier,
    )
}

/** Tomorrow before the auction: the "available from 15:00" note, styled as a card. */
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


/** Fire-and-forget cache warm for tomorrow's day, mirroring iOS `prefetchTomorrow()`. */
internal fun CoroutineScope.launchPrefetchTomorrow(repository: PriceRepository, region: Region) {
    launch(Dispatchers.IO) {
        runCatching { repository.loadFullDayData(Day.TOMORROW, region) }
    }
}
