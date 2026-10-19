package com.koodipardik.nordprice.ui.chart

import com.koodipardik.nordprice.core.PriceLevel
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * The chart's geometry and colours, computed once, in plain floats and ARGB longs.
 *
 * Both renderers — the live Compose chart in the app and the bitmap the home-screen widget draws
 * — consume the [ChartScene] produced here. That split exists because of a bug the iOS app
 * already hit: its widget and its app each kept their own copy of the price rules, the two drifted
 * apart, and the cache silently stopped working. Geometry is the same kind of rule, so it lives in
 * one place and each platform only translates drawing primitives.
 *
 * Where the iOS widget genuinely differs from the iOS app (a five-percent margin under the axis,
 * a permanent zero line, thinner strokes), the difference is a [ChartStyle] value rather than a
 * second implementation — see [AppChartStyle] and [WidgetChartStyle].
 */
object ChartSceneBuilder {

    fun build(
        values: List<ChartValue>,
        style: ChartStyle,
        selected: Int?,
        /** History draws the period average as a dashed guide; the day chart passes null. */
        averageLine: Double? = null,
        widthPx: Float,
        heightPx: Float,
        density: Float,
    ): ChartScene {
        val inset = style.insets
        val plot = SceneRect(
            left = inset.left * density,
            top = inset.top * density,
            right = widthPx - inset.right * density,
            bottom = heightPx - inset.bottom * density,
        ).let {
            // A surface narrower than the gutters still has to draw something.
            if (it.right <= it.left || it.bottom <= it.top) SceneRect(0f, 0f, max(widthPx, 1f), max(heightPx, 1f)) else it
        }

        val prices = values.map { it.price }
        val domain = style.domain(prices.minOrNull() ?: 0.0, prices.maxOrNull() ?: 1.0)
        val count = values.size
        val xStart = -0.5
        val xEnd = (count - 1).coerceAtLeast(0) + 0.5

        val x = SceneMapper { index -> plot.left + ((index - xStart) / (xEnd - xStart)).toFloat() * plot.width }
        val y = SceneMapper { value -> plot.top + ((domain.endInclusive - value) / (domain.endInclusive - domain.start)).toFloat() * plot.height }

        val ops = mutableListOf<SceneOp>()
        val yTicks = niceTickValues(domain.start, domain.endInclusive, style.yTickCount)
            .filter { it in domain.start..domain.endInclusive }
            .map { SceneTick(it, y(it), style.yTickLabel(it)) }

        yTicks.forEach { tick ->
            ops += SceneOp.GridLine(false, tick.position, plot.left, plot.right, style.gridYArgb, 1f)
        }
        val xTicks = style.xTickIndices(count)
        xTicks.forEach { index ->
            ops += SceneOp.GridLine(true, x(index.toDouble()), plot.top, plot.bottom, style.gridXArgb, 1f)
        }

        val nowIndex = values.indexOfFirst { it.isCurrent }.takeIf { it >= 0 }
        val slotWidth = if (count > 1) abs(x(1.0) - x(0.0)) else plot.width

        when (style.chartType) {
            SceneChartType.BAR -> ops += bars(values, style, x, y, plot, slotWidth, selected, nowIndex, density)
            SceneChartType.LINE -> ops += line(values, style, x, y, plot, domain, selected, nowIndex, density)
        }

        if (averageLine != null) {
            val y = y(averageLine)
            if (y >= plot.top && y <= plot.bottom) {
                ops += SceneOp.Rule(
                    vertical = false, at = y, from = plot.left, to = plot.right,
                    colorArgb = fade(style.ruleArgb, 0.4f), thickness = 1f,
                    dashOn = 4f * density, dashOff = 3f * density,
                )
            }
        }
        if (style.zeroRule == ZeroRule.Always || (style.zeroRule == ZeroRule.OnlyWhenNegative && domain.start < 0)) {
            ops += SceneOp.Rule(false, y(0.0), plot.left, plot.right, style.ruleArgb, style.zeroRuleWidthDp * density, 0f, 0f)
        }
        if (nowIndex != null && style.showNowRule) {
            val barCenter = x(nowIndex.toDouble()) + if (style.chartType == SceneChartType.BAR) style.nowRuleOffset(slotWidth) else 0f
            ops += SceneOp.Rule(
                vertical = true, at = barCenter, from = plot.top, to = plot.bottom,
                colorArgb = style.nowRuleArgb, thickness = 1f, dashOn = style.nowRuleDash.first, dashOff = style.nowRuleDash.second,
            )
        }
        if (selected != null && style.showSelectionRule) {
            val dimmed = values.firstOrNull { it.index == selected }?.let {
                if (style.isColorful) style.ruleArgb else style.currentHighlightArgb
            } ?: style.ruleArgb
            ops += SceneOp.Rule(
                vertical = true, at = x(selected.toDouble()), from = plot.top, to = plot.bottom,
                colorArgb = dimmed, thickness = 1.5f * density, dashOn = 0f, dashOff = 0f,
            )
        }

        val nowBadge = if (nowIndex != null && style.showNowRule) {
            val entry = values[nowIndex]
            ScenePoint(
                x = x(nowIndex.toDouble()),
                y = y(max(entry.price, min(0.0, domain.start))),
            )
        } else {
            null
        }

        return ChartScene(
            plot = plot,
            ops = ops,
            yTicks = yTicks,
            xTicks = xTicks.map { SceneTick(it.toDouble(), x(it.toDouble()), style.xTickLabel(it, count)) },
            nowBadge = nowBadge,
            x = x,
            y = y,
            domain = domain,
            indexAt = { px -> xStart + (px - plot.left) / plot.width * (xEnd - xStart) },
        )
    }

