package koodipardik.nordprice.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.BubbleChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Title
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import koodipardik.nordprice.BuildConfig
import koodipardik.nordprice.R
import koodipardik.nordprice.core.ChartResolution
import koodipardik.nordprice.core.ChartType
import koodipardik.nordprice.core.Language
import koodipardik.nordprice.core.PriceFormat
import koodipardik.nordprice.core.PriceUnit
import koodipardik.nordprice.core.Region
import koodipardik.nordprice.ui.components.CardHorizontalInset
import koodipardik.nordprice.ui.components.appCard
import koodipardik.nordprice.ui.components.unitLabel
import koodipardik.nordprice.ui.rememberContainer
import koodipardik.nordprice.ui.rememberSettings
import koodipardik.nordprice.ui.theme.AppTheme

/**
 * Settings, ported from the iOS `Form`: the same sections, rows, icons and footers. The iOS
 * `navigationLink` pickers become bottom sheets, which is how Android presents the same choice.
 */
@Composable
fun SettingsScreen() {
    val container = rememberContainer()
    val settings = rememberSettings()

    var languageSheet by remember { mutableStateOf(false) }
    var regionSheet by remember { mutableStateOf(false) }
    var unitSheet by remember { mutableStateOf(false) }
    var chartTypeSheet by remember { mutableStateOf(false) }
    var resolutionSheet by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = CardHorizontalInset, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.title_settings),
            fontSize = 32.sp,
            fontWeight = FontWeight.Medium,
            color = AppTheme.textOnBackground,
            modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
        )

        Section(title = stringResource(R.string.title_general)) {
            OptionRow(
                icon = Icons.Filled.Language,
                tint = AppTheme.brand,
                label = stringResource(R.string.title_language),
                value = stringResource(settings.language.nameRes),
                onClick = { languageSheet = true },
            )
            Divider()
            OptionRow(
                icon = Icons.Filled.Flag,
                tint = Color(0xFFF43F5E),
                label = stringResource(R.string.title_region),
                value = stringResource(settings.region.nameRes),
                onClick = { regionSheet = true },
            )
        }

        Section(title = stringResource(R.string.title_display)) {
            OptionRow(
                icon = Icons.Filled.AttachMoney,
                tint = Color(0xFFF59E0B),
                label = stringResource(R.string.title_unit),
                value = unitLabel(settings.unit),
                onClick = { unitSheet = true },
            )
            Divider()
            ToggleRow(
                icon = Icons.Filled.Percent,
                tint = Color(0xFF10B981),
                label = stringResource(R.string.title_include_tax),
                checked = settings.includeTax,
                onChange = container.settings::setIncludeTax,
            )
            Divider()
            InfoRow(
                icon = Icons.Filled.Title,
                tint = Color.Gray,
                label = stringResource(R.string.title_vat),
                value = settings.taxPercentage,
            )
            Divider()
            OptionRow(
                icon = Icons.Filled.BubbleChart,
                tint = Color(0xFF8B5CF6),
                label = stringResource(R.string.title_chart_type),
                value = stringResource(chartTypeLabel(settings.chartType)),
                onClick = { chartTypeSheet = true },
            )
            Divider()
            OptionRow(
                icon = Icons.Filled.Speed,
                tint = AppTheme.brand,
                label = stringResource(R.string.title_chart_resolution),
                value = settings.chartResolution.label,
                onClick = { resolutionSheet = true },
            )
            Divider()
            ToggleRow(
                icon = Icons.Filled.ColorLens,
                tint = Color(0xFFEC4899),
                label = stringResource(R.string.title_colorful_chart),
                checked = settings.colorfulChart,
                onChange = container.settings::setColorfulChart,
            )
            Divider()
            ToggleRow(
                icon = Icons.Filled.SettingsBrightness,
                tint = Color(0xFFFACC15),
                label = stringResource(R.string.title_always_on_display),
                checked = settings.alwaysOnDisplay,
                onChange = container.settings::setAlwaysOnDisplay,
            )
        }

        if (BuildConfig.DEBUG) {
            Section(title = "DEBUG") {
                ToggleRow(
                    icon = Icons.Filled.Science,
                    tint = Color(0xFFEC4899),
                    label = stringResource(R.string.debug_sample_data),
                    checked = settings.useSampleData,
                    onChange = container.settings::setUseSampleData,
                )
            }
        }

        Section(title = stringResource(R.string.title_notifications)) {
            ToggleRow(
                icon = Icons.Filled.NotificationsActive,
                tint = Color(0xFFF43F5E),
                label = stringResource(R.string.label_notify_max),
                checked = settings.notifyMaxEnabled,
                onChange = { container.settings.setNotifyMaxEnabled(it) },
            )
            if (settings.notifyMaxEnabled) {
                Divider()
                ThresholdRow(
                    label = stringResource(R.string.label_threshold) + " (${settings.unit.rawKey})",
                    value = settings.notifyMaxDisplay,
                    unit = settings.unit,
                    onCommit = container.settings::setNotifyMaxThreshold,
                )
            }
            Divider()
            ToggleRow(
                icon = Icons.Filled.NotificationsActive,
                tint = Color(0xFFF43F5E),
                label = stringResource(R.string.label_notify_min),
                checked = settings.notifyMinEnabled,
                onChange = { container.settings.setNotifyMinEnabled(it) },
            )
            if (settings.notifyMinEnabled) {
                Divider()
                ThresholdRow(
                    label = stringResource(R.string.label_threshold) + " (${settings.unit.rawKey})",
                    value = settings.notifyMinDisplay,
                    unit = settings.unit,
                    onCommit = container.settings::setNotifyMinThreshold,
                )
            }
        }

        Section(title = stringResource(R.string.title_about)) {
            InfoRow(
                icon = Icons.Filled.Info,
                tint = Color.Gray,
                label = stringResource(R.string.title_version),
                value = BuildConfig.VERSION_NAME,
            )
            Divider()
            Footnote(stringResource(R.string.text_information_about_app))
            Footnote(stringResource(R.string.text_information_about_market_time_unit))
        }

        // Phase 2: the AdMob banner slot and the UMP "Privacy options" entry live here.
        Spacer(Modifier.height(16.dp))
    }

    if (languageSheet) {
        ChoiceSheet(
            title = stringResource(R.string.title_language),
            options = Language.entries.map { it.code to stringResource(it.nameRes) },
            selected = settings.language.code,
            onDismiss = { languageSheet = false },
            onSelect = { container.settings.setLanguage(Language.fromCode(it)) },
        )
    }
    if (regionSheet) {
        ChoiceSheet(
            title = stringResource(R.string.title_region),
            options = Region.entries.map { it.code to stringResource(it.nameRes) },
            selected = settings.region.code,
            onDismiss = { regionSheet = false },
            onSelect = { container.settings.setRegion(Region.fromCode(it)) },
        )
    }
    if (unitSheet) {
        ChoiceSheet(
            title = stringResource(R.string.title_unit),
            options = PriceUnit.entries.map { it.rawKey to unitLabel(it) },
            selected = settings.unit.rawKey,
            onDismiss = { unitSheet = false },
            onSelect = { key -> container.settings.setUnit(PriceUnit.fromKey(key)) },
        )
    }
    if (chartTypeSheet) {
        ChoiceSheet(
            title = stringResource(R.string.title_chart_type),
            options = ChartType.entries.map { it.key to stringResource(chartTypeLabel(it)) },
            selected = settings.chartType.key,
            onDismiss = { chartTypeSheet = false },
            onSelect = { container.settings.setChartType(ChartType.fromKey(it)) },
        )
    }
    if (resolutionSheet) {
        ChoiceSheet(
            title = stringResource(R.string.title_chart_resolution),
            options = ChartResolution.entries.map { it.key to it.label },
            selected = settings.chartResolution.key,
            onDismiss = { resolutionSheet = false },
            onSelect = { container.settings.setChartResolution(ChartResolution.fromKey(it)) },
        )
    }
}

