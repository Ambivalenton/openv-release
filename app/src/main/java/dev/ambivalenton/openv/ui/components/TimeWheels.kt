// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.data.model.AppTheme
import dev.ambivalenton.openv.data.model.AppThemeGroup
import dev.ambivalenton.openv.data.model.DesignLanguage
import dev.ambivalenton.openv.data.model.appThemeGroup
import dev.ambivalenton.openv.data.model.reduceMotion
import dev.ambivalenton.openv.localization.tr
import dev.ambivalenton.openv.ui.theme.LocalAppSettings
import dev.ambivalenton.openv.ui.theme.LocalAppVisuals
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * Build 123 · the search's date and time as rotating rollers – the heart of the search form. One roller for
 * the day (weekday and date on its face; turned up, the next days come round), one for the hour and one for
 * the minute. They turn like drums seen from the front, click into every value with a tick, carry over like an
 * odometer (past :59 the hour turns on, past 23 the day), spool back to the current time on "Jetzt", and take
 * the look of the design: modern drums with a soft lens, mechanical rollers with knurled edges and a brass
 * window in the retro designs, glowing digits in the terminal and 8-bit designs.
 */

/** Days the day roller offers (today included): a year, like the calendar behind it. */
internal const val WHEEL_DAYS = 366

/** Minutes since the start of [today] (the stops' local time) for [time]; clamped to the roller's days. */
internal fun wheelTotal(time: Long, today: LocalDate, zone: ZoneId): Int {
    val at = java.time.Instant.ofEpochMilli(time).atZone(zone)
    val days = java.time.temporal.ChronoUnit.DAYS.between(today, at.toLocalDate()).toInt()
    return (days * 1440 + at.hour * 60 + at.minute).coerceIn(0, WHEEL_DAYS * 1440 - 1)
}

/** The moment of [total] minutes after the start of [today] (daylight saving handled by the zone). */
internal fun wheelTime(total: Int, today: LocalDate, zone: ZoneId): Long {
    val t = total.coerceIn(0, WHEEL_DAYS * 1440 - 1)
    return today.plusDays((t / 1440).toLong()).atTime((t % 1440) / 60, t % 60).atZone(zone).toInstant().toEpochMilli()
}

/** The index nearest to [position] that shows [value] on a roller of [count] values (no needless full turns). */
internal fun nearestIndex(position: Float, value: Int, count: Int): Int {
    val base = floor(position / count).toInt() * count + value
    return listOf(base - count, base, base + count).minBy { abs(it - position) }
}

/** The three looks of the rollers. */
enum class WheelLook { MODERN, MECHANICAL, DIGITAL }

/** Retro designs get mechanical rollers, command line and 8-bit designs glowing digits, all others modern drums. */
fun wheelLook(theme: AppTheme?): WheelLook = when {
    theme == null -> WheelLook.MODERN
    // Glowing digits for command line, 8-bit and neon designs (synthwave included).
    theme.language == DesignLanguage.TERMINAL || theme.language == DesignLanguage.PIXEL || theme.language == DesignLanguage.NEON ||
        theme == AppTheme.SYNTHWAVE -> WheelLook.DIGITAL
    theme == AppTheme.VINTAGE_TRANSIT || appThemeGroup(theme) == AppThemeGroup.RETRO -> WheelLook.MECHANICAL
    else -> WheelLook.MODERN
}

/** One roller: its visual position (unbounded index), its labels and what a value means for TalkBack. */
@Stable internal class RollerState(start: Float) {
    val position = Animatable(start)
    var dragging by mutableStateOf(false)
}

private const val ROW_ANGLE = 34f // degrees between two values on the drum
private const val DRUM_RADIUS = .62f // of the roller's height

/** The height of one value at the front of the drum, for a roller [height] pixels high. */
internal fun rollerRowPx(height: Float, angle: Float = ROW_ANGLE): Float = 2f * height * DRUM_RADIUS * sin((angle / 2f) * PI.toFloat() / 180f)

