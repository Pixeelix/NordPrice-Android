package koodipardik.nordprice

import android.content.Context
import koodipardik.nordprice.data.HistoryRepository
import koodipardik.nordprice.data.NetworkMonitor
import koodipardik.nordprice.data.PriceRepository
import koodipardik.nordprice.data.SampleData
import koodipardik.nordprice.settings.AppSettings
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
        priceRepository.onCompleteDaySaved = { /* Phase 3: reload the Glance widget timeline. */ }
        scope.launch {
            // Only debug builds may serve fake prices; a release can never flip this, as on iOS.
            settings.state.collect { SampleData.enabled = BuildConfig.DEBUG && it.useSampleData }
        }
    }
}
