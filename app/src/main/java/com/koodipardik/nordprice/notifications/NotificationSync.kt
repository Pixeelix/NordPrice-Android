package com.koodipardik.nordprice.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.messaging.FirebaseMessaging
import com.koodipardik.nordprice.BuildConfig
import com.koodipardik.nordprice.R
import com.koodipardik.nordprice.appContainer
import com.koodipardik.nordprice.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Registers this device for price alerts: FCM token → anonymous Auth → `syncNotificationDevice`.
 *
 * Structured like [com.koodipardik.nordprice.widget.WidgetRefresh] — a stateless object called
 * from the composition root and from the messaging service — rather than as a fifth long-lived
 * collaborator, so `AppContainer` keeps its four.
 *
 * The backend keys the device document by the Firebase Auth **uid**, not by `installationId`
 * (`index.ts:115`), which is why the anonymous account must survive restarts. It does: Firebase
 * Auth persists it in its own storage, so reinstalling the app is what creates a new device row,
 * not relaunching it.
 */
object NotificationSync {

    private const val TAG = "NordPriceNotifications"
    private const val PREFS = "nordprice_notifications"

    /** Same UserDefaults key the iOS app uses; the value is a random UUID on both platforms. */
    private const val KEY_INSTALLATION_ID = "remoteNotificationInstallationId"

    /** The functions deploy to europe-west1 (`index.ts:13`); the client default is us-central1. */
    private const val FUNCTIONS_REGION = "europe-west1"

    private const val CALLABLE = "syncNotificationDevice"

    /** One channel covers both directions, as one iOS authorisation does. */
    const val CHANNEL_ID = "price_alerts"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Sends the current settings to the backend.
     *
     * Always sent, even when both toggles are off — that is how a device tells the backend to stop
     * alerting it. Skipping the "nothing enabled" case would leave stale devices firing alerts.
     */
    fun sync(context: Context, settings: AppSettings) {
        val appContext = context.applicationContext
        ensureChannel(appContext)
        scope.launch {
            try {
                register(appContext, settings)
            } catch (e: Exception) {
                // iOS logs and carries on (NotificationService.swift:255) — an unreachable backend,
                // or one that rejects the payload, must not undo a settings change the user just
                // made. Retry is the next settings change or launch, which is all iOS has either.
                Log.w(TAG, "Notification device registration failed", e)
            }
        }
    }

    /**
     * Debug-only round trip, from Settings › DEBUG: asks the backend to push a test message to the
     * token it has stored for this device.
     *
     * Registration succeeding does not prove an alert can arrive. The first real one comes whenever
     * `checkTomorrowPrices` next finds a price crossing the threshold, which may be days; this
     * answers the question now, through `onMessageReceived` and the notification channel. The
     * backend reads the device document by Auth uid, so an unregistered device comes back as
     * `FAILED_PRECONDITION` instead of silently doing nothing.
     *
     * The failure text is whatever the server said, untranslated and deliberately — it is a
     * developer diagnostic in a debug-only row, not copy a user would ever see.
     */
    suspend fun sendTestPush(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            FirebaseFunctions.getInstance(FirebaseApp.getInstance(), FUNCTIONS_REGION)
                .getHttpsCallable("sendTestNotification")
                .call()
                .await()
        }.map { }
    }

    /**
     * `getToken` is deprecated in favour of `register()`, which hands the token over through
     * `onNewToken` instead of returning it. That shape cannot carry this feature: `onNewToken`
     * does not re-fire for a token the SDK already has, so a settings change on a device that has
     * not seen a callback yet would register nothing. `Messaging.messaging().token` — the direct
     * read iOS performs (NotificationService.swift:186) — is the behaviour being ported, so the
     * deprecated-but-correct call wins over the current-but-unusable one.
     */
    @Suppress("DEPRECATION")
    private suspend fun register(context: Context, settings: AppSettings) {
        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser == null) {
            auth.signInAnonymously().await()
        }

        val token = FirebaseMessaging.getInstance().token.await()
        if (token.isNullOrEmpty()) {
            Log.w(TAG, "No FCM token available; device not registered")
            return
        }

        val payload = DeviceRegistration(
            installationId = installationId(context),
            // Resolves to the applicationId — com.koodipardik.nordprice — which is what the
            // backend's bundleId field means on Android.
            bundleId = context.packageName,
            fcmToken = token,
            settings = settings.current,
            appVersion = BuildConfig.VERSION_NAME,
            buildNumber = BuildConfig.VERSION_CODE.toString(),
        ).toPayload()

        FirebaseFunctions.getInstance(FirebaseApp.getInstance(), FUNCTIONS_REGION)
            .getHttpsCallable(CALLABLE)
            .call(payload)
            .await()
        Log.i(TAG, "Registered device for ${settings.current.region.code} price alerts")
    }

    /**
     * The id is created once per install and kept forever, so the backend sees one device across
     * sign-outs and updates. SharedPreferences rather than the settings DataStore because it is not
     * user-facing state the UI observes, and [NordPriceMessagingService] needs it synchronously.
     */
    private fun installationId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(KEY_INSTALLATION_ID, null)?.takeIf { it.isNotEmpty() }?.let { return it }
        return UUID.randomUUID().toString().also { prefs.edit().putString(KEY_INSTALLATION_ID, it).apply() }
    }

    private fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_price_alerts),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.notification_channel_price_alerts_description)
        }
        manager.createNotificationChannel(channel)
    }

    /** Firebase's APIs are Task-based; this is the only place that bridges them to coroutines. */
    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
            .addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
            .addOnCanceledListener { if (continuation.isActive) continuation.cancel() }
    }
}
