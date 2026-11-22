// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.zIndex
import dev.ambivalenton.openv.R
import dev.ambivalenton.openv.data.model.AppTheme
import dev.ambivalenton.openv.data.model.ThemeShape
import dev.ambivalenton.openv.data.model.TimeMode
import dev.ambivalenton.openv.data.model.reduceMotion
import dev.ambivalenton.openv.localization.tr
import dev.ambivalenton.openv.rendering.drawSystemHeader
import dev.ambivalenton.openv.rendering.systemHeaderForeground
import dev.ambivalenton.openv.rendering.isOperatingSystem
import dev.ambivalenton.openv.ui.theme.LocalAppSettings
import dev.ambivalenton.openv.ui.theme.LocalAppVisuals
import dev.ambivalenton.openv.ui.theme.appCorner
import dev.ambivalenton.openv.ui.theme.systemSurfacePalette
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Height of the "Abfahrt | Ankunft" pill: 4 dp around a capsule as tall as the former chips. */
private val ModePillHeight = 40.dp
private val ModePillInset = 4.dp
/** Gap between the date/time frame and the row below; the "Jetzt" area grows through it. */
private val ScheduleGap = 8.dp

/**
 * Search schedule: the date/time frame, the departure/arrival pill below it and, once a
 * time is chosen, a "Jetzt" area that springs out of the frame's lower right edge and back.
 */
@Composable fun ScheduleControls(timeMillis: Long?, onTime: (Long?) -> Unit, mode: TimeMode, onMode: (TimeMode) -> Unit,
    onNow: () -> Unit, modifier: Modifier = Modifier) {
    var frameHeight by remember { mutableIntStateOf(0) }
    var tabLeft by remember { mutableIntStateOf(0) }
    Column(modifier.fillMaxWidth()) {
        // The frame is drawn above the area that slides out from underneath it.
        Box(Modifier.zIndex(1f).onSizeChanged { frameHeight = it.height }) { SplitDateTimePicker(timeMillis, onTime) }
        Layout(content = {
            TimeModeSwitch(mode, onMode)
            NowExtension(timeMillis != null, onNow, { frameHeight }, { tabLeft })
        }, modifier = Modifier.fillMaxWidth().zIndex(0f)) { measurables, constraints ->
            val width = constraints.maxWidth
            val gap = ScheduleGap.roundToPx()
            val tabWidth = measurables[1].maxIntrinsicWidth(ModePillHeight.roundToPx()).plus(32.dp.roundToPx())
                .coerceIn((width * .34f).toInt(), (width * .46f).toInt())
            val pill = measurables[0].measure(Constraints(maxWidth = (width - tabWidth - 8.dp.roundToPx()).coerceAtLeast(0)))
            val height = gap + max(pill.height, ModePillHeight.roundToPx())
            val tab = measurables[1].measure(Constraints.fixed(tabWidth, height))
            tabLeft = width - tabWidth
            layout(width, height) {
                pill.place(0, gap + (height - gap - pill.height) / 2)
                tab.place(width - tabWidth, 0)
            }
        }
    }
}

/**
 * A miniature of the navigation pill: one shell, one travelling capsule that bulges the shell.
 * Both labels share one font size that always fits the slot ("Ankunft" was cut off at
 * larger system fonts or narrow screens), and the pill grows in height with the system font.
 */
@Composable fun TimeModeSwitch(mode: TimeMode, onMode: (TimeMode) -> Unit, modifier: Modifier = Modifier) {
    val options = listOf(TimeMode.DEPARTURE to tr("Abfahrt"), TimeMode.ARRIVAL to tr("Ankunft"))
    val base = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    // Both weights are measured: a theme font may draw its medium cut wider than its semi-bold one.
    val widest = remember(options, base, density) {
        options.maxOf { (_, label) -> listOf(FontWeight.SemiBold, FontWeight.Medium).maxOf { w ->
            measurer.measure(label, base.copy(fontWeight = w), maxLines = 1, softWrap = false).size.width } }
    }
    val lineHeight = remember(base, density) { measurer.measure("Ag", base, maxLines = 1).size.height }
    BoxWithConstraints(modifier) {
        val inset = with(density) { ModePillInset.toPx() }
        val fit = remember(constraints.maxWidth, widest, base, density) {
            var f = timeModeLabelFit(constraints.maxWidth, widest, inset, with(density) { TimeModeLabelPadding.toPx() },
                with(density) { TimeModeMinPadding.toPx() })
            // Letter spacing and hinting do not scale linearly: check the real width at the new size.
            for (round in 0 until 4) {
                if (f.scale >= 1f) break
                val style = scaledLabel(base, f.scale)
                val width = options.maxOf { (_, label) -> listOf(FontWeight.SemiBold, FontWeight.Medium).maxOf { w ->
                    measurer.measure(label, style.copy(fontWeight = w), maxLines = 1, softWrap = false).size.width } }
                val available = (constraints.maxWidth - 2 * inset) / 2f - 2 * f.padding - 2f
                if (width <= available) break
                f = f.copy(scale = (f.scale * available / width * .98f).coerceAtLeast(.4f))
            }
            f
        }
        val text = with(density) { (lineHeight * fit.scale).toDp() }
        val height = if (text + ModePillInset * 2 + 14.dp > ModePillHeight) text + ModePillInset * 2 + 14.dp else ModePillHeight
        TimeModeSwitchBody(options, mode, onMode, if (fit.scale < 1f) scaledLabel(base, fit.scale) else base, fit.padding, height)
    }
}

