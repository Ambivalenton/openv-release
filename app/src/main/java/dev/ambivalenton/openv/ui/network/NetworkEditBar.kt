// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.network

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.graphics.luminance
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import dev.ambivalenton.openv.data.model.reduceMotion
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.graphics.toArgb
import androidx.compose.runtime.setValue
import dev.ambivalenton.openv.R
import dev.ambivalenton.openv.localization.tr
import dev.ambivalenton.openv.ui.components.AppIcon
import dev.ambivalenton.openv.ui.components.IconTapMotion
import dev.ambivalenton.openv.ui.components.iconTap
import dev.ambivalenton.openv.ui.components.rememberIconTap
import dev.ambivalenton.openv.ui.components.pressMorphShape
import dev.ambivalenton.openv.ui.components.shapeShadow
import dev.ambivalenton.openv.data.model.ThemeShape
import dev.ambivalenton.openv.ui.theme.LocalAppVisuals
import dev.ambivalenton.openv.ui.theme.appCorner
import dev.ambivalenton.openv.ui.theme.contrastContentColor
import dev.ambivalenton.openv.ui.theme.corner

/** Geometry of the bar, measured from its right edge (the pencil's slot stays where it is). */
internal object NetworkEditBarGeometry {
    val HEIGHT = 48.dp
    /** The open bar is a little taller, for labels over two lines. */
    val OPEN_HEIGHT = 60.dp
    val SLOT = 44.dp
    val BUTTON = 40.dp
    /** Build 102: the plus of "Favorit hinzufügen" is a tenth smaller than its neighbours. */
    val ADD_BUTTON = BUTTON * .9f
    /** Build 104: the open bar is twice as tall; the upper half (the info band) reaches above its row. */
    val INFO_HEIGHT = OPEN_HEIGHT
    /** Height of a tool box and of the delete zone in the lower half. */
    val BOX_HEIGHT = OPEN_HEIGHT - 8.dp
    /** Inset of the outer buttons from the bar's ends. */
    val INSET = (HEIGHT - BUTTON) / 2
    /** Room of the check at the right end (no box of its own). */
    val CHECK_AREA = INSET + BUTTON + 10.dp
    /** Gap between the two boxes in the middle of the bar. */
    val BOX_GAP = 6.dp
    /** Reveal of each element as a share of the morph, right to left: check, plus, gear. */
    const val CHECK_START = .12f
    const val ADD_START = .3f
    const val SETTINGS_START = .46f
}

private fun stagger(progress: Float, start: Float) = ((progress - start) / (1f - start)).coerceAtLeast(0f)

