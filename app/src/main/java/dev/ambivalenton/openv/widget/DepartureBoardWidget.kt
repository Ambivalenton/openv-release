// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
// Modified for the Öpen-V 1.0 FOSS release (2026-10-27).

package dev.ambivalenton.openv.widget

import dev.ambivalenton.openv.localization.tr

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.glance.*
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.*
import androidx.glance.appwidget.action.*
import androidx.glance.layout.*
import androidx.glance.text.*
import androidx.glance.semantics.semantics
import androidx.glance.semantics.contentDescription
import androidx.glance.unit.ColorProvider
import dev.ambivalenton.openv.MainActivity
import dev.ambivalenton.openv.OpenVApp
import dev.ambivalenton.openv.R
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.rendering.*
import dev.ambivalenton.openv.ui.theme.resolveColorScheme
import kotlinx.coroutines.flow.first

class DepartureBoardWidget : GlanceAppWidget(errorUiLayout = R.layout.board_widget_error) {
    override val sizeMode = SizeMode.Exact
    override val stateDefinition = DepartureBoardStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        if (!WidgetInstances.owns(context, id, DepartureBoardWidgetReceiver::class.java)) return
        val repository = (context.applicationContext as OpenVApp).locations
        val initial = repository.state.first()
        provideContent {
            GlanceTheme {
                val state by repository.state.collectAsState(initial)
                DepartureBoardContent(state, context.resources.configuration.fontScale)
            }
        }
    }

    suspend fun updateAllBoards(context: Context) =
        WidgetInstances.update(context, DepartureBoardWidgetReceiver::class.java, this)
}

fun departureBoardIntent(context: Context) =
    Intent(context, MainActivity::class.java).apply {
        action = Intent.ACTION_VIEW
        data = "openv://departures".toUri()
        flags =
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
    }

fun boardJourneyIntent(context:Context,row:Departure)=departureBoardIntent(context).apply {
    putExtra(BOARD_JOURNEY_EXTRA,row.journeyToken())
}

fun boardRefreshIntent(context: Context) =
    Intent(context, BoardRefreshActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }

internal data class BoardGeometry(
    val padding: Float,
    val heading: Boolean,
    val rows: Int,
    val rowHeight: Float,
    val narrow: Boolean,
)

internal fun boardGeometry(width: Float, height: Float, fontScale: Float): BoardGeometry {
    val layout =
        boardLayout(width, (height - if (height < 180) 28 else 36).coerceAtLeast(30f), fontScale)
    return BoardGeometry(layout.padding, false, layout.rows, layout.rowHeight, layout.narrow)
}

