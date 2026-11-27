// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import dev.ambivalenton.openv.ui.theme.corner
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
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
import dev.ambivalenton.openv.data.model.spec
import dev.ambivalenton.openv.data.model.themeLanguage
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

/** Retro designs get mechanical rollers, command line and neon designs glowing digits, all others modern drums. */
fun wheelLook(theme: AppTheme?): WheelLook = when {
    theme == null -> WheelLook.MODERN
    // Glowing digits for command line and neon designs (synthwave included). Build 130: 8-bit has its own skin.
    theme.language == DesignLanguage.TERMINAL || theme.language == DesignLanguage.NEON ||
        theme == AppTheme.SYNTHWAVE -> WheelLook.DIGITAL
    theme == AppTheme.VINTAGE_TRANSIT || appThemeGroup(theme) == AppThemeGroup.RETRO -> WheelLook.MECHANICAL
    else -> WheelLook.MODERN
}

/**
 * Build 130 · within the modern look every design family has its own roller – lens, light, type and motion:
 * Material's tonal liquid lens, real glass with a glow behind it, an aurora edge that turns with the drum,
 * a pressed soft-UI groove, puffy clay that squishes, a brutalist stamp with a hard shadow, Swiss rules,
 * an editorial double rule, a drafting outline with a moving scale, crisp tiles, tonal steps, a Japandi
 * brush stroke, a breathing pebble, inverted e-ink, polished chrome, cut paper, a stitched patch and
 * stepped 8-bit pixels.
 */
enum class RollerSkin(
    /** How much the lens stretches with the turn (0: a crisp, rigid selection). */
    val liquid: Float,
    /** Size of a value one row away from the lens; values grow continuously towards it. */
    val neighbour: Float,
    /** Fast turns leave a faint trail. */
    val trail: Boolean,
) {
    MATERIAL(1f, .84f, true), GLASS(.8f, .82f, true), AURORA(.8f, .84f, true), SOFT(.35f, .86f, true), CLAY(1f, .84f, true),
    BRUTAL(0f, .86f, false), SWISS(0f, .74f, false), EDITORIAL(0f, .84f, false), OUTLINE(.4f, .84f, true), TILE(0f, .86f, false),
    TONAL(.5f, .84f, true), JAPANDI(0f, .8f, false), ORGANIC(1.2f, .84f, true), INK(0f, .9f, false), CHROME(.45f, .84f, true),
    PAPER(0f, .86f, false), STITCH(.3f, .86f, true), PIXEL(0f, .88f, false),
}

/** The skin of a design: its own collection language first, then its design language. */
fun rollerSkin(theme: AppTheme?): RollerSkin {
    if (theme == null) return RollerSkin.MATERIAL
    if (theme.name.startsWith("TILE_")) return RollerSkin.TILE
    when (theme.themeLanguage) {
        dev.ambivalenton.openv.data.model.ThemeLanguage.NEO_BRUTAL -> return RollerSkin.BRUTAL
        dev.ambivalenton.openv.data.model.ThemeLanguage.SOFT_RELIEF -> return RollerSkin.SOFT
        dev.ambivalenton.openv.data.model.ThemeLanguage.CLAY -> return RollerSkin.CLAY
        dev.ambivalenton.openv.data.model.ThemeLanguage.OUTLINE -> return RollerSkin.OUTLINE
        dev.ambivalenton.openv.data.model.ThemeLanguage.BENTO, dev.ambivalenton.openv.data.model.ThemeLanguage.COLOR_BLOCK -> return RollerSkin.TILE
        dev.ambivalenton.openv.data.model.ThemeLanguage.TONAL -> return RollerSkin.TONAL
        dev.ambivalenton.openv.data.model.ThemeLanguage.JAPANDI -> return RollerSkin.JAPANDI
        dev.ambivalenton.openv.data.model.ThemeLanguage.ORGANIC -> return RollerSkin.ORGANIC
        dev.ambivalenton.openv.data.model.ThemeLanguage.GLOW_EDGE -> return RollerSkin.AURORA
        dev.ambivalenton.openv.data.model.ThemeLanguage.STANDARD -> Unit
    }
    return when (theme.language) {
        DesignLanguage.GLASS -> RollerSkin.GLASS
        DesignLanguage.AURORA -> RollerSkin.AURORA
        DesignLanguage.SOFT -> RollerSkin.SOFT
        DesignLanguage.CLAY -> RollerSkin.CLAY
        DesignLanguage.BRUTAL -> RollerSkin.BRUTAL
        DesignLanguage.SWISS -> RollerSkin.SWISS
        DesignLanguage.EDITORIAL -> RollerSkin.EDITORIAL
        DesignLanguage.OUTLINE -> RollerSkin.OUTLINE
        DesignLanguage.BENTO -> RollerSkin.TILE
        DesignLanguage.FLAT -> RollerSkin.TONAL
        DesignLanguage.INK -> RollerSkin.INK
        DesignLanguage.CHROME -> RollerSkin.CHROME
        DesignLanguage.PAPER -> RollerSkin.PAPER
        DesignLanguage.STITCH -> RollerSkin.STITCH
        DesignLanguage.PIXEL -> RollerSkin.PIXEL
        else -> RollerSkin.MATERIAL
    }
}

