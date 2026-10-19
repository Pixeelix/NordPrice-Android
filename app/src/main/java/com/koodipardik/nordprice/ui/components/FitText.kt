package com.koodipardik.nordprice.ui.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * Text that shrinks to fit the box it is given — the Android stand-in for SwiftUI's
 * `.font(.system(size: 300)) + .minimumScaleFactor(0.01)` pair, which is how the iOS app makes
 * every price fill its card no matter how many digits it has.
 *
 * Compose has no autoscaling, so this measures and binary-searches the largest size that fits
 * both axes. A price is a handful of glyphs, so the handful of measure passes is cheap, and the
 * chosen size is remembered against the text and the box.
 */
@Composable
fun FitText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight = FontWeight.Medium,
    fontFamily: FontFamily = FontFamily.Default,
    maxFontSize: TextUnit = 64.sp,
    minFontSize: TextUnit = 10.sp,
    lineHeight: TextUnit = TextUnit.Unspecified,
) {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val measurer = rememberTextMeasurer()

    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val availableWidth: Dp = maxWidth
        val availableHeight: Dp = if (constraints.hasBoundedHeight) {
            with(density) { constraints.maxHeight.toDp() }
        } else {
            maxHeight
        }

        val style = remember(
            text,
            availableWidth,
            availableHeight,
            color,
            fontWeight,
            fontFamily,
            maxFontSize,
            minFontSize,
        ) {
            val base = TextStyle(
                color = color,
                fontFamily = fontFamily,
                fontWeight = fontWeight,
                lineHeight = lineHeight,
            )
            var lo = minFontSize.value
            var hi = maxFontSize.value
            var best = minFontSize.value
            while (lo <= hi) {
                val candidate = ((lo + hi) / 2f)
                val layout = measurer.measure(
                    text = text,
                    style = base.copy(fontSize = candidate.sp),
                    maxLines = 1,
                    density = density,
                    layoutDirection = layoutDirection,
                )
                val fits = with(density) {
                    layout.size.width.toDp() <= availableWidth && layout.size.height.toDp() <= availableHeight
                }
                if (fits) {
                    best = candidate
                    lo = candidate + 1f
                } else {
                    hi = candidate - 1f
                }
            }
            base.copy(fontSize = best.sp)
        }

        BasicText(text = text, style = style, maxLines = 1)
    }
}
