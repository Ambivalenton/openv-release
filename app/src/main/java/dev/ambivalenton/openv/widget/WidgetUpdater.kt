// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
// Modified for the Öpen-V 1.0 FOSS release (2026-10-27).

package dev.ambivalenton.openv.widget

import android.content.Context
import dev.ambivalenton.openv.OpenVApp
import kotlinx.coroutines.flow.first

class WidgetUpdater(private val context: Context) {
    suspend fun updateQuick() {
        AppQuickWidget().updateAllWidgets(context)
    }

    suspend fun update() {
        AppQuickWidget().updateAllWidgets(context)
        val state = (context.applicationContext as OpenVApp).locations.state.first()
        if (WidgetRefreshScheduler.hasBoard(context)) {
            DepartureBoardWidget().updateAllBoards(context)
            WidgetRefreshScheduler.refresh(context)
        }
        WidgetRefreshScheduler.schedule(context, state)
    }
}
