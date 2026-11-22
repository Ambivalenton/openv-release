// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.ambivalenton.openv.R
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.localization.tr
import dev.ambivalenton.openv.ui.components.*
import dev.ambivalenton.openv.ui.components.AppModalBottomSheet as ModalBottomSheet
import dev.ambivalenton.openv.ui.theme.LocalAppSettings
import dev.ambivalenton.openv.ui.theme.appCorner
import dev.ambivalenton.openv.ui.theme.appPanelDecoration
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * The main setting "Favoriten". One composable for both entrances: the settings page and the sheet
 * behind the gear button of the network plan (Suchen → Bearbeiten).
 */
@Composable
fun FavoriteSettingsContent(
    store: StoredState,
    onAdd: () -> Unit,
    onEdit: (Favorite) -> Unit,
    onSettings: ((AppSettings) -> AppSettings) -> Unit,
    onGridSize: (Int, Int) -> Unit,
) {
    val settings = store.settings
    // The list lives in its own window (build 68): the sliders stay visible without scrolling,
    // however many favourites exist. The window stays open below the editor, so editing returns to it.
    var listOpen by rememberSaveable { mutableStateOf(false) }
    ThemedButton({ listOpen = true }, Modifier.fillMaxWidth().testTag("favorite-settings-list")) { Text(tr("Favoriten-Liste")) }
    if (listOpen) FavoriteListDialog(store.favorites, onAdd, onEdit) { listOpen = false }
    // Favourites at the current location are always hidden in the overview (no switch).
    FavoriteRadiusSetting(settings.favoriteRadiusMeters) { meters -> onSettings { it.copy(favoriteRadiusMeters = meters) } }
    // What holding a favourite in the plan does; fixed stops, default "frei bewegen".
    StepBubbleSetting(tr("Favorit im Netzplan gedrückt halten"), NetworkHoldAction.entries.map { it.title },
        settings.networkHoldAction.ordinal, { index -> onSettings { it.copy(networkHoldAction = NetworkHoldAction.entries[index]) } },
        "network-hold", description = { NetworkHoldAction.entries[it].description })
    // The grid comes last (scrolling through the list used to resize it by accident).
    // Every change re-arranges the favourites (and removes the lines): always confirmed first.
    NetworkGridSizeSetting(store.network.columns, store.network.rows, { c, r -> store.outsideGrid(c, r).map { it.label } }, onGridSize)
    // The lines as a list instead of the plan (the pencil still opens the plan for editing).
    ModeSwitchCard(tr("Strecken als Liste zeigen"),
        tr("Unter Suchen erscheinen deine Strecken als Liste statt als Netzplan, beide Richtungen in einem Rahmen. Zum Bearbeiten öffnet der Stift weiterhin den Netzplan."),
        settings.favoriteListView, { on -> onSettings { it.copy(favoriteListView = on) } }, "favorite-list-view") { on, m ->
        dev.ambivalenton.openv.ui.network.RouteListPicture(on, m) }
}