/** One roller: its visual position (unbounded index), its labels and what a value means for TalkBack. */
@Stable internal class RollerState(start: Float) {
    val position = Animatable(start)
    var dragging by mutableStateOf(false)
    /** Build 126: the finger's speed in values per second (smoothed), for the liquid lens while dragging. */
    var dragVelocity by mutableFloatStateOf(0f)
    /** The speed the lens reacts to: the finger's while dragging, the drum's own while it flies and settles. */
    val velocity: Float get() = if (dragging) dragVelocity else position.velocity
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
    modifier: Modifier = Modifier, height: Dp = 84.dp,
    /** Build 127: the outline of the frame the rollers sit in; a housing (retro, display) takes exactly this form. */
    frameShape: androidx.compose.ui.graphics.Shape? = null) {
    val zone = dev.ambivalenton.openv.data.model.transitZone
    val quiet = LocalAppSettings.current.reduceMotion
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val look = wheelLook(LocalAppVisuals.current.theme)
    val skin = rollerSkin(LocalAppVisuals.current.theme)
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
            if (quiet) roller.position.snapTo(value) else roller.position.animateTo(value, spring(dampingRatio = .72f, stiffness = 300f))
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
    val blink = timeMillis == null && !quiet && android.animation.ValueAnimator.areAnimatorsEnabled() &&
        !day.dragging && !hour.dragging && !minute.dragging
    val colors = rollerColors(look, skin)
    // Build 126: the retro counter and the display sit in a housing of their own; the rollers inside it.
    val inset = when (look) { WheelLook.MECHANICAL -> 6.dp; WheelLook.DIGITAL -> 5.dp; WheelLook.MODERN -> 0.dp }
    val rollerHeight = height - inset * 2
    val housingShape = frameShape ?: dev.ambivalenton.openv.ui.theme.appCorner(12.dp)
    Row(modifier.fillMaxWidth().height(height).rollerHousing(look, colors, housingShape).padding(horizontal = inset + if (inset > 0.dp) 2.dp else 0.dp, vertical = inset)
        .testTag("time-rollers"), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        // Build 124: the day roller carries a whole line ("Di. 01.12."), the time rollers two digits each.
        Roller(day, WHEEL_DAYS, bounded = true, look = look, skin = skin, colors = colors, height = rollerHeight,
            modifier = Modifier.weight(1.6f).fillMaxHeight().testTag("roller-day"),
            description = tr("Tag"), valueText = { i -> dayLabels[i.coerceIn(0, WHEEL_DAYS - 1)].let { "${it.prefix}, ${it.text}" } },
            label = { i -> dayLabels.getOrNull(i) ?: RollerLabel("") }, sample = daySample, mainScale = .86f,
            onStep = { stepped(1440, it) }, onTap = onPickDate, pressDown = true, onSettled = { scope.launch { follow(committed) } },
            later = tr("Einen Tag später"), earlier = tr("Einen Tag früher"))
        RollerGap(look, colors)
        Roller(hour, 24, bounded = false, look = look, skin = skin, colors = colors, height = rollerHeight, ghost = true, modifier = Modifier.weight(1f).fillMaxHeight().testTag("roller-hour"),
            description = tr("Stunde"), valueText = { i -> "%02d".format(Math.floorMod(i, 24)) }, label = { i -> RollerLabel("%02d".format(Math.floorMod(i, 24))) },
            onStep = { stepped(60, it) }, onTap = onPickTime, onSettled = { scope.launch { follow(committed) } }, later = tr("Eine Stunde später"), earlier = tr("Eine Stunde früher"))
        Colon(look, skin, colors, blinking = blink)
        Roller(minute, 60, bounded = false, look = look, skin = skin, colors = colors, height = rollerHeight, ghost = true, modifier = Modifier.weight(1f).fillMaxHeight().testTag("roller-minute"),
            description = tr("Minute"), valueText = { i -> "%02d".format(Math.floorMod(i, 60)) }, label = { i -> RollerLabel("%02d".format(Math.floorMod(i, 60))) },
            onStep = { stepped(1, it) }, onTap = onPickTime, onSettled = { scope.launch { follow(committed) } }, later = tr("Eine Minute später"), earlier = tr("Eine Minute früher"))
    }
}

/** Colours of a look, derived from the design. [lens]/[lensInk]: the selection and the chosen value on it. */
internal data class RollerColors(val ink: Color, val faint: Color, val accent: Color, val drum: Color, val shade: Color, val frame: Color,
    val glow: Color, val lens: Color = drum, val lensInk: Color = ink, val chroma: Boolean = false,
    /** Build 130: the lens's edge (outline, rule, stitch) and whether the design is dark. */
    val edge: Color = accent, val dark: Boolean = false)