/**
 * The pencil above the network plan morphs into a toolbar (build 73). The open bar spans the
 * whole width and covers the heading. On the left a box "Favoriten-Einstellungen" with the gear, in
 * the middle a box "Favorit hinzufügen" with the plus, on the right the check without a box. The boxes
 * stand slightly apart in colour, take the theme's corners and carry icon and two-line label; the
 * buttons lie above them with a drop shadow. The plus is always shaped unlike its neighbours: round
 * in every theme, square where the theme's buttons are round anyway.
 *
 * Motion uses the Material 3 Expressive springs of the app's motion scheme; with "Bewegung
 * reduzieren" the scheme snaps and the icon animations are skipped. Every button keeps its
 * TalkBack label and a 48 dp touch target height.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NetworkEditBar(
    editing: Boolean,
    onEdit: () -> Unit,
    onSettings: () -> Unit,
    onAdd: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    /** Build 104: while a favourite is moved, the tools give way to a zone that deletes it. */
    deleteZone: NetworkDeleteZone? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val quiet = dev.ambivalenton.openv.ui.theme.LocalAppSettings.current.reduceMotion
    val deleting = editing && deleteZone?.moving != null
    val zoneIn by androidx.compose.animation.core.animateFloatAsState(if (deleting) 1f else 0f,
        if (quiet) androidx.compose.animation.core.snap() else androidx.compose.animation.core.spring(dampingRatio = .62f, stiffness = 520f),
        label = "network-delete-zone")
    val motion = MaterialTheme.motionScheme
    val transition = updateTransition(editing, label = "network-edit-bar")
    // Opening uses the bouncier fast spatial spring (the bar overshoots and settles); closing the
    // calmer default spring, so the check stays visible a moment longer while it collapses.
    val progress by transition.animateFloat(
        transitionSpec = { if (targetState) motion.fastSpatialSpec() else motion.defaultSpatialSpec() },
        label = "network-edit-morph",
    ) { if (it) 1f else 0f }
    val geometry = NetworkEditBarGeometry
    // Build 102: the bar always takes the open height, so opening it does not push the plan below down.
    androidx.compose.foundation.layout.BoxWithConstraints(modifier.fillMaxWidth().height(geometry.OPEN_HEIGHT), contentAlignment = Alignment.CenterEnd) {
        val full = maxWidth
        val width = lerp(geometry.HEIGHT.value, full.value, progress).coerceAtLeast(geometry.BUTTON.value).dp
        // Build 104: open, the bar is twice the row's height. Its lower edge stays on the row, so the plan
        // below keeps its place; the upper half (the info band) lies over the row above, which steps aside.
        val opened = progress.coerceIn(0f, 1.08f)
        val height = lerp(geometry.HEIGHT.value, (geometry.OPEN_HEIGHT + geometry.INFO_HEIGHT).value, opened).dp
        val top = lerp((geometry.OPEN_HEIGHT - geometry.HEIGHT).value / 2, -geometry.INFO_HEIGHT.value, opened).dp
        val pencilInteraction = remember { MutableInteractionSource() }
        // Closed it is the round pencil button with the app's press morph; open, the theme's bar shape.
        val pressShape = themedPressShape(pencilInteraction, geometry.HEIGHT)
        val barShape: Shape = if (editing || progress > .02f) appCorner(geometry.OPEN_HEIGHT / 2) else pressShape
        val container = scheme.secondaryContainer
        val onContainer = scheme.onSecondaryContainer
        val showTools = editing || transition.currentState || transition.isRunning
        Box(
            Modifier.layout { measurable, constraints ->
                val placeable = measurable.measure(constraints.copy(minHeight = 0, maxHeight = androidx.compose.ui.unit.Constraints.Infinity))
                layout(placeable.width, geometry.OPEN_HEIGHT.roundToPx()) { placeable.place(0, top.roundToPx()) }
            }.width(width).height(height).then(if (editing) Modifier.testTag("network-edit-bar") else Modifier)
                .shapeShadow(barShape, (1.5f * progress.coerceIn(0f, 1f)).dp)
                .clip(barShape)
                .background(container)
                .then(
                    if (!editing) Modifier.clickable(pencilInteraction, ripple(), role = Role.Button, onClickLabel = tr("Bearbeiten"), onClick = onEdit)
                        .semantics { contentDescription = tr("Bearbeiten") }.testTag("network-edit")
                    else Modifier
                ),
        ) {
            // Pencil: turns and fades out where the check grows in.
            val pencil = (1f - progress * 2.2f).coerceIn(0f, 1f)
            if (pencil > 0f) AppIcon(R.drawable.ic_edit, null,
                Modifier.align(Alignment.BottomEnd).offset(x = -(geometry.HEIGHT - 24.dp) / 2, y = -(geometry.HEIGHT - 24.dp) / 2)
                    .graphicsLayer {
                        alpha = pencil
                        rotationZ = -40f * progress
                        val s = 1f - .35f * progress.coerceIn(0f, 1f)
                        scaleX = s; scaleY = s
                    },
                tint = onContainer)
            if (showTools) {
                val active = editing
                val dark = scheme.background.luminance() < .4f
                // The boxes stand slightly apart from the bar; the buttons are lighter still and cast a shadow.
                val boxFill = lerp(container, if (dark) Color.White else scheme.surface, if (dark) .10f else .45f).compositeOver(scheme.surface)
                val toolFill = lerp(container, if (dark) Color.White else scheme.surface, if (dark) .24f else .85f).compositeOver(scheme.surface)
                val toolInk = contrastContentColor(toolFill)
                val boxInk = contrastContentColor(boxFill)
                // Both boxes share the room left of the check equally, so neither label is cut.
                val boxWidth = ((full - geometry.CHECK_AREA - geometry.INSET - geometry.BOX_GAP) / 2).coerceAtLeast(geometry.BUTTON)
                val middleWidth = boxWidth
                val leftWidth = boxWidth
                // Build 104: the gestures of the edit mode in three short cells across the whole bar.
                GestureBand(stagger(progress, geometry.SETTINGS_START), boxFill, boxInk)
                // While a favourite is moved, the tools step back (shrink, sink, fade) and the delete zone springs in.
                val away = zoneIn.coerceIn(0f, 1f)
                val toolsHidden = Modifier.graphicsLayer {
                    alpha = 1f - away
                    val s = 1f - .14f * zoneIn
                    scaleX = s; scaleY = s
                    translationY = 10.dp.toPx() * zoneIn
                }
                Box(Modifier.matchParentSize().then(toolsHidden)) {
                ToolBox(rightGap = geometry.CHECK_AREA + boxWidth + geometry.BOX_GAP, boxWidth = leftWidth, reveal = stagger(progress, geometry.SETTINGS_START),
                    active = active && !deleting, label = tr("Favoriten-Einstellungen"), tag = "network-settings", boxFill = boxFill, boxInk = boxInk,
                    fill = toolFill, icon = R.drawable.ic_settings, tint = toolInk, motion = IconTapMotion.NUDGE, onClick = onSettings,
                    shape = null, buttonSize = geometry.BUTTON)
                ToolBox(rightGap = geometry.CHECK_AREA, boxWidth = middleWidth, reveal = stagger(progress, geometry.ADD_START),
                    active = active && !deleting, label = tr("Favorit hinzufügen"), tag = "network-add", boxFill = boxFill, boxInk = boxInk,
                    fill = toolFill, icon = R.drawable.ic_add, tint = toolInk, motion = IconTapMotion.QUARTER_TURN, onClick = onAdd,
                    shape = contrastingAddShape(), buttonSize = geometry.ADD_BUTTON)
                ToolButton(
                    slotEnd = geometry.INSET, reveal = stagger(progress, geometry.CHECK_START), active = active && !deleting,
                    label = tr("Fertig"), tag = "network-done", fill = scheme.primary,
                    icon = R.drawable.ic_check, tint = scheme.onPrimary, motion = IconTapMotion.DRAW, onClick = onDone,
                )
                }
                if (deleteZone != null && (deleting || zoneIn > .01f)) DeleteZone(deleteZone, zoneIn)
            }
        }
    }
}

