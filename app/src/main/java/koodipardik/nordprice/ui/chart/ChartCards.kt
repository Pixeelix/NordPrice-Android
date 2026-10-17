package koodipardik.nordprice.ui.chart

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import koodipardik.nordprice.R
import koodipardik.nordprice.core.ChartResolution
import koodipardik.nordprice.core.ChartType
import koodipardik.nordprice.core.PriceFormat
import koodipardik.nordprice.core.PriceLevel
import koodipardik.nordprice.core.PriceUnit
import koodipardik.nordprice.ui.components.appCard
import koodipardik.nordprice.ui.components.isLargeText
import koodipardik.nordprice.ui.components.unitLabel
import koodipardik.nordprice.ui.theme.AppTheme

/**
 * The chart card of the today / tomorrow screens: a header that shows either the day or the
 * value under the finger, the chart itself, and the four-level legend.
 * Mirrors the iOS `ChartView`.
 */
@Composable
fun PriceChartCard(
    values: List<ChartValue>,
    chartType: ChartType,
    colorful: Boolean,
    unit: PriceUnit,
    resolution: ChartResolution,
    loading: Boolean,
    title: String,
    onToggleChartType: () -> Unit,
    modifier: Modifier = Modifier,
    minHeight: androidx.compose.ui.unit.Dp = 220.dp,
) {
    var selected by remember { mutableStateOf<Int?>(null) }
    val colors = AppTheme
    val selectedEntry = selected?.let { index -> values.firstOrNull { it.index == index } }
    val nowIndex = values.indexOfFirst { it.isCurrent }.takeIf { it >= 0 }

    val prices = values.map { it.price }
    val rawMin = prices.minOrNull() ?: 0.0
    val rawMax = prices.maxOrNull() ?: 1.0
    val yMin = minOf(rawMin, 0.0)
    val yMax = maxOf(rawMax, 0.01) + (maxOf(rawMax, 0.01) - yMin) * 0.1

    val count = values.size
    // Bars live on integer slots; half a slot of padding at each end keeps the first and last
    // bar from being cut in half by the plot edge (Swift Charts gets this from its band scale).
    val xStart = -0.5
    val xEnd = (count - 1) + 0.5

    val xTicks = remember(count) {
        if (count <= 1) listOf(0)
        else listOf(0, 6, 12, 18).map { ((it.toDouble() * count / 24.0)).toInt().coerceIn(0, count - 1) }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .appCard(cornerRadius = 14.dp)
            .padding(bottom = 6.dp),
    ) {
        // ---- Header ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selectedEntry != null) {
                Text(
                    text = "${PriceFormat.format(selectedEntry.price, unit)} ${unitLabel(unit)}",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.cardText,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                Text(
                    text = selectedEntry.label,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.cardText,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                    maxLines = 1,
                )
            } else {
                Text(
                    text = title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.cardText,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
            }
            ChartTypeToggle(chartType = chartType, onToggle = onToggleChartType)
        }

        // ---- Chart ----
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(start = 10.dp, end = 20.dp, bottom = 4.dp),
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = colors.brand,
                )
            } else {
                ChartCanvas(
                    values = values,
                    chartType = chartType,
                    colorful = colorful,
                    xStart = xStart,
                    xEnd = xEnd,
                    yMin = yMin,
                    yMax = yMax,
                    xTicks = xTicks,
                    xTickLabel = { tick ->
                        // The day chart labels whole hours: "00", "06", "12", "18".
                        "%02d".format((tick * 24.0 / count).toInt().coerceIn(0, 23))
                    },
                    yLabel = { PriceFormat.axisLabel(it, unit) },
                    selected = selected,
                    onSelection = { selected = it },
                    showZeroRule = rawMin < 0,
                    nowIndex = nowIndex,
                    nowBadge = stringResource(R.string.label_now),
                    // iOS gives the 15-minute chart hairline bars (2 dp, 4 dp for the current
                    // interval) and lets the hourly one take its share of the slot.
                    barWidth = if (resolution == ChartResolution.FIFTEEN_MINUTES) {
                        BarWidthSpec.Fixed(normal = 2.dp, current = 4.dp)
                    } else {
                        BarWidthSpec.Ratio(ratio = 0.62f, corner = 0.dp)
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        if (colorful) {
            LevelLegend(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
        } else {
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun ChartTypeToggle(chartType: ChartType, onToggle: () -> Unit) {
    val description = stringResource(
        if (chartType == ChartType.BAR) R.string.a11y_show_line_chart else R.string.a11y_show_bar_chart,
    )
    IconButton(
        onClick = onToggle,
        modifier = Modifier.size(44.dp).semantics { contentDescription = description },
    ) {
        Icon(
            imageVector = if (chartType == ChartType.BAR) Icons.Filled.BarChart else Icons.Filled.ShowChart,
            contentDescription = null,
            tint = AppTheme.cardText,
            modifier = Modifier.size(22.dp),
        )
    }
}

/** One-line legend naming the four price levels; wraps to 2×2 at large text sizes. */
@Composable
fun LevelLegend(modifier: Modifier = Modifier) {
    val levels = PriceLevel.entries
    if (isLargeText()) {
        Column(modifier.fillMaxWidth()) {
            levels.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth()) {
                    row.forEach { LegendItem(it, Modifier.weight(1f)) }
                }
            }
        }
    } else {
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            levels.forEach { level ->
                LegendItem(level, Modifier.padding(horizontal = 6.dp))
            }
        }
    }
}

@Composable
private fun LegendItem(level: PriceLevel, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(7.dp)
                .clip(RoundedCornerShape(50))
                .background(Color(level.colorHex)),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = stringResource(level.labelRes),
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = AppTheme.secondaryText,
            maxLines = 1,
        )
    }
}
