package com.koodipardik.nordprice.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.koodipardik.nordprice.R
import com.koodipardik.nordprice.core.Region
import com.koodipardik.nordprice.core.PriceUnit
import com.koodipardik.nordprice.ui.theme.AppTheme

/** Screen title — the iOS `TitleView`: 32 pt medium, sitting on the page gradient. */
@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier
            .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 12.dp)
            .semantics { heading() },
        fontSize = 32.sp,
        fontWeight = FontWeight.Medium,
        color = AppTheme.textOnBackground,
        textAlign = TextAlign.Center,
    )
}

/**
 * The flag that opens the region picker. Small artwork, 44 dp of touch target, and the
 * region name is what screen readers actually hear.
 */
@Composable
fun RegionFlagButton(region: Region, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(region.nameRes)
    val hint = stringResource(R.string.a11y_change_region)
    Box(
        modifier = modifier
            .heightIn(min = 44.dp)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "$label, $hint" },
        contentAlignment = Alignment.CenterStart,
    ) {
        Image(
            painter = painterResource(region.flagRes),
            contentDescription = null,
            modifier = Modifier
                .size(width = 30.dp, height = 22.dp)
                .clip(RoundedCornerShape(6.dp))
                .shadow(5.dp, RoundedCornerShape(6.dp)),
            contentScale = ContentScale.Crop,
        )
    }
}

/**
 * The big price card: flag and timestamp on top, the number filling the middle, the unit under
 * it. Shared by today's current price, tomorrow's min–max range and the history average — the
 * three `heroCardStyle` cards in the iOS app.
 */
@Composable
fun HeroPriceCard(
    /** Left slot of the header row; usually the region flag or the "previous period" arrow. */
    leading: @Composable () -> Unit,
    /** Middle of the header row; the period title in history. */
    center: (@Composable () -> Unit)? = null,
    /** Right slot; the current-price time on today, the "next period" arrow in history. */
    trailing: (@Composable () -> Unit)? = null,
    value: String,
    unit: String,
    modifier: Modifier = Modifier,
    valueAccessibilityLabel: String? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp)
            .appCard(cornerRadius = 16.dp)
            .padding(bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 22.dp)
                .padding(start = 10.dp, end = 10.dp, top = 8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            leading()
            Spacer(Modifier.weight(1f))
            center?.let { Box(Modifier.align(Alignment.CenterVertically)) { it() } }
            Spacer(Modifier.weight(1f))
            trailing?.let {
                Box(modifier = Modifier.align(Alignment.CenterVertically).clearAndSetSemantics { }) { it() }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(62.dp)
                .padding(horizontal = 24.dp)
                .semantics(mergeDescendants = true) {
                    valueAccessibilityLabel?.let { contentDescription = "$it $value $unit" }
                },
            contentAlignment = Alignment.Center,
        ) {
            FitText(
                text = value,
                color = AppTheme.cardText,
                fontWeight = FontWeight.Medium,
                maxFontSize = 52.sp,
                minFontSize = 12.sp,
            )
        }

        Text(
            text = unit,
            fontSize = 24.sp,
            fontWeight = FontWeight.Medium,
            color = AppTheme.cardText,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

/** Min / average / max card. At large text sizes the three columns stack instead. */
@Composable
fun MinAvgMaxCard(
    min: String,
    avg: String,
    max: String,
    modifier: Modifier = Modifier,
) {
    val items = listOf(
        stringResource(R.string.title_min) to min,
        stringResource(R.string.title_avg) to avg,
        stringResource(R.string.title_max) to max,
    )
    val stacked = isLargeText()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .appCard(cornerRadius = 14.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        if (stacked) {
            items.forEach { (label, value) ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    CardLabel(label)
                    Spacer(Modifier.weight(1f))
                    CardValue(value)
                }
            }
        } else {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                items.forEachIndexed { index, (label, value) ->
                    if (index > 0) Spacer(Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CardLabel(label)
                        CardValue(value)
                    }
                }
            }
        }
    }
}

/** Cheapest / dearest pair used by the history tab, with the date under each value. */
@Composable
fun HistoryMinMaxCard(
    cheapestLabel: String,
    cheapestValue: String,
    cheapestDate: String,
    expensiveLabel: String,
    expensiveValue: String,
    expensiveDate: String,
    showLevelDots: Boolean,
    cheapestDot: androidx.compose.ui.graphics.Color?,
    expensiveDot: androidx.compose.ui.graphics.Color?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .appCard(cornerRadius = 14.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            HistoryEnd(
                label = cheapestLabel,
                value = cheapestValue,
                date = cheapestDate,
                dot = if (showLevelDots) cheapestDot else null,
                modifier = Modifier.weight(1f),
            )
            HistoryEnd(
                label = expensiveLabel,
                value = expensiveValue,
                date = expensiveDate,
                dot = if (showLevelDots) expensiveDot else null,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun HistoryEnd(
    label: String,
    value: String,
    date: String,
    dot: androidx.compose.ui.graphics.Color?,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        CardLabel(label)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (dot != null) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(dot),
                )
                Spacer(Modifier.width(6.dp))
            }
            CardValue(value)
        }
        Text(text = date, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = AppTheme.secondaryText)
    }
}

@Composable
private fun CardLabel(text: String) {
    Text(
        text = text,
        fontSize = 18.sp,
        fontWeight = FontWeight.Medium,
        color = AppTheme.cardLabel,
    )
}

@Composable
private fun CardValue(text: String) {
    Text(text = text, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AppTheme.cardText)
}

/** The localised name of a price unit, e.g. "senti/kWh" in Finnish. */
@Composable
fun unitLabel(unit: PriceUnit): String = stringResource(unit.labelRes)

/**
 * Stand-in for iOS Dynamic Type accessibility sizes: at a large system font the three
 * columns no longer fit, so cards stack their rows instead.
 */
@Composable
fun isLargeText(): Boolean = LocalDensity.current.fontScale >= 1.35f
