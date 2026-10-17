package koodipardik.nordprice.core

import androidx.annotation.StringRes
import koodipardik.nordprice.R
import java.time.ZoneId

/** Which day of prices a screen or a cache entry is about. */
enum class Day { TODAY, TOMORROW }

/** Nord Pool markets the app covers. [code] matches the Elering API and the iOS enum raw value. */
enum class Region(
    val code: String,
    @param:StringRes val nameRes: Int,
    val zoneId: ZoneId,
    val flagRes: Int,
) {
    ESTONIA("EE", R.string.estonia, ZoneId.of("Europe/Tallinn"), R.drawable.flag_ee),
    LATVIA("LV", R.string.latvia, ZoneId.of("Europe/Riga"), R.drawable.flag_lv),
    LITHUANIA("LT", R.string.lithuania, ZoneId.of("Europe/Vilnius"), R.drawable.flag_lt),
    FINLAND("FI", R.string.finland, ZoneId.of("Europe/Helsinki"), R.drawable.flag_fi);

    /** Lowercase key used in cache keys and in the API payload. */
    val cacheKey: String get() = code.lowercase()

    /** VAT multiplier for this market: 1.24 for Estonia's 24%. */
    val taxRate: Double
        get() = when (this) {
            ESTONIA -> 1.24
            LATVIA -> 1.21
            LITHUANIA -> 1.21
            FINLAND -> 1.255
        }

    /** VAT shown as text in Settings. Comma decimal separator, as everywhere in the app. */
    val taxPercentage: String
        get() = when (this) {
            ESTONIA -> "24%"
            LATVIA -> "21%"
            LITHUANIA -> "21%"
            FINLAND -> "25,5%"
        }

    companion object {
        fun fromCode(code: String): Region =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: ESTONIA
    }
}

/** In-app language override. The iOS app offers these four regardless of the system language. */
enum class Language(val code: String, @param:StringRes val nameRes: Int) {
    ESTONIAN("et", R.string.estonian),
    ENGLISH("en", R.string.english),
    FINNISH("fi", R.string.finnish),
    RUSSIAN("ru", R.string.russian);

    companion object {
        fun fromCode(code: String?): Language =
            entries.firstOrNull { it.code == code } ?: ESTONIAN

        /** Mirrors the iOS locale guess: a device in Estonia gets Estonian, and so on. */
        fun fromDeviceLocale(languageTag: String, countryTag: String): Language = when {
            languageTag == "et" -> ESTONIAN
            languageTag == "fi" -> FINNISH
            languageTag == "ru" -> RUSSIAN
            languageTag == "en" -> ENGLISH
            countryTag == "EE" -> ESTONIAN
            countryTag == "FI" -> FINNISH
            countryTag == "RU" -> RUSSIAN
            else -> ENGLISH
        }
    }
}

enum class ChartType {
    BAR, LINE;

    companion object {
        fun fromKey(key: String?): ChartType = when (key) {
            "line" -> LINE
            else -> BAR
        }
    }

    val key: String get() = name.lowercase()
}

/**
 * Interval the market publishes and the chart draws. The name in the UI is "market time unit",
 * because that is what it is called in the electricity contract.
 */
enum class ChartResolution(val key: String, val label: String) {
    FIFTEEN_MINUTES("15min", "15 min"),
    ONE_HOUR("1h", "1 h");

    companion object {
        fun fromKey(key: String?): ChartResolution =
            entries.firstOrNull { it.key == key } ?: ONE_HOUR
    }
}

/**
 * Absolute price level, shared with the iOS app and the nordprice landing page so a "cheap"
 * bar means the same thing everywhere. Thresholds are raw Nord Pool €/MWh (5 / 10 / 20 c/kWh)
 * and are applied *before* unit conversion and VAT, so colors stay stable when the user
 * switches units or toggles tax.
 */
enum class PriceLevel(val labelRes: Int, val colorHex: Long) {
    VERY_CHEAP(R.string.level_very_cheap, 0xFF10B981),
    CHEAP(R.string.level_cheap, 0xFF5C6EF5),
    NORMAL(R.string.level_normal, 0xFFF59E0B),
    EXPENSIVE(R.string.level_expensive, 0xFFF43F5E);

    companion object {
        const val CHEAP_UPPER_MWH = 50.0
        const val NORMAL_UPPER_MWH = 100.0
        const val EXPENSIVE_LOWER_MWH = 200.0

        fun of(rawMWh: Double): PriceLevel = when {
            rawMWh < CHEAP_UPPER_MWH -> VERY_CHEAP
            rawMWh < NORMAL_UPPER_MWH -> CHEAP
            rawMWh < EXPENSIVE_LOWER_MWH -> NORMAL
            else -> EXPENSIVE
        }
    }
}
