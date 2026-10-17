package koodipardik.nordprice.data

import koodipardik.nordprice.core.MarketDay
import koodipardik.nordprice.core.PricePoint
import koodipardik.nordprice.core.Region
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.util.zip.GZIPInputStream

@Serializable
internal data class PriceDto(val timestamp: Double, val price: Double)

/**
 * Elering answers with all four markets in one payload. A market without data comes back as
 * `null` rather than an empty array, so every list is optional here — the iOS decoder assumes
 * all four are always present and throws on a partial answer.
 */
@Serializable
internal data class CountriesDto(
    val ee: List<PriceDto>? = null,
    val fi: List<PriceDto>? = null,
    val lt: List<PriceDto>? = null,
    val lv: List<PriceDto>? = null,
)

@Serializable
internal data class NordPoolResponseDto(val data: CountriesDto, val success: Boolean = false)

class PriceApiException(message: String, val statusCode: Int = 0) : IOException(message)

internal val NordPriceJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = false
    explicitNulls = false
}

/**
 * HTTP layer for `dashboard.elering.ee/api/nps/price`.
 *
 * Plain [HttpURLConnection] on purpose: the API is a single GET returning JSON, which keeps a
 * networking library out of the APK. Timeouts mirror the iOS session (15 s) so a stale
 * connection cannot hang the app after it returns to the foreground.
 */
class PriceApi(
    private val json: Json = NordPriceJson,
    /** Overridable so tests can serve a payload from a loopback HTTP server. */
    private val endpoint: String = PRODUCTION_ENDPOINT,
) {

    /** One market day, bounded in the device time zone. */
    suspend fun fetchDay(day: koodipardik.nordprice.core.Day): String {
        val interval = MarketDay.utcInterval(day)
        return fetch(interval.start, interval.endInclusive)
    }

    /**
     * An arbitrary period. Elering caps a request at one year, and a whole year of 15-minute
     * data is ~6 MB, so callers ask for one month at a time — same as the iOS history layer.
     */
    suspend fun fetchRange(start: Instant, end: Instant): String = fetch(start, end)

    private suspend fun fetch(start: Instant, end: Instant): String = withContext(Dispatchers.IO) {
        val url = URL(
            "$endpoint?start=${MarketDay.formatUtc(start)}&end=${MarketDay.formatUtc(end)}",
        )
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 15_000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Accept-Encoding", "gzip")
        }
        try {
            val status = connection.responseCode
            if (status != HttpURLConnection.HTTP_OK) {
                throw PriceApiException("Elering responded with $status", status)
            }
            val raw = connection.inputStream.use { it.readBytes() }
            val bytes =
                if (connection.contentEncoding == "gzip") GZIPInputStream(raw.inputStream()).use { it.readBytes() } else raw
            bytes.toString(Charsets.UTF_8)
        } finally {
            connection.disconnect()
        }
    }

    fun decode(payload: String, region: Region): List<PricePoint> =
        toPoints(json.decodeFromString<NordPoolResponseDto>(payload).data.listFor(region))

    /** Full four-country payload, used by the history aggregation. */
    fun decodeAll(payload: String): Map<Region, List<PricePoint>> {
        val countries = json.decodeFromString<NordPoolResponseDto>(payload).data
        return Region.entries.associateWith { toPoints(countries.listFor(it)) }
    }

    private fun toPoints(items: List<PriceDto>?): List<PricePoint> =
        items.orEmpty().map { PricePoint(it.timestamp.toLong(), it.price) }

    companion object {
        const val PRODUCTION_ENDPOINT = "https://dashboard.elering.ee/api/nps/price"
    }
}

private fun CountriesDto.listFor(region: Region): List<PriceDto>? = when (region) {
    Region.ESTONIA -> ee
    Region.LATVIA -> lv
    Region.LITHUANIA -> lt
    Region.FINLAND -> fi
}