/** The plus differs from its round or cornered neighbours: a circle, or a square in round themes. */
@Composable
private fun contrastingAddShape(): Shape {
    val visuals = LocalAppVisuals.current
    val round = visuals.radiusScale >= 1f && (visuals.theme?.shapeStyle ?: ThemeShape.ROUNDED) == ThemeShape.ROUNDED
    return if (round) androidx.compose.foundation.shape.RoundedCornerShape(5.dp) else androidx.compose.foundation.shape.CircleShape
}

/**
 * A tool on its own box: the box (theme corners, slightly apart in colour) carries the
 * button with a shadow at its start and the label over two lines in the remaining room.
 * Only the box surface is clipped; the button floats above it, centred in the box's rounded end,
 * so neither its corners nor its shadow are cut by the box.
 */
@Composable
private fun BoxScope.ToolBox(
    rightGap: Dp, boxWidth: Dp, reveal: Float, active: Boolean, label: String, tag: String, boxFill: Color, boxInk: Color,
    fill: Color, icon: Int, tint: Color, motion: IconTapMotion, onClick: () -> Unit, shape: Shape?, buttonSize: Dp,
) {
    val tap = rememberIconTap(motion)
    val interaction = remember { MutableInteractionSource() }
    val buttonShape = shape ?: themedPressShape(interaction, buttonSize)
    val visible = reveal.coerceIn(0f, 1f)
    val boxHeight = NetworkEditBarGeometry.BOX_HEIGHT
    Box(
        Modifier.align(Alignment.BottomEnd).offset(x = -rightGap, y = -(NetworkEditBarGeometry.OPEN_HEIGHT - boxHeight) / 2).width(boxWidth).height(boxHeight)
            .graphicsLayer {
                alpha = (visible * 1.6f).coerceIn(0f, 1f)
                // Fading must not render into a buffer of the box's size, which would cut the shadow.
                compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.ModulateAlpha
            }
            .testTag("$tag-box"),
    ) {
        Box(Modifier.matchParentSize().clip(appCorner(boxHeight / 2)).background(boxFill)
            .then(if (active) Modifier.clickable(interaction, ripple(color = boxInk), role = Role.Button) { tap.play(); onClick() }
                .semantics(mergeDescendants = true) { contentDescription = label; role = Role.Button }.testTag(tag)
                else Modifier.clearAndSetSemantics { }))
        // The button's centre sits on the centre of the box's rounded end.
        val inset = (boxHeight - buttonSize) / 2
        androidx.compose.foundation.layout.Row(Modifier.align(Alignment.CenterStart).padding(start = inset, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(buttonSize)
                .graphicsLayer {
                    // Allows a little of the spring's overshoot without touching the neighbour.
                    val s = reveal.coerceIn(0f, 1.08f)
                    scaleX = s; scaleY = s
                    transformOrigin = TransformOrigin.Center
                }
                .shapeShadow(buttonShape, 4.dp)
                .clip(buttonShape).background(fill).testTag("$tag-button"), contentAlignment = Alignment.Center) {
                AppIcon(icon, null, Modifier.size(24.dp * (buttonSize / NetworkEditBarGeometry.BUTTON)).iconTap(tap), tint = tint)
            }
            // Over two lines, centred: "Favoriten-" / "Einstellungen", "Favorit" / "hinzufügen".
            androidx.compose.material3.Text(twoLines(label),
                Modifier.weight(1f).padding(start = 8.dp).clearAndSetSemantics { },
                color = boxInk, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                // A calmer size; with large system fonts it shrinks instead of being cut.
                style = MaterialTheme.typography.labelMedium.copy(lineHeight = androidx.compose.ui.unit.TextUnit.Unspecified), maxLines = 2,
                softWrap = false, overflow = androidx.compose.ui.text.style.TextOverflow.Clip,
                autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 7.sp,
                    maxFontSize = MaterialTheme.typography.labelMedium.fontSize, stepSize = .5.sp))
        }
    }
}

