// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
// Modified for the Öpen-V 1.0 FOSS release (2026-10-27).

package dev.ambivalenton.openv.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/** Android's provider binding is authoritative, including when Glance's receiver cache is stale. */
internal object WidgetInstances {
    fun ids(context: Context, receiver: Class<out GlanceAppWidgetReceiver>): IntArray {
        val provider = ComponentName(context, receiver)
        val manager = AppWidgetManager.getInstance(context)
        return manager
            .getAppWidgetIds(provider)
            .filter { manager.getAppWidgetInfo(it)?.provider == provider }
            .toIntArray()
    }

    fun owns(
        context: Context,
        id: GlanceId,
        receiver: Class<out GlanceAppWidgetReceiver>,
    ): Boolean {
        val manager = GlanceAppWidgetManager(context)
        val widgetId = runCatching { manager.getAppWidgetId(id) }.getOrNull() ?: return false
        return AppWidgetManager.getInstance(context).getAppWidgetInfo(widgetId)?.provider ==
            ComponentName(context, receiver)
    }

    suspend fun update(
        context: Context,
        receiver: Class<out GlanceAppWidgetReceiver>,
        widget: GlanceAppWidget,
    ) {
        val manager = GlanceAppWidgetManager(context)
        for (id in ids(context, receiver)) widget.update(context, manager.getGlanceIdBy(id))
    }
}
