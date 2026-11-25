// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import dev.ambivalenton.openv.R
import dev.ambivalenton.openv.data.model.LineType
import dev.ambivalenton.openv.data.model.Location
import dev.ambivalenton.openv.data.model.reduceMotion
import dev.ambivalenton.openv.localization.tr
import dev.ambivalenton.openv.ui.theme.LocalAppSettings
import dev.ambivalenton.openv.ui.theme.LocalAppVisuals
import dev.ambivalenton.openv.ui.theme.corner
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.drawscope.clipPath

/*
 * The start chip and the filter button of the search no longer open sheets. Both grow out
 * of themselves with a spring (Material 3 Expressive spatial motion) and fold back the same way.
 * The start chip becomes a field in the flow of the screen and pushes the destination field down;
 * the filter grows in place above the screen, anchored at its own top right corner.
 */

/** Height of the collapsed start chip and of the filter button. */
internal val MorphChipHeight = 40.dp
private val MorphChipCorner = 12.dp
private val MorphPanelCorner = 28.dp

/** Spring of an opening control: a visible but small overshoot. */
internal fun <T> morphOpenSpring(visibility: T? = null) = spring(dampingRatio = .66f, stiffness = 380f, visibilityThreshold = visibility)
/** Spring of a closing control: settles without bouncing below its chip size. */
internal fun <T> morphCloseSpring(visibility: T? = null) = spring(dampingRatio = .84f, stiffness = 460f, visibilityThreshold = visibility)

/** Follows a measured target with a spring; the first value is taken over without animation. */
@Stable private class SpringFollower(private val open: Boolean = true) {
    var animation: Animatable<Float, AnimationVector1D>? = null
    fun follow(target: Float, scope: kotlinx.coroutines.CoroutineScope, quiet: Boolean): Float {
        val current = animation ?: Animatable(target).also { animation = it }
        if (current.targetValue != target) {
            if (quiet) scope.launch { current.snapTo(target) }
            else scope.launch { current.animateTo(target, if (open) morphOpenSpring(.5f) else morphCloseSpring(.5f)) }
        }
        return current.value
    }
}

/**
 * The start of the search: a chip ("Dein Standort", "Standort wählen" …) that turns into a field like
 * the destination field. First row: "Aktuellen GPS-Standort verwenden" with a switch. Without GPS the
 * field grows further down and offers "Manuellen Standort festlegen" below a divider. While that input
 * has focus the whole field is framed and the label sits in the frame, as in the destination field.
 */