/** Round themes get the expressive press morph (circle → rounded square); other themes keep their
 * own corners (cut, bevelled, square), so the bar matches every app theme. */
@Composable
private fun themedPressShape(interaction: MutableInteractionSource, size: Dp): Shape {
    val visuals = LocalAppVisuals.current
    val morph = pressMorphShape(interaction, 50, 30)
    val round = visuals.radiusScale >= 1f && (visuals.theme?.shapeStyle ?: ThemeShape.ROUNDED) == ThemeShape.ROUNDED
    return if (round) morph else visuals.corner(size / 2)
}

/** One round tool. [slotEnd] is the distance of its right edge from the bar's right edge. */
@Composable
private fun BoxScope.ToolButton(
    slotEnd: Dp, reveal: Float, active: Boolean, label: String, tag: String, fill: Color,
    icon: Int, tint: Color, motion: IconTapMotion, onClick: () -> Unit,
) {
    val tap = rememberIconTap(motion)
    val interaction = remember { MutableInteractionSource() }
    val shape = themedPressShape(interaction, NetworkEditBarGeometry.BUTTON)
    val visible = reveal.coerceIn(0f, 1f)
    Box(
        Modifier.align(Alignment.BottomEnd).offset(x = -slotEnd, y = -(NetworkEditBarGeometry.OPEN_HEIGHT - NetworkEditBarGeometry.BUTTON) / 2)
            .size(NetworkEditBarGeometry.BUTTON)
            .graphicsLayer {
                alpha = (visible * 1.6f).coerceIn(0f, 1f)
                // Allows a little of the spring's overshoot without touching the neighbour.
                val s = reveal.coerceIn(0f, 1.08f)
                scaleX = s; scaleY = s
                transformOrigin = TransformOrigin.Center
            }
            .shapeShadow(shape, 3.dp)
            .clip(shape)
            .background(fill)
            .then(
                if (active) Modifier.clickable(interaction, ripple(color = tint), role = Role.Button) { tap.play(); onClick() }
                    .semantics { contentDescription = label; role = Role.Button }.testTag(tag)
                else Modifier.clearAndSetSemantics { }
            ),
        contentAlignment = Alignment.Center,
    ) {
        AppIcon(icon, null, Modifier.iconTap(tap), tint = tint)
    }
}