    private fun bars(
        values: List<ChartValue>,
        style: ChartStyle,
        x: SceneMapper,
        y: SceneMapper,
        plot: SceneRect,
        slotWidth: Float,
        selected: Int?,
        nowIndex: Int?,
        density: Float,
    ): List<SceneOp> {
        val zeroY = y(0.0).coerceIn(plot.top, plot.bottom)
        return values.map { entry ->
            val width = when (val spec = style.barWidth) {
                is SceneBarWidth.Fixed -> (if (entry.isCurrent) spec.currentDp else spec.normalDp) * density
                is SceneBarWidth.Ratio -> slotWidth * spec.ratio
            }
            SceneOp.Bar(
                centerX = x(entry.index.toDouble()),
                halfWidth = width / 2f,
                fromY = zeroY,
                toY = y(entry.price),
                colorArgb = barColor(entry, style, selected, nowIndex),
                cornerRadius = style.barCornerRadiusDp * density,
            )
        }
    }

    private fun barColor(entry: ChartValue, style: ChartStyle, selected: Int?, nowIndex: Int?): Long {
        val level = levelArgb(entry, style)
        if (!style.isColorful) {
            val base = if (entry.isCurrent) style.currentHighlightArgb else style.plainLineArgb
            return if (selected != null && selected != entry.index) fade(base, style.unselectedAlpha) else base
        }
        if (selected != null) {
            return if (selected == entry.index) level else fade(level, style.unselectedAlpha)
        }
        if (nowIndex == null) return level
        return when {
            entry.index == nowIndex -> level
            entry.index < nowIndex -> style.pastBarArgb
            else -> fade(level, style.futureAlpha)
        }
    }

