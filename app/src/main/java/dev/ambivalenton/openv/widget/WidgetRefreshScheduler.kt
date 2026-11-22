// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
// Modified for the Öpen-V 1.0 FOSS release (2026-10-27).

package dev.ambivalenton.openv.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.PowerManager
import androidx.work.*
import dev.ambivalenton.openv.OpenVApp
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.data.repository.DepartureBoardRepository
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/**
 * WorkManager edges are best-effort; GPS work requires the optional separate background grant.
 *
 * Timetable requests are made on demand (terms of VRR OpenService): the 15-minute clock asks the
 * service only while the screen is on. With the screen off it leaves the widgets as they are; the
 * next unlock refreshes them when their stand is older than [STALE_AFTER_MILLIS]. Refreshes the
 * person starts (adding a widget, changing a stop, opening the app) are never skipped.
 */
internal fun widgetRequestAllowed(periodic: Boolean, screenOn: Boolean) = !periodic || screenOn

/** True when widgets last refreshed at [last] should refresh again at [now] (e.g. after unlocking). */
internal fun widgetStandStale(last: Long, now: Long) = now - last >= WidgetRefreshScheduler.STALE_AFTER_MILLIS || now < last

object WidgetRefreshScheduler {
    /** The tag of the 15-minute clock (a new name, so an earlier untagged clock is replaced). */
    internal const val PERIODIC_TAG = "openv-widget-clock-on-demand"
    private const val PERIODIC = "openv-widget-clock-v2"
    private const val LEGACY_PERIODIC = "openv-widget-clock"
    internal const val STALE_AFTER_MILLIS = 15 * 60_000L
    private const val STAMP = "openv-widget-refresh"
    private const val EDGE = "openv-widget-rule-edge"
    private const val IMMEDIATE = "openv-widget-refresh"

    private fun clock() = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(15, TimeUnit.MINUTES).addTag(PERIODIC_TAG).build()

    fun start(context: Context) {
        val manager = WorkManager.getInstance(context)
        manager.cancelUniqueWork(LEGACY_PERIODIC)
        manager.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, clock())
        refresh(context)
    }

    fun noteRefreshed(context: Context, now: Long = System.currentTimeMillis()) {
        context.getSharedPreferences(STAMP, Context.MODE_PRIVATE).edit().putLong("at", now).apply()
    }

    /** After unlocking: refresh widgets whose stand is older than [STALE_AFTER_MILLIS]. */
    fun refreshIfStale(context: Context, now: Long = System.currentTimeMillis()) {
        val last = context.getSharedPreferences(STAMP, Context.MODE_PRIVATE).getLong("at", 0L)
        if (widgetStandStale(last, now) && hasWidgets(context)) refresh(context)
    }

    fun refresh(context: Context) {
        WorkManager.getInstance(context)
            .enqueueUniqueWork(
                IMMEDIATE,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<WidgetRefreshWorker>().build(),
            )
    }

    /** Cancel in-flight automatic widget work; closing the focus restarts normal scheduling. */
    fun pauseForPinnedJourney(context: Context) {
        WorkManager.getInstance(context).apply {
            cancelUniqueWork(IMMEDIATE)
            cancelUniqueWork(PERIODIC)
            cancelUniqueWork(LEGACY_PERIODIC)
            cancelUniqueWork(EDGE)
        }
    }

    fun schedule(context: Context, state: StoredState, append: Boolean = false) {
        val manager = WorkManager.getInstance(context)
        if (!hasWidgets(context)) {
            stop(context)
            return
        }
        manager.cancelUniqueWork(LEGACY_PERIODIC)
        manager.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, clock())
        val now = ZonedDateTime.now()
        val next = nextFavoriteChange(state, now)
        if (next == null) {
            // Let the running edge finish; no successor is needed.
            if (!append) manager.cancelUniqueWork(EDGE)
            return
        }
        val request =
            OneTimeWorkRequestBuilder<WidgetRefreshWorker>()
                .setInputData(workDataOf("rule_edge" to true))
                .setInitialDelay(
                    Duration.between(now.toInstant(), next).toMillis().coerceAtLeast(1000),
                    TimeUnit.MILLISECONDS,
                )
                .build()
        manager.enqueueUniqueWork(
            EDGE,
            if (append) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    fun hasWidgets(context: Context): Boolean =
        listOf(AppQuickWidgetReceiver::class.java, DepartureBoardWidgetReceiver::class.java).any {
            AppWidgetManager.getInstance(context)
                .getAppWidgetIds(ComponentName(context, it))
                .isNotEmpty()
        }

    fun hasBoard(context: Context): Boolean =
        AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(context, DepartureBoardWidgetReceiver::class.java))
            .isNotEmpty()

    fun stop(context: Context) {
        if (hasWidgets(context)) return
        WorkManager.getInstance(context).apply {
            cancelUniqueWork(PERIODIC)
            cancelUniqueWork(LEGACY_PERIODIC)
            cancelUniqueWork(EDGE)
            cancelUniqueWork(IMMEDIATE)
        }
    }
}

class WidgetRefreshWorker(context: Context, parameters: WorkerParameters) :
    CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        return try {
            val app = applicationContext as OpenVApp
            if(!app.deviceLocation.hasPermission() || !app.deviceLocation.locationEnabled())app.locations.activateManualOrigin()
            val tracker=app.koin.get<dev.ambivalenton.openv.data.repository.PinnedJourneyController>()
            tracker.restore()
            if (tracker.focusIsOpen) return Result.success()
            val screenOn = applicationContext.getSystemService(PowerManager::class.java)?.isInteractive != false
            if (!widgetRequestAllowed(WidgetRefreshScheduler.PERIODIC_TAG in tags, screenOn)) return Result.success()
            if (WidgetRefreshScheduler.hasBoard(applicationContext)) {
                refreshBoardLocation(app)
                app.koin.get<DepartureBoardRepository>().refresh()
                DepartureBoardWidget().updateAllBoards(applicationContext)
            }
            val state = app.locations.state.first()
            AppQuickWidget().updateAllWidgets(applicationContext)
            WidgetRefreshScheduler.noteRefreshed(applicationContext)
            WidgetRefreshScheduler.schedule(
                applicationContext,
                state,
                append = inputData.getBoolean("rule_edge", false),
            )
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