private fun chartTypeLabel(type: ChartType): Int = when (type) {
    ChartType.BAR -> R.string.chart_type_bar
    ChartType.LINE -> R.string.chart_type_line
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .appCard(cornerRadius = 14.dp),
    ) {
        Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = AppTheme.cardLabel,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
        )
        content()
    }
}

@Composable
private fun Divider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 56.dp)
            .height(1.dp)
            .background(AppTheme.cardStroke),
    )
}

/** Square tinted icon chip, the iOS `SettingsIcon`. */
@Composable
private fun RowIcon(icon: ImageVector, tint: Color) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .background(tint, RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun OptionRow(icon: ImageVector, tint: Color, label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 52.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowIcon(icon, tint)
        Spacer(Modifier.width(12.dp))
        Text(label, fontSize = 17.sp, color = AppTheme.cardText)
        Spacer(Modifier.weight(1f))
        Text(value, fontSize = 17.sp, color = AppTheme.secondaryText)
    }
}

@Composable
private fun InfoRow(icon: ImageVector, tint: Color, label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowIcon(icon, tint)
        Spacer(Modifier.width(12.dp))
        Text(label, fontSize = 17.sp, color = AppTheme.cardText)
        Spacer(Modifier.weight(1f))
        Text(value, fontSize = 17.sp, color = AppTheme.secondaryText)
    }
}

