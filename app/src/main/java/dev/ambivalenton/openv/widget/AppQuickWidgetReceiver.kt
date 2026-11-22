// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
// Modified for the Öpen-V 1.0 FOSS release (2026-10-27).

package dev.ambivalenton.openv.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import kotlinx.coroutines.*

class AppQuickWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = AppQuickWidget()

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val own = WidgetInstances.ids(context, AppQuickWidgetReceiver::class.java).toSet()
        super.onUpdate(context, appWidgetManager, appWidgetIds.filter { it in own }.toIntArray())
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetRefreshScheduler.start(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WidgetRefreshScheduler.stop(context)
    }
}

class WidgetTimeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (
            intent.action !in
                setOf(
                    Intent.ACTION_TIME_CHANGED,
                    Intent.ACTION_TIMEZONE_CHANGED,
                    Intent.ACTION_DATE_CHANGED,
                    Intent.ACTION_MY_PACKAGE_REPLACED,
                )
        )
            return
        WidgetRefreshScheduler.refresh(context.applicationContext)
    }
}
