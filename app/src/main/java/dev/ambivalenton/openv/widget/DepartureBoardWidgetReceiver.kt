// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
// Modified for the Öpen-V 1.0 FOSS release (2026-10-27).

package dev.ambivalenton.openv.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetReceiver

class DepartureBoardWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = DepartureBoardWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetRefreshScheduler.start(context)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val own = WidgetInstances.ids(context, DepartureBoardWidgetReceiver::class.java).toSet()
        super.onUpdate(context, appWidgetManager, appWidgetIds.filter { it in own }.toIntArray())
        WidgetRefreshScheduler.start(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WidgetRefreshScheduler.stop(context)
    }
}
