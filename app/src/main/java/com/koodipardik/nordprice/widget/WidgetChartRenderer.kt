package com.koodipardik.nordprice.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import com.koodipardik.nordprice.ui.chart.ChartScene
import com.koodipardik.nordprice.ui.chart.ChartStyle
import com.koodipardik.nordprice.ui.chart.SceneOp
import kotlin.math.abs

/**
 * Paints a [ChartScene] into a [Bitmap] for the home-screen widget.
 *
 * A widget is rendered as RemoteViews, so there is no live Compose tree to draw into and no way to
 * overlay positioned text — everything, including the axis labels, has to be burnt into one image.
 * The scene it draws is the same object the app renders on screen, which is what keeps the two
 * pictures in step.
 */
object WidgetChartRenderer {

    /**
     * [drawLabels] is off for the widget: the image gets stretched into whatever box Glance gives
     * it, and text must not be part of that. The curve, bars and rules scale harmlessly.
     */
    fun render(
        scene: ChartScene,
        style: ChartStyle,
        widthPx: Int,
        heightPx: Int,
        labelSizePx: Float,
        drawLabels: Boolean = true,
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(maxOf(widthPx, 1), maxOf(heightPx, 1), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = style.axisLabelArgb.toInt()
            textSize = labelSizePx
        }

        scene.ops.forEach { op ->
            when (op) {
                is SceneOp.GridLine -> {
                    paint.shader = null
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = op.thickness
                    paint.pathEffect = null
                    paint.color = op.colorArgb.toInt()
                    if (op.vertical) {
                        canvas.drawLine(op.at, op.from, op.at, op.to, paint)
                    } else {
                        canvas.drawLine(op.from, op.at, op.to, op.at, paint)
                    }
                }

                is SceneOp.Rule -> {
                    paint.shader = null
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = op.thickness
                    paint.color = op.colorArgb.toInt()
                    paint.pathEffect = if (op.dashOn > 0f) {
                        android.graphics.DashPathEffect(floatArrayOf(op.dashOn, op.dashOff), 0f)
                    } else {
                        null
                    }
                    if (op.vertical) {
                        canvas.drawLine(op.at, op.from, op.at, op.to, paint)
                    } else {
                        canvas.drawLine(op.from, op.at, op.to, op.at, paint)
                    }
                }

                is SceneOp.Bar -> {
                    paint.shader = null
                    paint.style = Paint.Style.FILL
                    paint.pathEffect = null
                    paint.color = op.colorArgb.toInt()
                    val left = op.centerX - op.halfWidth
                    val right = op.centerX + op.halfWidth
                    val top = minOf(op.fromY, op.toY)
                    val bottom = maxOf(op.fromY, op.toY)
                    if (op.cornerRadius > 0f) {
                        canvas.drawRoundRect(RectF(left, top, right, bottom), op.cornerRadius, op.cornerRadius, paint)
                    } else {
                        canvas.drawRect(left, top, right, bottom, paint)
                    }
                }

                is SceneOp.Area -> {
                    paint.style = Paint.Style.FILL
                    paint.pathEffect = null
                    paint.shader = LinearGradient(
                        0f, scene.plot.top, 0f, scene.plot.bottom,
                        intArrayOf(op.topColorArgb.toInt(), op.bottomColorArgb.toInt()),
                        null, Shader.TileMode.CLAMP,
                    )
                    canvas.drawPath(op.toPath(closeOnBaseline = true), paint)
                }

                is SceneOp.PolyLine -> {
                    paint.shader = null
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = op.thickness
                    paint.strokeCap = Paint.Cap.ROUND
                    paint.strokeJoin = Paint.Join.ROUND
                    paint.pathEffect = null
                    paint.color = op.colorArgb.toInt()
                    canvas.drawPath(op.toPath(), paint)
                }

                is SceneOp.Dot -> {
                    paint.shader = null
                    paint.style = Paint.Style.FILL
                    paint.pathEffect = null
                    paint.color = op.colorArgb.toInt()
                    canvas.drawCircle(op.center.x, op.center.y, maxOf(op.radius, 0.5f), paint)
                    op.ringColorArgb?.let {
                        paint.style = Paint.Style.STROKE
                        paint.strokeWidth = op.ringWidth
                        paint.color = it.toInt()
                        canvas.drawCircle(op.center.x, op.center.y, maxOf(op.radius, 0.5f), paint)
                    }
                }
            }
        }

        if (drawLabels) drawLabels(canvas, scene, textPaint)
        return bitmap
    }

    /** Y labels sit right-aligned in the left gutter, X labels centred under their tick. */
    private fun drawLabels(canvas: Canvas, scene: ChartScene, paint: Paint) {
        val gutter = scene.plot.left
        scene.yTicks.forEach { tick ->
            val y = (tick.position + abs(paint.ascent()) / 2f).coerceIn(paint.textSize, canvas.height.toFloat())
            val width = paint.measureText(tick.label)
            canvas.drawText(tick.label, gutter - 4f - width, y, paint)
        }
        scene.xTicks.forEach { tick ->
            val width = paint.measureText(tick.label)
            val x = (tick.position - width / 2f).coerceIn(0f, (canvas.width - width).toFloat())
            canvas.drawText(tick.label, x, canvas.height.toFloat(), paint)
        }
    }

    private fun SceneOp.PolyLine.toPath(): Path {
        val path = Path()
        if (points.isEmpty()) return path
        path.moveTo(points.first().x, points.first().y)
        points.drop(1).forEach { path.lineTo(it.x, it.y) }
        return path
    }

    private fun SceneOp.Area.toPath(closeOnBaseline: Boolean): Path {
        val path = Path()
        if (points.isEmpty()) return path
        path.moveTo(points.first().x, points.first().y)
        points.drop(1).forEach { path.lineTo(it.x, it.y) }
        if (closeOnBaseline) {
            path.lineTo(points.last().x, baselineY)
            path.lineTo(points.first().x, baselineY)
            path.close()
        }
        return path
    }
}
