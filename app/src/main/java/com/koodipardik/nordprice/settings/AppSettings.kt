package com.koodipardik.nordprice.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.koodipardik.nordprice.core.ChartResolution
import com.koodipardik.nordprice.core.ChartType
import com.koodipardik.nordprice.core.Language
import com.koodipardik.nordprice.core.Region
import com.koodipardik.nordprice.core.PriceUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale

/** Everything the user can change, and everything the UI reads. */
data class Settings(
    val language: Language = Language.ESTONIAN,
    val region: Region = Region.ESTONIA,
    val unit: PriceUnit = PriceUnit.EUR_PER_KWH,
    val includeTax: Boolean = false,
    val chartType: ChartType = ChartType.BAR,
    val chartResolution: ChartResolution = ChartResolution.ONE_HOUR,
    /** Off restores the original single-colour chart, without the level legend. */
    val colorfulChart: Boolean = true,
    /** Keeps the screen awake while a price screen is open. */
    val alwaysOnDisplay: Boolean = false,
    val notifyMaxEnabled: Boolean = false,
    /** Thresholds are stored raw €/MWh so toggling unit or VAT never changes the alert. */
    val notifyMaxRawMWh: Double = 200.0,
    val notifyMinEnabled: Boolean = false,
    val notifyMinRawMWh: Double = 0.0,
    /** Debug builds only: serve the built-in sample prices instead of the network. */
    val useSampleData: Boolean = false,
) {
    val taxRate: Double get() = region.taxRate
    val taxPercentage: String get() = region.taxPercentage

    private val taxMultiplier: Double get() = if (includeTax) taxRate else 1.0

    /** Threshold in the unit the user sees. */
    val notifyMaxDisplay: Double get() = notifyMaxRawMWh * taxMultiplier / unit.divider
    val notifyMinDisplay: Double get() = notifyMinRawMWh * taxMultiplier / unit.divider

    /** …and back, the same round trip the iOS `notifyMaxDisplay` setter performs. */
    fun notifyMaxRawFor(display: Double): Double = display * unit.divider / taxMultiplier
    fun notifyMinRawFor(display: Double): Double = display * unit.divider / taxMultiplier
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "nordprice_settings")

/**
 * User preferences — the Android counterpart of the iOS `AppSettings` ObservableObject.
 *
 * iOS persists through `UserDefaults` and mirrors what the widget needs into the App Group
 * suite. Here the store is one [StateFlow] that both the UI and, later, the Glance widget read.
 * Writes update the in-memory value synchronously and hit disk on an IO dispatcher, which is
 * the immediate-`didSet` behaviour every screen relies on.
 */
