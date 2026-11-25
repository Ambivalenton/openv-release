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
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import dev.ambivalenton.openv.R
import dev.ambivalenton.openv.color.utilities.Hct
import dev.ambivalenton.openv.data.model.reduceMotion
import dev.ambivalenton.openv.localization.tr
import dev.ambivalenton.openv.ui.theme.LocalAppSettings
import dev.ambivalenton.openv.ui.theme.colorHex
import dev.ambivalenton.openv.ui.theme.contrastContentColor
import dev.ambivalenton.openv.ui.theme.parseColorHex
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/*
 * Build 112 · the colour studio, rebuilt around one rule: what you touch to mix is never inside
 * something that scrolls. The field and the strips stand still; only the suggestions below them
 * scroll. So a finger that wants to scroll can never change the colour by accident, and a finger
 * that wants to mix gets the colour at once – no long press, no guessing.
 *
 * The pieces are shared by the colour sheet ("Auswahl" | "Mischen") and the stand-alone dialog
 * ("Eigene Farbe"): ColorDraft (the colour being mixed), ColorHero (what is chosen, with its name),
 * ColorMixer (field and strips) and ColorMixerExtras (hex code, partners, recent colours).
 */

/** The colour being mixed, as hue, saturation, brightness and opacity. Survives rotation. */
@Stable internal class ColorDraft(hue: Float, saturation: Float, brightness: Float, alpha: Float, val allowAlpha: Boolean) {
    var hue by mutableFloatStateOf(hue)
    var saturation by mutableFloatStateOf(saturation)
    var brightness by mutableFloatStateOf(brightness)
    var alpha by mutableFloatStateOf(alpha)
    val value: Long get() = studioColor(hue, saturation, brightness, if (allowAlpha) alpha else 1f)

    /** Takes a colour over; its opacity only when it brings its own (the "Vorher" colour, an 8-digit code). */
    fun choose(color: Long, withAlpha: Boolean = false) {
        val hsv = FloatArray(3).also { android.graphics.Color.colorToHSV(color.toInt(), it) }
        // A grey keeps the hue the strip shows, so the next drag does not jump to red.
        if (hsv[1] > .004f && hsv[2] > .004f) hue = hsv[0]
        saturation = hsv[1]; brightness = hsv[2]
        if (allowAlpha && withAlpha) alpha = (color ushr 24 and 255) / 255f
    }

    companion object {
        fun of(color: Long, allowAlpha: Boolean): ColorDraft {
            val start = if (allowAlpha) color else color or 0xff000000L
            val hsv = FloatArray(3).also { android.graphics.Color.colorToHSV(start.toInt(), it) }
            return ColorDraft(hsv[0], hsv[1], hsv[2], (start ushr 24 and 255) / 255f, allowAlpha)
        }
        val Saver = listSaver<ColorDraft, Any>(
            save = { listOf(it.hue, it.saturation, it.brightness, it.alpha, it.allowAlpha) },
            restore = { ColorDraft(it[0] as Float, it[1] as Float, it[2] as Float, it[3] as Float, it[4] as Boolean) })
    }
}

@Composable internal fun rememberColorDraft(seed: Long, allowAlpha: Boolean): ColorDraft =
    rememberSaveable(seed, allowAlpha, saver = ColorDraft.Saver) { ColorDraft.of(seed, allowAlpha) }

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

/**
 * A plain-language name for [color] – "Dunkles Blau", "Helles Grün", "Rosa", "Grau" – from its HCT
 * hue, chroma and tone, so everyone knows what they picked without reading a hex code.
 */