@Composable private fun rollerColors(look: WheelLook, skin: RollerSkin): RollerColors {
    val s = MaterialTheme.colorScheme
    val theme = LocalAppVisuals.current.theme
    val dark = s.background.luminance() < .4f
    return when (look) {
        // The shade is the frame's own colour: values run softly into it at the top and bottom.
        WheelLook.MODERN -> {
            val base = RollerColors(s.onSurface, s.onSurfaceVariant, s.primary, s.primary, s.surfaceContainerHigh, s.outlineVariant, s.primary,
                lens = s.primaryContainer, lensInk = s.onPrimaryContainer, edge = s.primary, dark = dark)
            when (skin) {
                RollerSkin.MATERIAL, RollerSkin.CLAY, RollerSkin.ORGANIC -> base
                RollerSkin.GLASS -> base.copy(lens = if (dark) Color.White else Color.White, lensInk = s.onSurface, edge = Color.White)
                RollerSkin.AURORA -> base.copy(lens = s.surfaceContainerHighest, lensInk = lerp(s.primary, s.onSurface, if (dark) .15f else .35f),
                    edge = s.tertiary)
                RollerSkin.SOFT -> base.copy(lens = s.surfaceContainerHigh, lensInk = s.primary)
                RollerSkin.BRUTAL -> base.copy(lens = s.primaryContainer, lensInk = s.onPrimaryContainer, edge = s.onSurface)
                RollerSkin.SWISS, RollerSkin.EDITORIAL, RollerSkin.JAPANDI -> base.copy(lensInk = s.onSurface)
                RollerSkin.OUTLINE -> base.copy(lens = Color.Transparent, lensInk = s.primary)
                // Tiles: a solid accent tile; bento: a white tile with a fine joint.
                RollerSkin.TILE -> if (theme?.themeLanguage == dev.ambivalenton.openv.data.model.ThemeLanguage.BENTO || theme?.language == DesignLanguage.BENTO)
                    base.copy(lens = s.surface, lensInk = s.primary, edge = s.outlineVariant)
                    else base.copy(lens = s.primary, lensInk = s.onPrimary)
                RollerSkin.TONAL -> base.copy(lens = s.secondaryContainer, lensInk = s.onSecondaryContainer)
                RollerSkin.INK -> base.copy(lens = s.onSurface, lensInk = s.surface, faint = s.onSurface)
                RollerSkin.CHROME -> base.copy(lens = Color(0xFFDCE1E8), lensInk = Color(0xFF15181D), edge = Color(0xFF8A929E))
                RollerSkin.PAPER -> base.copy(lens = s.surface, lensInk = s.primary, edge = s.outlineVariant)
                RollerSkin.STITCH -> base.copy(edge = s.primary)
                RollerSkin.PIXEL -> base.copy(lens = s.primary, lensInk = s.onPrimary, edge = s.onSurface)
            }
        }
        // Rollers of a counter: ivory drums, dark ink, edges falling into shadow, a brass window in a bakelite housing.
        WheelLook.MECHANICAL -> RollerColors(Color(0xFF1C1712), Color(0xFF4A423A), Color(0xFFB98B3E),
            lerp(Color(0xFFF4EAD5), s.surface, .1f), Color(0xFF120D09), Color(0xFFB98B3E), Color(0xFFF2D27C), lens = Color(0xFFB98B3E))
        // A display: unlit segments, lit digits with a bloom, glass and scan lines; synthwave splits its colours.
        WheelLook.DIGITAL -> RollerColors(s.primary, s.primary.copy(alpha = .3f), s.primary, Color(0xFF040608), Color.Black, s.primary.copy(alpha = .55f),
            s.primary, lens = s.primary.copy(alpha = .08f), lensInk = lerp(s.primary, Color.White, .38f),
            chroma = theme == AppTheme.SYNTHWAVE, dark = true)
    }
}

/** The little gap between the day and the hour roller. */
@Composable private fun RollerGap(look: WheelLook, colors: RollerColors) {
    Spacer(Modifier.width(if (look == WheelLook.MODERN) 10.dp else 6.dp))
}

/**
 * Build 126 · the housing around the rollers. Retro: a bakelite plate with a bevel, a fine brass inlay and four
 * brass screws. Display: black glass with a faint dot grid, a lit edge and a soft glow around it. Modern: none –
 * the drums sit directly on the card.
 */
private fun Modifier.rollerHousing(look: WheelLook, colors: RollerColors, shape: androidx.compose.ui.graphics.Shape): Modifier = when (look) {
    WheelLook.MODERN -> this
    WheelLook.MECHANICAL -> drawBehind {
        // Build 127: the housing takes the design's own corners (it is the frame itself, not a box inside it).
        val o = shape.createOutline(size, layoutDirection, this)
        drawOutline(o, Brush.verticalGradient(listOf(Color(0xFF3B2C21), Color(0xFF2A1F17), Color(0xFF1B140F))))
        // Bevel: light along the top, dark along the bottom; a fine brass inlay inside.
        drawOutline(o, Brush.verticalGradient(0f to Color.White.copy(alpha = .16f), .12f to Color.Transparent, .88f to Color.Transparent,
            1f to Color.Black.copy(alpha = .45f)), style = Stroke(1.5.dp.toPx()))
        val i = 3.dp.toPx()
        translate(i, i) {
            drawOutline(shape.createOutline(Size(size.width - 2 * i, size.height - 2 * i), layoutDirection, this), colors.frame.copy(alpha = .45f),
                style = Stroke(.8.dp.toPx()))
        }
        // Four brass screws, each slot at its own angle.
        val sr = 2.4.dp.toPx(); val e = 5.2.dp.toPx()
        listOf(Offset(e, e) to 28f, Offset(size.width - e, e) to -14f, Offset(e, size.height - e) to 61f, Offset(size.width - e, size.height - e) to 7f)
            .forEach { (c, a) ->
                drawCircle(Color.Black.copy(alpha = .45f), sr, c + Offset(.5.dp.toPx(), .7.dp.toPx()))
                drawCircle(Brush.radialGradient(listOf(Color(0xFFFBE7A6), Color(0xFFC79A48), Color(0xFF7A5823)), c - Offset(sr * .35f, sr * .35f), sr * 1.4f), sr, c)
                val rad = Math.toRadians(a.toDouble()); val d = Offset((kotlin.math.cos(rad) * sr * .8f).toFloat(), (kotlin.math.sin(rad) * sr * .8f).toFloat())
                drawLine(Color(0xFF4A3412), c - d, c + d, .7.dp.toPx())
            }
    }
    WheelLook.DIGITAL -> drawBehind {
        val o = shape.createOutline(size, layoutDirection, this)
        // A soft glow along the edge, then the glass itself with a dot grid and a lit edge – in the design's own form.
        for (k in 3 downTo 1) drawOutline(o, colors.glow.copy(alpha = .06f), style = Stroke(k * 3f))
        drawOutline(o, Brush.verticalGradient(listOf(Color(0xFF0B0E12), Color(0xFF030405))))
        clipPath(Path().apply { addOutline(o) }) {
            val step = 4.dp.toPx(); var y = step
            while (y < size.height) { var x = step; while (x < size.width) { drawCircle(colors.glow.copy(alpha = .05f), .5.dp.toPx(), Offset(x, y)); x += step }; y += step }
        }
        drawOutline(o, colors.frame.copy(alpha = .6f), style = Stroke(1.dp.toPx()))
    }
}

