// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components

import dev.ambivalenton.openv.ui.components.AppDialog as Dialog

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import dev.ambivalenton.openv.R
import dev.ambivalenton.openv.color.utilities.Hct
import dev.ambivalenton.openv.localization.tr
import dev.ambivalenton.openv.ui.theme.LocalAppSettings
import dev.ambivalenton.openv.data.model.reduceMotion
import dev.ambivalenton.openv.ui.theme.colorHex
import dev.ambivalenton.openv.ui.theme.contrastContentColor
import dev.ambivalenton.openv.ui.theme.parseColorHex
import kotlin.math.roundToInt

/**
 * Build 108 · "Eigene Farbe" as a colour studio anyone can use: one large field for strength and
 * brightness and a rainbow strip for the hue – both follow the finger at once – a before/after
 * preview with a readability sample, the hex code for those who have one, and colours that go with
 * the current one (its own lighter and darker steps, neighbours and opposite). A tap on any of them
 * takes it over; "Übernehmen" confirms, "Abbrechen" leaves everything as it was.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ColorStudioDialog(
    seed: Long,
    allowAlpha: Boolean,
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit,
    previewContent: (@Composable (Long?) -> Unit)? = null,
    themeAccentColor: Long? = null,
    recentColors: List<Long> = emptyList(),
) {
    val start = remember(seed) { if (allowAlpha) seed else seed or 0xff000000L }
    val initial = remember(seed) { FloatArray(3).also { android.graphics.Color.colorToHSV(start.toInt(), it) } }
    var hue by rememberSaveable(seed) { mutableFloatStateOf(initial[0]) }
    var saturation by rememberSaveable(seed) { mutableFloatStateOf(initial[1]) }
    var brightness by rememberSaveable(seed) { mutableFloatStateOf(initial[2]) }
    var alpha by rememberSaveable(seed) { mutableFloatStateOf((start ushr 24 and 255) / 255f) }
    val working = studioColor(hue, saturation, brightness, if (allowAlpha) alpha else 1f)
    /** Takes a colour over; its opacity only when it brings its own (the "Vorher" field, an 8-digit code). */
    fun choose(color: Long, withAlpha: Boolean = false) {
        val hsv = FloatArray(3).also { android.graphics.Color.colorToHSV(color.toInt(), it) }
        // A grey keeps the hue the strip shows, so the next drag does not jump to red.
        if (hsv[1] > .004f && hsv[2] > .004f) hue = hsv[0]
        saturation = hsv[1]; brightness = hsv[2]
        if (allowAlpha && withAlpha) alpha = (color ushr 24 and 255) / 255f
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        ReleaseDialogWindowOnDispose()
        Surface(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 24.dp).widthIn(max = 520.dp).testTag("advanced-color-dialog"),
            shape = RoundedCornerShape(30.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, tonalElevation = 2.dp) {
            Column(Modifier.imePadding().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    FittedText(tr("Eigene Farbe"), Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
                    IconButton(onDismiss) { AppIcon(R.drawable.ic_close, tr("Farbauswahl schließen")) }
                }
                previewContent?.invoke(working)
                BeforeAfter(start, working, allowAlpha) { color, withAlpha -> choose(color, withAlpha) }
                SaturationBrightnessField(hue, saturation, brightness) { s, v -> saturation = s; brightness = v }
                GradientStrip(tr("Farbton"), hue / 360f, { hue = (it * 360f).coerceIn(0f, 359.99f) },
                    Brush.horizontalGradient((0..6).map { Color.hsv((it * 60f).coerceAtMost(359.99f), 1f, 1f) }),
                    Color.hsv(hue, 1f, 1f), "hue-strip", describe = { tr("%1\$s Grad", (it * 360).roundToInt()) })
                if (allowAlpha) GradientStrip(tr("Deckkraft"), alpha, { alpha = it },
                    Brush.horizontalGradient(listOf(Color(working).copy(alpha = 0f), Color(working).copy(alpha = 1f))),
                    Color(working), "alpha-strip", checker = true, describe = { tr("%1\$s Prozent", (it * 100).roundToInt()) })
                Suggestions(working, recentColors, themeAccentColor) { choose(it) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onDismiss, Modifier.heightIn(min = 48.dp)) { Text(tr("Abbrechen")) }
                    val fill = Color(working).copy(alpha = 1f)
                    Button({ onConfirm(working) }, Modifier.heightIn(min = 52.dp).pressScale(.95f).testTag("advanced-color-apply")
                        .semantics { stateDescription = colorHex(working, allowAlpha) },
                        colors = ButtonDefaults.buttonColors(containerColor = fill, contentColor = contrastContentColor(fill)),
                        contentPadding = PaddingValues(horizontal = 22.dp)) {
                        AppIcon(R.drawable.ic_check, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(tr("Übernehmen"), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

/** HSV (and alpha) to an ARGB colour as the app stores it. */
internal fun studioColor(hue: Float, saturation: Float, brightness: Float, alpha: Float = 1f): Long =
    android.graphics.Color.HSVToColor((alpha.coerceIn(0f, 1f) * 255).roundToInt(),
        floatArrayOf(hue.coerceIn(0f, 359.99f), saturation.coerceIn(0f, 1f), brightness.coerceIn(0f, 1f))).toLong() and 0xffffffffL

/** Lighter and darker steps of the colour's own hue and chroma (HCT tones, light to dark). */
internal fun colorToneSteps(color: Long, tones: List<Double> = listOf(92.0, 80.0, 66.0, 52.0, 40.0, 28.0)): List<Long> {
    val hct = Hct.fromInt(color.toInt() or 0xff000000.toInt())
    return tones.map { Hct.from(hct.hue, hct.chroma, it).toInt().toLong() and 0xffffffffL }
}

/** Colours that go with [color] at its tone and chroma: two neighbours (±30°), the opposite (180°) and a triad partner (+120°). */
internal fun colorHarmonies(color: Long): List<Long> {
    val hct = Hct.fromInt(color.toInt() or 0xff000000.toInt())
    val chroma = maxOf(hct.chroma, 24.0)
    return listOf(-30.0, 30.0, 180.0, 120.0).map { shift ->
        Hct.from((hct.hue + shift + 360.0) % 360.0, chroma, hct.tone.coerceIn(25.0, 85.0)).toInt().toLong() and 0xffffffffL
    }
}

/** Before and after side by side; "Aa" shows how text reads on the new colour. A tap on "Vorher" goes back. */
@Composable private fun BeforeAfter(before: Long, after: Long, allowAlpha: Boolean, onPick: (Long, Boolean) -> Unit) {
    var hex by remember { mutableStateOf(colorHex(after, allowAlpha).removePrefix("#")) }
    var typing by remember { mutableStateOf(false) }
    LaunchedEffect(after, typing) { if (!typing) hex = colorHex(after, allowAlpha).removePrefix("#") }
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.weight(1f).height(64.dp).clip(RoundedCornerShape(20.dp)).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
            .testTag("color-before-after"), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            val old = Color(before)
            Box(Modifier.weight(.36f).fillMaxHeight().checkerboard().background(old)
                .clickable(onClickLabel = tr("Zurücksetzen"), role = Role.Button) { onPick(before, true) }
                .semantics(mergeDescendants = true) { contentDescription = tr("Vorher %1\$s", colorHex(before, allowAlpha)) }
                .testTag("color-before"), contentAlignment = Alignment.BottomStart) {
                Text(tr("Vorher"), Modifier.padding(8.dp), style = MaterialTheme.typography.labelSmall, color = contrastContentColor(old.copy(alpha = 1f)))
            }
            val new = Color(after)
            Box(Modifier.weight(.64f).fillMaxHeight().checkerboard().background(new)
                .semantics(mergeDescendants = true) { contentDescription = tr("Neu %1\$s", colorHex(after, allowAlpha)) },
                contentAlignment = Alignment.Center) {
                Text("Aa", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = contrastContentColor(new.copy(alpha = 1f)))
            }
        }
        OutlinedTextField(hex, { text ->
            val clean = text.uppercase().filter { it in "0123456789ABCDEF" }.take(if (allowAlpha) 8 else 6)
            hex = clean
            parseColorHex(clean, allowAlpha)?.let { onPick(it, clean.length == 8) }
        }, Modifier.width(128.dp).onFocusChanged { typing = it.isFocused }.testTag("color-hex"),
            label = { Text(tr("Hex-Code")) }, prefix = { Text("#") }, singleLine = true,
            isError = parseColorHex(hex, allowAlpha) == null,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { typing = false }))
    }
}

/** Strength (left → right) and brightness (top → bottom) of the current hue, followed by the finger at once. */
@Composable private fun SaturationBrightnessField(hue: Float, saturation: Float, brightness: Float, onChange: (Float, Float) -> Unit) {
    val change by rememberUpdatedState(onChange)
    val haptics = LocalHapticFeedback.current
    val quiet = LocalAppSettings.current.reduceMotion
    var pressed by remember { mutableStateOf(false) }
    val thumb by animateFloatAsState(if (pressed) 1.28f else 1f, if (quiet) snap() else spring(dampingRatio = .5f, stiffness = 600f), label = "field-thumb")
    val pure = Color.hsv(hue.coerceIn(0f, 359.99f), 1f, 1f)
    Box(Modifier.fillMaxWidth().height(196.dp).clip(RoundedCornerShape(24.dp)).testTag("color-field")
        .semantics {
            contentDescription = tr("Farbfläche: links blass, rechts kräftig, oben hell, unten dunkel")
            stateDescription = tr("Farbstärke %1\$s Prozent, Helligkeit %2\$s Prozent", (saturation * 100).roundToInt(), (brightness * 100).roundToInt())
            customActions = listOf(
                CustomAccessibilityAction(tr("Kräftiger")) { change((saturation + .1f).coerceAtMost(1f), brightness); true },
                CustomAccessibilityAction(tr("Blasser")) { change((saturation - .1f).coerceAtLeast(0f), brightness); true },
                CustomAccessibilityAction(tr("Heller")) { change(saturation, (brightness + .1f).coerceAtMost(1f)); true },
                CustomAccessibilityAction(tr("Dunkler")) { change(saturation, (brightness - .1f).coerceAtLeast(0f)); true })
        }
        .pointerInput(Unit) {
            fun at(p: Offset) = change((p.x / size.width).coerceIn(0f, 1f), (1f - p.y / size.height).coerceIn(0f, 1f))
            awaitEachGesture {
                val down = awaitFirstDown()
                down.consume(); pressed = true
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                at(down.position)
                drag(down.id) { it.consume(); at(it.position) }
                pressed = false
            }
        }
        .drawBehind {
            drawRect(pure)
            drawRect(Brush.horizontalGradient(listOf(Color.White, Color.White.copy(alpha = 0f))))
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
            drawStudioThumb(Offset(saturation * size.width, (1f - brightness) * size.height), Color.hsv(hue.coerceIn(0f, 359.99f), saturation, brightness), thumb)
        })
}

/** A rounded strip with a thumb: the rainbow for the hue, the colour's own fade for the opacity. */
@Composable private fun GradientStrip(label: String, position: Float, onChange: (Float) -> Unit, brush: Brush, thumbColor: Color, tag: String,
    checker: Boolean = false, describe: (Float) -> String) {
    val change by rememberUpdatedState(onChange)
    val quiet = LocalAppSettings.current.reduceMotion
    var pressed by remember { mutableStateOf(false) }
    val thumb by animateFloatAsState(if (pressed) 1.25f else 1f, if (quiet) snap() else spring(dampingRatio = .5f, stiffness = 600f), label = "$tag-thumb")
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
            Text(describe(position), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(Modifier.fillMaxWidth().height(32.dp).testTag(tag)
            .semantics {
                contentDescription = label
                progressBarRangeInfo = ProgressBarRangeInfo(position, 0f..1f)
                stateDescription = describe(position)
                setProgress { change(it.coerceIn(0f, 1f)); true }
            }
            .pointerInput(Unit) {
                val pad = 16.dp.toPx()
                fun at(p: Offset) = change(((p.x - pad) / (size.width - 2 * pad)).coerceIn(0f, 1f))
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume(); pressed = true; at(down.position)
                    drag(down.id) { it.consume(); at(it.position) }
                    pressed = false
                }
            }
            .drawBehind {
                val pad = 16.dp.toPx()
                val track = Size(size.width, 22.dp.toPx())
                val top = (size.height - track.height) / 2
                val radius = CornerRadius(track.height / 2)
                if (checker) drawCheckers(Offset(0f, top), track, radius)
                drawRoundRect(brush, Offset(0f, top), track, radius)
                drawRoundRect(Color.Black.copy(alpha = .12f), Offset(0f, top), track, radius, style = Stroke(1.dp.toPx()))
                drawStudioThumb(Offset(pad + position.coerceIn(0f, 1f) * (size.width - 2 * pad), size.height / 2), thumbColor, thumb)
            })
    }
}

/** A white ring with a soft dark edge around the chosen colour; it grows a little while held. */
private fun DrawScope.drawStudioThumb(at: Offset, color: Color, scale: Float) {
    val r = 13.dp.toPx() * scale
    drawCircle(Color.Black.copy(alpha = .22f), r + 1.5.dp.toPx(), at + Offset(0f, 1.dp.toPx()))
    drawCircle(Color.White, r, at)
    drawCircle(color.copy(alpha = 1f), r - 3.5.dp.toPx(), at)
    drawCircle(Color.Black.copy(alpha = .18f), r - 3.5.dp.toPx(), at, style = Stroke(1.dp.toPx()))
}

private fun DrawScope.drawCheckers(topLeft: Offset, size: Size, radius: CornerRadius) {
    val cell = 6.dp.toPx()
    val outline = Path().apply { addRoundRect(RoundRect(Rect(topLeft, size), radius)) }
    clipPath(outline) { checkers(topLeft, size, cell) }
}

private fun DrawScope.checkers(topLeft: Offset, size: Size, cell: Float) {
    drawRect(Color.White, topLeft, size)
    var y = 0
    while (y * cell < size.height) {
        var x = 0
        while (x * cell < size.width) {
            if ((x + y) % 2 == 0) drawRect(Color(0xFFD9D9DE), topLeft + Offset(x * cell, y * cell),
                Size(minOf(cell, size.width - x * cell), minOf(cell, size.height - y * cell)))
            x++
        }
        y++
    }
}

/** Transparent colours show the checker pattern behind them. */
private fun Modifier.checkerboard(): Modifier = drawBehind { drawCheckers(Offset.Zero, size, CornerRadius.Zero) }

/** Colours to tap: the current colour's steps, its partners, the app design's accent and the recently chosen ones. */
@OptIn(ExperimentalLayoutApi::class)
@Composable private fun Suggestions(color: Long, recent: List<Long>, accent: Long?, onChoose: (Long) -> Unit) {
    val steps = remember(color) { colorToneSteps(color) }
    val partners = remember(color) { colorHarmonies(color) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("color-harmonies")) {
        SwatchRow(tr("Heller und dunkler"), steps, color, onChoose)
        SwatchRow(tr("Passt dazu"), partners + listOfNotNull(accent), color, onChoose,
            labels = listOf(tr("Nachbar links"), tr("Nachbar rechts"), tr("Gegenfarbe"), tr("Dreiklang")) + listOfNotNull(accent?.let { tr("Akzent des App-Designs") }),
            tag = "picker-theme-accent".takeIf { accent != null })
        if (recent.isNotEmpty()) SwatchRow(tr("Zuletzt gewählt"), recent.distinct().take(6), color, onChoose, tag = "picker-recent-colors")
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun SwatchRow(title: String, colors: List<Long>, current: Long, onChoose: (Long) -> Unit, labels: List<String>? = null, tag: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(Modifier.fillMaxWidth().then(if (tag != null) Modifier.testTag(tag) else Modifier), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            colors.forEachIndexed { i, c ->
                ColorSwatch(c, labels?.getOrNull(i)?.let { "$it, ${colorHex(c)}" } ?: colorHex(c),
                    (c or 0xff000000L) == (current or 0xff000000L)) { onChoose(c) }
            }
        }
    }
}

/** A friendly entry to the colour studio: its rainbow mark, what it offers, and the colour chosen there. */
@Composable internal fun CustomColorEntry(color: Long?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(onClick, modifier.fillMaxWidth().heightIn(min = 64.dp).pressScale(.97f), shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Canvas(Modifier.size(40.dp)) {
                drawCircle(Brush.sweepGradient((0..6).map { Color.hsv((it * 60f).coerceAtMost(359.99f), .85f, 1f) }))
                drawCircle(Brush.radialGradient(listOf(Color.White, Color.White.copy(alpha = 0f))), size.minDimension / 2)
                color?.let { drawCircle(Color(it).copy(alpha = 1f), size.minDimension * .2f); drawCircle(Color.White, size.minDimension * .2f, style = Stroke(2.dp.toPx())) }
            }
            Column(Modifier.weight(1f)) {
                Text(tr("Eigene Farbe wählen"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(color?.let { tr("Gemischt: %1\$s", colorHex(it)) } ?: tr("Frei mischen oder Hex-Code eingeben"),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            AppIcon(R.drawable.ic_chevron_right, null, Modifier.size(22.dp))
        }
    }
}
