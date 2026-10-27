package com.koodipardik.nordprice.notifications

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.koodipardik.nordprice.MainActivity
import com.koodipardik.nordprice.R

/**
 * The single rendering path for price alerts. A real push arrives through
 * [NordPriceMessagingService]; the Settings › DEBUG rows post the same alert locally (the port
 * of iOS `scheduleDebugPriceNotification`) — and both land here, so what the developer sees on
 * an emulator with no FCM token is the same channel, style, tap intent and id that a delivery
 * over Firebase would show. Two copies of this builder is exactly what drifts.
 */
object PriceAlertNotifier {

    /**
     * The exact strings the backend puts in `data.type` (`functions/src/index.ts` sends
     * `` `price_threshold_${direction}` ``). They are the notification id, so a spelling that
     * drifted from the server would break nothing loudly — it would just stop a debug alert
     * and a real push from replacing each other. [PriceAlertNotifierTest] pins these values.
     */
    const val TYPE_MAX = "price_threshold_max"
    const val TYPE_MIN = "price_threshold_min"

    /**
     * `title`/`body` are nullable because the FCM path reaches here only after its own guard
     * on an empty payload, and that guard lets one of the two be null but not both; the debug
     * path always passes both. A null simply renders as nothing in that slot.
     */
    fun show(context: Context, title: String?, body: String?, type: String) {
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            // Tapping opens the app. iOS routes the same tap straight to the Tomorrow prices
            // (NordPriceApp.swift:74); Android has no shared navigation state to ask, yet.
            PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, NotificationSync.CHANNEL_ID)
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
            // notification instead of stacking a second copy in the shade. The debug rows share
            // the id by sending the same type strings the server sends.
            NotificationManagerCompat.from(context).notify(type.hashCode(), notification)
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS revoked between the push arriving and this line running.
            Log.w(TAG, "Notification permission revoked; alert not shown", e)
        }
    }

    private const val TAG = "NordPriceNotifications"
}