/** In live time, both dots breathe together. Only their canvas reads the animated value. */
@Composable private fun Colon(look: WheelLook, skin: RollerSkin, colors: RollerColors, blinking: Boolean) {
    val pulse = if (blinking) {
        rememberInfiniteTransition(label = "live-clock").animateFloat(
            initialValue = 1f, targetValue = 0f,
            animationSpec = infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "colon-radius",
        )
    } else null
    Canvas(Modifier.width(14.dp).fillMaxHeight()) {
        drawRollerColon(look, colors, pulse?.value ?: 1f, skin)
    }
}

internal fun DrawScope.drawRollerColon(look: WheelLook, colors: RollerColors, amount: Float, skin: RollerSkin = RollerSkin.MATERIAL) {
    // A zero-radius radial gradient is invalid, and all layers should disappear together.
    val r = 2.7.dp.toPx() * amount
    if (r < .05f) return
    val centerX = size.width / 2f
    fun dot(y: Float) {
        val p = Offset(centerX, y)
        when (look) {
            // Build 130: the dots take the skin's form – square blocks, a glow, a pixel, a chrome bead.
            WheelLook.MODERN -> when (skin) {
                RollerSkin.BRUTAL, RollerSkin.TILE, RollerSkin.SWISS, RollerSkin.INK, RollerSkin.PIXEL ->
                    drawRect(if (skin == RollerSkin.SWISS) colors.accent else colors.ink, p - Offset(r, r), Size(2 * r, 2 * r), alpha = amount)
                RollerSkin.GLASS, RollerSkin.AURORA -> {
                    drawCircle(colors.accent, r * 2.2f, p, alpha = .16f * amount)
                    drawCircle(colors.ink, r, p, alpha = amount)
                }
                RollerSkin.CHROME -> drawCircle(Brush.radialGradient(listOf(Color.White, Color(0xFFB8C0CB), Color(0xFF59616C)),
                    p - Offset(r * .4f, r * .4f), r * 1.5f), r, p, alpha = amount)
                else -> drawCircle(colors.ink, r, p, alpha = amount)
            }
            WheelLook.MECHANICAL -> {
                drawCircle(Color.Black, r, p + Offset(0f, .8.dp.toPx() * amount), alpha = .5f * amount)
                drawCircle(Brush.radialGradient(listOf(Color(0xFFFFF0B8), Color(0xFFC79A48), Color(0xFF6E4F1E)),
                    p - Offset(r * .4f, r * .4f), r * 1.5f), r, p, alpha = amount)
            }
            WheelLook.DIGITAL -> {
                drawCircle(colors.glow, r * 2.4f, p, alpha = .22f * amount)
                drawCircle(colors.glow, r * 1.5f, p, alpha = .35f * amount)
                drawCircle(lerp(colors.glow, Color.White, .45f), r * .9f, p, alpha = amount)
            }
        }
    }
    dot(size.height / 2f - 7.dp.toPx())
    dot(size.height / 2f + 7.dp.toPx())
}

/**
 * One roller. Dragged up, the values below come to the front (later days, later times); every value passed
 * clicks; let go, it flies on a little and settles with a spring. A tap presses it down ([pressDown]) and
 * calls [onTap].
 */