    private fun line(
        values: List<ChartValue>,
        style: ChartStyle,
        x: SceneMapper,
        y: SceneMapper,
        plot: SceneRect,
        domain: ClosedFloatingPointRange<Double>,
        selected: Int?,
        nowIndex: Int?,
        density: Float,
    ): List<SceneOp> {
        if (values.isEmpty()) return emptyList()
        val spline = MonotoneSpline.of(values, x, y)
        val lastIndex = values.size - 1
        val baseline = y(0.0).coerceIn(plot.top, plot.bottom)
        val line = if (style.isColorful) style.brandArgb else style.plainLineArgb
        val stroke = style.lineWidthDp * density
        val ops = mutableListOf<SceneOp>()

        ops += SceneOp.Area(
            points = sampledPath(spline, 0, lastIndex),
            baselineY = baseline,
            topColorArgb = fade(line, style.areaTopAlpha),
            bottomColorArgb = fade(line, style.areaBottomAlpha),
        )

        if (style.isColorful && nowIndex != null && nowIndex > 0 && nowIndex < lastIndex) {
            ops += SceneOp.PolyLine(sampledPath(spline, 0, nowIndex), fade(line, style.pastLineAlpha), stroke)
            ops += SceneOp.PolyLine(sampledPath(spline, nowIndex, lastIndex), line, stroke)
        } else {
            ops += SceneOp.PolyLine(sampledPath(spline, 0, lastIndex), line, stroke)
        }

        val dotRadius = style.dotSizeDp(values.size) / 2f * density
        values.forEach { entry ->
            val color = levelArgb(entry, style)
            val past = nowIndex != null && entry.index < nowIndex
            val center = ScenePoint(x(entry.index.toDouble()), y(entry.price))
            ops += when {
                entry.isCurrent -> SceneOp.Dot(
                    center, style.currentDotSizeDp / 2f * density, color, style.dotRingArgb,
                    style.dotRingWidthDp * density, style.currentDotShadow,
                )
                selected != null && selected != entry.index -> SceneOp.Dot(center, dotRadius, fade(color, style.unselectedAlpha), null, 0f, false)
                else -> SceneOp.Dot(
                    center, dotRadius,
                    if (past && style.isColorful) fade(color, style.pastLineAlpha) else color,
                    null, 0f, false,
                )
            }
        }
        return ops
    }

    /** The curve as a polyline so both renderers draw exactly the same shape. */
    private fun sampledPath(spline: MonotoneSpline, from: Int, to: Int): List<ScenePoint> {
        val samples = ArrayList<ScenePoint>()
        spline.sample(from, to, perSegment = 10, into = samples)
        return samples
    }

    private fun levelArgb(entry: ChartValue, style: ChartStyle): Long =
        entry.level?.let { style.levelArgb(it) } ?: style.brandArgb
}

/** ARGB with an alpha multiplier applied, staying in the 0xAARRGGBB shape. */
internal fun fade(argb: Long, alpha: Float): Long {
    val a = ((argb ushr 24) * alpha.coerceIn(0f, 1f)).toLong().coerceIn(0, 255)
    return (a shl 24) or (argb and 0x00FFFFFFL)
}

data class SceneTick(val value: Double, val position: Float, val label: String)

data class SceneRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top
}

data class ScenePoint(val x: Float, val y: Float)

fun interface SceneMapper {
    operator fun invoke(value: Double): Float
}

enum class SceneChartType { BAR, LINE }

enum class ZeroRule { Always, OnlyWhenNegative, Never }

sealed interface SceneBarWidth {
    /** Fixed width in dp — the dense quarter-hour chart. */
    data class Fixed(val normalDp: Float, val currentDp: Float) : SceneBarWidth

    /** Fraction of the slot — the hourly chart and history. */
    data class Ratio(val ratio: Float) : SceneBarWidth
}

sealed interface SceneOp {
    data class GridLine(val vertical: Boolean, val at: Float, val from: Float, val to: Float, val colorArgb: Long, val thickness: Float) : SceneOp
    data class Bar(val centerX: Float, val halfWidth: Float, val fromY: Float, val toY: Float, val colorArgb: Long, val cornerRadius: Float) : SceneOp
    data class PolyLine(val points: List<ScenePoint>, val colorArgb: Long, val thickness: Float) : SceneOp
    data class Area(val points: List<ScenePoint>, val baselineY: Float, val topColorArgb: Long, val bottomColorArgb: Long) : SceneOp
    data class Dot(
        val center: ScenePoint,
        val radius: Float,
        val colorArgb: Long,
        val ringColorArgb: Long?,
        val ringWidth: Float,
        /** The app lifts its current dot off the chart; the widget keeps it flat. */
        val shadow: Boolean,
    ) : SceneOp
    data class Rule(val vertical: Boolean, val at: Float, val from: Float, val to: Float, val colorArgb: Long, val thickness: Float, val dashOn: Float, val dashOff: Float) : SceneOp
}