/** Pop-up with every favourite (tap edits) and the button to add one; the list scrolls inside. */
@Composable
private fun FavoriteListDialog(favorites: List<Favorite>, onAdd: () -> Unit, onEdit: (Favorite) -> Unit, onDismiss: () -> Unit) {
    val maxList = with(androidx.compose.ui.platform.LocalDensity.current) { (androidx.compose.ui.platform.LocalWindowInfo.current.containerSize.height * .55f).toDp() }
    AppAlertDialog(onDismissRequest = onDismiss, modifier = Modifier.testTag("favorite-list-dialog"),
        title = { FittedText(tr("Favoriten"), Modifier.semantics { heading() }) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ThemedButton(onAdd, Modifier.fillMaxWidth().testTag("favorite-settings-add")) { Text(tr("Favorit hinzufügen")) }
                if (favorites.isEmpty()) Text(tr("Füge unter Suchen einen Favoriten hinzu."))
                else Column(Modifier.fillMaxWidth().heightIn(max = maxList).verticalScroll(rememberScrollState()).testTag("favorite-list"),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    favorites.forEach { favorite ->
                        key(favorite.id) {
                            Surface(onClick = { onEdit(favorite) }, Modifier.fillMaxWidth().pressScale().testTag("favorite-list-${favorite.id}"),
                                shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) {
                                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    AppIcon(favorite.icon.resource(), null)
                                    Column(Modifier.weight(1f)) {
                                        FittedText(favorite.label, style = MaterialTheme.typography.titleSmall)
                                        Text(tr("Bearbeiten · Symbol & Zeitregeln"), style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onDismiss, Modifier.testTag("favorite-list-close")) { Text(tr("Schließen")) } })
}

/** The same menu as a sheet with a drag handle: pull it down to close, nothing is nested. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoriteSettingsSheet(
    store: StoredState,
    onAdd: () -> Unit,
    onEdit: (Favorite) -> Unit,
    onSettings: ((AppSettings) -> AppSettings) -> Unit,
    onGridSize: (Int, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 28.dp)
            .testTag("favorite-settings-sheet"), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            FittedText(tr("Favoriten"), Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
            FavoriteSettingsContent(store, onAdd, onEdit, onSettings, onGridSize)
        }
    }
}

/** How long the finger rests on the grid before dragging resizes it (shorter than a long press). */
internal const val NETWORK_GRID_HOLD_MS = 170L

private const val RADIUS_SNAP = .04f
private const val RADIUS_MIN = 10
// The free part lies strictly between the two snap zones, so 10 m and 2000 m map back to themselves.
private const val RADIUS_LOW = RADIUS_SNAP + .001f
private const val RADIUS_HIGH = 1f - RADIUS_SNAP - .001f

/** Slider position 0…1 → radius: both ends snap (off / always), the middle is free in 10 m steps. */
internal fun favoriteRadiusForPosition(position: Float): Int = when {
    position <= RADIUS_SNAP -> 0
    position >= 1f - RADIUS_SNAP -> FAVORITE_RADIUS_ALWAYS
    else -> {
        val fraction = ((position - RADIUS_LOW) / (RADIUS_HIGH - RADIUS_LOW)).coerceIn(0f, 1f)
        (((RADIUS_MIN + fraction * (FAVORITE_RADIUS_MAX - RADIUS_MIN)) / 10f).roundToInt() * 10).coerceIn(RADIUS_MIN, FAVORITE_RADIUS_MAX)
    }
}

internal fun favoriteRadiusPosition(meters: Int): Float = when (val radius = normalizedFavoriteRadius(meters)) {
    0 -> 0f
    FAVORITE_RADIUS_ALWAYS -> 1f
    else -> RADIUS_LOW + (radius - RADIUS_MIN).coerceAtLeast(0) / (FAVORITE_RADIUS_MAX - RADIUS_MIN).toFloat() * (RADIUS_HIGH - RADIUS_LOW)
}

@Composable private fun favoriteRadiusText(meters: Int): String = when (meters) {
    0 -> tr("Aus: immer dein tatsächlicher Standort")
    FAVORITE_RADIUS_ALWAYS -> tr("Immer der nächstgelegene Favorit, egal wie weit er entfernt ist")
    else -> tr("Bis %1\$s m Entfernung startet die App am Favoriten", meters)
}

/** Radius around favourites as a speech bubble above its slider (like the bar-chart setting under
 * Modi), with snap points only at the far left and the far right. */
@Composable
internal fun FavoriteRadiusSetting(meters: Int, onCommit: (Int) -> Unit) {
    var position by rememberSaveable { mutableFloatStateOf(favoriteRadiusPosition(meters)) }
    LaunchedEffect(meters) { position = favoriteRadiusPosition(meters) }
    val calm = LocalAppSettings.current.reduceMotion
    val haptics = LocalHapticFeedback.current
    val shown by animateFloatAsState(position, if (calm) snap() else spring(dampingRatio = .7f, stiffness = 900f), label = "favorite-radius-position")
    val value = favoriteRadiusForPosition(position)
    val colors = MaterialTheme.colorScheme
    val description = favoriteRadiusText(value)
    SettingsCard(Modifier.testTag("favorite-radius-setting")) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(tr("Umkreis um Favoriten"), style = MaterialTheme.typography.titleSmall)
            BoxWithConstraints(Modifier.fillMaxWidth().height(88.dp)) {
                val width = minOf(230.dp, maxWidth); val thumb = 12.dp + (maxWidth - 24.dp) * shown
                val left = (thumb - width / 2).coerceIn(0.dp, maxWidth - width); val fraction = ((thumb - left) / width).coerceIn(0f, 1f)
                Surface(Modifier.offset(x = left).width(width).fillMaxHeight().testTag("favorite-radius-bubble"),
                    shape = SliderSpeechShape(appCorner(18.dp), fraction, shown <= .001f, shown >= .999f), color = colors.primaryContainer,
                    contentColor = colors.onPrimaryContainer, border = BorderStroke(.8.dp, colors.primary.copy(alpha = .45f)), shadowElevation = 2.dp) {
                    Box(Modifier.appPanelDecoration().padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 20.dp), contentAlignment = Alignment.Center) {
                        // Only the kind of text cross-fades (off / metres / always); the metres count runs live.
                        AnimatedContent(when (value) { 0 -> 0; FAVORITE_RADIUS_ALWAYS -> 2; else -> 1 },
                            transitionSpec = { fadeIn(tween(if (calm) 0 else 160)) togetherWith fadeOut(tween(if (calm) 0 else 110)) }, label = "favorite-radius-text") { kind ->
                            BasicText(favoriteRadiusText(when (kind) { 0 -> 0; 2 -> FAVORITE_RADIUS_ALWAYS; else -> value.takeIf { it in 1..FAVORITE_RADIUS_MAX } ?: FAVORITE_RADIUS_DEFAULT }),
                                Modifier.fillMaxWidth().testTag("favorite-radius-value"),
                                style = MaterialTheme.typography.bodyMedium.copy(color = colors.onPrimaryContainer, textAlign = TextAlign.Center, lineHeight = TextUnit.Unspecified),
                                maxLines = 3, autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = MaterialTheme.typography.bodyMedium.fontSize, stepSize = .5.sp))
                        }
                    }
                }
            }
            Slider(position, { raw ->
                val next = when { raw <= RADIUS_SNAP -> 0f; raw >= 1f - RADIUS_SNAP -> 1f; else -> raw }
                if (next != position && (next == 0f || next == 1f) && !calm) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                position = next
            }, onValueChangeFinished = { onCommit(favoriteRadiusForPosition(position)) },
                modifier = Modifier.testTag("favorite-radius-slider").semantics { contentDescription = tr("Umkreis um Favoriten"); stateDescription = description })
        }
    }
}

/**
 * Grid of the network plan: a rectangle anchored at the top left of the greyed 12 × 12 grid, from
 * 3 × 3 to 12 × 12 (also 3 × 12). H12: growing keeps positions, lines and directions at once; a
 * smaller size first names the favourites that would move into the tray ("In Ablage verschieben").
 */
@Composable
internal fun NetworkGridSizeSetting(columns: Int, rows: Int, outside: (Int, Int) -> List<String>, onChange: (Int, Int) -> Unit) {
    var draft by remember(columns, rows) { mutableStateOf(columns to rows) }
    var confirm by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    // Armed by a brief hold: the rectangle lifts a little so it is clear that dragging resizes now.
    var holding by remember { mutableStateOf(false) }
    val calm = LocalAppSettings.current.reduceMotion
    val haptics = LocalHapticFeedback.current
    val colors = MaterialTheme.colorScheme
    val latestOnChange by rememberUpdatedState(onChange)
    fun commit(size: Pair<Int, Int>) {
        if (size == columns to rows) return
        if (outside(size.first, size.second).isNotEmpty()) confirm = size else latestOnChange(size.first, size.second)
    }
    SettingsCard(Modifier.testTag("network-grid-setting")) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Raster des Netzplans"), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                Text(tr("%1\$s × %2\$s", draft.first, draft.second), Modifier.testTag("network-grid-value"), style = MaterialTheme.typography.titleSmall, color = colors.primary)
            }
            BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                val side = minOf(maxWidth, 264.dp)
                val pitch = side / NETWORK_GRID_SIZE
                val width by animateDpAsState(pitch * draft.first, if (calm) snap() else spring(dampingRatio = .55f, stiffness = 500f), label = "grid-width")
                val height by animateDpAsState(pitch * draft.second, if (calm) snap() else spring(dampingRatio = .55f, stiffness = 500f), label = "grid-height")
                val label = tr("Raster des Netzplans")
                val state = tr("%1\$s Spalten, %2\$s Zeilen", draft.first, draft.second)
                val wider = tr("Spalte hinzufügen"); val narrower = tr("Spalte entfernen"); val taller = tr("Zeile hinzufügen"); val shorter = tr("Zeile entfernen")
                Box(Modifier.size(side).testTag("network-grid-picker").semantics {
                    contentDescription = label; stateDescription = state
                    customActions = listOf(
                        CustomAccessibilityAction(wider) { commit(normalizedGridSize(columns + 1) to rows); true },
                        CustomAccessibilityAction(narrower) { commit(normalizedGridSize(columns - 1) to rows); true },
                        CustomAccessibilityAction(taller) { commit(columns to normalizedGridSize(rows + 1)); true },
                        CustomAccessibilityAction(shorter) { commit(columns to normalizedGridSize(rows - 1)); true })
                }.pointerInput(columns, rows) {
                    val cell = size.width / NETWORK_GRID_SIZE.toFloat()
                    fun sizeAt(o: Offset) = normalizedGridSize(ceil(o.x / cell).toInt()) to normalizedGridSize(ceil(o.y / cell).toInt())
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        // A brief hold arms the picker; a finger that moves on before (scrolling) or
                        // lifts (a tap) changes nothing and leaves the gesture to the list.
                        val armed = withTimeoutOrNull(NETWORK_GRID_HOLD_MS) {
                            while (true) {
                                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull false
                                if (!change.pressed || change.isConsumed || change.systemCancelled) return@withTimeoutOrNull false
                                if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) return@withTimeoutOrNull false
                            }
                            @Suppress("UNREACHABLE_CODE") false
                        }.let { it == null }
                        if (!armed) return@awaitEachGesture
                        holding = true
                        if (!calm) haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                        val before = draft
                        var dragging = false
                        var cancelled = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id }
                            // A touch the system takes away changes nothing (a new size removes all lines).
                            if (change == null || change.systemCancelled || change.isConsumed) { cancelled = true; break }
                            change.consume()
                            if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) dragging = true
                            if (dragging) {
                                val next = sizeAt(change.position)
                                if (next != draft) { draft = next; if (!calm) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick) }
                            }
                            if (!change.pressed) break
                        }
                        holding = false
                        if (cancelled) draft = before else commit(draft)
                    }
                }) {
                    Canvas(Modifier.matchParentSize()) {
                        val p = size.width / NETWORK_GRID_SIZE
                        for (x in 0 until NETWORK_GRID_SIZE) for (y in 0 until NETWORK_GRID_SIZE)
                            drawCircle(colors.onSurfaceVariant.copy(alpha = .32f), 1.6.dp.toPx(), Offset((x + .5f) * p, (y + .5f) * p))
                    }
                    val shape = appCorner(14.dp)
                    val lift by animateFloatAsState(if (holding) 1f else 0f, if (calm) snap() else spring(dampingRatio = .6f, stiffness = 700f), label = "grid-armed")
                    Box(Modifier.size(width, height).background(colors.primary.copy(alpha = .22f + .12f * lift), shape).border((2f + 1.5f * lift).dp, colors.primary, shape)) {
                        Box(Modifier.align(Alignment.BottomEnd).offset(6.dp, 6.dp).size(18.dp).graphicsLayer { scaleX = 1f + .35f * lift; scaleY = scaleX }
                            .background(colors.primary, CircleShape).border(2.dp, colors.surfaceContainerLow, CircleShape))
                    }
                }
            }
            Text(tr("Kurz halten, dann das Rechteck auf die gewünschte Größe ziehen (3 × 3 bis 12 × 12). Beim Vergrößern bleiben Favoriten, Linien und Richtungen erhalten. Beim Verkleinern kommen Favoriten außerhalb des Rasters in die Ablage; ihre Linien bleiben gespeichert."),
                style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
    }
    confirm?.let { size ->
        val moved = outside(size.first, size.second)
        AppAlertDialog(onDismissRequest = { confirm = null; draft = columns to rows },
            title = { Text(tr("Raster auf %1\$s × %2\$s verkleinern?", size.first, size.second)) },
            text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(tr("Diese Favoriten liegen außerhalb des neuen Rasters und kommen in die Ablage:"))
                Text(moved.joinToString(", "), Modifier.testTag("network-grid-outside"), style = MaterialTheme.typography.titleSmall)
                Text(tr("Ihre Linien bleiben gespeichert und erscheinen wieder, sobald du die Favoriten auf den Plan legst."),
                    style = MaterialTheme.typography.bodySmall)
            } },
            confirmButton = { TextButton({ confirm = null; latestOnChange(size.first, size.second) },
                Modifier.heightIn(min = 48.dp).testTag("network-grid-confirm")) { Text(tr("In Ablage verschieben")) } },
            dismissButton = { TextButton({ confirm = null; draft = columns to rows }, Modifier.heightIn(min = 48.dp)) { Text(tr("Abbrechen")) } })
    }
}