@Composable private fun Roller(state: RollerState, count: Int, bounded: Boolean, look: WheelLook, skin: RollerSkin, colors: RollerColors, height: Dp, modifier: Modifier,
    description: String, valueText: (Int) -> String, label: (Int) -> RollerLabel, onStep: (Int) -> Unit, onTap: () -> Unit,
    later: String, earlier: String, pressDown: Boolean = false, onSettled: () -> Unit = {},
    /** The widest faces that can come up; all faces share the size at which these fit. */
    sample: List<RollerLabel> = listOf(RollerLabel("88")), mainScale: Float = 1f,
    /** Build 126: unlit "88" segments behind the lit digits of a display. */
    ghost: Boolean = false) {
    val quiet = LocalAppSettings.current.reduceMotion
    val scope = rememberCoroutineScope()
    val measurer = rememberTextMeasurer()
    val visuals = LocalAppVisuals.current
    val density = LocalDensity.current
    val view = LocalView.current
    val title = MaterialTheme.typography.titleLarge
    // Build 124: the digits grow with the system font up to the height of one row of the drum, never beyond –
    // at a very large font the values would otherwise run into their neighbours.
    val valueSize = with(density) {
        val cap = rollerRowPx(height.toPx())
        if (title.fontSize.toPx() > cap) cap.toSp() else title.fontSize
    }
    // Build 130: each skin sets its own type – heavy for brutalism, bold for Swiss, light for Japandi, mono for 8-bit.
    val family = if (look == WheelLook.DIGITAL || (look == WheelLook.MODERN && skin == RollerSkin.PIXEL)) FontFamily.Monospace else visuals.headingFamily
    val weight = if (look != WheelLook.MODERN) FontWeight.SemiBold else when (skin) {
        RollerSkin.BRUTAL -> FontWeight.Black
        RollerSkin.SWISS, RollerSkin.PIXEL, RollerSkin.TILE -> FontWeight.Bold
        RollerSkin.JAPANDI -> FontWeight.Normal
        RollerSkin.INK, RollerSkin.EDITORIAL -> FontWeight.Medium
        else -> FontWeight.SemiBold
    }
    val mainStyle = title.copy(fontSize = valueSize * mainScale, lineHeight = TextUnit.Unspecified, fontWeight = weight,
        textAlign = TextAlign.Center, fontFamily = family)
    val prefixStyle = mainStyle.copy(fontSize = valueSize * .64f, fontWeight = if (weight == FontWeight.Black) FontWeight.Bold else FontWeight.Medium)
    val press = remember { Animatable(0f) }
    val stepLatest by rememberUpdatedState(onStep)
    val tapLatest by rememberUpdatedState(onTap)
    val settledLatest by rememberUpdatedState(onSettled)
    val current = state.position.value.roundToInt()
    val shown = if (bounded) current.coerceIn(0, count - 1) else Math.floorMod(current, count)
    // Build 126: every value that clicks in pops a little (a springy swell of the chosen digits, a flash on a display).
    val pop = remember { Animatable(0f) }
    // Build 130: a flung drum ticks for every value it passes, like a real detent – not only once at the end.
    var flying by remember { mutableStateOf(false) }
    LaunchedEffect(shown) {
        if (flying) view.performHapticFeedback(if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.SEGMENT_FREQUENT_TICK else HapticFeedbackConstants.CLOCK_TICK)
        if (!quiet) { pop.snapTo(1f); pop.animateTo(0f, spring(dampingRatio = .42f, stiffness = 520f)) }
    }
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
                    state.dragVelocity = 0f
                    state.dragging = true; tracker.resetTracking()
                    // The finger moves the drum exactly: one value is one row of the drawn cylinder.
                    rowPx = rollerRowPx(size.height.toFloat())
                    lastIndex = state.position.value.roundToInt()
                    scope.launch { state.position.stop() }
                },
                onDragEnd = {
                    val v = -tracker.calculateVelocity().y / rowPx
                    scope.launch {
                        // Build 130 · glides out like a real drum: a fast flick runs on further and comes to rest without
                        // bouncing (critically damped), a slow release clicks into the nearest value with a short spring.
                        val glide = (abs(v) / 14f).coerceIn(0f, 1f)
                        var to = (state.position.value + v * (.12f + .22f * glide)).roundToInt().toFloat()
                        if (bounded) to = to.coerceIn(0f, count - 1f)
                        val passed = to.roundToInt() - lastIndex
                        if (passed != 0) { lastIndex = to.roundToInt(); stepLatest(passed) }
                        state.dragging = false; state.dragVelocity = 0f
                        if (quiet) state.position.snapTo(to)
                        else try {
                            flying = abs(to - state.position.value) > 1.5f
                            state.position.animateTo(to, spring(dampingRatio = .84f + .16f * glide, stiffness = 420f - 330f * glide), initialVelocity = v)
                        } finally { flying = false }
                        // A turn beyond the first or last minute is brought back to the chosen time.
                        settledLatest()
                    }
                },
                onDragCancel = { state.dragging = false; scope.launch { state.position.animateTo(state.position.value.roundToInt().toFloat(), spring()) } },
            ) { change, dy ->
                change.consume()
                tracker.addPosition(change.uptimeMillis, change.position)
                // The lens follows the finger's speed, smoothed (values per second; positive: later values come up).
                state.dragVelocity = state.dragVelocity * .7f + (-tracker.calculateVelocity().y / rowPx) * .3f
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
        // Build 130 · optical centre: the middle of the real digit glyphs of this typeface (their ink, not the line box
        // with ascent and descent). Typefaces differ a lot here; a fixed share put the digits low in rounded faces.
        val center = remember(mainStyle) { digitCenter(measurer.measure("14", mainStyle, maxLines = 1, softWrap = false), mainPx) }
        fun face(l: RollerLabel) = RollerFace(l.prefix.takeIf { it.isNotEmpty() }?.let { layout(it, prefixStyle) }, layout(l.text, mainStyle), gap, mainPx, l.sunday, center)
        val ghostFace = if (ghost && look == WheelLook.DIGITAL) face(RollerLabel("88")) else null
        // Build 127: lens and drums take the design's corners (square, cut, pixel, round …); flat designs get a flat lens.
        val shapeOf: (Dp) -> androidx.compose.ui.graphics.Shape = { visuals.corner(it) }
        val flat = visuals.language.spec.shadow == dev.ambivalenton.openv.data.model.LanguageShadow.NONE || visuals.theme?.themeLanguage?.flat == true
        Canvas(Modifier.fillMaxSize()) {
            // One size for all faces of this roller: the widest possible face fits between the edges.
            val fit = fits.getOrPut(size.width.roundToInt()) {
                val room = size.width - 12.dp.toPx()
                (room / sample.maxOf { face(it).width }).coerceIn(.3f, 1f)
            }
            drawRoller(state.position.value, if (quiet) 0f else state.velocity, pop.value, count, bounded, look, skin, colors, fit, ghostFace, shapeOf, flat) { index -> face(label(index)) }
        }
    }
}

