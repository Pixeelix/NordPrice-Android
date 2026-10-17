package koodipardik.nordprice.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import koodipardik.nordprice.R
import koodipardik.nordprice.core.ChartType
import koodipardik.nordprice.core.PriceFormat
import koodipardik.nordprice.ui.barDate
import koodipardik.nordprice.ui.chart.BarWidthSpec
import koodipardik.nordprice.ui.chart.ChartCanvas
import koodipardik.nordprice.ui.chart.ChartValue
import koodipardik.nordprice.ui.chart.LevelLegend
import koodipardik.nordprice.ui.components.CardHorizontalInset
import koodipardik.nordprice.ui.components.HeroPriceCard
import koodipardik.nordprice.ui.components.HistoryMinMaxCard
import koodipardik.nordprice.ui.components.ScreenTitle
import koodipardik.nordprice.ui.components.appCard
import koodipardik.nordprice.ui.components.unitLabel
import koodipardik.nordprice.ui.periodTitle
import koodipardik.nordprice.ui.shortBarDate
import koodipardik.nordprice.ui.rememberContainer
import koodipardik.nordprice.ui.rememberSettings
import koodipardik.nordprice.ui.theme.AppTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.max

/**
 * Price history: average of a week, month or year, its cheapest and most expensive day, and a
 * chart of daily (or monthly) averages you step back and forth through. Port of the iOS
 * `HistoryView`, whose iOS sheet-style date picker becomes a Material date dialog.
 */
@Composable
fun HistoryScreen() {
    val container = rememberContainer()
    val settings = rememberSettings()
    val viewModel: HistoryViewModel = viewModel(factory = HistoryViewModel.factory(container))
    val state by viewModel.state.collectAsStateWithLifecycle()

    LifecycleResumeEffect(Unit) {
        viewModel.configure(settings.region)
        onPauseOrDispose { viewModel.cancelInFlight() }
    }
    // A region change is a different market's whole history.
    androidx.compose.runtime.LaunchedEffect(settings.region) { viewModel.configure(settings.region) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = CardHorizontalInset),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ScreenTitle(stringResource(R.string.title_price_history))

        AverageCard(
            state = state,
            onBack = viewModel::goBack,
            onForward = viewModel::goForward,
            onPickDate = { viewModel.select(it) },
        )
        Spacer(Modifier.height(12.dp))

        HistoryMinMaxCard(
            cheapestLabel = stringResource(R.string.title_min),
            cheapestValue = displayPrice(state.cheapest?.rawPriceMWh, settings),
            cheapestDate = state.cheapest?.let { shortBarDate(it.start, state.period) } ?: " ",
            expensiveLabel = stringResource(R.string.title_max),
            expensiveValue = displayPrice(state.mostExpensive?.rawPriceMWh, settings),
            expensiveDate = state.mostExpensive?.let { shortBarDate(it.start, state.period) } ?: " ",
            showLevelDots = settings.colorfulChart,
            cheapestDot = state.cheapest?.level?.let { Color(it.colorHex) },
            expensiveDot = state.mostExpensive?.level?.let { Color(it.colorHex) },
        )
        Spacer(Modifier.height(12.dp))

        PeriodPicker(period = state.period, onSelect = viewModel::setPeriod)
        Spacer(Modifier.height(12.dp))

        HistoryChart(
            state = state,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            onRetry = viewModel::load,
            onToggleChartType = {
                container.settings.setChartType(
                    if (settings.chartType == ChartType.BAR) ChartType.LINE else ChartType.BAR,
                )
            },
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun AverageCard(
    state: HistoryUiState,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onPickDate: (LocalDate) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    val average = if (state.loading) null else state.averageRawMWh
    val settings = rememberSettings()

    HeroPriceCard(
        leading = {
            ArrowButton(
                icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                label = stringResource(R.string.a11y_previous_period),
                enabled = state.canGoBack,
                onClick = onBack,
            )
        },
        center = {
            PeriodButton(
                text = periodTitle(state.intervalStart, state.intervalEndExclusive, state.period),
                onClick = { showPicker = true },
            )
        },
        trailing = {
            ArrowButton(
                icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                label = stringResource(R.string.a11y_next_period),
                enabled = state.canGoForward,
                onClick = onForward,
            )
        },
        value = if (state.loading) "0,00" else displayPrice(average, settings),
        unit = "${unitLabel(settings.unit)} · ${stringResource(R.string.title_period_average)}",
        valueAccessibilityLabel = stringResource(R.string.title_period_average),
    )

    if (showPicker) {
        PeriodDatePicker(
            initial = state.anchor,
            onDismiss = { showPicker = false },
            onPicked = {
                onPickDate(it)
                showPicker = false
            },
        )
    }
}

/** The period label doubles as the "jump to a date" button, as it does on iOS. */
@Composable
private fun PeriodButton(text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .heightIn(min = 44.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = AppTheme.cardText,
            maxLines = 1,
        )
        Icon(
            imageVector = Icons.Filled.ExpandMore,
            contentDescription = null,
            tint = AppTheme.cardText,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun ArrowButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(44.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = AppTheme.brand,
            modifier = Modifier.then(
                if (enabled) Modifier else Modifier.padding(0.dp),
            ),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PeriodDatePicker(initial: LocalDate, onDismiss: () -> Unit, onPicked: (LocalDate) -> Unit) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        yearRange = 2013..java.time.Year.now().value,
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val millis = state.selectedDateMillis
                    if (millis != null) {
                        onPicked(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                    } else {
                        onDismiss()
                    }
                },
            ) { Text(stringResource(R.string.button_done)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.button_cancel)) }
        },
    ) {
        DatePicker(
            state = state,
            showModeToggle = true,
            title = {
                Text(
                    text = stringResource(R.string.title_select_date),
                    modifier = Modifier.padding(start = 24.dp, top = 20.dp),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            },
        )
    }
}

@Composable
private fun PeriodPicker(period: HistoryPeriod, onSelect: (HistoryPeriod) -> Unit) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        HistoryPeriod.ordered.forEachIndexed { index, option ->
            SegmentedButton(
                selected = period == option,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index, HistoryPeriod.ordered.size),
                icon = {},
                label = {
                    Text(
                        text = stringResource(option.labelRes),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                    )
                },
            )
        }
    }
}