/** Line height shrinks with the font, otherwise the smaller label would still be clipped vertically. */
private fun scaledLabel(base: androidx.compose.ui.text.TextStyle, scale: Float) = base.copy(fontSize = base.fontSize * scale,
    lineHeight = if (base.lineHeight.isSpecified) base.lineHeight * scale else base.lineHeight)

/** Font scale and side padding so the wider label fits half of the pill (pure, unit-tested). */
internal data class TimeModeLabelFit(val scale: Float, val padding: Float)
internal fun timeModeLabelFit(maxWidth: Int, widest: Int, inset: Float, padding: Float, minPadding: Float): TimeModeLabelFit {
    if (maxWidth <= 0 || maxWidth == Constraints.Infinity || widest <= 0) return TimeModeLabelFit(1f, padding)
    val slot = (maxWidth - 2 * inset) / 2f
    if (widest + 2 * padding <= slot) return TimeModeLabelFit(1f, padding)
    // Two pixels of air: rounding to whole pixels must never clip the last letter.
    val room = (slot - 2 * minPadding - 2f).coerceAtLeast(1f)
    val scale = (room / widest).coerceIn(.45f, 1f)
    return TimeModeLabelFit(scale, ((slot - widest * scale) / 2f - 1f).coerceIn(0f, padding))
}
private val TimeModeLabelPadding = 16.dp
private val TimeModeMinPadding = 6.dp