/**
 * The visual middle of the digits relative to their baseline (negative: above it), measured on the rendered ink of
 * digits with a flat top and foot ("14"). Glyph outlines are no measure here: the bounds of a path include the
 * control points of its curves, which reach beyond the ink of round terminals (rounded typefaces). Falls back to
 * a typical share of the font size when nothing could be rendered.
 */
internal fun digitCenter(layout: TextLayoutResult, fontPx: Float): Float {
    val fallback = -fontPx * .36f
    val w = layout.size.width; val h = layout.size.height
    if (w <= 0 || h <= 0) return fallback
    return runCatching {
        val image = androidx.compose.ui.graphics.ImageBitmap(w, h)
        androidx.compose.ui.graphics.drawscope.CanvasDrawScope().draw(androidx.compose.ui.unit.Density(1f),
            androidx.compose.ui.unit.LayoutDirection.Ltr, androidx.compose.ui.graphics.Canvas(image), Size(w.toFloat(), h.toFloat())) {
            drawText(layout, Color.Black)
        }
        val pixels = IntArray(w * h); image.readPixels(pixels)
        // Coverage per row, so anti-aliased edges count with their share (sub-pixel exact).
        val coverage = FloatArray(h) { y -> var m = 0; for (x in 0 until w) m = maxOf(m, pixels[y * w + x] ushr 24); m / 255f }
        val top = coverage.indexOfFirst { it > 0f }; val bottom = coverage.indexOfLast { it > 0f }
        if (top < 0 || bottom - top < fontPx * .3f) fallback
        else {
            val inkTop = top + (1f - coverage[top]); val inkBottom = bottom + coverage[bottom]
            (inkTop + inkBottom) / 2f - layout.firstBaseline
        }
    }.getOrDefault(fallback)
}

/** One value laid out: the smaller word and the main text on one baseline. [center]: see [digitCenter]. */
internal class RollerFace(val prefix: TextLayoutResult?, val main: TextLayoutResult, val gap: Float, val mainPx: Float, val sunday: Boolean,
    val center: Float = -mainPx * .36f) {
    val width: Float get() = (prefix?.let { it.size.width + gap } ?: 0f) + main.size.width
}

/**
 * Draws the drum: the values around [position] on a cylinder, the look's lens/window, shading and edges.
 * Build 126 · [velocity] (values per second) makes the modern lens liquid – it stretches with the turn, lags
 * behind it and wobbles back when the drum springs into place; [pop] swells the value that just clicked in.
 * Build 130 · the values are drawn twice, split by the lens: in the lens's own ink inside it and softly outside,
 * so a value changes colour exactly where it crosses the lens edge instead of switching at once; values grow
 * continuously towards the lens; every skin has its own lens and light ([drawLensBody], [drawLensOverlay]).
 */
