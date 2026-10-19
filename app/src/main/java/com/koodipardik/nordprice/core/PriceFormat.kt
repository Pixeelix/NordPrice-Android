package com.koodipardik.nordprice.core

import androidx.annotation.StringRes
import com.koodipardik.nordprice.R
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Display unit. [rawKey] is what gets persisted (identical to the iOS `unit` string, so a
 * future settings import keeps working) and [labelRes] is the localised name — Finnish shows
 * "senti/kWh", Russian "цент/kWh".
 */
enum class PriceUnit(
    val rawKey: String,
    @param:StringRes val labelRes: Int,
    /** The API answers in €/MWh; this turns that into the chosen unit. */
    val divider: Double,
    val fractionDigits: Int,
) {
    EUR_PER_KWH("€/kWh", R.string.unit_eur_kwh, 1000.0, 4),
    EUR_PER_MWH("€/MWh", R.string.unit_eur_mwh, 1.0, 1),
    CENT_PER_KWH("cent/kWh", R.string.unit_cent_kwh, 10.0, 1);

    companion object {
        fun fromKey(key: String?): PriceUnit =
            entries.firstOrNull { it.rawKey == key || it.name.equals(key, ignoreCase = true) }
                ?: EUR_PER_KWH
    }
}

/**
 * Number formatting for prices.
 *
 * The decimal separator is always a comma and there is no thousands separator, matching the
 * iOS `NumberFormatter` exactly — a price must read the same on both platforms, and the
 * iPhone app deliberately does not follow the locale here.
 */
object PriceFormat {

    private val symbols = DecimalFormatSymbols(Locale.ROOT).apply {
        decimalSeparator = ','
        groupingSeparator = ' '
    }

    private fun formatter(digits: Int): DecimalFormat =
        DecimalFormat("0." + "0".repeat(digits), symbols).apply { isGroupingUsed = false }

    fun format(value: Double, unit: PriceUnit): String = formatter(unit.fractionDigits).format(value)

    /**
     * Y-axis labels are terser than the cards: two decimals for cent-range numbers, whole
     * numbers for the big €/MWh scale — same rule as the iOS chart.
     */
    fun axisLabel(value: Double, unit: PriceUnit): String =
        if (unit == PriceUnit.EUR_PER_MWH) DecimalFormat("0", symbols).format(value)
        else DecimalFormat("0.00", symbols).format(value)

    /**
     * Parses user input from the threshold field. Accepts both "," and "." so a keyboard
     * that types a dot still works.
     */
    fun parse(text: String): Double? = text.trim()
        .replace(" ", "")
        .replace(',', '.')
        .toDoubleOrNull()
}
