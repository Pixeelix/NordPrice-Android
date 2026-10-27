package com.koodipardik.nordprice.widget

import android.graphics.Bitmap
import androidx.compose.ui.unit.Dp
import com.koodipardik.nordprice.core.PriceFormat
import com.koodipardik.nordprice.ui.chart.ChartSceneBuilder
import com.koodipardik.nordprice.ui.chart.ChartStyles
import com.koodipardik.nordprice.ui.theme.AppColors

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
        drawAxisLabels: Boolean = false,
    ): Bitmap {
        // The bitmap is painted at 2x the display density and scaled back down by the launcher,
        // which is what keeps bars and rules crisp however much the FillBounds slot deviates
        // from the box estimated in NordPriceWidget — an upscale was where the soft, smeared
        // look came from on low-density screens; a downscale is what this now always is.
        val scale = density * 2f
        val widthPx = (width.value * scale).toInt().coerceAtLeast(1)
        val heightPx = (height.value * scale).toInt().coerceAtLeast(1)
        val style = ChartStyles.widget(colors, snapshot.chartType, axisLabels = drawAxisLabels) {
            PriceFormat.axisLabel(it, snapshot.unit)
        }
        val scene = ChartSceneBuilder.build(
            values = snapshot.values,
            style = style,
            selected = null,
            widthPx = widthPx.toFloat(),
            heightPx = heightPx.toFloat(),
            density = scale,
        )
        return WidgetChartRenderer.render(
            scene, style, widthPx, heightPx, 9f * scale, drawLabels = drawAxisLabels,
        )
    }
}