private fun DrawScope.drawRoller(position: Float, velocity: Float, pop: Float, count: Int, bounded: Boolean, look: WheelLook, skin: RollerSkin,
    colors: RollerColors, fit: Float, ghost: RollerFace?, shapeOf: (Dp) -> androidx.compose.ui.graphics.Shape, flat: Boolean, text: (Int) -> RollerFace) {
    val w = size.width; val h = size.height; val cy = h / 2f
    val radius = h * DRUM_RADIUS
    val row = rollerRowPx(h, ROW_ANGLE)
    val speed = kotlin.math.tanh(abs(velocity) / 7f)
    val lean = kotlin.math.tanh(velocity / 7f)
    val modern = look == WheelLook.MODERN
    val liquid = if (modern) skin.liquid else 0f
    // The selection all looks share: a liquid lens (stretched along the turn, narrower for it, lagging behind),
    // a rigid one for the crisp skins; clay squishes when a value lands.
    val squish = if (modern && skin == RollerSkin.CLAY) pop else 0f
    val lensH = row * (1.06f + .3f * speed * liquid + .05f * pop * liquid - .1f * squish)
    val lensW = (w - 4.dp.toPx() - 5.dp.toPx() * speed * liquid) * (1f + .05f * squish)
    val lens = androidx.compose.ui.geometry.Rect(Offset((w - lensW) / 2f, cy + row * .12f * lean * liquid - lensH / 2f), Size(lensW, lensH))
    val lensShape: androidx.compose.ui.graphics.Shape = when {
        !modern -> shapeOf(5.dp)
        skin == RollerSkin.PIXEL -> SteppedShape(3.dp)
        skin == RollerSkin.ORGANIC -> BlobShape(position, speed, pop)
        skin == RollerSkin.SWISS || skin == RollerSkin.INK -> shapeOf(4.dp)
        else -> shapeOf(16.dp + 6.dp * speed * liquid)
    }
    val lensOutline = lensShape.createOutline(lens.size, layoutDirection, this)
    val lensPath = Path().apply { addOutline(lensOutline); translate(lens.topLeft) }
    when (look) {
        WheelLook.MODERN -> drawLensBody(skin, lens, lensOutline, colors, position, speed, lean, pop, flat, row, lensShape)
        WheelLook.MECHANICAL -> drawOutline(shapeOf(5.dp).createOutline(size, layoutDirection, this), colors.drum)
        WheelLook.DIGITAL -> {
            drawOutline(shapeOf(5.dp).createOutline(size, layoutDirection, this), Brush.verticalGradient(listOf(Color(0xFF07090C), colors.drum, Color(0xFF07090C))))
            // The faint lens of the display: a band of the colour behind the lit row.
            drawRect(colors.lens, Offset(0f, cy - row / 2f), Size(w, row))
            // Unlit segments behind the digits, as on a real display.
            ghost?.let { g ->
                scale(fit, fit, Offset(w / 2f, cy)) {
                    drawText(g.main, colors.glow.copy(alpha = .07f), Offset((w - g.width) / 2f, cy - g.center - g.main.firstBaseline))
                }
            }
        }
    }
    /** The values around the position; [inside]: the part within the lens, in the lens's ink. */
    fun values(inside: Boolean, shift: Float = 0f, fade: Float = 1f) {
        val first = floor(position).toInt() - 3; val last = first + 7
        for (index in first..last) {
            if (bounded && index !in 0 until count) continue
            val offset = index - position
            val angle = offset * ROW_ANGLE
            if (abs(angle) >= 88f) continue
            val rad = angle * PI.toFloat() / 180f
            val y = cy + radius * sin(rad) + shift
            val squash = cos(rad)
            val front = squash * squash
            val near = (1f - abs(offset)).coerceIn(0f, 1f)
            val face = text(index)
            val neighbour = when {
                look == WheelLook.MECHANICAL -> colors.ink.copy(alpha = .4f + .6f * front)
                look == WheelLook.DIGITAL -> colors.faint.copy(alpha = colors.faint.alpha * front)
                // E-ink knows a few grey levels, no smooth fade.
                skin == RollerSkin.INK -> colors.ink.copy(alpha = if (front > .8f) .62f else if (front > .4f) .36f else .16f)
                else -> lerp(colors.faint, colors.ink, .2f).copy(alpha = .2f + .7f * front)
            }
            val ink = (if (inside && look != WheelLook.MECHANICAL) colors.lensInk else neighbour).let { it.copy(alpha = it.alpha * fade) }
            // The word in front: the accent on the chosen day of a modern roller, a calendar's red on Sundays
            // of a mechanical one, otherwise a little softer than the date.
            val wordInk = when {
                look == WheelLook.MECHANICAL && face.sunday -> Color(0xFFA3261C).copy(alpha = ink.alpha)
                modern && inside -> ink.copy(alpha = ink.alpha * .8f)
                else -> ink.copy(alpha = ink.alpha * .86f)
            }
            // Values grow continuously towards the lens (and swell when they click in); glass magnifies what lies under it.
            val grow = (if (modern) skin.neighbour + (1f - skin.neighbour) * near else 1f) * (1f + .07f * pop * near) *
                (if (inside && modern && skin == RollerSkin.GLASS) 1.04f else 1f)
            // On a drum the far values are a touch narrower as well (perspective), not only flatter.
            val narrow = .9f + .1f * front
            fun part(l: TextLayoutResult, x: Float, top: Float, color: Color) {
                if (look == WheelLook.MECHANICAL) drawText(l, Color.White.copy(alpha = .5f * front), Offset(x, top + 1.dp.toPx()))
                if (modern && skin == RollerSkin.CHROME && inside) drawText(l, Color.White.copy(alpha = .7f), Offset(x, top + 1.dp.toPx()))
                if (look == WheelLook.DIGITAL && inside) {
                    // Bloom: the lit digits spill light around them, brighter for a moment when they click in.
                    val strength = 1f + 1.3f * pop
                    for (k in 0 until 8) {
                        val a = k * PI.toFloat() / 4f
                        val near2 = Offset(cos(a), sin(a)) * 1.5.dp.toPx(); val far = near2 * 2.2f
                        drawText(l, colors.glow.copy(alpha = (.1f * strength).coerceAtMost(.35f) * fade), Offset(x, top) + far)
                        drawText(l, colors.glow.copy(alpha = (.2f * strength).coerceAtMost(.55f) * fade), Offset(x, top) + near2)
                    }
                    if (colors.chroma) {
                        drawText(l, Color(0xFFFF3DCB).copy(alpha = .55f * fade), Offset(x - 1.3.dp.toPx(), top))
                        drawText(l, Color(0xFF39F3FF).copy(alpha = .55f * fade), Offset(x + 1.3.dp.toPx(), top))
                    }
                }
                drawText(l, color, Offset(x, top))
            }
            scale(fit * grow * narrow, squash.coerceAtLeast(.05f) * fit * grow, Offset(w / 2f, y)) {
                // Optically centred on the row: the middle of the digit glyphs sits on it. A mechanical detent clicks:
                // the chosen row jumps a hair down and back; the brutalist stamp presses the chosen value with its lens.
                val stamp = if (modern && skin == RollerSkin.BRUTAL && inside) (1f - (1f - .7f * pop).coerceIn(0f, 1f)) * 4.dp.toPx() else 0f
                val baseline = y - face.center + stamp + (if (look == WheelLook.MECHANICAL && near > .5f) pop * 1.2.dp.toPx() else 0f)
                var x = (w - face.width) / 2f + stamp
                face.prefix?.let { p -> part(p, x, baseline - p.firstBaseline, wordInk); x += p.size.width + face.gap }
                part(face.main, x, baseline - face.main.firstBaseline, ink)
            }
        }
    }
    clipRect {
        if (look == WheelLook.MECHANICAL) values(inside = false)
        else {
            clipPath(lensPath, androidx.compose.ui.graphics.ClipOp.Difference) {
                // A fast turn leaves a faint trail behind the values (motion blur), only where the skin is soft.
                if (modern && skin.trail && speed > .08f) {
                    val trail = -kotlin.math.sign(velocity) * row * .2f * speed
                    values(inside = false, shift = trail, fade = .3f * speed)
                }
                values(inside = false)
            }
            clipPath(lensPath) { values(inside = true) }
        }
    }
    when (look) {
        WheelLook.MECHANICAL -> drawMechanicalLight(position, row, radius, colors, shapeOf)
        WheelLook.DIGITAL -> {
            // Scan lines, a slightly curved screen (dark corners), glass catching light at the top, glowing rails.
            var y = 0f
            while (y < h) { drawLine(Color.Black.copy(alpha = .28f), Offset(0f, y), Offset(w, y), 1f); y += 3.dp.toPx() }
            drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .55f)), Offset(w / 2f, cy), maxOf(w, h) * .75f), Offset.Zero, size)
            drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = .07f), Color.Transparent), endY = h * .4f), Offset.Zero, Size(w, h * .4f))
            for (ry in listOf(cy - row / 2f, cy + row / 2f)) {
                drawLine(colors.glow.copy(alpha = .18f + .2f * pop), Offset(0f, ry), Offset(w, ry), 4.dp.toPx())
                drawLine(colors.frame, Offset(0f, ry), Offset(w, ry), 1.dp.toPx())
            }
        }
        WheelLook.MODERN -> {
            drawLensOverlay(skin, lens, lensOutline, colors, position, speed, pop, row, radius, text(position.roundToInt().let {
                if (bounded) it.coerceIn(0, count - 1) else it }), fit)
            // Depth: the drum turns away into the frame.
            drawRect(Brush.verticalGradient(0f to colors.shade.copy(alpha = .94f), .24f to colors.shade.copy(alpha = 0f),
                .76f to colors.shade.copy(alpha = 0f), 1f to colors.shade.copy(alpha = .94f)), Offset.Zero, size)
        }
    }
}