@Composable fun OriginMorphField(
    label: String,
    open: Boolean,
    onOpen: () -> Unit,
    gpsOn: Boolean,
    onGps: (Boolean) -> Unit,
    manual: Location?,
    providerKey: String,
    search: suspend (String) -> List<Location>,
    onManual: (Location?) -> Unit,
    collapsedMaxWidth: Dp,
    modifier: Modifier = Modifier,
    /** H02: a new value opens the manual input with the focus in it ("Start wählen"). */
    focusRequest: Int = 0,
    /** A new value empties the manual input (the pull gesture starts a fresh entry). */
    clearRequest: Int = 0,
    /** H02: saved places stay directly selectable as start. */
    favorites: List<dev.ambivalenton.openv.data.model.Favorite> = emptyList(),
    /** Build 122: no start yet – the chip is filled with the accent so it is found at once. */
    missing: Boolean = false,
) {
    val quiet = LocalAppSettings.current.reduceMotion
    val manualFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(focusRequest, open, gpsOn) {
        if (focusRequest > 0 && open && !gpsOn) { kotlinx.coroutines.delay(180); runCatching { manualFocus.requestFocus() } }
    }
    val visuals = LocalAppVisuals.current
    val scheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val progress = remember { Animatable(if (open) 1f else 0f) }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(gpsOn) { if (gpsOn) focused = false }
    LaunchedEffect(open, quiet) {
        if (!open && focused) { focusManager.clearFocus(force = true); keyboard?.hide() }
        val target = if (open) 1f else 0f
        if (quiet) progress.snapTo(target)
        else progress.animateTo(target, if (open) morphOpenSpring() else morphCloseSpring())
    }
    val panelHeight = remember { SpringFollower() }
    val frameAlpha by animateFloatAsState(if (focused && open) 1f else 0f, if (quiet) snap() else tween(160), label = "origin-frame")
    val measurer = rememberTextMeasurer()
    val frameLabel = tr("Manuellen Standort festlegen")
    val labelStyle = MaterialTheme.typography.bodySmall
    val labelLayout = remember(frameLabel, labelStyle, density) { measurer.measure(frameLabel, labelStyle) }
    val p = progress.value
    val shape = visuals.corner(lerp(MorphChipCorner, MorphPanelCorner, p.coerceIn(0f, 1f)))
    val fill = scheme.surfaceContainerHigh
    val outline = scheme.outline
    val panelComposed = open || p > .001f
    Layout(content = {
        // Collapsed: the familiar chip. It stays measured so the morph always starts from its true size.
        // Build 122: "Ab" says what the chip is, the symbol how the start is found (GPS mark or a fixed place).
        val chipInk = if (missing) scheme.onPrimaryContainer else scheme.onSurface
        Row(Modifier.layoutId("chip").height(MorphChipHeight).padding(horizontal = 12.dp)
            .graphicsLayer { alpha = (1f - progress.value * 2.6f).coerceIn(0f, 1f) },
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppIcon(if (gpsOn) R.drawable.ic_gps else R.drawable.ic_place, null, Modifier.size(18.dp),
                tint = if (missing) scheme.onPrimaryContainer else scheme.primary)
            Text(tr("Ab"), style = MaterialTheme.typography.labelMedium, color = chipInk.copy(alpha = .72f), maxLines = 1)
            Text(label, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.labelLarge, color = chipInk,
                fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            AppIcon(R.drawable.ic_expand, null, Modifier.size(16.dp), tint = chipInk)
        }
        if (panelComposed) Column(Modifier.layoutId("panel").fillMaxWidth()
            .graphicsLayer { alpha = ((progress.value - .3f) / .45f).coerceIn(0f, 1f) }.testTag("origin-panel")) {
            Row(Modifier.fillMaxWidth().heightIn(min = 64.dp)
                .toggleable(gpsOn, enabled = open, role = Role.Switch, onValueChange = onGps)
                .padding(start = 18.dp, end = 14.dp, top = 8.dp, bottom = 8.dp).testTag("origin-gps-row"),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AppIcon(R.drawable.ic_gps, null, Modifier.size(22.dp), tint = if (gpsOn) scheme.primary else scheme.onSurfaceVariant)
                // Same type as the labels of the destination field and the manual input below.
                Text(tr("Aktuellen GPS-Standort verwenden"), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Switch(gpsOn, null, Modifier.testTag("origin-gps-switch"), enabled = open)
            }
            if (!gpsOn) {
                // The same quiet divider as between "Heute" and "Jetzt", laid horizontally.
                HorizontalDivider(Modifier.padding(horizontal = 18.dp).testTag("origin-divider"), color = scheme.outlineVariant)
                PlaceSearchField(frameLabel, manual, providerKey, search, onManual,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).testTag("origin-manual"),
                    enabled = open, bare = true, onFocusChange = { focused = it }, focusRequester = manualFocus, clearText = clearRequest)
            }
        }
    }, modifier = modifier
        .semantics(mergeDescendants = !open) {
            if (!open) { role = Role.Button; contentDescription = tr("Startpunkt ändern") + ": " + label }
            stateDescription = if (open) tr("Ausgeklappt") else tr("Eingeklappt")
        }
        .testTag("origin-morph")
        .drawWithContent {
            val o = shape.createOutline(size, layoutDirection, this)
            val reveal = progress.value.coerceIn(0f, 1f)
            if (missing && reveal < 1f) drawOutline(o, scheme.primaryContainer.copy(alpha = 1f - reveal))
            drawOutline(o, fill.copy(alpha = fill.alpha * reveal))
            if (reveal < 1f) drawOutline(o, outline.copy(alpha = 1f - reveal), style = Stroke(1.dp.toPx()))
            val path = Path().apply { addOutline(o) }
            clipPath(path) { this@drawWithContent.drawContent() }
            if (frameAlpha > 0f) {
                // Focus frame with the label set into it, like an outlined text field.
                val start = 16.dp.toPx(); val gap = 4.dp.toPx(); val half = labelLayout.size.height / 2f
                clipRect(start - gap, -half - 2f, start + labelLayout.size.width + gap, half + 2f, ClipOp.Difference) {
                    drawOutline(o, scheme.primary.copy(alpha = frameAlpha), style = Stroke(2.dp.toPx()))
                }
                drawText(labelLayout, scheme.primary, Offset(start, -half), alpha = frameAlpha)
            }
        }
        // Only the chip is one button; the open field keeps its switch and input separately accessible.
        .then(if (!open) Modifier.clickable(role = Role.Button, onClickLabel = tr("Startpunkt ändern"), onClick = onOpen) else Modifier)
    ) { measurables, constraints ->
        val full = constraints.maxWidth
        val chip = measurables.first { it.layoutId == "chip" }
            .measure(Constraints(maxWidth = collapsedMaxWidth.roundToPx().coerceIn(0, full)))
        val panel = measurables.firstOrNull { it.layoutId == "panel" }?.measure(Constraints(minWidth = full, maxWidth = full))
        val t = progress.value
        val panelH = panel?.let { panelHeight.follow(it.height.toFloat(), scope, quiet) } ?: chip.height.toFloat()
        val width = (chip.width + (full - chip.width) * t.coerceIn(0f, 1f)).roundToInt().coerceIn(0, full)
        val height = (chip.height + (panelH - chip.height) * t).roundToInt().coerceAtLeast((chip.height * .85f).roundToInt())
        layout(width, height) {
            chip.place(0, 0)
            panel?.place(0, 0)
        }
    }
}

// H04/2.3 (build 93): the filter button and its grown panel left the search; the transport-mode
// filter now lives under Einstellungen → Fahrt & Navigation → Verbindungssuche (TransportModeFrame).