/**
 * Build 124 · what one value shows: the main text and, on the day roller, a smaller word in front of it on the
 * same line ("Di." or "Heute" before "01.12."). One line on every roller, so all three lenses stand at the same
 * height and nothing overlaps at a large system font. [sunday] gets the red of a printed calendar on the
 * mechanical rollers.
 */
internal data class RollerLabel(val text: String, val prefix: String = "", val sunday: Boolean = false)

/** The day's face on the roller (pure, unit-tested): "Heute"/"Morgen" for the first two days, else the weekday. */
internal fun dayRollerLabel(index: Int, today: LocalDate, todayWord: String, tomorrowWord: String,
    locale: Locale = if (dev.ambivalenton.openv.localization.serviceLanguage() == "de") Locale.GERMAN else Locale.ENGLISH): RollerLabel {
    val d = today.plusDays(index.toLong())
    // The weekday in the app's language ("Di." / "Tue").
    val prefix = when (index) { 0 -> todayWord; 1 -> tomorrowWord; else -> d.format(DateTimeFormatter.ofPattern("EEE", locale)) }
    return RollerLabel(d.format(DateTimeFormatter.ofPattern("dd.MM.", Locale.GERMAN)), prefix, d.dayOfWeek == java.time.DayOfWeek.SUNDAY)
}

/**
 * The rollers of the search: [timeMillis] null means "now" – the rollers show the current time and keep turning
 * with it, the colon between hour and minute blinks. Turning a roller chooses a time ([onChange]); "Jetzt"
 * elsewhere sets null, and the rollers spool back. [onPickDate]/[onPickTime] open the calendar and the typed
 * time when a roller is tapped.
 */