@Composable
private fun HistoryChart(
    state: HistoryUiState,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit,
    onToggleChartType: () -> Unit,
) {
    val settings = rememberSettings()
    var selected by remember { mutableStateOf<Int?>(null) }
    val priced = state.priced

    val displayPrice: (Double) -> Double = { raw ->
        raw / settings.unit.divider * (if (settings.includeTax) settings.taxRate else 1.0)
    }
    val values: List<ChartValue> = remember(priced, settings.unit, settings.includeTax) {
        priced.toChartValues(displayPrice)
    }
    val count = state.bars.size
    val tickIndices = remember(state.period, count) {
        when (state.period) {
            HistoryPeriod.WEEK -> (0 until count).toList()
            HistoryPeriod.MONTH -> (0 until count step 7).toList()
            HistoryPeriod.YEAR -> (0 until count step 2).toList()
        }
    }
    // Axis labels are resolved up front: the canvas takes a plain function, and Compose text
    // cannot be produced from one.
    val axisFormatter = axisLabelFormatter(state.period)
    val axisLabels: Map<Int, String> = remember(state.bars, tickIndices, axisFormatter) {
        tickIndices.mapNotNull { index ->
            state.bars.getOrNull(index)?.let { bar -> index to axisFormatter.format(bar.start) }
        }.toMap()
    }

    val prices = values.map { it.price }
    val yMin = minOf(prices.minOrNull() ?: 0.0, 0.0)
    val yMaxBase = max(prices.maxOrNull() ?: 1.0, 0.01)
    val yMax = yMaxBase + (yMaxBase - yMin) * 0.1
    val average = state.averageRawMWh?.let(displayPrice)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .appCard(cornerRadius = 14.dp)
            .padding(bottom = 6.dp),
    ) {
        val selectedBar = selected?.let { index -> priced.firstOrNull { it.index == index } }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selectedBar != null) {
                Text(
                    text = "${displayPriceText(selectedBar.rawPriceMWh, settings)} ${unitLabel(settings.unit)}",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppTheme.cardText,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                Text(
                    text = barDate(selectedBar.start, state.period),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppTheme.cardText,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.End,
                    maxLines = 1,
                )
            } else {
                Text(
                    text = stringResource(
                        if (state.period == HistoryPeriod.YEAR) {
                            R.string.title_monthly_average
                        } else {
                            R.string.title_daily_average
                        },
                    ),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppTheme.cardText,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
            }
            IconButton(onClick = onToggleChartType, modifier = Modifier.size(44.dp)) {
                Icon(
                    imageVector = if (settings.chartType == ChartType.BAR) {
                        Icons.Filled.ShowChart
                    } else {
                        Icons.Filled.BarChart
                    },
                    contentDescription = stringResource(
                        if (settings.chartType == ChartType.BAR) {
                            R.string.a11y_show_line_chart
                        } else {
                            R.string.a11y_show_bar_chart
                        },
                    ),
                    tint = AppTheme.cardText,
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(start = 10.dp, end = 20.dp, bottom = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            when {
                state.loading -> CircularProgressIndicator(color = AppTheme.brand)

                state.loadFailed -> Message(
                    text = stringResource(R.string.text_history_load_failed),
                    actionLabel = stringResource(R.string.button_retry),
                    onAction = onRetry,
                )

                !state.hasData -> Message(stringResource(R.string.text_no_history_data))

                else -> ChartCanvas(
                    values = values,
                    chartType = settings.chartType,
                    colorful = settings.colorfulChart,
                    xStart = -0.5,
                    xEnd = (count - 1) + 0.5,
                    yMin = yMin,
                    yMax = yMax,
                    xTicks = tickIndices,
                    xTickLabel = { index -> axisLabels[index] ?: "" },
                    yLabel = { PriceFormat.axisLabel(it, settings.unit) },
                    selected = selected,
                    onSelection = { selected = it },
                    averageRule = average,
                    showZeroRule = yMin < 0,
                    barWidth = BarWidthSpec.Ratio(0.7f, if (count > 20) 1.5.dp else 3.dp),
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        if (settings.colorfulChart && state.hasData) {
            LevelLegend(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
        } else {
            Spacer(Modifier.height(6.dp))
        }
    }
}

/**
 * Formatter for one history axis slot: day-of-week for the week view, day-of-month for the
 * month view, month name for the year view — as in the iOS chart.
 */
@Composable
private fun axisLabelFormatter(period: HistoryPeriod): java.time.format.DateTimeFormatter {
    val locale = koodipardik.nordprice.ui.rememberAppLocale()
    val pattern = when (period) {
        HistoryPeriod.WEEK -> "EEE"
        HistoryPeriod.MONTH -> "d"
        HistoryPeriod.YEAR -> "MMM"
    }
    return remember(locale, pattern) { java.time.format.DateTimeFormatter.ofPattern(pattern, locale) }
}

@Composable
private fun Message(text: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    Column(
        modifier = Modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = text, fontSize = 17.sp, color = AppTheme.cardText, textAlign = TextAlign.Center)
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

private fun displayPriceText(rawMWh: Double?, settings: koodipardik.nordprice.settings.Settings): String {
    if (rawMWh == null) return "---"
    val display = rawMWh / settings.unit.divider * (if (settings.includeTax) settings.taxRate else 1.0)
    return PriceFormat.format(display, settings.unit)
}

private fun displayPrice(rawMWh: Double?, settings: koodipardik.nordprice.settings.Settings): String =
    displayPriceText(rawMWh, settings)
