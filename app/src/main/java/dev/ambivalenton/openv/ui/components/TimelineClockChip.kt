// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.data.model.ThemeShape
import dev.ambivalenton.openv.data.model.reduceMotion
import dev.ambivalenton.openv.ui.theme.AppVisuals
import dev.ambivalenton.openv.ui.theme.LocalAppSettings
import dev.ambivalenton.openv.ui.theme.LocalAppVisuals

/**
 * Chip shape: the side touching the bar is almost straight, so the chip reads as part of the bar;
 * the free side is fully rounded. Walking times are free-floating pills. The theme's corner scale
 * and cut-corner styles apply, square themes get square chips.
 * [tucked] (build 74): the contact side lies under a filled bar and is not seen, so all corners are
 * the free kind; where a strongly rounded bar curves away, the chip's sides run on underneath it.
 */
internal fun timelineClockChipShape(visuals: AppVisuals, placement: TimelineClockPlacement, tucked: Boolean = false): CornerBasedShape {
    val scale = visuals.radiusScale
    val outer = (10f * scale).dp
    val contact = if (tucked) outer else (3f * scale).dp
    val (top, bottom) = when (placement) {
        TimelineClockPlacement.ABOVE_BAR -> outer to contact
        TimelineClockPlacement.BELOW_BAR -> contact to outer
        else -> outer to outer
    }
    return if (visuals.theme?.shapeStyle == ThemeShape.CUT) CutCornerShape(top * .55f, top * .55f, bottom * .55f, bottom * .55f)
    else RoundedCornerShape(top, top, bottom, bottom)
}

/**
 * One time of the comparison chart as a chip (build 73). Vehicle times sit flush on their bar edge;
 * walking times are lighter so the departure stays the primary information. An actually changed time
 * rolls in with a short spring (new delay or caught-up delay); "Bewegung reduzieren" swaps it without
 * motion.
 *
 * No drop shadow of its own. The chip's surface continues [tuck] under its filled bar, which
 * is drawn on top and casts the shadow, so the contour ends under the bar instead of meeting a rounded
 * bar end with square corners. The layout box is unchanged (it still ends exactly at the bar edge).
 * The contour speaks the app theme's border language: the bar's line colour at the bars' 60 % (as the
 * App-Theme bars draw it), the theme's ink outline in graphic designs, the theme's border width.
 *
 * [modifier] gives the label box of [timelineClockHeight]; the chip aligns to the box edge facing
 * its bar (departure: bottom, arrival: top), walking chips to the edge facing the walk.
 */
/**
 * The ride bars of the chip's column in the coordinates of its label box. Wherever a bar
 * lies, the chip is not drawn, so its tucked surface and contour never shine through a translucent
 * bar, whatever outline the bar has.
 */
internal class ChipBarCut(val shape: androidx.compose.ui.graphics.Shape, val bars: List<Rect>)