@Composable
private fun ToggleRow(icon: ImageVector, tint: Color, label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable { onChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowIcon(icon, tint)
        Spacer(Modifier.width(12.dp))
        Text(label, fontSize = 17.sp, color = AppTheme.cardText, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = AppTheme.brand,
                checkedThumbColor = Color.White,
            ),
        )
    }
}

/**
 * Threshold editor. It keeps its own text while typing and only converts to the stored raw
 * €/MWh value on commit, so a half-typed "0," does not round away.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThresholdRow(label: String, value: Double, unit: PriceUnit, onCommit: (Double) -> Unit) {
    var text by remember(value) { mutableStateOf(PriceFormat.format(value, unit)) }

    // iOS commits from UITextFieldDelegate.textFieldDidEndEditing; the equivalent moments here
    // are the keyboard's Done action and the field leaving composition. Committing on every
    // keystroke would round away a half-typed "0,".
    fun commit() {
        val parsed = PriceFormat.parse(text)
        if (parsed != null) onCommit(parsed) else text = PriceFormat.format(value, unit)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 17.sp, color = AppTheme.cardText, modifier = Modifier.weight(1f))
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.width(130.dp),
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(
                fontSize = 17.sp,
                color = AppTheme.cardText,
            ),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                onDone = { commit() },
            ),
        )
    }

    DisposableEffect(Unit) {
        onDispose { commit() }
    }
}

@Composable
private fun Footnote(text: String) {
    Text(
        text = text,
        fontSize = 13.sp,
        color = AppTheme.secondaryText,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChoiceSheet(
    title: String,
    options: List<Pair<String, String>>,
    selected: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        containerColor = AppTheme.card,
    ) {
        Column(Modifier.padding(bottom = 16.dp)) {
            Text(
                text = title,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = AppTheme.cardText,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            options.forEach { (key, label) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onSelect(key)
                            onDismiss()
                        }
                        .heightIn(min = 52.dp)
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(label, fontSize = 17.sp, color = AppTheme.cardText, modifier = Modifier.weight(1f))
                    if (key == selected) {
                        Text(
                            text = "✓",
                            fontSize = 17.sp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

private typealias ColumnScope = androidx.compose.foundation.layout.ColumnScope
