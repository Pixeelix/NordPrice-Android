package com.koodipardik.nordprice

import android.content.Context
import com.koodipardik.nordprice.data.HistoryRepository
import com.koodipardik.nordprice.data.NetworkMonitor
import com.koodipardik.nordprice.data.PriceRepository
import com.koodipardik.nordprice.data.SampleData
import com.koodipardik.nordprice.notifications.NotificationSync
import com.koodipardik.nordprice.settings.AppSettings
import com.koodipardik.nordprice.widget.WidgetRefresh
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Hand-rolled composition root. The app has four long-lived collaborators, which is well below
 * the point where a DI framework earns its annotation processing; it also keeps the iOS
 * singletons (`PriceRepository()`, `HistoryRepository.shared`) recognisable in the port.
 */
class AppContainer(context: Context) {

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settings: AppSettings = AppSettings(context)
    val networkMonitor: NetworkMonitor = NetworkMonitor(context)
    val priceRepository: PriceRepository = PriceRepository(context)
    val historyRepository: HistoryRepository = HistoryRepository(context)

    init {
        // A complete day is new data the widget can use; settings changes change how it is drawn.
        priceRepository.onCompleteDaySaved = { WidgetRefresh.refresh(context) }
        settings.onWidgetAffectingChange = { WidgetRefresh.refresh(context) }
        // The seam this hook was reserved for. Every settings change is pushed, including the ones
        // that only touch the thresholds — the backend stores the whole snapshot, not a delta.
        settings.onSettingsChanged = { NotificationSync.sync(context, settings) }
        WidgetRefresh.schedule(context)
        scope.launch {
            // Only debug builds may serve fake prices; a release can never flip this, as on iOS.
            settings.state.collect { SampleData.enabled = BuildConfig.DEBUG && it.useSampleData }
        }
    }
}
