package koodipardik.nordprice.ui.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import koodipardik.nordprice.core.ChartType
import koodipardik.nordprice.core.PriceLevel
import koodipardik.nordprice.ui.theme.AppTheme
import koodipardik.nordprice.ui.theme.ChartPalette
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/** How wide a bar is: fixed for the dense 15-minute chart, proportional otherwise. */
internal sealed interface BarWidthSpec {
    /** 2 dp normally, 4 dp for the current interval — the iOS 15-minute day chart. */
    data class Fixed(val normal: Dp, val current: Dp) : BarWidthSpec

    /** Fraction of the slot, with rounded corners — the hourly day chart and history. */
    data class Ratio(val ratio: Float, val corner: Dp) : BarWidthSpec
}

/** The drawing area, inside the axis-label gutters. */
private class Plot(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val xStart: Double,
    val xEnd: Double,
    val yMin: Double,
    val yMax: Double,
) {
    val width get() = max(right - left, 1f)
    val height get() = max(bottom - top, 1f)

    fun x(index: Double): Float = left + ((index - xStart) / (xEnd - xStart)).toFloat() * width
    fun y(value: Double): Float = top + ((yMax - value) / (yMax - yMin)).toFloat() * height

    /** Pixel x back to the nearest data index. */
    fun indexAt(px: Float): Double = xStart + (px - left) / width * (xEnd - xStart)
}

private val Insets = ChartCanvasInsets()

private class ChartCanvasInsets {
    val left = 34.dp
    val top = 14.dp      // room for the "Now" capsule above the current bar
    val right = 8.dp
    val bottom = 18.dp   // x axis labels
}

/**
 * The price chart, drawn by hand.
 *
 * iOS uses Swift Charts, which has no Android equivalent with the same marks, so this reproduces
 * its behaviour directly on a Canvas: the plot rectangle, nice-number gridlines, bars coloured by
 * price level, the dimmed elapsed part of the day, the dashed "now" rule with its capsule, the
 * zero rule when prices go negative, and drag-to-inspect with a selection line.
 *
 * Grid, bars, curves, dots and rules are canvas drawing; axis labels and the "Now" capsule are
 * real Compose text positioned on top, so they follow the user's font scale instead of being
 * painted at a fixed pixel size.
 */
@Composable
internal fun ChartCanvas(
    values: List<ChartValue>,
    chartType: ChartType,
    colorful: Boolean,
    /** Domain of the x axis; the day chart starts one slot left of the first bar, as iOS does. */
    xStart: Double,
    xEnd: Double,
    yMin: Double,
    yMax: Double,
    xTicks: List<Int>,
    xTickLabel: (Int) -> String,
    yLabel: (Double) -> String,
    selected: Int?,
    onSelection: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    averageRule: Double? = null,
    showZeroRule: Boolean = false,
    nowIndex: Int? = null,
    nowBadge: String? = null,
    barWidth: BarWidthSpec = BarWidthSpec.Ratio(0.62f, 0.dp),
    yTickCount: Int = 5,
    axisFontSize: TextUnit = 11.sp,
) {
    val colors = AppTheme
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val reportSelection = rememberUpdatedState(onSelection)

    BoxWithConstraints(modifier) {
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val plot = remember(widthPx, heightPx, xStart, xEnd, yMin, yMax) {
            with(density) {
                Plot(
                    left = Insets.left.toPx(),
                    top = Insets.top.toPx(),
                    right = widthPx - Insets.right.toPx(),
                    bottom = heightPx - Insets.bottom.toPx(),
                    xStart = xStart,
                    xEnd = max(xEnd, xStart + 1),
                    yMin = yMin,
                    yMax = max(yMax, yMin + 0.0001),
                )
            }
        }
        val yTicks = remember(yMin, yMax, yTickCount) { niceTickValues(yMin, yMax, yTickCount) }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(values.size, plot) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        var lastIndex: Int? = null
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            if (!change.pressed) break
                            val index = plot.indexAt(change.position.x)
                                .roundToInt()
                                .coerceIn(0, max(values.size - 1, 0))
                            if (index < values.size && index != lastIndex) {
                                lastIndex = index
                                reportSelection.value(index)
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            change.consume()
                        }
                        reportSelection.value(null)
                    }
                },
        ) {
            if (values.isEmpty()) return@Canvas
            drawGrid(plot, yTicks, xTicks)
            when (chartType) {
                ChartType.BAR -> drawBars(plot, values, colorful, barWidth, selected, nowIndex, colors)
                ChartType.LINE -> drawLineChart(plot, values, colorful, selected, colors)
            }
            drawRules(plot, yTicks, values, selected, averageRule, showZeroRule, nowIndex, colorful, colors)
        }

        // ---- Axis labels, as text so they honour the user's font scale ----
        yTicks.forEach { tick ->
            val y = (plot.y(tick) / density.density - 7f).coerceAtLeast(0f)
            Text(
                text = yLabel(tick),
                style = TextStyle(fontSize = axisFontSize, color = colors.secondaryText),
                textAlign = TextAlign.End,
                maxLines = 1,
                modifier = Modifier
                    .offset(y = y.dp)
                    .width(Insets.left - 4.dp),
            )
        }
        xTicks.forEach { tick ->
            if (tick !in values.indices) return@forEach
            val x = plot.x(tick.toDouble()) / density.density - 15f
            Text(
                text = xTickLabel(tick),
                style = TextStyle(
                    fontSize = axisFontSize,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.secondaryText,
                ),
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier
                    .offset(x = x.coerceAtLeast(0f).dp, y = maxHeight - Insets.bottom)
                    .width(30.dp),
            )
        }

        if (colorful && nowBadge != null && nowIndex != null && nowIndex in values.indices) {
            val entry = values[nowIndex]
            NowBadge(
                text = nowBadge,
                level = entry.level,
                x = (plot.x(nowIndex.toDouble()) / density.density - 16f).coerceIn(0f, maxWidth.value - 32f),
                y = (plot.y(max(entry.price, 0.0)) / density.density - 20f).coerceAtLeast(0f),
            )
        }
    }
}

