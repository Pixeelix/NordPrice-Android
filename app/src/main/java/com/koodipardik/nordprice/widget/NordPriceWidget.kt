package com.koodipardik.nordprice.widget

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceComposable
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.koodipardik.nordprice.MainActivity
import com.koodipardik.nordprice.R
import com.koodipardik.nordprice.appContainer
import com.koodipardik.nordprice.core.Day
import com.koodipardik.nordprice.ui.theme.AppColors
import com.koodipardik.nordprice.ui.theme.DarkColors
import com.koodipardik.nordprice.ui.theme.LightColors

/**
 * The home-screen widget: today's current price, its interval, and the day's curve. Port of the
 * iOS WidgetKit extension's `systemSmall` / `systemMedium`.
 *
 * Android needs no App Group: the widget's receiver runs in the app's own process, so it reads the
 * same price cache and the same [com.koodipardik.nordprice.settings.AppSettings] the app writes — iOS
 * had to mirror every setting into shared defaults, here there is one source.
 *
 * One provider serves both sizes and adapts to the cell span the user drops it on, which is how a
 * resizable Android widget works, rather than two families as on iOS.
 */
class NordPriceWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = context.appContainer
        val settings = container.settings.current

        // Cache first, network second, and if the network fails fall back to whatever is cached
        // even when it does not cover the whole day: stale-but-real prices beat a blank widget.
        val points = runCatching {
            container.priceRepository.loadFullDayData(Day.TODAY, settings.region)
        }.getOrElse { container.priceRepository.loadCached(Day.TODAY, settings.region) }

        val snapshot = WidgetSnapshotBuilder.build(
            settings = settings,
            points = points,
            unitLabel = context.getString(settings.unit.labelRes),
        )

        provideContent { WidgetContent(snapshot) }
    }
}

@GlanceComposable
@Composable
private fun WidgetContent(snapshot: WidgetSnapshot) {
    val context = LocalContext.current
    val size = LocalSize.current
    val palette = widgetPalette(context)
    val colors = palette.colors
    // Wide enough for two cells gets the medium treatment: a bigger price and a taller curve.
    val wide = size.width >= 250.dp
    val density = context.resources.displayMetrics.density

    // The curve takes whatever the two text rows leave over, and is drawn at that size so the
    // bitmap's aspect matches the box it lands in. That matters twice: a fixed height stretched to
    // fill made the zero line a band, and scaling that preserves the aspect (Fit) instead shrinks
    // the curve out from under its own hour labels. Nothing in the bitmap is text, so the residual
    // difference only changes bar widths.
    val textRows = if (wide) 96.dp else 76.dp
    val axisRow = 18.dp
    val chartWidth = size.width - 24.dp
    val chartHeight = (size.height - 24.dp - textRows - axisRow - 12.dp).coerceAtLeast(48.dp)

    val chart = if (snapshot.values.isEmpty()) {
        null
    } else {
        ImageProvider(WidgetChartImage.render(colors, snapshot, chartWidth, chartHeight, density))
    }

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(palette.background))
            .clickable(actionStartActivity<MainActivity>()),
    ) {
        // Glance's Column has no verticalArrangement: `verticalAlignment` positions the whole set of
        // children when they do not fill the column, and `defaultWeight()` on a child is what makes
        // it grow. Left at the Compose-like default the content ended up floating in the middle of
        // the widget, and a fixed chart height wasted the space below it.
        Column(
            modifier = GlanceModifier.fillMaxSize().padding(12.dp),
            verticalAlignment = Alignment.Top,
            horizontalAlignment = Alignment.Start,
        ) {
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                Text(
                    text = context.getString(R.string.text_current_price),
                    style = TextStyle(
                        color = ColorProvider(colors.secondaryText),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    maxLines = 1,
                )
                Spacer(GlanceModifier.defaultWeight())
                Text(
                    text = snapshot.timeText,
                    style = TextStyle(color = ColorProvider(colors.secondaryText), fontSize = 11.sp),
                    maxLines = 1,
                )
            }

            Row(
                modifier = GlanceModifier.fillMaxWidth().padding(top = 2.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    text = snapshot.priceText,
                    style = TextStyle(
                        color = ColorProvider(colors.cardText),
                        fontSize = if (wide) 40.sp else 30.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    maxLines = 1,
                )
                Spacer(GlanceModifier.width(6.dp))
                Text(
                    text = snapshot.unitLabel,
                    style = TextStyle(
                        color = ColorProvider(colors.secondaryText),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    maxLines = 1,
                )
            }

            if (chart != null) {

                // FillBounds is safe here precisely because the bitmap holds no text: only bars and
                // lines scale, and a wider slot for them is what filling the widget means.
                // The curve owns whatever the text leaves over, so a stretched or resized widget
                // grows with it instead of leaving a void. Vertical scaling only affects bar widths,
                // the dashed rules and the zero line — the bitmap holds no text, which is what makes
                // filling safe; the axis labels below are real text and never scale.
                Image(
                    provider = chart,
                    contentDescription = context.getString(R.string.a11y_price_chart),
                    contentScale = ContentScale.FillBounds,
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .defaultWeight(),
                )

                // The hour scale, as real text: four equal cells land on 00 / 06 / 12 / 18 of the
                // day, which is where the curve's own ticks are.
                Row(modifier = GlanceModifier.fillMaxWidth().padding(top = 2.dp)) {
                    hourLabels.forEach { label ->
                        Text(
                            text = label,
                            style = TextStyle(
                                color = ColorProvider(colors.secondaryText),
                                fontSize = 10.sp,
                            ),
                            modifier = GlanceModifier.defaultWeight(),
                            maxLines = 1,
                        )
                    }
                }
            }

        }
    }
}

/** Four equal slots along the day: 00, 06, 12 and 18 o'clock, as the iOS widget labels its axis. */
private val hourLabels = listOf("00", "06", "12", "18")

internal class WidgetPalette(val colors: AppColors, val background: Color)

/**
 * The widget's colours. It borrows the app's palette but overrides the page background: the iOS
 * widget has its own `WidgetBackground` asset, white like the app's cards in light mode but
 * `#1E2235` in dark, where the app's cards are the lighter `#262B47`.
 */
internal fun widgetPalette(context: Context): WidgetPalette {
    val night = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
        Configuration.UI_MODE_NIGHT_YES
    return WidgetPalette(
        colors = if (night) DarkColors else LightColors,
        background = if (night) Color(0xFF1E2235) else Color.White,
    )
}
