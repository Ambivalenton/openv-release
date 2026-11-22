// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components

import dev.ambivalenton.openv.data.model.reduceMotion

import android.content.Context
import android.view.accessibility.AccessibilityManager
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import java.text.BreakIterator
import kotlin.coroutines.coroutineContext

/** Frame-clock animation; complete paragraphs are measured only when text/constraints change.
 * Scrambled glyphs reuse the real glyph's position, so they never cause line reflow.
 * TalkBack sees one complete target sentence, never the decorative intermediate glyphs.
 */
@Composable
fun ScrambleText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    background: Color = MaterialTheme.colorScheme.background,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer(cacheSize = 32)
    val quiet=dev.ambivalenton.openv.ui.theme.LocalAppSettings.current.reduceMotion
    val progress = remember { Animatable(1f) }
    var settled by remember { mutableStateOf(text) }
    var previous by remember { mutableStateOf(text) }
    LaunchedEffect(text,quiet) {
        previous = settled
        val accessibility = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        if (quiet || previous == text || coroutineContext[MotionDurationScale]?.scaleFactor == 0f ||
            accessibility.isTouchExplorationEnabled) {
            progress.snapTo(1f)
        } else {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(700, easing = LinearEasing))
        }
        settled = text
    }
    val accessibleText = remember(text) { AnnotatedString(text) }
    BoxWithConstraints(modifier.fillMaxWidth().calmContentSize(tween(180)).clearAndSetSemantics {
        this.text = accessibleText
    }) {
        val width = with(density) { maxWidth.roundToPx() }.coerceAtLeast(1)
        val resolvedStyle = style.copy(color = color, textIndent = TextIndent.None)
        val old = remember(previous, width, resolvedStyle) {
            measurer.measure(previous, resolvedStyle, constraints = Constraints(minWidth = width, maxWidth = width))
        }
        val target = remember(text, width, resolvedStyle) {
            measurer.measure(text, resolvedStyle, constraints = Constraints(minWidth = width, maxWidth = width))
        }
        val oldRanges = remember(previous) { scrambleGraphemes(previous) }
        val newRanges = remember(text) { scrambleGraphemes(text) }
        val oldCells = remember(old, oldRanges) { scrambleCells(old, oldRanges) }
        val newCells = remember(target, newRanges) { scrambleCells(target, newRanges) }
        val glyphs = remember(resolvedStyle) {
            "·+*#%/=?<>".map { measurer.measure(it.toString(), resolvedStyle) }
        }
        val height = with(density) {
            (if (progress.isRunning) maxOf(old.size.height, target.size.height) else target.size.height).toDp()
        }
        Canvas(Modifier.fillMaxWidth().height(height).graphicsLayer {compositingStrategy=CompositingStrategy.Offscreen}) {
            val p = progress.value
            val disappearing = p < .5f
            val layout = if (disappearing) old else target
            val cells = if (disappearing) oldCells else newCells
            drawText(layout)
            if (p > 0f && p < 1f) {
                val tick = (p * 24).toInt()
                cells.forEachIndexed { index, bounds ->
                    if (bounds == null) return@forEachIndexed
                    val threshold = ((index * 73 + 19) % 101) / 101f
                    val scrambled = if (disappearing) p * 2 > threshold else (p - .5f) * 2 < threshold
                    if (scrambled) {
                        drawRect(Color.Transparent, bounds.topLeft, bounds.size,blendMode=BlendMode.Clear)
                        val glyph = glyphs[Math.floorMod(index * 31 + tick * 17, glyphs.size)]
                        drawText(glyph, topLeft = Offset(
                            bounds.center.x - glyph.size.width / 2f,
                            bounds.top + (bounds.height - glyph.size.height) / 2f,
                        ), alpha = if ((index + tick) % 7 == 0) .3f else .8f)
                    }
                }
            }
        }
    }
}

/** Preserve emoji/surrogate pairs and combining sequences when replacing decorative glyphs. */
internal fun scrambleGraphemes(value: String): List<IntRange> = buildList {
    val iterator = BreakIterator.getCharacterInstance().apply { setText(value) }
    var start = iterator.first()
    var end = iterator.next()
    while (end != BreakIterator.DONE) {
        add(start until end)
        start = end
        end = iterator.next()
    }
}

private fun scrambleCells(layout: TextLayoutResult, ranges: List<IntRange>): List<Rect?> = ranges.map { range ->
    if (layout.layoutInput.text.text.substring(range).isBlank()) null
    else {
        var bounds = layout.getBoundingBox(range.first)
        for (i in range.first + 1..range.last) {
            val cell = layout.getBoundingBox(i)
            bounds = Rect(minOf(bounds.left, cell.left), minOf(bounds.top, cell.top),
                maxOf(bounds.right, cell.right), maxOf(bounds.bottom, cell.bottom))
        }
        bounds
    }
}