/** The retro counter's light: cylinder shading, toothed side wheels turning with the drum, the brass window, a glass sheen. */
private fun DrawScope.drawMechanicalLight(position: Float, row: Float, radius: Float, colors: RollerColors,
    shapeOf: (Dp) -> androidx.compose.ui.graphics.Shape) {
    val w = size.width; val h = size.height; val cy = h / 2f
    drawOutline(shapeOf(5.dp).createOutline(size, layoutDirection, this), Brush.verticalGradient(0f to colors.shade.copy(alpha = .82f), .16f to colors.shade.copy(alpha = .32f),
        .38f to Color.White.copy(alpha = .26f), .5f to Color.White.copy(alpha = .06f), .62f to Color.Transparent,
        .84f to colors.shade.copy(alpha = .36f), 1f to colors.shade.copy(alpha = .85f)))
    val rim = 5.dp.toPx()
    val tooth = row / 4f
    val phase = ((position * row) % tooth + tooth) % tooth
    for (side in listOf(0f, w - rim)) {
        drawRect(Brush.horizontalGradient(listOf(Color(0xFF6B5230), Color(0xFFA9864A), Color(0xFF5A4326)), startX = side, endX = side + rim),
            Offset(side, 0f), Size(rim, h))
        var ty = -phase
        while (ty < h) {
            val c = cos(kotlin.math.asin(((ty - cy) / radius).coerceIn(-1f, 1f)))
            drawLine(Color.White.copy(alpha = .35f * c), Offset(side, ty), Offset(side + rim, ty), 1.dp.toPx())
            drawLine(Color.Black.copy(alpha = .5f * c), Offset(side, ty + 1.2.dp.toPx()), Offset(side + rim, ty + 1.2.dp.toPx()), 1.dp.toPx())
            ty += tooth
        }
        drawRect(Brush.verticalGradient(0f to Color.Black.copy(alpha = .7f), .2f to Color.Transparent, .8f to Color.Transparent,
            1f to Color.Black.copy(alpha = .7f)), Offset(side, 0f), Size(rim, h))
    }
    val win = row * 1.06f; val bar = 2.6.dp.toPx()
    val brass = Brush.verticalGradient(listOf(Color(0xFFFCE6A0), Color(0xFFC9A04F), Color(0xFF7D5C25)))
    val top = cy - win / 2f - bar; val bottom = cy + win / 2f
    drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .3f), Color.Transparent), startY = top + bar, endY = top + bar + 5.dp.toPx()),
        Offset(0f, top + bar), Size(w, 5.dp.toPx()))
    translate(top = top) { drawRect(brass, Offset.Zero, Size(w, bar)) }
    translate(top = bottom) { drawRect(brass, Offset.Zero, Size(w, bar)) }
    drawRect(Color.Black.copy(alpha = .35f), Offset(0f, bottom + bar), Size(w, 1.dp.toPx()))
    drawRect(Brush.linearGradient(0f to Color.Transparent, .42f to Color.White.copy(alpha = .0f), .5f to Color.White.copy(alpha = .16f),
        .58f to Color.Transparent, 1f to Color.Transparent, start = Offset(0f, 0f), end = Offset(w, h)), Offset.Zero, size)
}
