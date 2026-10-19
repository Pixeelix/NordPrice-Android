package com.koodipardik.nordprice.notifications

import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.koodipardik.nordprice.MainActivity
import com.koodipardik.nordprice.R
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
            // it belongs here rather than in a silent fallback.
            Log.w(TAG, "Message with no notification payload ignored: ${message.data}")
            return
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            // Tapping opens the app. iOS routes the same tap straight to the Tomorrow prices
            // (NordPriceApp.swift:74); Android has no shared navigation state to ask, yet.
            PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(this, NotificationSync.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_nordprice)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            // One alert per direction per market day: a resend for the same day replaces the old
            // notification instead of stacking a second copy in the shade.
            val id = (message.data["type"] ?: "") .hashCode()
            NotificationManagerCompat.from(this).notify(id, notification)
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS revoked between the push arriving and this line running.
            Log.w(TAG, "Notification permission revoked; alert not shown", e)
        }
    }

    private companion object {
        const val TAG = "NordPriceNotifications"
    }
}