@Composable fun TimeRollers(timeMillis: Long?, onChange: (Long) -> Unit, onPickDate: () -> Unit, onPickTime: () -> Unit,
    modifier: Modifier = Modifier, height: Dp = 84.dp) {
    val zone = dev.ambivalenton.openv.data.model.transitZone
    val quiet = LocalAppSettings.current.reduceMotion
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val look = wheelLook(LocalAppVisuals.current.theme)
    // The current minute (the rollers follow it while no time is chosen). Build 124: the shared minute clock –
    // no second ticker any more that recomposed the rollers every second; the colon blinks in the draw phase.
    val wall = rememberWallClock()
    val nowTick = wall.toInstant().toEpochMilli()
    val today = remember(nowTick / 60_000) { java.time.Instant.ofEpochMilli(nowTick).atZone(zone).toLocalDate() }
    val target = wheelTotal(timeMillis ?: nowTick, today, zone)
    val latestTarget by rememberUpdatedState(target)
    val latestChange by rememberUpdatedState(onChange)
    val day = remember { RollerState((target / 1440).toFloat()) }
    val hour = remember { RollerState(((target % 1440) / 60).toFloat()) }
    val minute = remember { RollerState((target % 60).toFloat()) }
    // The rollers' own idea of the chosen minute while a finger turns them (written to onChange on every click).
    var committed by remember { mutableIntStateOf(target) }
    fun tick(strong: Boolean = false) {
        if (Build.VERSION.SDK_INT >= 34) view.performHapticFeedback(if (strong) HapticFeedbackConstants.SEGMENT_TICK else HapticFeedbackConstants.SEGMENT_FREQUENT_TICK)
        else view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }
    /** Brings the rollers a finger does not hold to [total] – the carry of an odometer, with a spring. */
    suspend fun follow(total: Int) {
        val d = total / 1440; val h = (total % 1440) / 60; val m = total % 60
        suspend fun to(roller: RollerState, value: Float) {
            if (roller.dragging) return
            if (quiet) roller.position.snapTo(value) else roller.position.animateTo(value, spring(dampingRatio = .58f, stiffness = 320f))
        }
        kotlinx.coroutines.coroutineScope {
            launch { to(day, d.toFloat()) }
            launch { to(hour, nearestIndex(hour.position.value, h, 24).toFloat()) }
            launch { to(minute, nearestIndex(minute.position.value, m, 60).toFloat()) }
        }
    }
    // A time from outside (another search, "Jetzt", the clock while nothing is chosen) moves the rollers.
    LaunchedEffect(target) {
        if (target == committed) return@LaunchedEffect
        val rewind = timeMillis == null && abs(target - committed) > 1
        val from = committed
        committed = target
        if (rewind && !quiet) {
            // Spool back: every roller runs the whole way back, the minute fastest.
            kotlinx.coroutines.coroutineScope {
                launch { minute.position.animateTo(minute.position.value + (target - from), tween(820, easing = FastOutSlowInEasing)) }
                launch { hour.position.animateTo(hour.position.value + ((target / 60) - (from / 60)), tween(780, easing = FastOutSlowInEasing)) }
                launch { day.position.animateTo((target / 1440).toFloat(), spring(dampingRatio = .6f, stiffness = 240f)) }
            }
            tick(strong = true)
        } else follow(target)
    }
    /** A finger moved [roller] by whole values: the chosen time follows, the other rollers carry over. */
    fun stepped(unit: Int, steps: Int) {
        val next = (committed + unit * steps).coerceIn(0, WHEEL_DAYS * 1440 - 1)
        if (next == committed) return
        committed = next
        tick()
        latestChange(wheelTime(next, today, zone))
        scope.launch { follow(next) }
    }
    val todayWord = tr("Heute"); val tomorrowWord = tr("Morgen")
    val dayLabels = remember(today, todayWord, tomorrowWord) { List(WHEEL_DAYS) { dayRollerLabel(it, today, todayWord, tomorrowWord) } }
    // All faces of a roller share one size: the widest that can come up (every weekday word, the widest digits) fits.
    val daySample = remember(dayLabels) { dayLabels.take(9).map { it.copy(text = "28.08.") } }
    val blink = timeMillis == null && !quiet
    val colors = rollerColors(look)
    Row(modifier.fillMaxWidth().height(height).testTag("time-rollers"), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        // Build 124: the day roller carries a whole line ("Di. 01.12."), the time rollers two digits each.
        Roller(day, WHEEL_DAYS, bounded = true, look = look, colors = colors, height = height,
            modifier = Modifier.weight(1.6f).fillMaxHeight().testTag("roller-day"),
            description = tr("Tag"), valueText = { i -> dayLabels[i.coerceIn(0, WHEEL_DAYS - 1)].let { "${it.prefix}, ${it.text}" } },
            label = { i -> dayLabels.getOrNull(i) ?: RollerLabel("") }, sample = daySample, mainScale = .86f,
            onStep = { stepped(1440, it) }, onTap = onPickDate, pressDown = true, onSettled = { scope.launch { follow(committed) } },
            later = tr("Einen Tag später"), earlier = tr("Einen Tag früher"))
        RollerGap(look, colors)
        Roller(hour, 24, bounded = false, look = look, colors = colors, height = height, modifier = Modifier.weight(1f).fillMaxHeight().testTag("roller-hour"),
            description = tr("Stunde"), valueText = { i -> "%02d".format(Math.floorMod(i, 24)) }, label = { i -> RollerLabel("%02d".format(Math.floorMod(i, 24))) },
            onStep = { stepped(60, it) }, onTap = onPickTime, onSettled = { scope.launch { follow(committed) } }, later = tr("Eine Stunde später"), earlier = tr("Eine Stunde früher"))
        Colon(look, colors, blinking = blink)
        Roller(minute, 60, bounded = false, look = look, colors = colors, height = height, modifier = Modifier.weight(1f).fillMaxHeight().testTag("roller-minute"),
            description = tr("Minute"), valueText = { i -> "%02d".format(Math.floorMod(i, 60)) }, label = { i -> RollerLabel("%02d".format(Math.floorMod(i, 60))) },
            onStep = { stepped(1, it) }, onTap = onPickTime, onSettled = { scope.launch { follow(committed) } }, later = tr("Eine Minute später"), earlier = tr("Eine Minute früher"))
    }
}