internal fun colorName(color: Long): String {
    val hct = Hct.fromInt(color.toInt() or 0xff000000.toInt())
    val tone = hct.tone; val chroma = hct.chroma; val hue = hct.hue
    if (chroma < 7.0 || (chroma < 12.0 && (tone > 92.0 || tone < 12.0))) return when {
        tone >= 95.0 -> tr("Weiß")
        tone >= 72.0 -> tr("Hellgrau")
        tone >= 38.0 -> tr("Grau")
        tone >= 12.0 -> tr("Dunkelgrau")
        else -> tr("Schwarz")
    }
    val warm = hue < 95.0 || hue >= 345.0
    if (warm && tone < 46.0 && chroma < 48.0 && hue in 20.0..95.0) return if (tone < 26.0) tr("Dunkles %1\$s", tr("Braun")) else tr("Braun")
    val base = when {
        hue >= 345.0 || hue < 40.0 -> if (tone > 74.0) "Rosa" else "Rot"
        hue < 80.0 -> "Orange"
        hue < 120.0 -> "Gelb"
        hue < 170.0 -> "Grün"
        hue < 225.0 -> "Türkis"
        hue < 290.0 -> "Blau"
        hue < 320.0 -> "Violett"
        else -> if (tone > 74.0) "Rosa" else "Pink"
    }
    val name = tr(base)
    return when {
        base == "Rosa" -> if (chroma < 22.0) tr("Gedecktes %1\$s", name) else name
        tone > 74.0 -> tr("Helles %1\$s", name)
        tone < 30.0 -> tr("Dunkles %1\$s", name)
        chroma < 22.0 -> tr("Gedecktes %1\$s", name)
        chroma > 68.0 -> tr("Kräftiges %1\$s", name)
        else -> name
    }
}

/**
 * What is chosen: the new colour large, with its name and code, and the colour it started from.
 * A tap on "Vorher" brings the old colour back. [after] null = the design's own colour.
 */
