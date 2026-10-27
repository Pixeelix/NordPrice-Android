package com.koodipardik.nordprice.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalConfiguration
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
import com.koodipardik.nordprice.core.PriceFormat
import com.koodipardik.nordprice.ui.barDate
import com.koodipardik.nordprice.ui.chart.ChartCanvas
import com.koodipardik.nordprice.ui.chart.ChartValue
import com.koodipardik.nordprice.ui.chart.ChartStyles
import com.koodipardik.nordprice.ui.chart.LevelLegend
import com.koodipardik.nordprice.ui.components.CardHorizontalInset
import com.koodipardik.nordprice.ui.components.HeroPriceCard
import com.koodipardik.nordprice.ui.components.HistoryMinMaxCard
import com.koodipardik.nordprice.ui.components.ScreenTitle
import com.koodipardik.nordprice.ui.chart.ChartHeader
import com.koodipardik.nordprice.ui.components.appCard
import com.koodipardik.nordprice.ui.components.unitLabel
import com.koodipardik.nordprice.ui.TabletSideColumnWidth
import com.koodipardik.nordprice.ui.periodTitle
import com.koodipardik.nordprice.ui.rememberIsTablet
import com.koodipardik.nordprice.ui.shortBarDate
import com.koodipardik.nordprice.ui.rememberContainer
import com.koodipardik.nordprice.ui.rememberSettings
import com.koodipardik.nordprice.ui.theme.AppTheme
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

    val title = stringResource(R.string.title_price_history)
    val retryAndToggle: @Composable (Modifier) -> Unit = { modifier ->
        HistoryChart(
            state = state,
            modifier = modifier,
            onRetry = viewModel::load,
            onToggleChartType = {
                container.settings.setChartType(
                    if (settings.chartType == ChartType.BAR) ChartType.LINE else ChartType.BAR,
                )
            },
        )
    }

    // A tablet gets the iOS wide layout: the cards in a column beside the chart, which needs the
    // room more than the cards do. In portrait the cards end around 40 % of the window while the
    // chart filled the rest — a tall, stretched widget; it is capped at half the height there.
    // Landscape keeps the fill: its window is only 800 dp tall to begin with. Phones never reach
    // this branch; their chart keeps its `weight(1f)` share of the column.
    if (rememberIsTablet()) {
        val configuration = LocalConfiguration.current
        val isPortrait = configuration.screenHeightDp > configuration.screenWidthDp
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = CardHorizontalInset, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.width(TabletSideColumnWidth),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ScreenTitle(title)
                HistoryCards(state, settings, viewModel)
            }
            retryAndToggle(Modifier.weight(1f).fillMaxHeight(if (isPortrait) 0.5f else 1f))
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = CardHorizontalInset),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ScreenTitle(title)
        HistoryCards(state, settings, viewModel)
        Spacer(Modifier.height(12.dp))
        retryAndToggle(Modifier.fillMaxWidth().weight(1f))
        Spacer(Modifier.height(8.dp))
    }
}

/** Average card, cheapest/dearest card and the period control, shared by both layouts. */
@Composable
private fun HistoryCards(
    state: HistoryUiState,
    settings: com.koodipardik.nordprice.settings.Settings,
    viewModel: HistoryViewModel,
) {
    // The three cards used to sit flush: the phone layout spaces only what is *below* this group and
    // the tablet column spaces only the title above it, so nothing ever separated these siblings.
    // 12 dp is the rhythm every other card stack in the app uses (PriceScreen's phone and tablet
    // columns), and these cards carry a 1 dp border, where touching reads as one box.
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AverageCard(
            state = state,
            onBack = viewModel::goBack,
            onForward = viewModel::goForward,
            onPickDate = { viewModel.select(it) },
        )
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
        PeriodPicker(period = state.period, onSelect = viewModel::setPeriod)
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

/**
 * Week / month / year.
 *
 * The Material 3 control defaults to `MaterialTheme` colours, which are the Compose baseline palette
 * and not this app's iOS one — so the unselected segments read as a washed-out grey box that matches
 * nothing on the screen. Every colour below is an [AppTheme] token instead, which puts the control in
 * the same family as the two cards above it: same fill, same hairline, and the selected segment in
 * brand blue with white text, the way the app marks a choice everywhere else.
 *
 * Corner radius 12 dp is deliberately one notch tighter than the cards' 14 dp: the segments sit inside
 * each other, so a shared radius would make the row look like a fourth card rather than a control.
 */
@Composable
private fun PeriodPicker(period: HistoryPeriod, onSelect: (HistoryPeriod) -> Unit) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        HistoryPeriod.ordered.forEachIndexed { index, option ->
            val selected = period == option
            SegmentedButton(
                selected = selected,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(
                    index = index,
                    count = HistoryPeriod.ordered.size,
                    baseShape = RoundedCornerShape(12.dp),
                ),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = AppTheme.brand,
                    activeContentColor = Color.White,
                    inactiveContainerColor = AppTheme.card,
                    inactiveContentColor = AppTheme.secondaryText,
                ),
                border = BorderStroke(1.dp, AppTheme.cardStroke),
                icon = {},
                label = {
                    Text(
                        text = stringResource(option.labelRes),
                        fontSize = 15.sp,
                        // Weight, not just colour, carries the state — the palette alone is too subtle
                        // for three options that look this similar at a glance.
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
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

    val average = state.averageRawMWh?.let(displayPrice)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .appCard(cornerRadius = 14.dp)
            .padding(bottom = 6.dp),
    ) {
        val selectedBar = selected?.let { index -> priced.firstOrNull { it.index == index } }
        ChartHeader(
            title = stringResource(
                if (state.period == HistoryPeriod.YEAR) R.string.title_monthly_average
                else R.string.title_daily_average,
            ),
            selectedValue = selectedBar?.rawPriceMWh?.let {
                "${displayPriceText(it, settings)} ${unitLabel(settings.unit)}"
            },
            selectedLabel = selectedBar?.let { barDate(it.start, state.period) },
            chartType = settings.chartType,
            onToggleChartType = onToggleChartType,
        )

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
                    style = ChartStyles.history(
                        colors = AppTheme,
                        chartType = settings.chartType,
                        colorful = settings.colorfulChart,
                        barCount = count,
                        yLabel = { PriceFormat.axisLabel(it, settings.unit) },
                        xLabel = { index -> axisLabels[index] ?: "" },
                        xTickIndices = { _ -> tickIndices },
                    ),
                    selected = selected,
                    onSelection = { selected = it },
                    averageLine = average,
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
    val locale = com.koodipardik.nordprice.ui.rememberAppLocale()
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

private fun displayPriceText(rawMWh: Double?, settings: com.koodipardik.nordprice.settings.Settings): String {
    if (rawMWh == null) return "---"
    val display = rawMWh / settings.unit.divider * (if (settings.includeTax) settings.taxRate else 1.0)
    return PriceFormat.format(display, settings.unit)
}

private fun displayPrice(rawMWh: Double?, settings: com.koodipardik.nordprice.settings.Settings): String =
    displayPriceText(rawMWh, settings)