/** Colours of a look, derived from the design. */
internal data class RollerColors(val ink: Color, val faint: Color, val accent: Color, val drum: Color, val shade: Color, val frame: Color, val glow: Color)

@Composable private fun rollerColors(look: WheelLook): RollerColors {
    val s = MaterialTheme.colorScheme
    return when (look) {
        // The shade is the frame's own colour: values run softly into it at the top and bottom.
        WheelLook.MODERN -> RollerColors(s.onSurface, s.onSurfaceVariant, s.primary, s.primary, s.surfaceContainerHigh, s.outlineVariant, s.primary)
        // Rollers of a counter: light drums, dark ink, edges falling into shadow, a brass window.
        WheelLook.MECHANICAL -> RollerColors(Color(0xFF1E1A16), Color(0xFF4A423A), Color(0xFFB98B3E),
            lerp(Color(0xFFF1E6D0), s.surface, .18f), Color.Black, Color(0xFFB98B3E), Color(0xFFE8C77E))
        WheelLook.DIGITAL -> RollerColors(s.primary, s.primary.copy(alpha = .38f), s.primary, Color(0xFF07090B), Color.Black, s.primary.copy(alpha = .5f), s.primary)
    }
}

/** The little gap between the day and the hour roller. */
@Composable private fun RollerGap(look: WheelLook, colors: RollerColors) {
    Spacer(Modifier.width(if (look == WheelLook.MECHANICAL) 6.dp else 10.dp))
}

/**
 * The colon between hour and minute: lit while a time is chosen, blinking with the seconds while it is "now".
 * The blink is read only while drawing (no recomposition) and only its short fades ask for frames.
 */
@Composable private fun Colon(look: WheelLook, colors: RollerColors, blinking: Boolean) {
    val alpha = remember { Animatable(1f) }
    LaunchedEffect(blinking) {
        if (!blinking) { alpha.animateTo(1f, tween(220)); return@LaunchedEffect }
        while (true) {
            alpha.animateTo(.25f, tween(260)); kotlinx.coroutines.delay(740)
            alpha.animateTo(1f, tween(260)); kotlinx.coroutines.delay(740)
        }
    }
    Canvas(Modifier.width(14.dp).fillMaxHeight()) {
        val r = 2.6.dp.toPx(); val c = Offset(size.width / 2f, size.height / 2f)
        val ink = if (look == WheelLook.MODERN) colors.ink else colors.accent
        listOf(-1f, 1f).forEach { drawCircle(ink.copy(alpha = alpha.value), r, c + Offset(0f, it * 7.dp.toPx())) }
    }
}

/**
 * One roller. Dragged up, the values below come to the front (later days, later times); every value passed
 * clicks; let go, it flies on a little and settles with a spring. A tap presses it down ([pressDown]) and
 * calls [onTap].
 */
