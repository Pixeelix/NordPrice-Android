package koodipardik.nordprice.ui.chart

import koodipardik.nordprice.core.PriceLevel
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.round

/** One plotted value. */
data class ChartValue(
    val index: Int,
    /** Already converted to the user's unit and VAT. */
    val price: Double,
    /** Interval label, e.g. "14:15", or the date of a history bar. */
    val label: String,
    /** Derived from the *raw* €/MWh figure, so colours do not move when the unit changes. */
    val level: PriceLevel? = null,
    val isCurrent: Boolean = false,
)

/** How a value maps to a pixel along one axis. */
internal typealias SceneMapperFn = (Double) -> Float

/**
 * Monotone cubic (Fritsch–Carlson) interpolation — the curve Swift Charts'
 * `.interpolationMethod(.monotone)` draws: smooth, but never overshooting a data point, so a
 * cheap hour cannot sprout a bump above its expensive neighbours.
 *
 * Tangents are solved once for the whole series and segments emitted on demand, so the elapsed
 * and upcoming halves of today's line join without a kink at the current interval.
 *
 * The curve is handed out as sampled points rather than a platform path: the app draws it with
 * Compose and the home-screen widget with `android.graphics`, and a shared polyline is what keeps
 * those two pixel-identical.
 */
internal class MonotoneSpline(private val px: DoubleArray, private val py: DoubleArray) {

    private val n: Int = px.size
    private val dx = DoubleArray((n - 1).coerceAtLeast(0)) { px[it + 1] - px[it] }
    private val slope = DoubleArray(dx.size) { i ->
        if (dx[i] == 0.0) 0.0 else (py[i + 1] - py[i]) / dx[i]
    }

    private val tangents: DoubleArray = DoubleArray(n).also { t ->
        if (n < 2) return@also
        for (i in 0 until n) {
            t[i] = when {
                i == 0 -> slope[0]
                i == n - 1 -> slope[n - 2]
                slope[i - 1] * slope[i] <= 0.0 -> 0.0
                else -> (slope[i - 1] + slope[i]) / 2.0
            }
        }
        // Clamp so the interpolant stays monotone between neighbouring points.
        for (i in 0 until n - 1) {
            if (slope[i] == 0.0) {
                t[i] = 0.0
                t[i + 1] = 0.0
            } else {
                val a = t[i] / slope[i]
                val b = t[i + 1] / slope[i]
                val radius = hypot(a, b)
                if (radius > 3.0) {
                    val tau = 3.0 / radius
                    t[i] = tau * a * slope[i]
                    t[i + 1] = tau * b * slope[i]
                }
            }
        }
    }

    /** Points of the curve through `from..=to`, [perSegment] samples per data segment. */
    fun sample(from: Int, to: Int, perSegment: Int, into: MutableList<ScenePoint> = ArrayList()) {
        if (n == 0 || from !in 0 until n || to !in 0 until n || from > to) return
        into += ScenePoint(px[from].toFloat(), py[from].toFloat())
        for (i in from until to) {
            val h = dx[i]
            // Control points of the equivalent cubic Bézier for this segment.
            val c1x = px[i] + h / 3.0
            val c1y = py[i] + tangents[i] * h / 3.0
            val c2x = px[i + 1] - h / 3.0
            val c2y = py[i + 1] - tangents[i + 1] * h / 3.0
            for (s in 1..perSegment) {
                val t = s.toDouble() / perSegment
                val mt = 1.0 - t
                val a = mt * mt * mt
                val b = 3.0 * mt * mt * t
                val c = 3.0 * mt * t * t
                val d = t * t * t
                into += ScenePoint(
                    x = (a * px[i] + b * c1x + c * c2x + d * px[i + 1]).toFloat(),
                    y = (a * py[i] + b * c1y + c * c2y + d * py[i + 1]).toFloat(),
                )
            }
        }
    }

    companion object {
        fun of(values: List<ChartValue>, x: SceneMapper, y: SceneMapper): MonotoneSpline = MonotoneSpline(
            px = DoubleArray(values.size) { x(values[it].index.toDouble()).toDouble() },
            py = DoubleArray(values.size) { y(values[it].price).toDouble() },
        )
    }
}

/** Nice round axis steps, standing in for the ones Swift Charts picks automatically. */
internal fun niceTickValues(min: Double, max: Double, targetCount: Int): List<Double> {
    if (min.isNaN() || max.isNaN() || targetCount <= 1 || max <= min) return listOf(min)
    val range = niceNumber(max - min, roundDown = false)
    val step = niceNumber(range / (targetCount - 1), roundDown = true)
    if (step <= 0.0) return listOf(min)

    val values = mutableListOf<Double>()
    var value = ceil(min / step) * step
    var guard = 0
    while (value <= max + step * 0.5 && guard < 64) {
        values += round(value * 1e10) / 1e10
        value += step
        guard++
    }
    return values
}

/** Round a range or step to 1, 2 or 5 times a power of ten. */
private fun niceNumber(range: Double, roundDown: Boolean): Double {
    if (range <= 0.0 || range.isNaN() || range.isInfinite()) return 1.0
    val exponent = floor(ln(range) / ln(10.0))
    val fraction = range / 10.0.pow(exponent)
    val nice = if (roundDown) {
        when {
            fraction < 1.5 -> 1.0
            fraction < 3.0 -> 2.0
            fraction < 7.0 -> 5.0
            else -> 10.0
        }
    } else {
        when {
            fraction <= 1.0 -> 1.0
            fraction <= 2.0 -> 2.0
            fraction <= 5.0 -> 5.0
            else -> 10.0
        }
    }
    return nice * 10.0.pow(exponent)
}
