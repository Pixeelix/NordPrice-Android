package koodipardik.nordprice.widget

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
import koodipardik.nordprice.MainActivity
import koodipardik.nordprice.R
import koodipardik.nordprice.appContainer
import koodipardik.nordprice.core.Day
import koodipardik.nordprice.ui.theme.AppColors
import koodipardik.nordprice.ui.theme.DarkColors
import koodipardik.nordprice.ui.theme.LightColors

/**
 * The home-screen widget: today's current price, its interval, and the day's curve. Port of the
 * iOS WidgetKit extension's `systemSmall` / `systemMedium`.
 *
 * Android needs no App Group: the widget's receiver runs in the app's own process, so it reads the
 * same price cache and the same [koodipardik.nordprice.settings.AppSettings] the app writes — iOS
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
    val chartHeight = if (wide) 80.dp else 60.dp
    val density = context.resources.displayMetrics.density

    val chart: ImageProvider? = if (snapshot.values.isEmpty()) {
        null
    } else {
        ImageProvider(
            WidgetChartImage.render(colors, snapshot, size.width - 24.dp, chartHeight, density),
        )
    }

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(palette.background))
            .clickable(actionStartActivity<MainActivity>()),
    ) {
        Column(
            modifier = GlanceModifier.fillMaxSize().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.Start,
        ) {
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                Text(
                    text = context.getString(R.string.text_current_price),
                    style = TextStyle(
                        color = ColorProvider(colors.secondaryText),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                )
                Spacer(GlanceModifier.defaultWeight())
                Text(
                    text = snapshot.timeText,
                    style = TextStyle(color = ColorProvider(colors.secondaryText), fontSize = 11.sp),
                )
            }

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = snapshot.priceText,
                    style = TextStyle(
                        color = ColorProvider(colors.cardText),
                        fontSize = if (wide) 42.sp else 34.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    maxLines = 1,
                )
                Spacer(GlanceModifier.width(6.dp))
                Text(
                    text = snapshot.unitLabel,
                    style = TextStyle(
                        color = ColorProvider(colors.secondaryText),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    maxLines = 1,
                )
            }

            chart?.let { provider ->
                Image(
                    provider = provider,
                    contentDescription = context.getString(R.string.a11y_price_chart),
                    // FillBounds: the bitmap is rendered at exactly this size, so any letterboxing
                    // the default crop/fit scale applies would just shrink the curve.
                    contentScale = ContentScale.FillBounds,
                    modifier = GlanceModifier.fillMaxWidth().padding(top = 10.dp).height(chartHeight),
                )
            }
        }
    }
}

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
