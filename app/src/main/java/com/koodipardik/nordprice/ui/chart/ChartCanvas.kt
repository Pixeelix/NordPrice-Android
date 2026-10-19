package com.koodipardik.nordprice.ui.chart

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The chart as the app draws it: a [ChartScene] — geometry and colours, computed once by
 * [ChartSceneBuilder] — painted onto a Canvas, with the axis labels and the "Now" capsule laid out
 * as real Compose text on top so they follow the user's font scale instead of being burnt in at a
 * fixed pixel size.
 *
 * The home-screen widget hands the same scene to a bitmap painter. Neither of them decides where a
 * bar goes or what colour it is; that is the point of the split.
 */
@Composable
internal fun ChartCanvas(
    values: List<ChartValue>,
    style: ChartStyle,
    selected: Int?,
    onSelection: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    nowBadge: String? = null,
    averageLine: Double? = null,
    axisFontSize: TextUnit = 11.sp,
) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val reportSelection = rememberUpdatedState(onSelection)

    BoxWithConstraints(modifier) {
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val scene = remember(values, selected, style.isColorful, style.chartType, widthPx, heightPx) {
            ChartSceneBuilder.build(values, style, selected, averageLine, widthPx, heightPx, density.density)
        }
        // Selecting rebuilds the scene (the other bars dim, a rule appears), so the gesture reads
        // it through state instead of keying on it: a pointerInput block restarts when its keys
        // change, which would cancel the drag on its very first step — and skip the reset on lift,
        // leaving the selection frozen on whichever bar the finger landed on.
        val currentScene by rememberUpdatedState(scene)
        val valueCount by rememberUpdatedState(values.size)
        val axisColor = Color(style.axisLabelArgb)

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var lastIndex: Int? = null
                        fun select(px: Float) {
                            if (valueCount == 0) return
                            val index = currentScene.indexAt(px)
                                .roundToInt()
                                .coerceIn(0, valueCount - 1)
                            if (index != lastIndex) {
                                lastIndex = index
                                reportSelection.value(index)
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        }
                        // iOS runs its DragGesture with minimumDistance 0, so the touched bar
                        // answers from the touch-down, not from the first millimetre of movement.
                        select(down.position.x)
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            if (!change.pressed) break
                            select(change.position.x)
                            change.consume()
                        }
                        reportSelection.value(null)
                    }
                },
        ) {
            drawScene(scene, density.density)
        }

        scene.yTicks.forEach { tick ->
            Text(
                text = tick.label,
                style = TextStyle(fontSize = axisFontSize, color = axisColor),
                textAlign = TextAlign.End,
                maxLines = 1,
                modifier = Modifier
                    .offset(y = ((tick.position / density.density) - 7f).coerceAtLeast(0f).dp)
                    .width(style.insets.left.dp - 4.dp),
            )
        }
        scene.xTicks.forEach { tick ->
            Text(
                text = tick.label,
                style = TextStyle(fontSize = axisFontSize, fontWeight = FontWeight.SemiBold, color = axisColor),
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier
                    .offset(
                        x = ((tick.position / density.density) - 15f).coerceAtLeast(0f).dp,
                        y = maxHeight - style.insets.bottom.dp,
                    )
                    .width(30.dp),
            )
        }

        val badge = scene.nowBadge
        if (nowBadge != null && badge != null) {
            val current = values.firstOrNull { it.isCurrent }
            Box(
                modifier = Modifier
                    .offset(
                        x = (badge.x / density.density - 16f).coerceIn(0f, maxWidth.value - 32f).dp,
                        y = (badge.y / density.density - 20f).coerceAtLeast(0f).dp,
                    )
                    .width(32.dp)
                    .height(14.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(current?.level?.let(style.levelArgb) ?: style.brandArgb)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = nowBadge,
                    style = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White),
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private fun DrawScope.drawScene(scene: ChartScene, density: Float) {
    scene.ops.forEach { op ->
        when (op) {
            is SceneOp.GridLine -> drawLine(
                color = Color(op.colorArgb),
                start = if (op.vertical) Offset(op.at, op.from) else Offset(op.from, op.at),
                end = if (op.vertical) Offset(op.at, op.to) else Offset(op.to, op.at),
                strokeWidth = op.thickness,
            )

            is SceneOp.Rule -> drawLine(
                color = Color(op.colorArgb),
                start = if (op.vertical) Offset(op.at, op.from) else Offset(op.from, op.at),
                end = if (op.vertical) Offset(op.at, op.to) else Offset(op.to, op.at),
                strokeWidth = op.thickness,
                pathEffect = if (op.dashOn > 0f) {
                    PathEffect.dashPathEffect(floatArrayOf(op.dashOn * density, op.dashOff * density))
                } else {
                    null
                },
            )

            is SceneOp.Bar -> {
                val top = minOf(op.fromY, op.toY)
                val barSize = Size(op.halfWidth * 2f, abs(op.toY - op.fromY))
                val topLeft = Offset(op.centerX - op.halfWidth, top)
                if (op.cornerRadius > 0f) {
                    drawRoundRect(Color(op.colorArgb), topLeft, barSize, CornerRadius(op.cornerRadius, op.cornerRadius))
                } else {
                    drawRect(Color(op.colorArgb), topLeft, barSize)
                }
            }

            is SceneOp.Area -> drawPath(
                path = op.toPath(),
                brush = Brush.verticalGradient(
                    colors = listOf(Color(op.topColorArgb), Color(op.bottomColorArgb)),
                    startY = scene.plot.top,
                    endY = scene.plot.bottom,
                ),
            )

            is SceneOp.PolyLine -> drawPath(
                path = op.toPath(),
                color = Color(op.colorArgb),
                style = Stroke(width = op.thickness, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )

            is SceneOp.Dot -> {
                val center = Offset(op.center.x, op.center.y)
                if (op.shadow) {
                    drawCircle(
                        color = Color(op.colorArgb).copy(alpha = 0.25f),
                        radius = op.radius * 1.3f,
                        center = Offset(center.x, center.y + density),
                    )
                }
                drawCircle(Color(op.colorArgb), radius = op.radius, center = center)
                op.ringColorArgb?.let {
                    drawCircle(Color(it), radius = op.radius, center = center, style = Stroke(width = op.ringWidth))
                }
            }
        }
    }
}

private fun SceneOp.Area.toPath(): Path {
    val path = Path()
    if (points.isEmpty()) return path
    path.moveTo(points.first().x, points.first().y)
    points.drop(1).forEach { path.lineTo(it.x, it.y) }
    path.lineTo(points.last().x, baselineY)
    path.lineTo(points.first().x, baselineY)
    path.close()
    return path
}

private fun SceneOp.PolyLine.toPath(): Path {
    val path = Path()
    if (points.isEmpty()) return path
    path.moveTo(points.first().x, points.first().y)
    points.drop(1).forEach { path.lineTo(it.x, it.y) }
    return path
}
