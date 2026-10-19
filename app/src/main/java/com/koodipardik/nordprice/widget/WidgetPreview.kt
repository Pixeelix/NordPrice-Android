package com.koodipardik.nordprice.widget

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.koodipardik.nordprice.R
import com.koodipardik.nordprice.core.Day
import com.koodipardik.nordprice.ui.rememberContainer
import com.koodipardik.nordprice.ui.rememberSettings
import com.koodipardik.nordprice.ui.theme.AppTheme

/**
 * Debug-only look at what the home-screen widget would draw, using the widget's own code path:
 * the same snapshot builder, the same chart style and the same bitmap painter.
 *
 * A Glance layout cannot be composed inside the running app, so the text row here is drawn with
 * ordinary Compose — what this actually verifies is the chart, which is the part with real logic.
 * Placing the widget on a launcher is still the only end-to-end check.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetPreviewSheet(onDismiss: () -> Unit) {
    val container = rememberContainer()
    val settings = rememberSettings()
    val context = LocalContext.current
    val density = LocalDensity.current.density
    var snapshot by remember { mutableStateOf<WidgetSnapshot?>(null) }

    LaunchedEffect(settings.region, settings.unit, settings.includeTax, settings.chartType, settings.chartResolution) {
        val points = runCatching {
            container.priceRepository.loadFullDayData(Day.TODAY, settings.region)
        }.getOrElse { container.priceRepository.loadCached(Day.TODAY, settings.region) }
        snapshot = WidgetSnapshotBuilder.build(
            settings = settings,
            points = points,
            unitLabel = context.getString(settings.unit.labelRes),
        )
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = AppTheme.card) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                text = stringResource(R.string.title_widget_preview),
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = AppTheme.cardText,
            )

            val current = snapshot
            if (current == null) {
                Text(stringResource(R.string.loading), fontSize = 13.sp, color = AppTheme.secondaryText)
            } else {
                PreviewCard("2 × 2", current, 140.dp, 60.dp, density)
                PreviewCard("4 × 2", current, 320.dp, 80.dp, density)
                Text(
                    text = stringResource(R.string.hint_widget_preview_chart_only),
                    fontSize = 12.sp,
                    color = AppTheme.secondaryText,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PreviewCard(
    label: String,
    snapshot: WidgetSnapshot,
    chartWidth: androidx.compose.ui.unit.Dp,
    chartHeight: androidx.compose.ui.unit.Dp,
    density: Float,
) {
    val palette = widgetPalette(LocalContext.current)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, fontSize = 12.sp, color = AppTheme.secondaryText)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(palette.background)
                .padding(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.text_current_price),
                    fontSize = 13.sp,
                    color = palette.colors.secondaryText,
                )
                Spacer(Modifier.weight(1f))
                Text(text = snapshot.timeText, fontSize = 11.sp, color = palette.colors.secondaryText)
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = snapshot.priceText,
                    fontSize = if (chartHeight > 70.dp) 42.sp else 34.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.colors.cardText,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = snapshot.unitLabel,
                    fontSize = 13.sp,
                    color = palette.colors.secondaryText,
                )
            }
            if (snapshot.values.isNotEmpty()) {
                Image(
                    bitmap = WidgetChartImage
                        .render(palette.colors, snapshot, chartWidth, chartHeight, density)
                        .asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .width(chartWidth)
                        .height(chartHeight),
                    contentScale = ContentScale.FillBounds,
                )
            }
        }
    }
}
