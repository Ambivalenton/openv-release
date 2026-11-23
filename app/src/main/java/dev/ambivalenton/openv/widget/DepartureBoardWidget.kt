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
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.TextAlign
import androidx.glance.unit.ColorProvider
import dev.ambivalenton.openv.MainActivity
import dev.ambivalenton.openv.OpenVApp
import dev.ambivalenton.openv.R
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.rendering.*
import dev.ambivalenton.openv.ui.theme.TimelineInk
import dev.ambivalenton.openv.ui.theme.createTimelinePalette
import dev.ambivalenton.openv.ui.theme.resolveColorScheme
import kotlin.math.floor
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

/**
 * Build 107 · the departures widget's arrangement at its real size. [single]: a flat widget shows the
 * next departure only, with stop and source in one small line below. Otherwise a header (stop, stand,
 * refresh), as many rows as fit – two-line rows with platform and delay where there is room, rows that
 * put the destination under line and time in narrow widgets – and the source line at the bottom.
 */
internal data class BoardWidgetPlan(
    val padding: Float,
    val single: Boolean,
    val header: Float,
    /** A low widget's header: stop and stand in one line, a smaller refresh button. */
    val compactHeader: Boolean,
    val footer: Float,
    val rows: Int,
    val rowHeight: Float,
    val twoLine: Boolean,
    val narrow: Boolean,
)

internal fun boardWidgetPlan(width: Float, height: Float, fontScale: Float = 1f): BoardWidgetPlan {
    val w = if (width.isFinite()) width.coerceAtLeast(48f) else 180f
    val h = if (height.isFinite()) height.coerceAtLeast(48f) else 110f
    val font = if (fontScale.isFinite()) fontScale.coerceIn(.85f, 1.6f) else 1f
    val narrow = w < 190f * font
    if (h < 108f * font) {
        val padding = if (h < 72f) 6f else 10f
        val footer = 15f * font
        return BoardWidgetPlan(padding, true, 0f, false, footer, 1, (h - 2 * padding - footer).coerceAtLeast(24f), false, narrow)
    }
    val compactHeader = h < 176f * font
    val padding = if (compactHeader) (if (narrow) 6f else 10f) else 12f
    val header = (if (compactHeader) (if (narrow) 26f else 30f) else 42f) * font
    val footer = (if (compactHeader) 14f else 16f) * font
    val body = (h - 2 * padding - header - footer - headerGap(compactHeader)).coerceAtLeast(30f)
    // Platform and delay get their own line only where at least three rows still fit.
    val twoLine = !narrow && body >= 3 * 46f * font
    val minRow = (if (narrow) 40f else if (twoLine) 46f else 30f) * font
    val rows = floor(body / minRow).toInt().coerceIn(1, 12)
    return BoardWidgetPlan(padding, false, header, compactHeader, footer, rows, body / rows, twoLine, narrow)
}

/** Space between the header and the first row. */
internal fun headerGap(compact: Boolean) = if (compact) 2f else 6f

/** Colours of the line badges for day and night, from the app's timeline palette. */
private class LineInks(day: (LineType) -> TimelineInk, night: (LineType) -> TimelineInk) {
    private val inks = LineType.entries.associateWith { type ->
        val d = day(type); val n = night(type)
        androidx.glance.color.ColorProvider(d.container, n.container) to androidx.glance.color.ColorProvider(d.content, n.content)
    }
    fun container(type: LineType) = inks.getValue(type).first
    fun content(type: LineType) = inks.getValue(type).second
}

private fun lineInks(context: Context, settings: AppSettings): LineInks {
    fun palette(dark: Boolean): (LineType) -> TimelineInk {
        val scheme = resolveColorScheme(context, settings, dark)
        val p = createTimelinePalette(normalizedTimelineTheme(settings.timelineTheme), scheme, scheme, settings.timelineSeed,
            settings.timelineLineColors, settings.appTheme)
        return { p[it] }
    }
    return LineInks(palette(false), palette(true))
}

/**
 * Build 107 · the departures widget, rebuilt with real text instead of a drawn board: stop and stand,
 * the next departures with line, destination, time, platform and delay, a refresh button and the
 * source of the timetable data. Times are clock times – a widget is not redrawn every minute, so a
 * countdown would soon be wrong; the stand says how fresh the data are.
 */
