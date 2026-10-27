package com.koodipardik.nordprice.notifications

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.koodipardik.nordprice.appContainer

/**
 * Receives FCM traffic. The backend sends `notification` + `data` (index.ts:372), so:
 *
 * * while the app is **backgrounded**, the system draws the alert itself and this class is not
 *   consulted — except that the delivery lands in FCM's default channel until the function sets an
 *   `android.channel_id`, which is a known gap listed in README;
 * * while the app is **foregrounded**, only this callback can show anything, which is the Android
 *   equivalent of iOS `willPresent` returning `[.banner, .list, .sound]` (NordPriceApp.swift:60).
 */
class NordPriceMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        // iOS: didReceiveRegistrationToken → updateFCMToken → sync (NordPriceApp.swift:56). The
        // token is not stored here; the registration reads it fresh from Firebase.
        Log.i(TAG, "FCM token refreshed")
        NotificationSync.sync(this, appContainer.settings)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title
        val body = message.notification?.body
        if (title.isNullOrEmpty() && body.isNullOrEmpty()) {
            // A data-only message is not part of the current backend contract; if one ever is,
            // it belongs here rather than in a silent fallback. Keys only: the values are
            // server-chosen text and this line survives into the release build.
            Log.w(TAG, "Message with no notification payload ignored: ${message.data.keys}")
            return
        }

        // The device gets the last word on what the user switched off. Settings are pushed to
        // `syncNotificationDevice` on every change, but the push already in flight when someone
        // toggles alerts off still arrives here, and a registration that failed while offline is
        // only logged (NotificationSync.sync) — so "the backend should have stopped" is not a
        // guarantee. Only the both-off case is suppressed: `data["type"]` is not pinned anywhere in
        // this repo, and guessing at its spelling could drop an alert the user does want. iOS needs
        // no equivalent check, the OS suppresses the alert for it. `w`, not `i`, so R8 keeps it — a
        // silently missing alert is the one thing here that needs to be explainable from logcat.
        val alerts = appContainer.settings.current
        if (!alerts.notifyMaxEnabled && !alerts.notifyMinEnabled) {
            Log.w(TAG, "Both price-alert directions are off on this device; alert not shown")
            return
        }

        // Rendering lives in PriceAlertNotifier, the same path the Settings › DEBUG mirror rows
        // use, so a debug alert and a real push cannot drift apart.
        PriceAlertNotifier.show(this, title, body, message.data["type"] ?: "")
    }

    private companion object {
        const val TAG = "NordPriceNotifications"
    }
}