@Composable private fun Roller(state: RollerState, count: Int, bounded: Boolean, look: WheelLook, colors: RollerColors, height: Dp, modifier: Modifier,
    description: String, valueText: (Int) -> String, label: (Int) -> RollerLabel, onStep: (Int) -> Unit, onTap: () -> Unit,
    later: String, earlier: String, pressDown: Boolean = false, onSettled: () -> Unit = {},
    /** The widest faces that can come up; all faces share the size at which these fit. */
    sample: List<RollerLabel> = listOf(RollerLabel("88")), mainScale: Float = 1f) {
    val quiet = LocalAppSettings.current.reduceMotion
    val scope = rememberCoroutineScope()
    val measurer = rememberTextMeasurer()
    val visuals = LocalAppVisuals.current
    val density = LocalDensity.current
    val title = MaterialTheme.typography.titleLarge
    // Build 124: the digits grow with the system font up to the height of one row of the drum, never beyond –
    // at a very large font the values would otherwise run into their neighbours.
    val valueSize = with(density) {
        val cap = rollerRowPx(height.toPx())
        if (title.fontSize.toPx() > cap) cap.toSp() else title.fontSize
    }
    val family = if (look == WheelLook.DIGITAL) FontFamily.Monospace else visuals.headingFamily
    val mainStyle = title.copy(fontSize = valueSize * mainScale, lineHeight = TextUnit.Unspecified, fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center, fontFamily = family)
    val prefixStyle = mainStyle.copy(fontSize = valueSize * .64f, fontWeight = FontWeight.Medium)
    val press = remember { Animatable(0f) }
    val stepLatest by rememberUpdatedState(onStep)
    val tapLatest by rememberUpdatedState(onTap)
    val settledLatest by rememberUpdatedState(onSettled)
    val current = state.position.value.roundToInt()
    val shown = if (bounded) current.coerceIn(0, count - 1) else Math.floorMod(current, count)
    Box(modifier
        .graphicsLayer {
            // Pressed down: a little smaller and lower, like a roller pushed into its housing.
            val p = press.value
            scaleX = 1f - .05f * p; scaleY = 1f - .07f * p; translationY = 2.dp.toPx() * p
        }
        .semantics {
            contentDescription = description; stateDescription = valueText(current)
            role = Role.Button
            onClick { tapLatest(); true }
            customActions = listOf(CustomAccessibilityAction(later) { stepLatest(1); true }, CustomAccessibilityAction(earlier) { stepLatest(-1); true })
        }
        .pointerInput(count, bounded) {
            detectTapGestures(onPress = {
                if (pressDown && !quiet) scope.launch { press.animateTo(1f, spring(dampingRatio = .6f, stiffness = 900f)) }
                val released = tryAwaitRelease()
                if (pressDown) scope.launch { press.animateTo(0f, spring(dampingRatio = .35f, stiffness = 420f)) }
                if (released) tapLatest()
            })
        }
        .pointerInput(count, bounded) {
            val tracker = VelocityTracker()
            var rowPx = 1f
            var lastIndex = 0
            fun limit(p: Float): Float = if (!bounded) p else when {
                p < 0f -> p * .35f
                p > count - 1f -> (count - 1f) + (p - (count - 1f)) * .35f
                else -> p
            }
            detectVerticalDragGestures(
                onDragStart = {
                    state.dragging = true; tracker.resetTracking()
                    // The finger moves the drum exactly: one value is one row of the drawn cylinder.
                    rowPx = rollerRowPx(size.height.toFloat())
                    lastIndex = state.position.value.roundToInt()
                    scope.launch { state.position.stop() }
                },
                onDragEnd = {
                    val v = -tracker.calculateVelocity().y / rowPx
                    scope.launch {
                        // Flies on a little (about a quarter second's worth), then settles into a value with a spring.
                        var to = (state.position.value + v * .22f).roundToInt().toFloat()
                        if (bounded) to = to.coerceIn(0f, count - 1f)
                        val passed = to.roundToInt() - lastIndex
                        if (passed != 0) { lastIndex = to.roundToInt(); stepLatest(passed) }
                        state.dragging = false
                        if (quiet) state.position.snapTo(to)
                        else state.position.animateTo(to, spring(dampingRatio = .55f, stiffness = 300f), initialVelocity = v)
                        // A turn beyond the first or last minute is brought back to the chosen time.
                        settledLatest()
                    }
                },
                onDragCancel = { state.dragging = false; scope.launch { state.position.animateTo(state.position.value.roundToInt().toFloat(), spring()) } },
            ) { change, dy ->
                change.consume()
                tracker.addPosition(change.uptimeMillis, change.position)
                val next = limit(state.position.value - dy / rowPx)
                scope.launch { state.position.snapTo(next) }
                val index = next.roundToInt().let { if (bounded) it.coerceIn(0, count - 1) else it }
                if (index != lastIndex) { stepLatest(index - lastIndex); lastIndex = index }
            }
        }) {
        val layouts = remember(mainStyle, prefixStyle) { HashMap<String, TextLayoutResult>() }
        val fits = remember(mainStyle, prefixStyle, sample) { HashMap<Int, Float>() }
        fun layout(text: String, style: TextStyle) = layouts.getOrPut(text + "\u0000" + style.fontSize.value) { measurer.measure(text, style, maxLines = 1, softWrap = false) }
        val gap = with(density) { mainStyle.fontSize.toPx() } * .3f
        val mainPx = with(density) { mainStyle.fontSize.toPx() }
        fun face(l: RollerLabel) = RollerFace(l.prefix.takeIf { it.isNotEmpty() }?.let { layout(it, prefixStyle) }, layout(l.text, mainStyle), gap, mainPx, l.sunday)
        Canvas(Modifier.fillMaxSize()) {
            // One size for all faces of this roller: the widest possible face fits between the edges.
            val fit = fits.getOrPut(size.width.roundToInt()) {
                val room = size.width - 12.dp.toPx()
                (room / sample.maxOf { face(it).width }).coerceIn(.3f, 1f)
            }
            drawRoller(state.position.value, count, bounded, look, colors, shown, fit) { index -> face(label(index)) }
        }
    }
}