@Composable private fun TimeModeSwitchBody(options: List<Pair<TimeMode, String>>, mode: TimeMode, onMode: (TimeMode) -> Unit,
    labelStyle: androidx.compose.ui.text.TextStyle, labelPadding: Float, pillHeight: androidx.compose.ui.unit.Dp) {
    val index = if (mode == TimeMode.ARRIVAL) 1 else 0
    val visuals = LocalAppVisuals.current
    val quiet = LocalAppSettings.current.reduceMotion
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val head by animateFloatAsState(index.toFloat(), if (quiet) snap() else spring(PILL_HEAD_DAMPING, 700f), label = "mode-capsule-head")
    val tail by animateFloatAsState(index.toFloat(), if (quiet) snap() else spring(PILL_TAIL_DAMPING, 300f), label = "mode-capsule-tail")
    val shell = remember { PillShellMotion() }
    val roundness = if (visuals.theme?.shapeStyle in setOf(ThemeShape.CUT, ThemeShape.BEVEL)) .15f else visuals.radiusScale.coerceIn(0f, 1f)
    val shellShape = appCorner(20.dp)
    val capsuleShape = appCorner(16.dp)
    val os = visuals.theme?.takeIf { it.isOperatingSystem }
    val xp = os == AppTheme.OS_WIN_XP
    val surfacePalette = MaterialTheme.colorScheme.systemSurfacePalette()
    val bg = if (xp) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh
    val tones = rememberCapsuleTones(options.size, bg)
    val capsule = MaterialTheme.colorScheme.primaryContainer
    val selectedInk = tones?.at((head + tail) / 2)?.ink ?: MaterialTheme.colorScheme.onPrimaryContainer
    val idleInk = if (os != null) Color(systemHeaderForeground(os, surfacePalette)) else MaterialTheme.colorScheme.onSurfaceVariant
    val padding = with(density) { labelPadding.toDp() }
    fun choose(next: Int) {
        if (next == index) return
        if (!quiet) {
            view.performHapticFeedback(if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.SEGMENT_TICK else HapticFeedbackConstants.CLOCK_TICK)
            scope.launch { shell.inflate(); shell.settle() }
            scope.launch { kotlinx.coroutines.delay(140); shell.land() }
        }
        onMode(options[next].first)
    }
    Layout(content = {
        options.forEachIndexed { i, (_, label) ->
            Box(Modifier.testTag("time-mode-${options[i].first.name}")
                .selectable(i == index, interactionSource = remember { MutableInteractionSource() }, indication = null,
                    role = Role.Tab, onClick = { choose(i) }), contentAlignment = Alignment.Center) {
                // One size for both labels; never an ellipsis. The shared size is computed above; auto-size
                // only trims the last fraction a theme font may need beyond that estimate.
                androidx.compose.foundation.text.BasicText(label, Modifier.padding(horizontal = padding),
                    style = labelStyle.copy(fontWeight = if (i == index) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (i == index) selectedInk else idleInk),
                    maxLines = 1, softWrap = false, overflow = TextOverflow.Clip,
                    autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 6.sp,
                        maxFontSize = labelStyle.fontSize, stepSize = .25.sp))
            }
        }
    }, modifier = Modifier.height(pillHeight).selectableGroup().testTag("time-mode-pill").drawBehind {
        val inset = ModePillInset.toPx()
        val inner = Rect(inset, inset, size.width - inset, size.height - inset)
        val slot = inner.width / 2
        val stretch = (abs(head - tail) * .9f).coerceIn(0f, .55f)
        val squash = if (quiet) 0f else shell.landing.value
        val cw = (slot * (1 + stretch + squash * .6f)).coerceIn(slot * .7f, inner.width)
        val ch = (inner.height * (1 - stretch * .14f + squash * .4f)).coerceAtMost(inner.height)
        val cx = (inner.left + slot * ((head + tail) / 2 + .5f)).coerceIn(inner.left + cw / 2, inner.right - cw / 2)
        val capsuleRect = Rect(cx - cw / 2, size.height / 2 - ch / 2, cx + cw / 2, size.height / 2 + ch / 2)
        val amount = if (quiet) 0f else shell.amount.value.coerceIn(-.42f, 1.15f) * 3.5.dp.toPx()
        val outline = themedPillPath(shellShape, Rect(0f, 0f, size.width, size.height), density, direction,
            capsuleRect.center.x, maxOf(slot, cw * 1.1f), amount, roundness = roundness)
        // Soft contour shadow like the navigation pill, scaled down.
        translate(top = 1.5.dp.toPx()) {
            for (layer in 6 downTo 1) drawPath(outline, Color.Black.copy(alpha = if (quiet) .004f else .008f), style = Stroke((layer * 1.4f).dp.toPx()))
        }
        drawPath(outline, bg)
        clipPath(outline) {
            if (os != null) drawIntoCanvas { drawSystemHeader(it.nativeCanvas, os, size.width, size.height, surfacePalette, density.density) }
            val drop = themedPillPath(capsuleShape, capsuleRect, density, direction, strain = if (quiet) 0f else head - tail, roundness = roundness)
            drawPath(drop, tones?.at((head + tail) / 2)?.fill ?: capsule)
            if (capsule == bg && tones == null) drawPath(drop, selectedInk.copy(alpha = .6f), style = Stroke(1.dp.toPx()))
        }
    }) { measurables, constraints ->
        val inset = ModePillInset.roundToPx()
        val height = pillHeight.roundToPx()
        val natural = measurables.maxOf { it.maxIntrinsicWidth(height) }
        val slot = min(natural, ((constraints.maxWidth - 2 * inset) / 2).coerceAtLeast(0))
        val placeables = measurables.map { it.measure(Constraints.fixed(slot, height - 2 * inset)) }
        layout(slot * 2 + 2 * inset, height) { placeables.forEachIndexed { i, p -> p.place(inset + i * slot, inset) } }
    }
}

/**
 * The "Jetzt" area. It is not a second frame: it sits underneath the date/time frame and springs
 * out of its lower right edge (Material 3 Expressive spatial springs), joined by a concave fillet.
 * The label rides along with a softer spring, so it lags, stretches and settles elastically.
 */