/** A label over two lines, broken after its first hyphen or at its first space. */
internal fun twoLines(label: String): String {
    val hyphen = label.indexOf('-')
    if (hyphen in 1 until label.lastIndex) return label.substring(0, hyphen + 1) + "\n" + label.substring(hyphen + 1)
    val space = label.indexOf(' ')
    return if (space > 0) label.substring(0, space) + "\n" + label.substring(space + 1) else label
}

/**
 * Build 104: shared between the edit bar and the plan while a favourite is moved. The plan names the
 * favourite in [moving] and reads [over]; the bar shows the delete zone and reports its [bounds] (root
 * coordinates), so the plan can tell whether the finger is above it.
 */
@androidx.compose.runtime.Stable
class NetworkDeleteZone {
    var moving by androidx.compose.runtime.mutableStateOf<String?>(null)
    var bounds by androidx.compose.runtime.mutableStateOf<androidx.compose.ui.geometry.Rect?>(null)
    var over by androidx.compose.runtime.mutableStateOf(false)
}

/** The three gestures of the edit mode, each a word and what it does, on the band above the tools. */
@Composable
private fun BoxScope.GestureBand(reveal: Float, fill: Color, ink: Color) {
    val geometry = NetworkEditBarGeometry
    val cells = listOf(tr("Halten und schieben") to tr("Favorit verschieben"), tr("Ziehen") to tr("Linie verbinden"),
        tr("Tippen") to tr("Favorit einstellen"))
    androidx.compose.foundation.layout.Row(
        Modifier.align(Alignment.TopStart).padding(start = geometry.INSET, end = geometry.INSET, top = geometry.INSET)
            .fillMaxWidth().height(geometry.INFO_HEIGHT - geometry.INSET)
            .graphicsLayer { alpha = (reveal * 1.6f).coerceIn(0f, 1f); translationY = (1f - reveal.coerceIn(0f, 1f)) * 8.dp.toPx() }
            .clip(appCorner((geometry.INFO_HEIGHT - geometry.INSET) / 2)).background(fill)
            .semantics(mergeDescendants = true) {}.testTag("network-gesture-band"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        cells.forEachIndexed { index, (gesture, effect) ->
            if (index > 0) Box(Modifier.width(1.dp).height(26.dp).background(ink.copy(alpha = .18f)))
            androidx.compose.foundation.layout.Column(Modifier.weight(1f).padding(horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                androidx.compose.material3.Text(gesture, color = ink, maxLines = 1, softWrap = false,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
                    autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 7.sp,
                        maxFontSize = MaterialTheme.typography.labelMedium.fontSize, stepSize = .5.sp))
                androidx.compose.material3.Text(effect, color = ink.copy(alpha = .78f), maxLines = 1, softWrap = false,
                    style = MaterialTheme.typography.labelSmall,
                    autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 7.sp,
                        maxFontSize = MaterialTheme.typography.labelSmall.fontSize, stepSize = .5.sp))
            }
        }
    }
}