@Composable
internal fun DepartureBoardContent(
    state: StoredState,
    fontScale: Float,
    now: Long = System.currentTimeMillis(),
) {
    val context = LocalContext.current
    val size = LocalSize.current
    val systemDark =
        context.resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
    val model =
        boardPresentation(state, resolveColorScheme(context, state.settings, systemDark), now = now,typeface=dev.ambivalenton.openv.ui.theme.AppTypefaces.get(context,state.settings.fontId),
            themeTypeface=dev.ambivalenton.openv.ui.theme.AppTypefaces.get(context,state.settings.appTheme?.bodyFace))
    val hidden = state.settings.board.hideSettingsButtons
    // M10: a fixed stop only reloads departures; the automatic choice also determines the location.
    val refreshLabel = dev.ambivalenton.openv.data.repository.boardRefreshLabel(state)
    val footer = if (hidden) 0f else 24f
    // Reserve a line for data source attribution.
    val notice = if (TimetableProfile.sourceNotice) 20f else 0f
    val bodyHeight = (size.height.value - footer - notice).coerceAtLeast(24f)
    val layout = model.layout(size.width.value,bodyHeight,fontScale)
    val bitmap =
        remember(model, size, fontScale, bodyHeight, hidden) {
            BoardRenderer.render(
                model,
                size.width.value,
                bodyHeight,
                fontScale,
                headerAction = hidden,
            )
        }
    Column(
        GlanceModifier.fillMaxSize()
            .appWidgetBackground()
            .background(ColorProvider(androidx.compose.ui.graphics.Color(model.colors.background)))
            .cornerRadius(20.dp)
    ) {
        Box(GlanceModifier.fillMaxWidth().defaultWeight()) {
            Image(
                ImageProvider(bitmap),
                model.description(layout.rows),
                GlanceModifier.fillMaxSize()
                    .clickable(actionStartActivity(departureBoardIntent(context))),
                contentScale = ContentScale.FillBounds,
            )
            Column(GlanceModifier.fillMaxSize()) {
                Spacer(GlanceModifier.height((layout.padding+layout.header).dp))
                model.rows.take(layout.rows).forEach {row->
                    if(row.journey!=null) Box(GlanceModifier.fillMaxWidth().height(layout.rowHeight.dp)
                        .clickable(actionStartActivity(boardJourneyIntent(context,row)))
                        .semantics {contentDescription=tr("%1\$s nach %2\$s · Zeitstrahl öffnen",row.lineName+" "+clockTime(row.time),row.destination)}) {
                        // The bitmap supplies the visible label; the overlay supplies a distinct action.
                    } else Spacer(GlanceModifier.height(layout.rowHeight.dp))
                }
            }
            if (hidden)
                Box(
                    GlanceModifier.fillMaxWidth().height(28.dp),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Box(
                        GlanceModifier.size(28.dp)
                            .clickable(actionStartActivity(boardRefreshIntent(context))),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            ImageProvider(R.drawable.ic_refresh),
                            refreshLabel,
                            GlanceModifier.size(18.dp),
                            colorFilter =
                                ColorFilter.tint(
                                    ColorProvider(
                                        androidx.compose.ui.graphics.Color(model.colors.accent)
                                    )
                                ),
                        )
                    }
                }
        }
        if (TimetableProfile.sourceNotice) Text(tr(dev.ambivalenton.openv.data.model.TimetableServices.active.attribution),
            GlanceModifier.fillMaxWidth().height(20.dp).padding(horizontal=8.dp)
                .clickable(actionStartActivity(Intent(Intent.ACTION_VIEW,dev.ambivalenton.openv.data.model.TimetableServices.active.sourceUrl.toUri()))),
            style=TextStyle(color=ColorProvider(androidx.compose.ui.graphics.Color(model.colors.foreground)),fontSize=11.sp),
            maxLines=1)
        if (!hidden)
            Row(
                GlanceModifier.fillMaxWidth().height(footer.dp),
                verticalAlignment = Alignment.Vertical.CenterVertically,
            ) {
                Box(
                    GlanceModifier.defaultWeight()
                        .fillMaxHeight()
                        .clickable(actionStartActivity(boardRefreshIntent(context))),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        ImageProvider(R.drawable.ic_refresh),
                        refreshLabel,
                        GlanceModifier.size(20.dp),
                        colorFilter =
                            ColorFilter.tint(
                                ColorProvider(
                                    androidx.compose.ui.graphics.Color(model.colors.accent)
                                )
                            ),
                    )
                }
                if (size.width.value >= 140) {
                    Box(
                        GlanceModifier.defaultWeight()
                            .fillMaxHeight()
                            .clickable(actionRunCallback<CycleBoardDesignAction>()),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            ImageProvider(R.drawable.ic_palette),
                            tr("Nächstes Anzeigetafel-Design, aktuell %1\$s", model.design.title),
                            GlanceModifier.size(20.dp),
                            colorFilter =
                                ColorFilter.tint(
                                    ColorProvider(
                                        androidx.compose.ui.graphics.Color(model.colors.accent)
                                    )
                                ),
                        )
                    }
                    Box(
                        GlanceModifier.defaultWeight()
                            .fillMaxHeight()
                            .clickable(actionStartActivity(settingsIntent(context))),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            ImageProvider(R.drawable.ic_settings),
                            tr("Anzeigetafel einstellen"),
                            GlanceModifier.size(20.dp),
                            colorFilter =
                                ColorFilter.tint(
                                    ColorProvider(
                                        androidx.compose.ui.graphics.Color(model.colors.accent)
                                    )
                                ),
                        )
                    }
                }
            }
    }
}

/** A widget-side edit writes the very same setting observed by the in-app board. */
class CycleBoardDesignAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val app = context.applicationContext as OpenVApp
        app.locations.updateSettings { settings ->
            val styles = BoardDesign.entries
            settings.copy(
                board =
                    settings.board.copy(
                        design = styles[(styles.indexOf(settings.board.design) + 1) % styles.size]
                    )
            )
        }
        DepartureBoardWidget().updateAllBoards(context)
    }
}

