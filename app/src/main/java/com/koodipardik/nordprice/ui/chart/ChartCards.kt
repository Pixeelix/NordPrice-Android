package com.koodipardik.nordprice.ui.chart

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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.koodipardik.nordprice.R
import com.koodipardik.nordprice.core.ChartResolution
import com.koodipardik.nordprice.core.ChartType
import com.koodipardik.nordprice.core.PriceFormat
import com.koodipardik.nordprice.core.PriceLevel
import com.koodipardik.nordprice.core.PriceUnit
import com.koodipardik.nordprice.ui.components.appCard
import com.koodipardik.nordprice.ui.components.isLargeText
import com.koodipardik.nordprice.ui.components.unitLabel
import com.koodipardik.nordprice.ui.theme.AppTheme

/**
 * The chart card of the today / tomorrow screens: a header that shows either the day or the value
 * under the finger, the chart, and the four-level legend. Mirrors the iOS `ChartView`.
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
    minHeight: Dp = 220.dp,
) {
    var selected by remember { mutableStateOf<Int?>(null) }
    val colors = AppTheme
    val selectedEntry = selected?.let { index -> values.firstOrNull { it.index == index } }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .appCard(cornerRadius = 14.dp)
            .padding(bottom = 6.dp),
    ) {
        ChartHeader(
            title = title,
            selectedValue = selectedEntry?.let { "${PriceFormat.format(it.price, unit)} ${unitLabel(unit)}" },
            selectedLabel = selectedEntry?.label,
            chartType = chartType,
            onToggleChartType = onToggleChartType,
        )

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
                    style = ChartStyles.day(colors, chartType, colorful, resolution) {
                        PriceFormat.axisLabel(it, unit)
                    },
                    selected = selected,
                    onSelection = { selected = it },
                    nowBadge = stringResource(R.string.label_now),
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

/** Title or selected readout on the left, the bar ↔ line toggle on the right. */
@Composable
internal fun ChartHeader(
    title: String?,
    selectedValue: String?,
    selectedLabel: String?,
    chartType: ChartType,
    onToggleChartType: () -> Unit,
) {
    val color = AppTheme.cardText
    val toggleLabel = stringResource(
        if (chartType == ChartType.BAR) R.string.a11y_show_line_chart else R.string.a11y_show_bar_chart,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selectedValue != null && selectedLabel != null) {
            Text(
                text = selectedValue,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = color,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            Text(
                text = selectedLabel,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = color,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.End,
                maxLines = 1,
            )
        } else {
            Text(
                text = title.orEmpty(),
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = color,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
        }
        IconButton(
            onClick = onToggleChartType,
            modifier = Modifier
                .size(44.dp)
                .semantics { contentDescription = toggleLabel },
        ) {
            Icon(
                imageVector = if (chartType == ChartType.BAR) Icons.Filled.BarChart else Icons.Filled.ShowChart,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(22.dp),
            )
        }
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
            levels.forEach { level -> LegendItem(level, Modifier.padding(horizontal = 6.dp)) }
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
