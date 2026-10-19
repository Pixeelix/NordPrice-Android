package com.koodipardik.nordprice.ui.chart

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.koodipardik.nordprice.core.ChartResolution
import com.koodipardik.nordprice.core.ChartType
import com.koodipardik.nordprice.core.PriceLevel
import com.koodipardik.nordprice.ui.theme.AppColors
import com.koodipardik.nordprice.ui.theme.ChartPalette

/**
 * The three chart looks in the app, built from one palette type: the day chart, the history chart
 * and the home-screen widget's chart. They differ in stroke weight, padding and which reference
 * lines they draw — the point of building them here is that the differences are visible side by
 * side instead of buried in two renderers.
 */
object ChartStyles {

    /** Gutter for axis labels, in dp: leading numbers, room above for the "Now" capsule. */
    private val AppInsets = SceneInsets(left = 34f, top = 14f, right = 8f, bottom = 18f)
    private val WidgetInsets = SceneInsets(left = 26f, top = 4f, right = 2f, bottom = 12f)

    fun day(
        colors: AppColors,
        chartType: ChartType,
        colorful: Boolean,
        resolution: ChartResolution,
        yLabel: (Double) -> String,
    ) = ChartStyle(
        chartType = chartType.sceneType(),
        isColorful = colorful,
        insets = AppInsets,
        domain = ::appDomain,
        barWidth = if (resolution == ChartResolution.FIFTEEN_MINUTES) {
            // iOS gives the dense chart hairline bars, and doubles the current one.
            SceneBarWidth.Fixed(normalDp = 2f, currentDp = 4f)
        } else {
            SceneBarWidth.Ratio(0.62f)
        },
        barCornerRadiusDp = 0f,
        lineWidthDp = 2.5f,
        zeroRuleWidthDp = 1.5f,
        dotSizeDp = { count -> levelDotSizeDp(count).toFloat() },
        currentDotSizeDp = 11f,
        dotRingWidthDp = 2f,
        areaTopAlpha = ChartPalette.areaTopAlpha,
        areaBottomAlpha = ChartPalette.areaBottomAlpha,
        pastLineAlpha = ChartPalette.linePastAlpha,
        futureAlpha = ChartPalette.futureBarAlpha,
        unselectedAlpha = ChartPalette.unselectedBarAlpha,
        // The app's axis already floors at zero, so a rule there only earns its keep when
        // negative prices push the floor below it.
        zeroRule = ZeroRule.OnlyWhenNegative,
        showNowRule = colorful,
        // The scrub line only earns its keep on the line chart. Over bars it was a white
        // (textOnBackground) stroke cutting straight through the columns, and the dimming already
        // says which bar is touched: every other bar fades to `unselectedAlpha` while this one stays
        // saturated. A line has no such width to dim, so the rule is what marks the point there.
        // History inherits this through `day(…).copy(…)`, so it follows the same chart-type switch.
        showSelectionRule = chartType == ChartType.LINE,
        nowRuleDash = 3f to 3f,
        nowRuleOffset = { 0f },
        xTickIndices = ::hourTicks,
        yTickCount = 5,
        levelArgb = { it.colorArgb },
        brandArgb = colors.brand.argb(),
        plainLineArgb = colors.cardText.argb(),
        pastBarArgb = colors.pastBar.argb(),
        currentHighlightArgb = colors.currentHighlight.argb(),
        gridXArgb = Gray.alpha(ChartPalette.gridAlpha),
        gridYArgb = Gray.alpha(ChartPalette.gridYAlpha),
        ruleArgb = colors.textOnBackground.argb(),
        nowRuleArgb = colors.textOnBackground.alpha(ChartPalette.nowRuleAlpha),
        dotRingArgb = Color.White.argb(),
        currentDotShadow = true,
        axisLabelArgb = colors.secondaryText.argb(),
        yTickLabel = yLabel,
        xTickLabel = { index, count -> hourOf(index, count) },
    )

    fun history(
        colors: AppColors,
        chartType: ChartType,
        colorful: Boolean,
        barCount: Int,
        yLabel: (Double) -> String,
        xLabel: (Int) -> String,
        xTickIndices: (count: Int) -> List<Int>,
    ) = day(colors, chartType, colorful, ChartResolution.ONE_HOUR, yLabel).copy(
        // History steps by bars, not by hour of the day: the week view labels every day, the month
        // view every week, the year view every other month.
        xTickIndices = xTickIndices,
        barWidth = SceneBarWidth.Ratio(0.7f),
        // More than ~20 bars in a day-width chart and rounded corners turn into mush.
        barCornerRadiusDp = if (barCount > 20) 1.5f else 3f,
        showNowRule = false,
        // History has no "current interval": every bar is a finished period.
        zeroRule = ZeroRule.OnlyWhenNegative,
        yTickLabel = yLabel,
        xTickLabel = { index, _ -> xLabel(index) },
    )