class AppSettings(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _settings = MutableStateFlow(deviceDefaults())
    val state: StateFlow<Settings> = _settings.asStateFlow()

    /** Called after a change the home-screen widget renders (region, unit, tax, chart…). */
    var onWidgetAffectingChange: (() -> Unit)? = null

    /** Called after any change the notification backend needs to hear about. */
    var onSettingsChanged: (() -> Unit)? = null

    val current: Settings get() = _settings.value

    /** Reads persisted values. Until this returns, the app runs on device-derived defaults. */
    suspend fun load() {
        val prefs = runCatching { context.dataStore.data.first() }.getOrNull() ?: return
        _settings.value = read(prefs)
    }

    private fun read(prefs: Preferences): Settings {
        val defaults = deviceDefaults()
        return Settings(
            language = Language.fromCode(prefs[stringPreferencesKey(KEY_LANGUAGE)] ?: defaults.language.code),
            region = Region.fromCode(prefs[stringPreferencesKey(KEY_REGION)] ?: defaults.region.code),
            unit = PriceUnit.fromKey(prefs[stringPreferencesKey(KEY_UNIT)] ?: defaults.unit.rawKey),
            includeTax = prefs[booleanPreferencesKey(KEY_INCLUDE_TAX)] ?: false,
            chartType = ChartType.fromKey(prefs[stringPreferencesKey(KEY_CHART_TYPE)]),
            chartResolution = ChartResolution.fromKey(prefs[stringPreferencesKey(KEY_CHART_RESOLUTION)]),
            colorfulChart = prefs[booleanPreferencesKey(KEY_COLORFUL)] ?: true,
            alwaysOnDisplay = prefs[booleanPreferencesKey(KEY_ALWAYS_ON)] ?: false,
            notifyMaxEnabled = prefs[booleanPreferencesKey(KEY_NOTIFY_MAX_ENABLED)] ?: false,
            notifyMaxRawMWh = prefs[doublePreferencesKey(KEY_NOTIFY_MAX_RAW)] ?: 200.0,
            notifyMinEnabled = prefs[booleanPreferencesKey(KEY_NOTIFY_MIN_ENABLED)] ?: false,
            notifyMinRawMWh = prefs[doublePreferencesKey(KEY_NOTIFY_MIN_RAW)] ?: 0.0,
            useSampleData = prefs[booleanPreferencesKey(KEY_SAMPLE_DATA)] ?: false,
        )
    }

    /** Region and language are guessed from the device on first launch, as on iOS. */
    private fun deviceDefaults(): Settings {
        val locale: Locale = Locale.getDefault()
        val country = locale.country.uppercase()
        return Settings(
            region = Region.entries.firstOrNull { it.code == country } ?: Region.ESTONIA,
            language = Language.fromDeviceLocale(locale.language, country),
        )
    }

    fun setLanguage(value: Language) = mutate { it.copy(language = value) }
    fun setRegion(value: Region) = mutate { it.copy(region = value) }
    fun setUnit(value: PriceUnit) = mutate { it.copy(unit = value) }
    fun setIncludeTax(value: Boolean) = mutate { it.copy(includeTax = value) }
    fun setChartType(value: ChartType) = mutate { it.copy(chartType = value) }
    fun setChartResolution(value: ChartResolution) = mutate { it.copy(chartResolution = value) }
    fun setColorfulChart(value: Boolean) = mutate { it.copy(colorfulChart = value) }
    fun setAlwaysOnDisplay(value: Boolean) = mutate { it.copy(alwaysOnDisplay = value) }
    fun setNotifyMaxEnabled(value: Boolean) = mutate { it.copy(notifyMaxEnabled = value) }
    fun setNotifyMinEnabled(value: Boolean) = mutate { it.copy(notifyMinEnabled = value) }
    fun setNotifyMaxThreshold(display: Double) = mutate { it.copy(notifyMaxRawMWh = it.notifyMaxRawFor(display)) }
    fun setNotifyMinThreshold(display: Double) = mutate { it.copy(notifyMinRawMWh = it.notifyMinRawFor(display)) }
    fun setUseSampleData(value: Boolean) = mutate { it.copy(useSampleData = value) }

    /**
     * Applies a change and tells the widget and the backend. iOS splits this per setter —
     * `saveRegion()` reloads widget timelines, `saveAlwaysOnDisplay()` does not — the same
     * triage happens here by diffing what actually moved.
     */
    private fun mutate(transform: (Settings) -> Settings) {
        val before = _settings.value
        val after = transform(before)
        if (after == before) return
        _settings.value = after
        persist(after)

        val affectsWidget = before.region != after.region ||
            before.language != after.language ||
            before.unit != after.unit ||
            before.includeTax != after.includeTax ||
            before.chartType != after.chartType ||
            before.chartResolution != after.chartResolution
        if (affectsWidget) onWidgetAffectingChange?.invoke()
        onSettingsChanged?.invoke()
    }

    private fun persist(settings: Settings) {
        scope.launch {
            context.dataStore.edit { prefs ->
                prefs[key(KEY_LANGUAGE)] = settings.language.code
                prefs[key(KEY_REGION)] = settings.region.code
                prefs[key(KEY_UNIT)] = settings.unit.rawKey
                prefs[booleanPreferencesKey(KEY_INCLUDE_TAX)] = settings.includeTax
                prefs[key(KEY_CHART_TYPE)] = settings.chartType.key
                prefs[key(KEY_CHART_RESOLUTION)] = settings.chartResolution.key
                prefs[booleanPreferencesKey(KEY_COLORFUL)] = settings.colorfulChart
                prefs[booleanPreferencesKey(KEY_ALWAYS_ON)] = settings.alwaysOnDisplay
                prefs[booleanPreferencesKey(KEY_NOTIFY_MAX_ENABLED)] = settings.notifyMaxEnabled
                prefs[doublePreferencesKey(KEY_NOTIFY_MAX_RAW)] = settings.notifyMaxRawMWh
                prefs[booleanPreferencesKey(KEY_NOTIFY_MIN_ENABLED)] = settings.notifyMinEnabled
                prefs[doublePreferencesKey(KEY_NOTIFY_MIN_RAW)] = settings.notifyMinRawMWh
                prefs[booleanPreferencesKey(KEY_SAMPLE_DATA)] = settings.useSampleData
            }
        }
    }

    private fun key(name: String) = stringPreferencesKey(name)

    private companion object {
        const val KEY_LANGUAGE = "language"
        const val KEY_REGION = "region"
        const val KEY_UNIT = "unit"
        const val KEY_INCLUDE_TAX = "includeTax"
        const val KEY_CHART_TYPE = "chartType"
        const val KEY_CHART_RESOLUTION = "chartResolution"
        const val KEY_COLORFUL = "colorfulChart"
        const val KEY_ALWAYS_ON = "alwaysOnDisplay"
        const val KEY_NOTIFY_MAX_ENABLED = "notifyMaxEnabled"
        const val KEY_NOTIFY_MAX_RAW = "notifyMaxRawMWh"
        const val KEY_NOTIFY_MIN_ENABLED = "notifyMinEnabled"
        const val KEY_NOTIFY_MIN_RAW = "notifyMinRawMWh"
        const val KEY_SAMPLE_DATA = "debugUseTestData"
    }
}