@Composable
internal fun TimelineClockChip(
    text: String,
    placement: TimelineClockPlacement,
    accent: Color?,
    modifier: Modifier,
    maxWidth: Dp,
    tuck: Dp = 0.dp,
    /** Visible chip bounds and its digits, both in the coordinates of [modifier]'s box,
     * so footprints crossing the chip can take the accent colour and keep the digits readable. */
    onPlaced: ((Rect, FootprintClockText?) -> Unit)? = null,
    cut: ChipBarCut? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val visuals = LocalAppVisuals.current
    val quiet = LocalAppSettings.current.reduceMotion
    val opaque = scheme.background.copy(alpha = 1f)
    val raised = if (scheme.background.luminance() > .5f) scheme.surfaceContainerLowest else scheme.surfaceContainerHighest
    val container = raised.compositeOver(opaque)
    val graphic = visuals.theme.chartGraphicBorder()
    val contour = when {
        graphic -> scheme.outline
        accent != null -> accent.copy(alpha = .6f)
        else -> scheme.outlineVariant
    }.compositeOver(container)
    val borderWidth = visuals.theme.chartBorderWidth().coerceAtMost(2.dp)
    val ink = if (accent == null) scheme.onSurfaceVariant else scheme.onSurface
    val tucked = tuck > 0.dp && (placement == TimelineClockPlacement.ABOVE_BAR || placement == TimelineClockPlacement.BELOW_BAR)
    val shape = remember(visuals, placement, tucked) { timelineClockChipShape(visuals, placement, tucked) }
    val density = LocalDensity.current
    val alignment = when (placement) {
        TimelineClockPlacement.ABOVE_BAR, TimelineClockPlacement.WALK_START -> Alignment.BottomCenter
        else -> Alignment.TopCenter
    }
    // Layout anchors: outer box, chip surface, digits (LayoutCoordinates stay valid while attached).
    val anchors = remember { arrayOfNulls<LayoutCoordinates>(3) }
    val boxSize = remember { arrayOf(androidx.compose.ui.unit.IntSize.Zero) }
    val digits = remember { arrayOfNulls<TextLayoutResult>(1) }
    val report by rememberUpdatedState(onPlaced)
    fun placed() {
        val callback = report ?: return
        val outer = anchors[0]?.takeIf { it.isAttached } ?: return
        val chip = anchors[1]?.takeIf { it.isAttached } ?: return
        val text = anchors[2]?.takeIf { it.isAttached }
        val layout = digits[0]
        val box = outer.localBoundingBoxOf(chip, clipBounds = false)
        val under = with(density) { if(tucked) tuck.toPx() else 0f }
        val stroke = with(density) { borderWidth.toPx() }
        val frame = FootprintClockFrame(shape,
            Size((chip.size.width-stroke).coerceAtLeast(0f),(chip.size.height+under-stroke).coerceAtLeast(0f)),
            outer.localPositionOf(chip,Offset.Zero)+Offset(stroke/2,stroke/2-if(placement==TimelineClockPlacement.BELOW_BAR)under else 0f),
            stroke,contour,box)
        callback(box,
            if (text != null && layout != null) FootprintClockText(layout, outer.localPositionOf(text, Offset.Zero), ink,frame) else null)
    }
    Box(modifier.then(if (onPlaced != null) Modifier.onGloballyPositioned { anchors[0] = it; placed() } else Modifier)
        .then(if (cut != null) Modifier.onSizeChanged { boxSize[0] = it } else Modifier),
        contentAlignment = alignment) {
        Box(
            Modifier.widthIn(max = maxWidth)
                .then(if (onPlaced != null) Modifier.onGloballyPositioned { anchors[1] = it; placed() } else Modifier)
                .drawBehind {
                    val under = if (tucked) tuck.toPx() else 0f
                    val stroke = borderWidth.toPx()
                    val full = Size(size.width, size.height + under)
                    fun chip() = translate(0f, if (placement == TimelineClockPlacement.BELOW_BAR) -under else 0f) {
                        drawOutline(shape.createOutline(full, layoutDirection, this), container)
                        translate(stroke / 2, stroke / 2) {
                            drawOutline(shape.createOutline(Size(full.width - stroke, full.height - stroke), layoutDirection, this),
                                contour, style = Stroke(stroke))
                        }
                    }
                    val bars = cut?.let { c ->
                        // Where this surface sits in its label box, then every bar relative to it.
                        val at = alignment.align(androidx.compose.ui.unit.IntSize(size.width.toInt(), size.height.toInt()), boxSize[0], layoutDirection)
                        androidx.compose.ui.graphics.Path().apply {
                            c.bars.forEach { bar ->
                                addPath(androidx.compose.ui.graphics.Path().apply {
                                    addOutline(c.shape.createOutline(bar.size, layoutDirection, this@drawBehind))
                                }, Offset(bar.left - at.x, bar.top - at.y))
                            }
                        }
                    }
                    if (bars == null || bars.isEmpty) chip()
                    else clipPath(bars, androidx.compose.ui.graphics.ClipOp.Difference) { chip() }
                }
                .clipToBounds()
                .padding(horizontal = 6.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center,
        ) {
            @Composable
            fun Clock(value: String) = Text(
                value,
                if (onPlaced != null) Modifier.onGloballyPositioned { anchors[2] = it; placed() } else Modifier,
                onTextLayout = { digits[0] = it },
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (accent == null) FontWeight.SemiBold else FontWeight.Bold,
                color = ink,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
            )
            if (quiet) Clock(text)
            else AnimatedContent(
                text,
                transitionSpec = {
                    // Later time: the new value comes from below; earlier: from above.
                    val later = targetState > initialState
                    (slideInVertically(spring(dampingRatio = .62f, stiffness = 520f)) { if (later) it else -it } + fadeIn())
                        .togetherWith(slideOutVertically(spring(dampingRatio = .9f, stiffness = 700f)) { if (later) -it else it } + fadeOut())
                },
                contentAlignment = Alignment.Center,
                label = "timeline-clock",
            ) { Clock(it) }
        }
    }
}

