// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
// Modified for the Öpen-V 1.0 FOSS release (2026-10-27).

package dev.ambivalenton.openv.widget

import dev.ambivalenton.openv.localization.tr

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.glance.*
import androidx.glance.action.clickable
import androidx.glance.appwidget.*
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.layout.*
import androidx.glance.text.*
import androidx.glance.unit.ColorProvider
import dev.ambivalenton.openv.MainActivity
import dev.ambivalenton.openv.OpenVApp
import dev.ambivalenton.openv.R
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.ui.components.resource
import dev.ambivalenton.openv.ui.theme.resolveColorScheme
import java.time.ZonedDateTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*

/** The quick renderer cannot receive or modify a DepartureBoardState. */
internal data class QuickWidgetState(
    val favorites: List<Favorite>,
    val settings: AppSettings,
    val presence: DevicePresence?,
) {
    companion object {
        fun from(state: StoredState) =
            QuickWidgetState(state.favorites, state.settings, state.routingPresence())
    }
}

class AppQuickWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact
    override val stateDefinition = QuickWidgetStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        if (!WidgetInstances.owns(context, id, AppQuickWidgetReceiver::class.java)) return
        val repository = (context.applicationContext as OpenVApp).locations
        val flow = repository.state.map(QuickWidgetState::from).distinctUntilChanged()
        val initial =
            try {
                flow.first()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
        provideContent {
            GlanceTheme {
                if (initial == null)
                    QuickWidgetFallback(tr("Favoriten konnten nicht gelesen werden. App öffnen."))
                else {
                    val state by flow.collectAsState(initial)
                    QuickFavoriteContent(
                        state,
                        context.resources.configuration.fontScale,
                        ZonedDateTime.now(),
                    )
                }
            }
        }
    }

    suspend fun updateAllWidgets(context: Context) =
        WidgetInstances.update(context, AppQuickWidgetReceiver::class.java, this)
}

fun favoriteIntent(context: Context, id: String) =
    Intent(context, MainActivity::class.java).apply {
        action = Intent.ACTION_VIEW
        data = Uri.Builder().scheme("openv").authority("route").appendPath(id).build()
        flags =
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
    }

fun settingsIntent(context: Context) =
    Intent(context, MainActivity::class.java).apply {
        action = Intent.ACTION_VIEW
        data = "openv://settings".toUri()
        flags =
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
    }

fun searchIntent(context: Context) =
    settingsIntent(context).apply { data = "openv://search".toUri() }

fun quickLaunchIntent(context: Context, id: String, style: WidgetStyle): Intent =
    if (style.clickFeedback == ClickFeedback.PROGRESS)
        Intent(context, QuickLaunchActivity::class.java)
            .putExtra("targetId", id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    else favoriteIntent(context, id)

@Composable
internal fun QuickWidgetContent(snapshot:StoredState,fontScale:Float,now:ZonedDateTime=ZonedDateTime.now(),preview:Boolean=false) =
    QuickFavoriteContent(QuickWidgetState.from(snapshot),fontScale,now,preview)
