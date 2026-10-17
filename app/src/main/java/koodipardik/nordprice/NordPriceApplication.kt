package koodipardik.nordprice

import android.app.Application
import android.content.Context
import kotlinx.coroutines.runBlocking

/**
 * Owns the [AppContainer].
 *
 * Stored settings are read before the first frame on purpose: the iOS app loads them in
 * `ContentView.onAppear`, which is invisible there because `UserDefaults` is synchronous, but
 * the Android equivalent would flash the wrong language and region for a frame. DataStore is a
 * single small file, so blocking here costs a few milliseconds at cold start.
 */
class NordPriceApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        runBlocking { container.settings.load() }
    }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as NordPriceApplication).container