    /**
     * The widget's chart, matching the iOS widget rather than the iOS app: five percent of
     * margin under the axis as well as above, a permanent dashed zero line, thinner strokes,
     * smaller dots, and always coloured by price level — the iOS widget never reads the
     * "colourful chart" switch, so neither does this one.
     */
    fun widget(
        colors: AppColors,
        chartType: ChartType,
        axisLabels: Boolean = false,
        yLabel: (Double) -> String,
    ) = ChartStyle(
        chartType = chartType.sceneType(),
        isColorful = true,
        // Without burnt-in labels the curve owns the whole surface, which is what the widget wants:
        // Glance scales the bitmap into the box it is given.
        insets = if (axisLabels) WidgetInsets else SceneInsets(1f, 2f, 1f, 2f),
        domain = ::widgetDomain,
        barWidth = SceneBarWidth.Ratio(0.75f),
        barCornerRadiusDp = 1f,
        lineWidthDp = 1.5f,
        zeroRuleWidthDp = 0.5f,
        dotSizeDp = { count -> if (count > 48) 2.5f else 4f },
        currentDotSizeDp = 7f,
        dotRingWidthDp = 1.5f,
        areaTopAlpha = ChartPalette.areaTopAlpha,
        areaBottomAlpha = ChartPalette.areaBottomAlpha,
        pastLineAlpha = ChartPalette.linePastAlpha,
        futureAlpha = ChartPalette.futureBarAlpha,
        unselectedAlpha = 1f,
        // No zero line here, unlike the iOS widget: the image is drawn taller than Glance's
        // LocalSize reports, so a hairline rule arrives on screen several times too thick. The bars
        // already share a baseline, which reads as the axis, and the dashed "now" rule survives the
        // stretch as dots.
        zeroRule = ZeroRule.Never,
        showNowRule = true,
        showSelectionRule = false,
        nowRuleDash = 2f to 2f,
        nowRuleOffset = ::centerOfSlot,
        xTickIndices = ::hourTicks,
        yTickCount = 3,
        levelArgb = { it.colorArgb },
        brandArgb = colors.brand.argb(),
        plainLineArgb = colors.cardText.argb(),
        pastBarArgb = colors.pastBar.argb(),
        currentHighlightArgb = colors.currentHighlight.argb(),
        gridXArgb = Color.Transparent.argb(),
        gridYArgb = Color.Transparent.argb(),
        ruleArgb = colors.secondaryText.argb(),
        nowRuleArgb = colors.textOnBackground.alpha(ChartPalette.nowRuleAlpha),
        dotRingArgb = Color.White.argb(),
        currentDotShadow = false,
        axisLabelArgb = colors.secondaryText.argb(),
        yTickLabel = yLabel,
        xTickLabel = { index, count -> hourOf(index, count) },
    )

    /** Ticks at 00, 06, 12 and 18 o'clock, whatever the interval. */
    private fun hourTicks(count: Int): List<Int> {
        if (count <= 1) return listOf(0)
        return listOf(0, 6, 12, 18).map { (it.toLong() * count / 24L).toInt().coerceIn(0, count - 1) }
    }

    private fun hourOf(index: Int, count: Int): String =
        "%02d".format((index * 24.0 / count.coerceAtLeast(1)).toInt().coerceIn(0, 23))
}

/** Dot size shrinks as intervals get denser, so 15-minute data still reads as a line. */
internal fun levelDotSizeDp(count: Int): Int = when {
    count > 48 -> 4
    count > 20 -> 6
    else -> 9
}

private fun ChartType.sceneType() = when (this) {
    ChartType.BAR -> SceneChartType.BAR
    ChartType.LINE -> SceneChartType.LINE
}

private val PriceLevel.colorArgb: Long get() = colorHex

private fun Color.argb(): Long = toArgb().toLong() and 0xFFFFFFFFL

private fun Color.alpha(factor: Float): Long {
    val base = toArgb().toLong() and 0xFFFFFFFFL
    return fade(base, factor)
}

private val Gray = Color(0xFF808080)