@Composable
internal fun DepartureBoardContent(
    state: StoredState,
    fontScale: Float,
    now: Long = System.currentTimeMillis(),
) {
    val context = LocalContext.current
    val size = LocalSize.current
    val palette = remember(state.settings) { widgetPalette(context, state.settings) }
    val inks = remember(state.settings) { lineInks(context, state.settings) }
    val systemDark = context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
        android.content.res.Configuration.UI_MODE_NIGHT_YES
    val model = boardPresentation(state, resolveColorScheme(context, state.settings, systemDark), now = now)
    val plan = boardWidgetPlan(size.width.value, size.height.value, fontScale)
    val refreshLabel = dev.ambivalenton.openv.data.repository.boardRefreshLabel(state)
    val rows = model.rows.take(plan.rows)
    Column(GlanceModifier.fillMaxSize().appWidgetBackground().widgetFill(WidgetShape.OUTER, palette.surface).padding(plan.padding.dp)) {
        if (!plan.single) {
            BoardHeader(model, palette, refreshLabel, plan)
            Spacer(GlanceModifier.height(headerGap(plan.compactHeader).dp))
        }
        Column(GlanceModifier.fillMaxWidth().defaultWeight()) {
            if (rows.isEmpty()) BoardEmpty(model, palette, plan)
            else rows.forEach { row -> DepartureRow(row, plan, palette, inks) }
        }
        BoardFooter(model, palette, plan, refreshLabel)
    }
}

@Composable private fun BoardHeader(model: BoardPresentation, palette: WidgetPalette, refreshLabel: String, plan: BoardWidgetPlan) {
    val context = LocalContext.current
    Row(GlanceModifier.fillMaxWidth().height(plan.header.dp), verticalAlignment = Alignment.Vertical.CenterVertically) {
        val open = GlanceModifier.defaultWeight().clickable(actionStartActivity(departureBoardIntent(context)))
            .semantics { contentDescription = tr("%1\$s. %2\$s. Abfahrten öffnen", model.title, model.caption) }
        if (plan.compactHeader) Row(open, verticalAlignment = Alignment.Vertical.CenterVertically) {
            WidgetText(model.title, palette.onSurface, 14.5.sp, GlanceModifier.defaultWeight(), FontWeight.Bold)
            // Narrow: the stop's name needs the room; the stand is read aloud and shown in the app.
            if (!plan.narrow) {
                Spacer(GlanceModifier.width(6.dp))
                WidgetText(model.caption, palette.muted, 11.sp)
            }
        } else Column(open) {
            WidgetText(model.title, palette.onSurface, 16.sp, weight = FontWeight.Bold)
            WidgetText(model.caption, palette.muted, 11.5.sp)
        }
        Spacer(GlanceModifier.width(8.dp))
        val button = if (plan.compactHeader) 28.dp else 36.dp
        Box(GlanceModifier.size(button).widgetFill(WidgetShape.PILL, palette.tile).clickable(actionStartActivity(boardRefreshIntent(context)))
            .semantics { contentDescription = refreshLabel }, contentAlignment = Alignment.Center) {
            Image(ImageProvider(R.drawable.ic_refresh), null, GlanceModifier.size(18.dp), colorFilter = ColorFilter.tint(palette.accent))
        }
    }
}