/**
 * The zone that deletes a moved favourite: red, or another signal colour where the theme itself is red.
 * It springs in where the tools were, grows a little while the favourite is above it and reports its bounds.
 */
@Composable
private fun BoxScope.DeleteZone(zone: NetworkDeleteZone, shown: Float) {
    val geometry = NetworkEditBarGeometry
    val quiet = dev.ambivalenton.openv.ui.theme.LocalAppSettings.current.reduceMotion
    val (signal, ink) = deleteSignal()
    val over = zone.over
    val grow by androidx.compose.animation.core.animateFloatAsState(if (over) 1f else 0f,
        if (quiet) androidx.compose.animation.core.snap() else androidx.compose.animation.core.spring(dampingRatio = .45f, stiffness = 600f),
        label = "network-delete-over")
    androidx.compose.runtime.DisposableEffect(zone) { onDispose { zone.bounds = null; zone.over = false } }
    androidx.compose.foundation.layout.Row(
        Modifier.align(Alignment.BottomStart).padding(start = geometry.INSET, end = geometry.INSET, bottom = (geometry.OPEN_HEIGHT - geometry.BOX_HEIGHT) / 2)
            .fillMaxWidth().height(geometry.BOX_HEIGHT)
            .onGloballyPositioned { zone.bounds = it.boundsInRoot() }
            .graphicsLayer {
                alpha = (shown * 1.4f).coerceIn(0f, 1f)
                val s = .86f + .14f * shown + .05f * grow
                scaleX = s; scaleY = s
            }
            .clip(appCorner(geometry.BOX_HEIGHT / 2)).background(lerp(signal, Color.Black, .14f * grow))
            .semantics(mergeDescendants = true) {}.testTag("network-delete-zone"),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        AppIcon(R.drawable.ic_delete, null, Modifier.size(22.dp).graphicsLayer { rotationZ = -14f * grow }, tint = ink)
        androidx.compose.material3.Text(if (over) tr("Loslassen zum Löschen") else tr("Hierhin verschieben, um den Favoriten zu löschen"),
            Modifier.padding(start = 10.dp, end = 6.dp), color = ink, maxLines = 2,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 8.sp,
                maxFontSize = MaterialTheme.typography.labelLarge.fontSize, stepSize = .5.sp))
    }
}

/** Red for deleting; a theme whose own accent is red gets another signal colour (orange or magenta). */
@Composable
internal fun deleteSignal(): Pair<Color, Color> {
    val scheme = MaterialTheme.colorScheme
    return androidx.compose.runtime.remember(scheme.error, scheme.primary) { deleteSignalFor(scheme.error, scheme.primary) }
}

internal fun deleteSignalFor(error: Color, accent: Color): Pair<Color, Color> {
    fun hct(c: Color) = dev.ambivalenton.openv.color.utilities.Hct.fromInt(c.toArgb())
    fun distance(a: Double, b: Double) = kotlin.math.min(kotlin.math.abs(a - b), 360 - kotlin.math.abs(a - b))
    val e = hct(error); val p = hct(accent)
    val base = if (p.chroma > 20 && distance(e.hue, p.hue) < 35) {
        val hue = if (distance(55.0, p.hue) >= distance(330.0, p.hue)) 55.0 else 330.0
        Color(dev.ambivalenton.openv.color.utilities.Hct.from(hue, maxOf(e.chroma, 60.0), e.tone.coerceIn(35.0, 60.0)).toInt())
    } else error
    return base to contrastContentColor(base)
}