/** One value laid out: the smaller word and the main text on one baseline. */
private class RollerFace(val prefix: TextLayoutResult?, val main: TextLayoutResult, val gap: Float, val mainPx: Float, val sunday: Boolean) {
    val width: Float get() = (prefix?.let { it.size.width + gap } ?: 0f) + main.size.width
}

/** Draws the drum: the values around [position] on a cylinder, the look's lens/window, shading and edges. */
private fun DrawScope.drawRoller(position: Float, count: Int, bounded: Boolean, look: WheelLook, colors: RollerColors, chosen: Int,
    fit: Float, text: (Int) -> RollerFace) {
    val rowAngle = ROW_ANGLE
    val w = size.width; val h = size.height; val cy = h / 2f
    val radius = h * DRUM_RADIUS
    val row = rollerRowPx(h, rowAngle)
    val corner = CornerRadius(minOf(16.dp.toPx(), h * .18f))
    // Drum body.
    when (look) {
        WheelLook.MODERN -> {
            // A soft lens behind the chosen value; it breathes a little while the roller turns.
            val frac = abs(position - position.roundToInt())
            val lensH = row * (1.02f + .06f * (1f - frac * 2f).coerceIn(0f, 1f))
            val lensAt = Offset(2.dp.toPx(), cy - lensH / 2); val lensSize = Size(w - 4.dp.toPx(), lensH)
            // Visible in light and dark designs: a tint of the accent with a fine rim.
            drawRoundRect(colors.drum.copy(alpha = .18f), lensAt, lensSize, corner)
            drawRoundRect(colors.drum.copy(alpha = .42f), lensAt, lensSize, corner, style = Stroke(1.dp.toPx()))
        }
        WheelLook.MECHANICAL -> {
            drawRoundRect(colors.drum, Offset.Zero, size, CornerRadius(6.dp.toPx()))
            // Cylinder light: dark at the top and bottom, a bright band just above the middle.
            drawRoundRect(Brush.verticalGradient(0f to colors.shade.copy(alpha = .55f), .3f to Color.Transparent, .42f to Color.White.copy(alpha = .22f),
                .58f to Color.Transparent, .72f to Color.Transparent, 1f to colors.shade.copy(alpha = .6f)), Offset.Zero, size, CornerRadius(6.dp.toPx()))
        }
        WheelLook.DIGITAL -> drawRoundRect(colors.drum, Offset.Zero, size, CornerRadius(4.dp.toPx()))
    }
    clipRect {
        val first = floor(position).toInt() - 3; val last = first + 7
        for (index in first..last) {
            if (bounded && index !in 0 until count) continue
            val offset = index - position
            val angle = offset * rowAngle
            if (abs(angle) >= 88f) continue
            val rad = angle * PI.toFloat() / 180f
            val y = cy + radius * sin(rad)
            val squash = cos(rad)
            val front = squash * squash
            val isChosen = abs(offset) < .5f
            val face = text(index)
            val ink = when (look) {
                WheelLook.MODERN -> lerp(colors.faint, colors.ink, if (isChosen) 1f else .2f).copy(alpha = .25f + .75f * front)
                WheelLook.MECHANICAL -> colors.ink.copy(alpha = .35f + .65f * front)
                WheelLook.DIGITAL -> if (isChosen) colors.ink else colors.faint.copy(alpha = colors.faint.alpha * front)
            }
            // The word in front: the accent on the chosen day of a modern roller, a calendar's red on Sundays
            // of a mechanical one, otherwise a little softer than the date.
            val wordInk = when {
                look == WheelLook.MECHANICAL && face.sunday -> Color(0xFFA3261C).copy(alpha = ink.alpha)
                look == WheelLook.MODERN && isChosen -> colors.accent
                else -> ink.copy(alpha = ink.alpha * .86f)
            }
            fun part(l: TextLayoutResult, x: Float, top: Float, color: Color) {
                if (look == WheelLook.MECHANICAL) drawText(l, Color.White.copy(alpha = .45f * front), Offset(x, top + 1.dp.toPx()))
                if (look == WheelLook.DIGITAL && isChosen) drawText(l, colors.glow.copy(alpha = .35f), Offset(x, top))
                drawText(l, color, Offset(x, top))
            }
            scale(fit, squash.coerceAtLeast(.05f) * fit, Offset(w / 2f, y)) {
                // Optically centred: the middle of the digits (not of the line with its descent) sits on the row.
                val baseline = y + face.mainPx * .36f
                var x = (w - face.width) / 2f
                face.prefix?.let { p -> part(p, x, baseline - p.firstBaseline, wordInk); x += p.size.width + face.gap }
                part(face.main, x, baseline - face.main.firstBaseline, ink)
            }
        }
    }
    when (look) {
        WheelLook.MECHANICAL -> {
            // Knurled edges that turn with the roller, and the brass window around the chosen value.
            val step = row / 3f
            val phase = ((position * row) % step + step) % step
            var y = -phase
            while (y < h) {
                val rad = ((y - cy) / radius).coerceIn(-1f, 1f)
                val alpha = (.5f * cos(kotlin.math.asin(rad))).coerceIn(0f, .5f)
                drawLine(colors.shade.copy(alpha = alpha), Offset(0f, y), Offset(4.dp.toPx(), y), 1.dp.toPx())
                drawLine(colors.shade.copy(alpha = alpha), Offset(w - 4.dp.toPx(), y), Offset(w, y), 1.dp.toPx())
                y += step
            }
            val win = row * 1.04f
            drawRect(colors.frame, Offset(0f, cy - win / 2 - 1.5.dp.toPx()), Size(w, 1.5.dp.toPx()))
            drawRect(colors.frame, Offset(0f, cy + win / 2), Size(w, 1.5.dp.toPx()))
            drawRect(Color.Black.copy(alpha = .18f), Offset(0f, cy + win / 2 + 1.5.dp.toPx()), Size(w, 2.dp.toPx()))
        }
        WheelLook.DIGITAL -> {
            // Scan lines and two thin rails around the lit value.
            var y = 0f
            while (y < h) { drawLine(Color.White.copy(alpha = .05f), Offset(0f, y), Offset(w, y), 1f); y += 3.dp.toPx() }
            drawLine(colors.frame, Offset(0f, cy - row / 2), Offset(w, cy - row / 2), 1.dp.toPx())
            drawLine(colors.frame, Offset(0f, cy + row / 2), Offset(w, cy + row / 2), 1.dp.toPx())
        }
        WheelLook.MODERN -> {
            // Depth: the drum turns away into the frame.
            drawRect(Brush.verticalGradient(0f to colors.shade.copy(alpha = .92f), .26f to colors.shade.copy(alpha = 0f),
                .74f to colors.shade.copy(alpha = 0f), 1f to colors.shade.copy(alpha = .92f)), Offset.Zero, size)
        }
    }
}
