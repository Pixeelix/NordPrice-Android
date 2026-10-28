package com.koodipardik.nordprice

import com.koodipardik.nordprice.core.ChartResolution
import com.koodipardik.nordprice.core.ChartType
import com.koodipardik.nordprice.ui.chart.ChartSceneBuilder
import com.koodipardik.nordprice.ui.chart.ChartStyles
import com.koodipardik.nordprice.ui.chart.ChartValue
import com.koodipardik.nordprice.ui.chart.SceneOp
import com.koodipardik.nordprice.ui.chart.nowBadgeOffsetX
import com.koodipardik.nordprice.ui.theme.LightColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the chart owes a window that is smaller than it would like.
 *
 * Android-only rules, not iOS ports: the Compose stacks hand the chart whatever height is left over,
 * and on a foldable's cover screen or in Samsung's split-screen that can be a fraction of what the
 * axis needs. Swift Charts sizes itself down instead, so iOS never reaches this shape.
 */
class ShortWindowChartTest {

    /** A whole day of hourly prices, 50..73, so the app axis runs 0..80.3 with 20-unit ticks. */
    private val day = (0 until 24).map { ChartValue(it, 50.0 + it, "%02d:00".format(it)) }

    private val style = ChartStyles.day(
        colors = LightColors,
        chartType = ChartType.BAR,
        colorful = true,
        resolution = ChartResolution.ONE_HOUR,
    ) { "%.2f".format(it) }

    private fun scene(widthPx: Float, heightPx: Float, labelPx: Float, density: Float = 2f) =
        ChartSceneBuilder.build(
            values = day, style = style, selected = null,
            widthPx = widthPx, heightPx = heightPx, density = density, axisLabelHeightPx = labelPx,
        )

    @Test
    fun `a roomy plot keeps every label it asked for`() {
        // 300 px tall, 264 px of plot: five 30 px labels fit with 59 px between them.
        val scene = scene(widthPx = 800f, heightPx = 300f, labelPx = 30f)

        assertEquals(5, scene.yTicks.size)
        assertEquals(4, scene.xTicks.size)
    }

    @Test
    fun `big axis labels thin the ticks rather than overlap them`() {
        // Same plot, 56 px labels: only every other 20-unit tick has room for one.
        val scene = scene(widthPx = 800f, heightPx = 300f, labelPx = 56f)

        assertEquals(listOf(0.0, 40.0, 80.0), scene.yTicks.map { it.value })
    }

    @Test
    fun `a plot too short to label drops its labels entirely`() {
        // 90 px tall leaves 26 px of plot: two labels cannot both fit, and one conveys nothing.
        val scene = scene(widthPx = 800f, heightPx = 90f, labelPx = 30f)

        assertTrue("expected no y labels, got ${scene.yTicks.map { it.label }}", scene.yTicks.isEmpty())
    }

    @Test
    fun `the plot keeps its label gutter whatever the box size`() {
        // Narrower than the left gutter itself. Dropping the gutter here is what put the bars under
        // the axis text; it stays reserved even when that leaves almost nothing to plot in.
        val scene = scene(widthPx = 64f, heightPx = 40f, labelPx = 30f)

        assertTrue("plot started at ${scene.plot.left}, gutter is ${style.insets.left}",
            scene.plot.left >= style.insets.left * 2f)
        assertTrue(scene.plot.width > 0f)
        assertTrue(scene.plot.height > 0f)
    }

    @Test
    fun `a bottom band too short for the labels drops the labels and keeps the grid`() {
        // Large text in a 90 px box: no band holds a 60 px label, but the hourly grid still stands.
        val scene = scene(widthPx = 800f, heightPx = 90f, labelPx = 60f)

        assertTrue("expected no x labels, got ${scene.xTicks.map { it.label }}", scene.xTicks.isEmpty())
        assertEquals(
            "the grid belongs to the plot, not to the labels",
            4,
            scene.ops.filterIsInstance<SceneOp.GridLine>().count { it.vertical },
        )
    }

    @Test
    fun `the now badge survives a box narrower than the badge`() {
        // 20 dp of canvas against a 32 dp badge: coerceIn throws on an empty range instead of
        // clamping to nothing, and this ran on the layout path of every colourful day chart.
        assertEquals(0f, nowBadgeOffsetX(badgeX = 400f, density = 2f, maxWidthDp = 20f), 0f)
        // 100 dp from the left edge of a 100 dp box, minus the 16 dp half-badge overhang, clamped so
        // the 32 dp badge still ends inside the box.
        assertEquals(68f, nowBadgeOffsetX(badgeX = 200f, density = 2f, maxWidthDp = 100f), 0f)
    }
}
