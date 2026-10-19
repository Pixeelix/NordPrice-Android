package com.koodipardik.nordprice.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.koodipardik.nordprice.ui.history.HistoryPeriod
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Dates follow the *in-app* language, not the device one — the same rule the iOS formatters use
 * (`formatter.locale = getLocale()` from the selected language).
 */
@Composable
fun rememberAppLocale(): Locale {
    val tag = currentLanguageTag()
    return remember(tag) { Locale.forLanguageTag(tag) }
}

@Composable
private fun formatterFor(pattern: String): DateTimeFormatter {
    val locale = rememberAppLocale()
    return remember(locale, pattern) { DateTimeFormatter.ofPattern(pattern, locale) }
}

/** Chart header: "17. oktoober" / "17 October". */
@Composable
fun dayTitle(date: LocalDate): String = formatterFor("d. MMMM").format(date)

/** Title above the history arrows: "5 Oct – 11 Oct", "Oktoober 2026", "2026". */
@Composable
fun periodTitle(start: LocalDate, endExclusive: LocalDate, period: HistoryPeriod): String {
    val locale = rememberAppLocale()
    return when (period) {
        HistoryPeriod.WEEK -> {
            val short = formatterFor("d MMM")
            "${short.format(start)} – ${short.format(endExclusive.minusDays(1))}"
        }

        HistoryPeriod.MONTH -> start.month
            .getDisplayName(TextStyle.FULL, locale)
            .replaceFirstChar { it.titlecase(locale) } + " ${start.year}"

        HistoryPeriod.YEAR -> start.year.toString()
    }
}

/** Full date of one history bar: "E, 5 Oct" for days, "October 2026" for months. */
@Composable
fun barDate(date: LocalDate, period: HistoryPeriod): String =
    formatterFor(if (period == HistoryPeriod.YEAR) "MMMM yyyy" else "EEE d MMM").format(date)

/** Short date under the min/max values: "5 Oct" or "Oct". */
@Composable
fun shortBarDate(date: LocalDate, period: HistoryPeriod): String =
    formatterFor(if (period == HistoryPeriod.YEAR) "MMM" else "d MMM").format(date)

/** X-axis label of one history bar. */
@Composable
fun historyAxisLabel(date: LocalDate, period: HistoryPeriod): String {
    val pattern = when (period) {
        HistoryPeriod.WEEK -> "EEE"
        HistoryPeriod.MONTH -> "d"
        HistoryPeriod.YEAR -> "MMM"
    }
    return formatterFor(pattern).format(date)
}