@Composable private fun DepartureRow(row: Departure, plan: BoardWidgetPlan, palette: WidgetPalette, inks: LineInks) {
    val context = LocalContext.current
    val font = LocalContext.current.resources.configuration.fontScale
    val minutes = row.delay.minutes
    val late = minutes != null && minutes > 0 && !row.delay.cancelled
    val platform = platformName(row.platform, row.lineType)
    val time = clockTime(row.time)
    val description = listOfNotNull(
        tr("%1\$s %2\$s nach %3\$s", row.lineType.label, row.lineName, row.destination), time, platform,
        if (row.delay.cancelled) tr("fällt aus") else if (late) tr("%1\$s Minuten verspätet", minutes) else null,
        if (row.journey != null) tr("Zeitstrahl öffnen") else null).joinToString(", ")
    val action = actionStartActivity(if (row.journey != null) boardJourneyIntent(context, row) else departureBoardIntent(context))
    val timeColor = if (row.delay.cancelled) palette.error else if (late) palette.late else palette.onSurface
    if (plan.narrow && !plan.single) {
        // Narrow: line and time on top, the destination below in the full width.
        Column(GlanceModifier.fillMaxWidth().height(plan.rowHeight.dp).clickable(action).semantics { contentDescription = description },
            verticalAlignment = Alignment.Vertical.CenterVertically) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Vertical.CenterVertically) {
                LineBadge(row, inks, font, height = 20f)
                Spacer(GlanceModifier.defaultWeight())
                if (late) WidgetText("+$minutes ", palette.late, 11.sp, weight = FontWeight.Medium)
                WidgetText(time, timeColor, 14.5.sp, weight = FontWeight.Bold, struck = row.delay.cancelled)
            }
            WidgetText(if (row.delay.cancelled) tr("%1\$s · fällt aus", row.destination) else row.destination,
                if (row.delay.cancelled) palette.error else palette.onSurface, 12.sp, GlanceModifier.padding(top = 1.dp))
        }
        return
    }
    Row(GlanceModifier.fillMaxWidth().height(plan.rowHeight.dp).clickable(action).semantics { contentDescription = description },
        verticalAlignment = Alignment.Vertical.CenterVertically) {
        LineBadge(row, inks, font)
        Spacer(GlanceModifier.width(10.dp))
        Column(GlanceModifier.defaultWeight()) {
            WidgetText(row.destination, palette.onSurface, (if (plan.single) 14f else 14.5f).sp, weight = FontWeight.Medium, struck = row.delay.cancelled)
            if (plan.twoLine) {
                val second = listOfNotNull(platform, if (row.delay.cancelled) tr("fällt aus") else null).joinToString(" · ")
                if (second.isNotEmpty()) WidgetText(second, if (row.delay.cancelled) palette.error else palette.muted, 11.5.sp)
            }
        }
        Spacer(GlanceModifier.width(8.dp))
        Column(horizontalAlignment = Alignment.Horizontal.End) {
            WidgetText(time, timeColor, (if (plan.single) 16f else 15.5f).sp, weight = FontWeight.Bold, align = TextAlign.End,
                struck = row.delay.cancelled)
            if (late && plan.twoLine) WidgetText("+$minutes", palette.late, 11.5.sp, weight = FontWeight.Medium, align = TextAlign.End)
        }
    }
}

/** The line in its own colour: "U35", "RE3", "SB20" – the colours of the app's timeline. */
@Composable private fun LineBadge(row: Departure, inks: LineInks, font: Float, height: Float = 22f) {
    val name = row.lineName.ifBlank { row.lineType.label }
    Box(GlanceModifier.width(lineBadgeWidth(name, font).coerceAtMost(76f).dp).height((height * font.coerceIn(.85f, 1.4f)).dp)
        .widgetFill(WidgetShape.SMALL, inks.container(row.lineType)), contentAlignment = Alignment.Center) {
        WidgetText(name, inks.content(row.lineType), 12.sp, weight = FontWeight.Bold, align = TextAlign.Center)
    }
}

@Composable private fun BoardEmpty(model: BoardPresentation, palette: WidgetPalette, plan: BoardWidgetPlan) {
    val context = LocalContext.current
    Box(GlanceModifier.fillMaxSize().clickable(actionStartActivity(departureBoardIntent(context))), contentAlignment = Alignment.Center) {
        Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
            Image(ImageProvider(R.drawable.ic_train), null, GlanceModifier.size(18.dp), colorFilter = ColorFilter.tint(palette.muted))
            Spacer(GlanceModifier.width(8.dp))
            WidgetText(model.emptyMessage, palette.muted, 13.sp, lines = if (plan.single) 1 else 2)
        }
    }
}

/** The source of the timetable data (required); in a flat widget together with the stop and the refresh. */
@Composable private fun BoardFooter(model: BoardPresentation, palette: WidgetPalette, plan: BoardWidgetPlan, refreshLabel: String) {
    val context = LocalContext.current
    val service = TimetableServices.active
    val attribution = if (TimetableProfile.sourceNotice) tr(service.attribution) else null
    Row(GlanceModifier.fillMaxWidth().height(plan.footer.dp), verticalAlignment = Alignment.Vertical.CenterVertically) {
        val text = if (plan.single) listOfNotNull(model.title, attribution).joinToString(" · ") else attribution.orEmpty()
        WidgetText(text, palette.muted, 10.5.sp, GlanceModifier.defaultWeight().then(
            if (attribution != null) GlanceModifier.clickable(actionStartActivity(Intent(Intent.ACTION_VIEW, service.sourceUrl.toUri())))
            else GlanceModifier))
        if (plan.single) Box(GlanceModifier.size(plan.footer.dp).clickable(actionStartActivity(boardRefreshIntent(context)))
            .semantics { contentDescription = refreshLabel }, contentAlignment = Alignment.Center) {
            Image(ImageProvider(R.drawable.ic_refresh), null, GlanceModifier.size((plan.footer - 2f).dp), colorFilter = ColorFilter.tint(palette.accent))
        }
    }
}

/** Kept so a tap on an older widget (before 1.0.7) still lands; it cycles the board design as before. */
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
