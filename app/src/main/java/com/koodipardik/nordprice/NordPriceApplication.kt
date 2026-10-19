package com.koodipardik.nordprice

import android.app.Application
import android.content.Context
import com.koodipardik.nordprice.notifications.NotificationSync
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
        // Registered only after the settings have landed, and the reason is destructive: until
        // `load()` returns the container holds device defaults, and a registration sent from
        // `AppContainer.init` would overwrite this device's stored snapshot on the backend —
        // switching alerts off, because a fresh Settings has both toggles false.
        // iOS registers at launch too (ContentView.onAppear), which is what corrects a token that
        // rotated while the app was closed and no settings change ever fired.
        NotificationSync.sync(this, container.settings)
    }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as NordPriceApplication).container
