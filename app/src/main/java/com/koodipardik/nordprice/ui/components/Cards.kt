package com.koodipardik.nordprice.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.koodipardik.nordprice.ui.theme.AppTheme

/** The page background: a diagonal gradient, edge to edge behind the system bars. */
@Composable
fun GradientBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val colors = AppTheme
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(listOf(colors.backgroundTop, colors.backgroundBottom)),
            ),
        content = content,
    )
}

/**
 * Card fill used everywhere: rounded rectangle, card colour, and in dark mode a hairline ring
 * matching the website's `ring-ink/8`. Port of the iOS `cardStyle(cornerRadius:)`.
 */
@Composable
fun Modifier.appCard(cornerRadius: Dp = 14.dp): Modifier {
    val shape = RoundedCornerShape(cornerRadius)
    return clip(shape)
        .background(AppTheme.card)
        .border(1.dp, AppTheme.cardStroke, shape)
}

/**
 * Side inset of the price cards and the chart. The phone layout keeps them at 90 % of the
 * screen width; the wide (tablet) layout fills its container and pads by 24 dp instead —
 * the same split as the iOS `cardWidth` environment value.
 */
val CardHorizontalInset = 20.dp

val WideContentInset = 24.dp
