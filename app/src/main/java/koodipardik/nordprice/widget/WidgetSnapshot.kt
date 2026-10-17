package koodipardik.nordprice.widget

import koodipardik.nordprice.core.ChartResolution
import koodipardik.nordprice.core.ChartType
import koodipardik.nordprice.core.MarketDay
import koodipardik.nordprice.core.PriceFormat
import koodipardik.nordprice.core.PriceLevel
import koodipardik.nordprice.core.PriceMath
import koodipardik.nordprice.core.PricePoint
import koodipardik.nordprice.core.PriceUnit
import koodipardik.nordprice.settings.Settings
import koodipardik.nordprice.ui.chart.ChartValue
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Everything the widget draws, already resolved for the user's unit, VAT and region clock. */
data class WidgetSnapshot(
    val priceText: String,
    val unit: PriceUnit,
    val unitLabel: String,
    val timeText: String,
    val values: List<ChartValue>,
    val chartType: ChartType,
    val isEmpty: Boolean,
)

/**
 * Turns a day of prices into what the widget shows.
 *
 * Kept free of Android types so the same numbers can be asserted in a test and rendered by both
 * the in-app preview and the real Glance widget.
 *
 * The current interval is picked by timestamp here — the last point at or before "now" — which is
 * what the iOS widget does. The iOS *app* estimates it as a proportion of the day instead; both
 * are ported faithfully, and the two agree except on the two DST days a year when they don't.
 */
object WidgetSnapshotBuilder {

    fun build(
        settings: Settings,
        points: List<PricePoint>,
        unitLabel: String,
        now: Instant = Instant.now(),
        deviceZone: ZoneId = ZoneId.systemDefault(),
    ): WidgetSnapshot {
        val hourly = settings.chartResolution == ChartResolution.ONE_HOUR
        val source = (if (hourly) PriceMath.aggregateToHourly(points, deviceZone) else points)
            .sortedBy { it.timestamp }

        val priceOf = (if (settings.includeTax) settings.taxRate else 1.0) / settings.unit.divider
        val labelFor = MarketDay.labelsIn(settings.region, settings.chartResolution)

        val boundary = if (hourly) {
            now.atZone(deviceZone).truncatedTo(ChronoUnit.HOURS).toInstant()
        } else {
            now
        }
        val currentIndex = source.indexOfLast { it.instant <= boundary }
            .takeIf { it >= 0 }
            ?: source.indices.lastOrNull()

        val current = currentIndex?.let { source[it] }

        val values = source.mapIndexed { index, point ->
            ChartValue(
                index = index,
                price = point.price * priceOf,
                label = labelFor(point.instant),
                level = PriceLevel.of(point.price),
                isCurrent = index == currentIndex,
            )
        }

        return WidgetSnapshot(
            unit = settings.unit,
            priceText = current?.let { PriceFormat.format(it.price * priceOf, settings.unit) } ?: "---",
            unitLabel = unitLabel,
            timeText = current?.let { labelFor(it.instant) } ?: "--:--",
            values = values,
            chartType = settings.chartType,
            isEmpty = source.isEmpty(),
        )
    }
}
