package koodipardik.nordprice.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Duration

/**
 * When the widget repaints.
 *
 * iOS hands WidgetKit a pre-computed timeline of entries and a reload date, so the widget can tick
 * forward on its own. RemoteViews cannot: an Android widget only changes when something updates it.
 * The equivalent here is a periodic work request aligned to the quarter-hour the market moves on,
 * plus immediate pushes when the app learns something new — a settings change, or a complete day
 * landing in the cache.
 */
object WidgetRefresh {

    private const val UNIQUE_WORK = "nordprice-widget-refresh"

    fun schedule(context: Context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<WidgetRefreshWorker>(Duration.ofMinutes(15))
                .setInitialDelay(Duration.ofMinutes(1))
                .build(),
        )
    }

    private val scope = CoroutineScope(Dispatchers.IO)

    /** Called from the app whenever the rendered day might have moved. */
    fun refresh(context: Context) {
        val appContext = context.applicationContext
        scope.launch {
            // With no widget placed yet, updateAll throws — nothing to update, which is normal.
            runCatching { NordPriceWidget().updateAll(appContext) }
        }
    }
}

class WidgetRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        runCatching { NordPriceWidget().updateAll(applicationContext) }
        return Result.success()
    }
}
