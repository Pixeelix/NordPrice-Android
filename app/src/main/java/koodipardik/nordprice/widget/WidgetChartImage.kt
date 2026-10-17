package koodipardik.nordprice.widget

import android.graphics.Bitmap
import androidx.compose.ui.unit.Dp
import koodipardik.nordprice.core.PriceFormat
import koodipardik.nordprice.ui.chart.ChartSceneBuilder
import koodipardik.nordprice.ui.chart.ChartStyles
import koodipardik.nordprice.ui.theme.AppColors

/**
 * The widget's chart, as a bitmap.
 *
 * Shared by the Glance widget and the debug preview screen so what you inspect in the app is the
 * exact pixels the launcher will show.
 */
object WidgetChartImage {

    fun render(
        colors: AppColors,
        snapshot: WidgetSnapshot,
        width: Dp,
        height: Dp,
        density: Float,
    ): Bitmap {
        val widthPx = (width.value * density).toInt().coerceAtLeast(1)
        val heightPx = (height.value * density).toInt().coerceAtLeast(1)
        val style = ChartStyles.widget(colors, snapshot.chartType) {
            PriceFormat.axisLabel(it, snapshot.unit)
        }
        val scene = ChartSceneBuilder.build(
            values = snapshot.values,
            style = style,
            selected = null,
            widthPx = widthPx.toFloat(),
            heightPx = heightPx.toFloat(),
            density = density,
        )
        return WidgetChartRenderer.render(scene, style, widthPx, heightPx, 9f * density)
    }
}