/** What a renderer needs to draw, plus the labels the host lays out as real text. */
data class ChartScene(
    val plot: SceneRect,
    val ops: List<SceneOp>,
    val yTicks: List<SceneTick>,
    val xTicks: List<SceneTick>,
    val nowBadge: ScenePoint?,
    val x: SceneMapper,
    val y: SceneMapper,
    val domain: ClosedFloatingPointRange<Double>,
    /** Pixel position back to the nearest data index, for drag-to-inspect. */
    val indexAt: (Float) -> Double,
)

/**
 * Everything that makes the app's chart and the widget's chart look different, as data.
 * Colours are ARGB longs so the scene stays free of any UI toolkit.
 */
data class ChartStyle(
    val chartType: SceneChartType,
    val isColorful: Boolean,
    val insets: SceneInsets,
    val domain: (min: Double, max: Double) -> ClosedFloatingPointRange<Double>,
    val barWidth: SceneBarWidth,
    val barCornerRadiusDp: Float,
    val lineWidthDp: Float,
    val zeroRuleWidthDp: Float,
    val dotSizeDp: (count: Int) -> Float,
    val currentDotSizeDp: Float,
    val dotRingWidthDp: Float,
    val areaTopAlpha: Float,
    val areaBottomAlpha: Float,
    val pastLineAlpha: Float,
    val futureAlpha: Float,
    val unselectedAlpha: Float,
    val zeroRule: ZeroRule,
    val showNowRule: Boolean,
    val showSelectionRule: Boolean,
    val nowRuleDash: Pair<Float, Float>,
    val nowRuleOffset: (slotWidth: Float) -> Float,
    val xTickIndices: (count: Int) -> List<Int>,
    val yTickCount: Int,
    val levelArgb: (PriceLevel) -> Long,
    val brandArgb: Long,
    val plainLineArgb: Long,
    val pastBarArgb: Long,
    val currentHighlightArgb: Long,
    val gridXArgb: Long,
    val gridYArgb: Long,
    val ruleArgb: Long,
    val nowRuleArgb: Long,
    val dotRingArgb: Long,
    val currentDotShadow: Boolean,
    val axisLabelArgb: Long,
    /** Label text is resolved in the scene, so every renderer prints the same thing. */
    val yTickLabel: (Double) -> String,
    val xTickLabel: (index: Int, count: Int) -> String,
)

data class SceneInsets(val left: Float, val top: Float, val right: Float, val bottom: Float)

/**
 * The app's axis: the plot floor is the lowest price or zero, and the ceiling gets a tenth of the
 * range so the tallest bar never touches the top. Nothing is padded below.
 */
internal fun appDomain(minPrice: Double, maxPrice: Double): ClosedFloatingPointRange<Double> {
    val low = min(minPrice, 0.0)
    val high = max(maxPrice, 0.01)
    return low..(high + (high - low) * 0.1)
}

/**
 * The widget's axis, as the iOS widget computes it: five percent of breathing room above *and*
 * below, a flat day gets a symmetric window instead of a zero-height one, and the domain always
 * includes zero so the baseline is on screen.
 */
internal fun widgetDomain(minPrice: Double, maxPrice: Double): ClosedFloatingPointRange<Double> {
    var low = minPrice
    var high = maxPrice
    if (low == high) {
        if (low == 0.0) {
            low = -1.0
            high = 1.0
        } else {
            val pad = abs(low) * 0.1
            low -= pad
            high += pad
        }
    } else {
        val pad = max((high - low) * 0.05, 0.01)
        low -= pad
        high += pad
    }
    return min(low, 0.0)..max(high, 0.0)
}

/** The iOS widget draws its "now" rule at the middle of the bar's slot, not its left edge. */
internal fun centerOfSlot(slotWidth: Float): Float = slotWidth * 0.125f