@Composable internal fun ColorHero(before: Long?, after: Long?, allowAlpha: Boolean, autoLabel: String, onRevert: () -> Unit,
    modifier: Modifier = Modifier, onHex: ((Long, Boolean) -> Unit)? = null) {
    val quiet = LocalAppSettings.current.reduceMotion
    // Build 120: the preview flows into the new colour instead of switching.
    val shownColor by androidx.compose.animation.animateColorAsState(after?.let { Color(it) } ?: Color.Transparent,
        if (quiet) snap() else spring(dampingRatio = .9f, stiffness = 320f), label = "hero-colour")
    Row(modifier.fillMaxWidth().testTag("color-hero"), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(64.dp).clip(RoundedCornerShape(20.dp)).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
            .then(if (after != null) Modifier.checkerboard().background(shownColor) else Modifier.automaticSwatch())
            .semantics { contentDescription = after?.let { tr("Neu %1\$s", colorHex(it, allowAlpha)) } ?: autoLabel },
            contentAlignment = Alignment.Center) {
            after?.let { Text("Aa", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                color = contrastContentColor(Color(it).copy(alpha = 1f))) }
        }
        Column(Modifier.weight(1f)) {
            Text(after?.let { colorName(it) } ?: autoLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                maxLines = 2, modifier = Modifier.testTag("color-name"))
            if (after != null && onHex != null) InlineHex(after, allowAlpha, onHex)
            else Text(after?.let { colorHex(it, allowAlpha) } ?: tr("Farbe des Designs"), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (before != after) Surface(onRevert, Modifier.heightIn(min = 48.dp).testTag("color-before"), shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest) {
            Row(Modifier.padding(start = 8.dp, end = 12.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(28.dp).clip(CircleShape).border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                    .then(if (before != null) Modifier.checkerboard().background(Color(before)) else Modifier.automaticSwatch()))
                Text(tr("Vorher"), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** The design's own colour, shown as a quiet diagonal stripe (no colour of its own). */
private fun Modifier.automaticSwatch(): Modifier = drawBehind {
    drawRect(Color(0xFFE9E9EE))
    val step = 7.dp.toPx(); var x = -size.height
    while (x < size.width) { drawLine(Color(0xFFB9B9C2), Offset(x, size.height), Offset(x + size.height, 0f), 1.5.dp.toPx()); x += step }
}

/**
 * The mixing tools: a field for strength (left → right) and brightness (top → bottom), a rainbow
 * strip for the hue and, where a colour may be see-through, a strip for its opacity. [fieldHeight]
 * adapts to the room; nothing here may be placed inside a scrolling container.
 */
@Composable internal fun ColorMixer(draft: ColorDraft, onChange: () -> Unit, fieldHeight: Dp = 200.dp) {
    val changed by rememberUpdatedState(onChange)
    Column(Modifier.fillMaxWidth().testTag("color-mixer"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SaturationBrightnessField(draft.hue, draft.saturation, draft.brightness, fieldHeight) { s, v ->
            draft.saturation = s; draft.brightness = v; changed() }
        HueStrip(draft.hue) { draft.hue = it; changed() }
        if (draft.allowAlpha) GradientStrip(tr("Deckkraft"), draft.alpha, { draft.alpha = it; changed() },
            Brush.horizontalGradient(listOf(Color(draft.value).copy(alpha = 0f), Color(draft.value).copy(alpha = 1f))),
            Color(draft.value), "alpha-strip", checker = true, describe = { tr("%1\$s Prozent", (it * 100).roundToInt()) })
    }
}

/** Below the mixer (this part may scroll): the hex code, colours that go with it, and recent ones. */
@Composable internal fun ColorMixerExtras(draft: ColorDraft, recent: List<Long>, accent: Long?, onChange: () -> Unit) {
    val changed by rememberUpdatedState(onChange)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HexField(draft.value, draft.allowAlpha) { color, withAlpha -> draft.choose(color, withAlpha); changed() }
        Suggestions(draft.value, recent, accent) { draft.choose(it); changed() }
    }
}

/** Fits the mixer into [available] height: the field shrinks first, never below a comfortable size. */
internal fun mixerFieldHeight(available: Dp, allowAlpha: Boolean): Dp {
    val strips = if (allowAlpha) 132.dp else 74.dp
    return if (available == Dp.Infinity) 200.dp else (available - strips - 72.dp).coerceIn(128.dp, 220.dp)
}

/**
 * "Eigene Farbe" as its own dialog (favourites): header, the chosen colour, the mixer standing still,
 * the extras scrolling below it, and "Abbrechen" / "Übernehmen" always in reach.
 */
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
    val draft = rememberColorDraft(start, allowAlpha)
    var working by remember { mutableLongStateOf(draft.value) }
    val sync = { working = draft.value }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        ReleaseDialogWindowOnDispose()
        Surface(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 20.dp).widthIn(max = 520.dp).testTag("advanced-color-dialog"),
            shape = RoundedCornerShape(30.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, tonalElevation = 2.dp) {
            BoxWithConstraints {
                val room = maxHeight
                Column(Modifier.imePadding().padding(top = 14.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        FittedText(tr("Eigene Farbe"), Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
                        IconButton(onDismiss) { AppIcon(R.drawable.ic_close, tr("Farbauswahl schließen")) }
                    }
                    ColorHero(start, working, allowAlpha, tr("Automatisch"), { draft.choose(start, true); sync() }, Modifier.padding(horizontal = 20.dp),
                        onHex = { color, withAlpha -> draft.choose(color, withAlpha); sync() })
                    // Build 120: everything on one page; the field takes what is left, nothing scrolls.
                    Box(Modifier.weight(1f, fill = false)) {
                        ColorMixPage(draft, recentColors, themeAccentColor, sync, Modifier.padding(horizontal = 20.dp))
                    }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onDismiss, Modifier.heightIn(min = 48.dp)) { Text(tr("Abbrechen")) }
                        ApplyColorButton(working, allowAlpha, { onConfirm(working) }, Modifier.testTag("advanced-color-apply"))
                    }
                }
            }
        }
    }
}

/** "Übernehmen" in the chosen colour itself, so the last look before confirming is the colour. */
@Composable internal fun ApplyColorButton(color: Long?, allowAlpha: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier,
    fillWidth: Boolean = false) {
    val fill = color?.let { Color(it).copy(alpha = 1f) } ?: MaterialTheme.colorScheme.primary
    Button(onClick, modifier.heightIn(min = 52.dp).then(if (fillWidth) Modifier.fillMaxWidth() else Modifier).pressScale(.96f)
        .semantics { stateDescription = color?.let { colorHex(it, allowAlpha) } ?: tr("Designstandard") },
        colors = ButtonDefaults.buttonColors(containerColor = fill, contentColor = contrastContentColor(fill)),
        contentPadding = PaddingValues(horizontal = 22.dp)) {
        AppIcon(R.drawable.ic_check, null, Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(tr("Übernehmen"), fontWeight = FontWeight.SemiBold)
    }
}

/** The hex code for those who have one; a complete code takes over at once. */
@Composable private fun HexField(color: Long, allowAlpha: Boolean, onPick: (Long, Boolean) -> Unit) {
    var hex by remember { mutableStateOf(colorHex(color, allowAlpha).removePrefix("#")) }
    var typing by remember { mutableStateOf(false) }
    LaunchedEffect(color, typing) { if (!typing) hex = colorHex(color, allowAlpha).removePrefix("#") }
    OutlinedTextField(hex, { text ->
        val clean = text.uppercase().filter { it in "0123456789ABCDEF" }.take(if (allowAlpha) 8 else 6)
        hex = clean
        parseColorHex(clean, allowAlpha)?.let { onPick(it, clean.length == 8) }
    }, Modifier.fillMaxWidth().onFocusChanged { typing = it.isFocused }.testTag("color-hex"),
        label = { Text(tr("Hex-Code")) }, prefix = { Text("#") }, singleLine = true,
        isError = parseColorHex(hex, allowAlpha) == null,
        supportingText = if (parseColorHex(hex, allowAlpha) == null) ({ Text(tr(if (allowAlpha) "6 oder 8 Zeichen, 0–9 und A–F" else "6 Zeichen, 0–9 und A–F")) }) else null,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { typing = false }))
}

/** Strength (left → right) and brightness (top → bottom) of the current hue; a loupe above the finger shows the colour. */
@Composable private fun SaturationBrightnessField(hue: Float, saturation: Float, brightness: Float, height: Dp, onChange: (Float, Float) -> Unit) {
    val change by rememberUpdatedState(onChange)
    val haptics = LocalHapticFeedback.current
    val quiet = LocalAppSettings.current.reduceMotion
    var pressed by remember { mutableStateOf(false) }
    val lift by animateFloatAsState(if (pressed) 1f else 0f, if (quiet) snap() else spring(dampingRatio = .55f, stiffness = 520f), label = "field-loupe")
    val pure = Color.hsv(hue.coerceIn(0f, 359.99f), 1f, 1f)
    val current = Color.hsv(hue.coerceIn(0f, 359.99f), saturation, brightness)
    val shape = RoundedCornerShape(24.dp)
    // Build 120: a picked suggestion or code makes the thumb spring to its place; under the finger it follows at once.
    val follow = if (pressed || quiet) snap<Float>() else spring(dampingRatio = .62f, stiffness = 360f)
    val shownS by animateFloatAsState(saturation, follow, label = "field-saturation")
    val shownV by animateFloatAsState(brightness, follow, label = "field-brightness")
    // Touch lives on the unclipped frame: the rounded corners must not swallow a tap on pure white or black.
    Box(Modifier.fillMaxWidth().height(height).testTag("color-field")
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
                var edge = false
                fun at(p: Offset) {
                    val s = (p.x / size.width).coerceIn(0f, 1f); val v = (1f - p.y / size.height).coerceIn(0f, 1f)
                    // A light tick when the finger reaches an edge: pure colour, white, black or grey.
                    val atEdge = s == 0f || s == 1f || v == 0f || v == 1f
                    if (atEdge && !edge) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    edge = atEdge
                    change(s, v)
                }
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume(); pressed = true; edge = false
                    at(down.position)
                    drag(down.id) { it.consume(); at(it.position) }
                    pressed = false
                }
            }) {
        Box(Modifier.matchParentSize().clip(shape)
            .drawBehind {
                drawRect(pure)
                drawRect(Brush.horizontalGradient(listOf(Color.White, Color.White.copy(alpha = 0f))))
                drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
                drawRoundRect(Color.Black.copy(alpha = .08f), style = Stroke(1.dp.toPx()), cornerRadius = CornerRadius(24.dp.toPx()))
            })
        // Thumb and loupe are drawn outside the clipped field: whole at the edges, and floating above the finger.
        Canvas(Modifier.matchParentSize()) {
            val thumb = Offset(shownS * size.width, (1f - shownV) * size.height)
            drawStudioThumb(thumb, current, 1f + .2f * lift)
            if (lift <= .01f) return@Canvas
            val r = 26.dp.toPx() * lift
            val above = thumb.y - 58.dp.toPx() > -24.dp.toPx()
            val c = Offset(thumb.x.coerceIn(r, size.width - r), if (above) thumb.y - 58.dp.toPx() * lift else thumb.y + 58.dp.toPx() * lift)
            drawCircle(Color.Black.copy(alpha = .22f * lift), r + 2.dp.toPx(), c + Offset(0f, 2.dp.toPx()))
            drawCircle(Color.White, r + 1.5.dp.toPx(), c)
            drawCircle(current, r - 2.dp.toPx(), c)
        }
    }
}

/** The hue as a rainbow strip; a light tick at each of the six pure colours. */
@Composable private fun HueStrip(hue: Float, onChange: (Float) -> Unit) {
    val haptics = LocalHapticFeedback.current
    var sextant by remember { mutableIntStateOf(floor(hue / 60f).toInt()) }
    GradientStrip(tr("Farbton"), hue / 360f, { position ->
        val next = (position * 360f).coerceIn(0f, 359.99f)
        val nearest = (next / 60f).roundToInt() % 6
        if (abs(next - nearest * 60f).let { minOf(it, 360f - it) } < 2.5f && nearest != sextant) {
            sextant = nearest; haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        onChange(next)
    }, Brush.horizontalGradient((0..6).map { Color.hsv((it * 60f).coerceAtMost(359.99f), 1f, 1f) }),
        Color.hsv(hue.coerceIn(0f, 359.99f), 1f, 1f), "hue-strip", describe = { tr("%1\$s Grad", (it * 360).roundToInt()) })
}

/** A rounded strip with a thumb: the rainbow for the hue, the colour's own fade for the opacity. */
@Composable private fun GradientStrip(label: String, position: Float, onChange: (Float) -> Unit, brush: Brush, thumbColor: Color, tag: String,
    checker: Boolean = false, describe: (Float) -> String) {
    val change by rememberUpdatedState(onChange)
    val quiet = LocalAppSettings.current.reduceMotion
    var pressed by remember { mutableStateOf(false) }
    val thumb by animateFloatAsState(if (pressed) 1.25f else 1f, if (quiet) snap() else spring(dampingRatio = .5f, stiffness = 600f), label = "$tag-thumb")
    // Build 120: the thumb springs to a picked colour; while dragged it follows the finger directly.
    val shown by animateFloatAsState(position, if (pressed || quiet) snap() else spring(dampingRatio = .62f, stiffness = 360f), label = "$tag-position")
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
            Text(describe(position), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(Modifier.fillMaxWidth().height(36.dp).testTag(tag)
            .semantics {
                contentDescription = label
                progressBarRangeInfo = ProgressBarRangeInfo(position, 0f..1f)
                stateDescription = describe(position)
                setProgress { change(it.coerceIn(0f, 1f)); true }
            }
            .pointerInput(Unit) {
                val pad = 18.dp.toPx()
                fun at(p: Offset) = change(((p.x - pad) / (size.width - 2 * pad)).coerceIn(0f, 1f))
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume(); pressed = true; at(down.position)
                    drag(down.id) { it.consume(); at(it.position) }
                    pressed = false
                }
            }
            .drawBehind {
                val pad = 18.dp.toPx()
                val track = Size(size.width, 24.dp.toPx())
                val top = (size.height - track.height) / 2
                val radius = CornerRadius(track.height / 2)
                if (checker) drawCheckers(Offset(0f, top), track, radius)
                drawRoundRect(brush, Offset(0f, top), track, radius)
                drawRoundRect(Color.Black.copy(alpha = .12f), Offset(0f, top), track, radius, style = Stroke(1.dp.toPx()))
                drawStudioThumb(Offset(pad + shown.coerceIn(0f, 1f) * (size.width - 2 * pad), size.height / 2), thumbColor, thumb)
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
internal fun Modifier.checkerboard(): Modifier = drawBehind { drawCheckers(Offset.Zero, size, CornerRadius.Zero) }

/** Colours to tap: the current colour's steps, its partners, the app design's accent and the recently chosen ones. */
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
@Composable internal fun SwatchRow(title: String, colors: List<Long>, current: Long?, onChoose: (Long) -> Unit, labels: List<String>? = null, tag: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(Modifier.fillMaxWidth().then(if (tag != null) Modifier.testTag(tag) else Modifier), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            colors.forEachIndexed { i, c ->
                ColorSwatch(c, labels?.getOrNull(i)?.let { "$it, ${colorName(c)}" } ?: colorName(c),
                    current != null && (c or 0xff000000L) == (current or 0xff000000L)) { onChoose(c) }
            }
        }
    }
}

/** Name of a curated swatch; generated colour worlds name their colours from the colour itself. */
internal fun dev.ambivalenton.openv.data.model.DesignSwatch.label(): String = if (nameKey.isEmpty()) colorName(color) else tr(nameKey)

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
                Text(color?.let { tr("Gemischt: %1\$s", colorName(it)) } ?: tr("Frei mischen oder Hex-Code eingeben"),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            AppIcon(R.drawable.ic_chevron_right, null, Modifier.size(22.dp))
        }
    }
}

/** "Auswahl" or "Mischen": a two-part switch whose capsule glides with a spring. */
@Composable internal fun ColorModeSwitch(mixing: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val quiet = LocalAppSettings.current.reduceMotion
    val position by animateFloatAsState(if (mixing) 1f else 0f, if (quiet) snap() else spring(dampingRatio = .72f, stiffness = 520f), label = "color-mode")
    val scheme = MaterialTheme.colorScheme
    BoxWithConstraints(modifier.fillMaxWidth().height(48.dp).clip(CircleShape).background(scheme.surfaceContainerHighest)
        .semantics { selectableGroup() }.testTag("color-mode")) {
        val half = maxWidth / 2
        Box(Modifier.padding(4.dp).width(half - 8.dp).fillMaxHeight().offset(x = half * position)
            .clip(CircleShape).background(scheme.primaryContainer))
        Row(Modifier.fillMaxSize()) {
            listOf(false to tr("Auswahl"), true to tr("Mischen")).forEach { (mix, label) ->
                val selected = mix == mixing
                Box(Modifier.weight(1f).fillMaxHeight().clip(CircleShape)
                    .clickable(role = Role.Tab) { if (!selected) onChange(mix) }
                    .semantics { this.selected = selected }.testTag(if (mix) "color-mode-mix" else "color-mode-choose"),
                    contentAlignment = Alignment.Center) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (mix) Canvas(Modifier.size(16.dp)) {
                            drawCircle(Brush.sweepGradient((0..6).map { Color.hsv((it * 60f).coerceAtMost(359.99f), .85f, 1f) }))
                        } else Canvas(Modifier.size(16.dp)) {
                            val r = size.minDimension / 4
                            listOf(Color(0xFFE53935), Color(0xFF43A047), Color(0xFF1E88E5), Color(0xFFFDD835)).forEachIndexed { i, c ->
                                drawCircle(c, r * .9f, Offset(r + (i % 2) * 2 * r, r + (i / 2) * 2 * r))
                            }
                        }
                        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (selected) scheme.onPrimaryContainer else scheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/**
 * Build 120 · the hex code inside the hero, editable in place (it replaces the separate field, so the
 * colour page needs no scrolling). A complete code takes over at once; a wrong one turns red.
 */
@Composable private fun InlineHex(color: Long, allowAlpha: Boolean, onPick: (Long, Boolean) -> Unit) {
    var hex by remember { mutableStateOf(colorHex(color, allowAlpha).removePrefix("#")) }
    var typing by remember { mutableStateOf(false) }
    LaunchedEffect(color, typing) { if (!typing) hex = colorHex(color, allowAlpha).removePrefix("#") }
    val valid = parseColorHex(hex, allowAlpha) != null
    val ink = if (valid) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
    val line = if (typing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    androidx.compose.foundation.text.BasicTextField(hex, { text ->
        val clean = text.uppercase().filter { it in "0123456789ABCDEF" }.take(if (allowAlpha) 8 else 6)
        hex = clean
        parseColorHex(clean, allowAlpha)?.let { onPick(it, clean.length == 8) }
    }, Modifier.widthIn(min = 96.dp).onFocusChanged { typing = it.isFocused }.testTag("color-hex")
        .semantics { contentDescription = tr("Hex-Code") }
        .drawBehind { drawLine(line, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx(),
            pathEffect = if (typing) null else androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()))) },
        singleLine = true, textStyle = MaterialTheme.typography.bodyMedium.copy(color = ink),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { typing = false }),
        decorationBox = { inner -> Row { Text("#", style = MaterialTheme.typography.bodyMedium, color = ink); inner() } })
}

/**
 * Build 120 · the whole mixing page on one screen, without scrolling: field, hue (and opacity), then
 * one row of lighter and darker tones and one row of colours that go with it (and the recent ones).
 * The field takes what is left, never more than 220 dp; everything else keeps its size.
 */
@Composable internal fun ColorMixPage(draft: ColorDraft, recent: List<Long>, accent: Long?, onChange: () -> Unit, modifier: Modifier = Modifier) {
    val changed by rememberUpdatedState(onChange)
    BoxWithConstraints(modifier.fillMaxWidth().testTag("color-mix-page")) {
        val strips = if (draft.allowAlpha) 128.dp else 64.dp
        val rows = (20.dp + MixSwatch) * 2
        val field = if (maxHeight == Dp.Infinity) 180.dp else (maxHeight - strips - rows - 12.dp * 3 - 6.dp).coerceIn(96.dp, 220.dp)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ColorMixer(draft, changed, field)
            val steps = remember(draft.value) { colorToneSteps(draft.value) }
            val partners = remember(draft.value) { colorHarmonies(draft.value) }
            CompactSwatchRow(tr("Heller und dunkler"), steps, draft.value, { draft.choose(it); changed() })
            val more = partners + listOfNotNull(accent) + recent.distinct().filter { it !in partners && it != accent }.take(3)
            CompactSwatchRow(tr("Passt dazu"), more, draft.value, { draft.choose(it); changed() },
                labels = listOf(tr("Nachbar links"), tr("Nachbar rechts"), tr("Gegenfarbe"), tr("Dreiklang")) +
                    listOfNotNull(accent?.let { tr("Akzent des App-Designs") }), tag = "color-harmonies")
        }
    }
}

private val MixSwatch = 34.dp

/** One row of round swatches that always fits its width (they get smaller instead of wrapping); the chosen one springs larger. */
@Composable private fun CompactSwatchRow(title: String, colors: List<Long>, current: Long, onChoose: (Long) -> Unit,
    labels: List<String>? = null, tag: String? = null) {
    val quiet = LocalAppSettings.current.reduceMotion
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        BoxWithConstraints(Modifier.fillMaxWidth().then(if (tag != null) Modifier.testTag(tag) else Modifier)) {
            val gap = 8.dp
            val size = if (colors.isEmpty()) MixSwatch else minOf(MixSwatch, (maxWidth - gap * (colors.size - 1)) / colors.size)
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                colors.forEachIndexed { i, c ->
                    val on = (c or 0xff000000L) == (current or 0xff000000L)
                    val grow by animateFloatAsState(if (on) 1.12f else 1f, if (quiet) snap() else spring(dampingRatio = .45f, stiffness = 520f), label = "swatch-$i")
                    val name = labels?.getOrNull(i)?.let { "$it, ${colorName(c)}" } ?: colorName(c)
                    Box(Modifier.size(size).graphicsLayer { scaleX = grow; scaleY = grow }.clip(CircleShape).checkerboard().background(Color(c))
                        .border(if (on) 2.5.dp else 1.dp, if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant, CircleShape)
                        .pressScale(.88f).clickable(role = androidx.compose.ui.semantics.Role.Button, onClickLabel = tr("Farbe wählen")) { onChoose(c) }
                        .semantics { contentDescription = name; selected = on })
                }
            }
        }
    }
}

/**
 * Build 120 · a page that never scrolls: when its content is taller than the room (very small screens,
 * very large type), the whole page is drawn a little smaller instead of cutting anything off.
 */
@Composable internal fun ShrinkToFit(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    androidx.compose.ui.layout.Layout(content, modifier.clipToBounds()) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minHeight = 0, maxHeight = androidx.compose.ui.unit.Constraints.Infinity)) }
        val height = placeables.maxOfOrNull { it.height } ?: 0
        val width = (placeables.maxOfOrNull { it.width } ?: 0).coerceIn(constraints.minWidth, constraints.maxWidth)
        val scale = if (constraints.hasBoundedHeight && height > constraints.maxHeight && height > 0) constraints.maxHeight.toFloat() / height else 1f
        layout(width, if (scale < 1f) constraints.maxHeight else height.coerceAtLeast(constraints.minHeight)) {
            placeables.forEach { it.placeWithLayer(0, 0) { scaleX = scale; scaleY = scale
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(.5f, 0f) } }
        }
    }
}