/** Small capsule over the current bar, in the bar's own level colour. */
@Composable
private fun NowBadge(text: String, level: PriceLevel?, x: Float, y: Float) {
    Box(
        modifier = Modifier
            .offset(x = x.dp, y = y.dp)
            .clip(RoundedCornerShape(50))
            .background(Color(level?.colorHex ?: AppTheme.brand.toArgb().toLong()))
            .height(14.dp)
            .width(32.dp),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        Text(
            text = text,
            style = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White),
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

private fun DrawScope.drawGrid(plot: Plot, yTicks: List<Double>, xTicks: List<Int>) {
    val yColor = Color.Gray.copy(alpha = ChartPalette.gridYAlpha)
    yTicks.forEach { tick ->
        val y = plot.y(tick)
        if (y >= plot.top - 1 && y <= plot.bottom + 1) {
            drawLine(yColor, Offset(plot.left, y), Offset(plot.right, y), strokeWidth = 1f)
        }
    }
    val xColor = Color.Gray.copy(alpha = ChartPalette.gridAlpha)
    xTicks.forEach { tick ->
        val x = plot.x(tick.toDouble())
        if (x >= plot.left - 1 && x <= plot.right + 1) {
            drawLine(xColor, Offset(x, plot.top), Offset(x, plot.bottom), strokeWidth = 1f)
        }
    }
}

private fun DrawScope.drawBars(
    plot: Plot,
    values: List<ChartValue>,
    colorful: Boolean,
    barWidth: BarWidthSpec,
    selected: Int?,
    nowIndex: Int?,
    colors: koodipardik.nordprice.ui.theme.AppColors,
) {
    val zeroY = plot.y(0.0).coerceIn(plot.top, plot.bottom)
    val slot = if (values.size > 1) abs(plot.x(1.0) - plot.x(0.0)) else plot.width

    values.forEach { entry ->
        val x = plot.x(entry.index.toDouble())
        val y = plot.y(entry.price)
        val height = abs(zeroY - y)
        val top = minOf(y, zeroY)

        val (width, corner) = when (barWidth) {
            is BarWidthSpec.Fixed ->
                dp(if (entry.isCurrent) barWidth.current else barWidth.normal) to 0f
            is BarWidthSpec.Ratio ->
                slot * barWidth.ratio to dp(barWidth.corner)
        }
        val size = Size(width, height)
        val color = barColor(entry, colorful, selected, nowIndex, colors)

        if (corner > 0f) {
            drawRoundRect(
                color = color,
                topLeft = Offset(x - width / 2f, top),
                size = size,
                cornerRadius = CornerRadius(corner, corner),
            )
        } else {
            drawRect(
                color = color,
                topLeft = Offset(x - width / 2f, top),
                size = size,
            )
        }
    }
}

private fun barColor(
    entry: ChartValue,
    colorful: Boolean,
    selected: Int?,
    nowIndex: Int?,
    colors: koodipardik.nordprice.ui.theme.AppColors,
): Color {
    if (!colorful) {
        // The original single-colour chart: card text colour, current interval highlighted.
        val base = if (entry.isCurrent) colors.currentHighlight else colors.cardText
        return if (selected != null && selected != entry.index) {
            base.copy(alpha = ChartPalette.unselectedBarAlpha)
        } else {
            base
        }
    }

    val levelColor = entry.level?.let { Color(it.colorHex) } ?: colors.brand
    if (selected != null) {
        return if (selected == entry.index) levelColor
        else levelColor.copy(alpha = ChartPalette.unselectedBarAlpha)
    }
    if (nowIndex == null) return levelColor
    return when {
        entry.index == nowIndex -> levelColor
        entry.index < nowIndex -> colors.pastBar
        else -> levelColor.copy(alpha = ChartPalette.futureBarAlpha)
    }
}

private fun DrawScope.drawLineChart(
    plot: Plot,
    values: List<ChartValue>,
    colorful: Boolean,
    selected: Int?,
    colors: koodipardik.nordprice.ui.theme.AppColors,
) {
    val spline = MonotoneSpline.of(values, { plot.x(it) }, { plot.y(it) })
    val lastIndex = values.size - 1
    val currentIndex = values.indexOfFirst { it.isCurrent }.takeIf { it >= 0 }
    val baseline = plot.y(0.0).coerceIn(plot.top, plot.bottom)
    val lineColor = if (colorful) colors.brand else colors.cardText
    val stroke = Stroke(width = dp(2.5.dp), cap = StrokeCap.Round)

    drawPath(
        path = spline.areaPath(0, lastIndex, baseline).asComposePath(),
        brush = Brush.verticalGradient(
            colors = listOf(
                lineColor.copy(alpha = ChartPalette.areaTopAlpha),
                lineColor.copy(alpha = ChartPalette.areaBottomAlpha),
            ),
            startY = plot.top,
            endY = plot.bottom,
        ),
    )

    if (colorful && currentIndex != null && currentIndex > 0 && currentIndex < lastIndex) {
        // Elapsed part of the day dimmed, the rest full strength.
        drawPath(spline.path(0, currentIndex).asComposePath(), lineColor.copy(alpha = ChartPalette.linePastAlpha), style = stroke)
        drawPath(spline.path(currentIndex, lastIndex).asComposePath(), lineColor, style = stroke)
    } else {
        drawPath(spline.path(0, lastIndex).asComposePath(), lineColor, style = stroke)
    }

    val dotRadius = dp(levelDotSizeDp(values.size).dp / 2f)
    values.forEach { entry ->
        val center = Offset(plot.x(entry.index.toDouble()), plot.y(entry.price))
        val levelColor = Color(entry.level?.colorHex ?: colors.brand.toArgb().toLong())
        val past = currentIndex != null && entry.index < currentIndex
        when {
            entry.isCurrent -> {
                drawCircle(levelColor.copy(alpha = 0.25f), radius = dp(7.dp), center = Offset(center.x, center.y + dp(1.dp)))
                drawCircle(levelColor, radius = dp(5.5f.dp), center = center)
                drawCircle(Color.White, radius = dp(5.5f.dp), center = center, style = Stroke(width = dp(2.dp)))
            }

            selected != null && selected != entry.index ->
                drawCircle(levelColor.copy(alpha = ChartPalette.unselectedBarAlpha), radius = dotRadius, center = center)

            else ->
                drawCircle(
                    levelColor.copy(alpha = if (past && colorful) ChartPalette.linePastAlpha else 1f),
                    radius = dotRadius,
                    center = center,
                )
        }
    }
}

private fun DrawScope.drawRules(
    plot: Plot,
    yTicks: List<Double>,
    values: List<ChartValue>,
    selected: Int?,
    averageRule: Double?,
    showZeroRule: Boolean,
    nowIndex: Int?,
    colorful: Boolean,
    colors: koodipardik.nordprice.ui.theme.AppColors,
) {
    val rule = colors.textOnBackground
    averageRule?.let {
        val y = plot.y(it)
        if (y >= plot.top && y <= plot.bottom) {
            drawDashed(rule.copy(alpha = 0.4f), Offset(plot.left, y), Offset(plot.right, y), 4f to 3f)
        }
    }
    if (showZeroRule) {
        drawLine(
            rule.copy(alpha = ChartPalette.ruleAlpha),
            Offset(plot.left, plot.y(0.0)),
            Offset(plot.right, plot.y(0.0)),
            strokeWidth = dp(1.5.dp),
        )
    }
    if (colorful && nowIndex != null) {
        val x = plot.x(nowIndex.toDouble())
        drawDashed(
            rule.copy(alpha = ChartPalette.nowRuleAlpha),
            Offset(x, plot.top),
            Offset(x, plot.bottom),
            3f to 3f,
        )
    }
    if (selected != null) {
        val x = plot.x(selected.toDouble())
        drawLine(
            color = if (colorful) rule.copy(alpha = ChartPalette.ruleAlpha)
            else colors.currentHighlight.copy(alpha = 0.6f),
            start = Offset(x, plot.top),
            end = Offset(x, plot.bottom),
            strokeWidth = dp(1.5.dp),
        )
    }
}

private fun DrawScope.drawDashed(
    color: Color,
    from: Offset,
    to: Offset,
    dash: Pair<Float, Float>,
) {
    drawLine(
        color = color,
        start = from,
        end = to,
        strokeWidth = 1f,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash.first, dash.second)),
    )
}

/** Dp and literal-float dp to pixels, using the DrawScope's own density. */
private fun DrawScope.dp(value: Dp): Float = with(density) { value.toPx() }