@Composable private fun NowExtension(open: Boolean, onNow: () -> Unit, frameHeight: () -> Int, tabLeft: () -> Int) {
    val motion = MaterialTheme.motionScheme
    val quiet = LocalAppSettings.current.reduceMotion
    val body = remember { Animatable(if (open) 1f else 0f) }
    val label = remember { Animatable(if (open) 1f else 0f) }
    LaunchedEffect(open, quiet) {
        val target = if (open) 1f else 0f
        if (quiet) { body.snapTo(target); label.snapTo(target) }
        else {
            launch { body.animateTo(target, if (open) motion.fastSpatialSpec<Float>() else motion.defaultSpatialSpec<Float>()) }
            launch { label.animateTo(target, if (open) motion.defaultSpatialSpec<Float>() else motion.fastSpatialSpec<Float>()) }
        }
    }
    val visuals = LocalAppVisuals.current
    val scheme = MaterialTheme.colorScheme
    val frame = scheme.surfaceContainerHigh
    // Slightly set apart from the frame: the same surface pulled towards the accent.
    val fill = lerp(frame.compositeOver(scheme.background), scheme.primary, .16f).copy(alpha = frame.alpha.coerceAtLeast(.92f))
    val ink = scheme.primary
    val style = visuals.theme?.shapeStyle ?: ThemeShape.ROUNDED
    val radius = 20.dp * visuals.radiusScale
    val shown = open || body.value > .002f
    val frameShape = appCorner(24.dp)
    val layoutDirection = LocalLayoutDirection.current
    Box(Modifier.fillMaxSize().drawWithContent {
        if (!shown) return@drawWithContent
        // Everything stays outside the frame's own outline, so translucent (glass) frames never
        // show the area or its label behind them while they slide in and out.
        val frameW = tabLeft() + size.width; val frameH = frameHeight().toFloat()
        val frameOutline = Path().apply {
            if (frameH > 0f) when (val o = frameShape.createOutline(Size(frameW, frameH), layoutDirection, this@drawWithContent)) {
                is Outline.Generic -> addPath(o.path); is Outline.Rounded -> addRoundRect(o.roundRect); is Outline.Rectangle -> addRect(o.rect)
            }
            translate(Offset(-tabLeft().toFloat(), -frameH))
        }
        clipPath(frameOutline, ClipOp.Difference) {
            val p = body.value.coerceAtLeast(0f)
            val overlap = 26.dp.toPx()
            val bottom = size.height * p
            if (bottom > .5f) {
                val left = 0f; val right = size.width
                val r = min(radius.toPx(), bottom * .9f).coerceAtLeast(0f)
                val fillet = min(10.dp.toPx() * visuals.radiusScale.coerceIn(0f, 1f) + 3.dp.toPx(), bottom) * p.coerceAtMost(1f)
                val straight = style in setOf(ThemeShape.CUT, ThemeShape.BEVEL, ThemeShape.PIXEL, ThemeShape.SHARP)
                val (bottomStart, bottomEnd) = when (style) {
                    ThemeShape.ROUNDED, ThemeShape.SQUIRCLE -> r to r
                    ThemeShape.ASYMMETRIC -> r * .25f to r
                    ThemeShape.CUT -> r * .55f to r * .55f
                    ThemeShape.BEVEL -> r * .7f to r * .2f
                    // Corner forms of the design languages.
                    ThemeShape.PEBBLE -> r * .7f to r * 1.2f
                    ThemeShape.LEAF -> r * .22f to r * 1.5f
                    ThemeShape.PIXEL -> r * .45f to r * .45f
                    ThemeShape.SHARP -> 0f to 0f
                }
                val bs = bottomStart.coerceAtMost(bottom - fillet).coerceAtLeast(0f)
                val be = bottomEnd.coerceAtMost(bottom).coerceAtLeast(0f)
                val path = Path().apply {
                    moveTo(right, -overlap)
                    lineTo(left - fillet, -overlap)
                    lineTo(left - fillet, 0f)
                    // Concave joint where the area leaves the frame's lower edge.
                    if (straight) lineTo(left, fillet) else quadraticTo(left, 0f, left, fillet)
                    lineTo(left, bottom - bs)
                    if (straight) lineTo(left + bs, bottom) else quadraticTo(left, bottom, left + bs, bottom)
                    lineTo(right - be, bottom)
                    if (straight) lineTo(right, bottom - be) else quadraticTo(right, bottom, right, bottom - be)
                    close()
                }
                drawPath(path, fill)
            }
            this@drawWithContent.drawContent()
        }
    }.testTag("search-now-area")) {
        if (shown) {
            val t = label.value
            val emerging = (1 - t).coerceIn(0f, 1f)
            val over = (t - 1).coerceAtLeast(0f)
            // Icon and text sit close together as one block in the middle of the area, both ways.
            Row(Modifier.align(Alignment.Center).fillMaxHeight().fillMaxWidth().padding(horizontal = 8.dp)
                .graphicsLayer {
                    translationY = -emerging * 30.dp.toPx()
                    scaleY = 1 + emerging * .38f - over * .55f
                    scaleX = 1 - emerging * .22f + over * .35f
                    alpha = (t * 1.6f).coerceIn(0f, 1f)
                    transformOrigin = TransformOrigin(.5f, 0f)
                }
                .clickable(enabled = open, role = Role.Button, onClickLabel = tr("Jetzt abfahren"), onClick = onNow)
                .semantics(mergeDescendants = true) { contentDescription = tr("Jetzt abfahren") }
                .testTag("search-now"),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                AnalogClockIcon(null, Modifier.size(18.dp), tint = ink)
                Spacer(Modifier.width(4.dp))
                // M05: the reset says what it does: departure from now (also from an arrival search).
                FittedText(tr("Jetzt abfahren"), Modifier.weight(1f, fill = false),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    style = MaterialTheme.typography.labelLarge.copy(color = ink, fontWeight = FontWeight.SemiBold))
            }
        }
    }
}
