// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components

import android.animation.ValueAnimator
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.progressSemantics
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import dev.ambivalenton.openv.data.model.BriefStepKind
import dev.ambivalenton.openv.data.model.effectiveDeparture
import dev.ambivalenton.openv.data.model.reduceMotion
import dev.ambivalenton.openv.data.model.PinnedTrackingStatus
import dev.ambivalenton.openv.localization.tr
import dev.ambivalenton.openv.ui.theme.LocalAppSettings
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

/*
 * Build 123 · the progress of a pinned journey as a living wave (Material 3 Expressive). Its mood follows the
 * situation: a calm, breathing line while there is time before boarding; a thick red line beating fast when it
 * is time to board; a thin, slow green flow while the ride is on time; amber and more restless when it is late;
 * short steps like footprints while walking, quicker and orange when the change is tight; a jagged, flickering
 * line when something broke; a wave that calms down and lands on the destination; a still dashed line when the
 * companion is paused. Every change of mood springs (thickness, height, colour), the phase never jumps.
 */

/** The situations the wave shows. */
enum class WaveMood { WAITING, BOARD_NOW, RIDING, RIDING_LATE, WALKING, HURRY, ARRIVING, DISRUPTED, PAUSED }

/**
 * The wave's mood for the step in focus. [disrupted]: a cancelled ride, a missed boarding or a change the
 * forecasts rule out; [imminent]: the card's own boarding alert; [slackMinutes]: time left for the change ahead
 * while walking (null without one); [progress] of the whole journey.
 */
fun journeyWaveMood(kind: BriefStepKind, status: PinnedTrackingStatus, imminent: Boolean, disrupted: Boolean,
    delayMinutes: Int, slackMinutes: Int?, progress: Float): WaveMood = when {
    status == PinnedTrackingStatus.PAUSED -> WaveMood.PAUSED
    disrupted -> WaveMood.DISRUPTED
    imminent -> WaveMood.BOARD_NOW
    kind == BriefStepKind.ARRIVE || progress >= .92f -> WaveMood.ARRIVING
    kind == BriefStepKind.WALK -> if (slackMinutes != null && slackMinutes <= 3) WaveMood.HURRY else WaveMood.WALKING
    kind == BriefStepKind.BOARD -> WaveMood.WAITING
    delayMinutes > 0 -> WaveMood.RIDING_LATE
    else -> WaveMood.RIDING
}

private enum class WavePattern { SMOOTH, PULSE, STEPS, JAGGED, STILL }

/**
 * How a mood looks and moves: stroke, wave height and length, seconds per wavelength, glow and pattern; how far
 * the wave puffs up and draws together ([breath], 0..1) and how long one breath takes ([breathPeriod], s); how
 * strongly swells run along it ([swell], 0..1 – some stretches grow while others calm down).
 */
private data class WaveLook(val color: Color, val thickness: Dp, val amplitude: Dp, val wavelength: Dp, val period: Float,
    val glow: Float, val pattern: WavePattern, val label: String, val breath: Float, val breathPeriod: Float, val swell: Float)

@Composable private fun waveLook(mood: WaveMood): WaveLook {
    val s = MaterialTheme.colorScheme
    val mono = LocalAppSettings.current.monochrome
    val dark = s.background.luminance() < .5f
    fun tone(c: Color, weight: Float = .7f) = if (mono) (if (dark) Color.White else Color.Black) else lerp(s.primary, c, weight)
    val green = tone(Color(0xFF2E9D57)); val amber = tone(Color(0xFFE8890C), .8f); val red = tone(Color(0xFFD32F2F), .9f)
    return when (mood) {
        // Calm, deep breaths while there is time.
        WaveMood.WAITING -> WaveLook(s.primary, 3.dp, 4.dp, 40.dp, 3.6f, .10f, WavePattern.SMOOTH, tr("Warten auf den Einstieg"), .4f, 4.5f, .35f)
        // Thick and red, beating like a heart: now.
        WaveMood.BOARD_NOW -> WaveLook(red, 6.5.dp, 5.5.dp, 26.dp, .85f, .24f, WavePattern.PULSE, tr("Jetzt einsteigen"), .55f, 1.3f, .2f)
        // Thin, slow, green swells: all is well.
        WaveMood.RIDING -> WaveLook(green, 2.5.dp, 3.5.dp, 50.dp, 6.5f, .07f, WavePattern.SMOOTH, tr("Unterwegs, pünktlich"), .25f, 7f, .45f)
        // Amber and restless: an uneven breath, stronger swells.
        WaveMood.RIDING_LATE -> WaveLook(amber, 3.5.dp, 4.5.dp, 34.dp, 3f, .12f, WavePattern.SMOOTH, tr("Unterwegs, verspätet"), .35f, 2.7f, .55f)
        // Short steps, bobbing with every step.
        WaveMood.WALKING -> WaveLook(tone(s.tertiary, .55f), 3.dp, 3.dp, 18.dp, 1.3f, .08f, WavePattern.STEPS, tr("Zu Fuß unterwegs"), .2f, 1.3f, .2f)
        WaveMood.HURRY -> WaveLook(lerp(amber, red, .5f), 4.5.dp, 4.5.dp, 20.dp, .9f, .18f, WavePattern.PULSE, tr("Umstieg knapp"), .45f, 1.1f, .3f)
        // Long, calm waves that flatten into the destination.
        WaveMood.ARRIVING -> WaveLook(green, 3.dp, 3.5.dp, 56.dp, 5f, .10f, WavePattern.SMOOTH, tr("Gleich am Ziel"), .3f, 5.5f, .3f)
        WaveMood.DISRUPTED -> WaveLook(red, 3.5.dp, 4.5.dp, 22.dp, 2.4f, .16f, WavePattern.JAGGED, tr("Störung"), .3f, 1.7f, .5f)
        // A still, dashed wave: nothing moves while the companion rests.
        WaveMood.PAUSED -> WaveLook(s.onSurfaceVariant, 2.dp, 2.dp, 44.dp, 16f, 0f, WavePattern.STILL, tr("Pausiert"), 0f, 6f, 0f)
    }
}

/** Height of the wave's slot (fixed, so a change of mood never moves the card). */
private val WaveHeight = 32.dp

/** A heartbeat: two quick beats, then rest ([t] in beats). */
internal fun heartbeat(t: Float): Float {
    val x = t - floor(t)
    fun beat(at: Float) = exp(-((x - at) * 14f).pow(2))
    return (beat(.12f) + .7f * beat(.32f)).coerceIn(0f, 1f)
}

/** Seconds between two showings of the progress (and after a change of mood or progress, at once). */
internal const val WAVE_REVEAL_EVERY = 10f

/**
 * Build 124 · how much the wave shows the progress [t] seconds after a showing began (0..1): it rises quickly,
 * holds for a little more than two seconds and fades; in between the wave is simply a wave.
 */
internal fun progressReveal(t: Float): Float = when {
    t < 0f -> 0f
    t < .8f -> 1f - (1f - t / .8f).pow(3)
    t < 3.2f -> 1f
    t < 4.2f -> ((4.2f - t) / 1f).let { it * it * (3f - 2f * it) }
    else -> 0f
}

/** The wave's height at [x] px for [jagged] (a sharper zigzag) or a sine with a slowly drifting overtone, -1..1. */
private fun waveShape(x: Float, wavelength: Float, phase: Float, drift: Float, jagged: Boolean): Float {
    val u = x / wavelength * 2f * PI.toFloat() - phase
    if (!jagged) return (sin(u) + .28f * sin(1.9f * u + drift)) / 1.28f
    // A zigzag with a small random-looking kink: something is not running smoothly.
    val t = (u / (2f * PI.toFloat())).let { it - floor(it) }
    val zig = if (t < .5f) t * 4f - 1f else 3f - t * 4f
    return zig * .85f + .15f * sin(u * 3.3f)
}

/**
 * Build 123/124 · a living wave across the whole width in the mood of the moment (Material 3 Expressive). It
 * breathes – puffs up and draws together – with swells running along it; how fast, how thick and in which colour
 * depends on the situation. Every few seconds (and whenever the mood or the progress changes) it shows the
 * journey's [progress] (0..1): a gleam runs up to where the journey stands, a dot lands there, the stretch still
 * ahead fades back a little; then it is just a wave again. With reduced motion the wave stands still and shows
 * the progress all the time.
 */
@Composable fun JourneyWave(progress: Float, mood: WaveMood, modifier: Modifier = Modifier) {
    val quiet = LocalAppSettings.current.reduceMotion || !ValueAnimator.areAnimatorsEnabled()
    val look = waveLook(mood)
    val springy = spring<Float>(dampingRatio = .5f, stiffness = Spring.StiffnessLow)
    val springyDp = spring<Dp>(dampingRatio = .5f, stiffness = Spring.StiffnessLow)
    val color by animateColorAsState(look.color, spring(stiffness = Spring.StiffnessLow), label = "wave-color")
    val thickness by animateDpAsState(look.thickness, springyDp, label = "wave-thickness")
    val amplitude by animateDpAsState(look.amplitude, springyDp, label = "wave-amplitude")
    val wavelength by animateDpAsState(look.wavelength, spring(stiffness = Spring.StiffnessVeryLow), label = "wave-length")
    val glow by animateFloatAsState(look.glow, springy, label = "wave-glow")
    val breath by animateFloatAsState(if (quiet) 0f else look.breath, springy, label = "wave-breath")
    val swell by animateFloatAsState(if (quiet) 0f else look.swell, springy, label = "wave-swell")
    val landingCalm by animateFloatAsState(if (mood == WaveMood.ARRIVING) 1f else 0f, springy, label = "wave-landing")
    val shown by animateFloatAsState(progress.coerceIn(0f, 1f), spring(dampingRatio = .8f, stiffness = Spring.StiffnessVeryLow), label = "wave-progress")
    val period by rememberUpdatedState(look.period)
    val breathPeriod by rememberUpdatedState(look.breathPeriod)
    // Continuous phases: a change of tempo never makes the wave or its breath jump.
    val phase = remember { mutableFloatStateOf(0f) }
    val breathing = remember { mutableFloatStateOf(0f) }
    val time = remember { mutableFloatStateOf(0f) }
    val revealFrom = remember { mutableFloatStateOf(0f) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val moving = !quiet && look.pattern != WavePattern.STILL
    LaunchedEffect(moving, lifecycle) {
        if (!moving) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var last = withInfiniteAnimationFrameNanos { it }
            while (true) {
                val now = withInfiniteAnimationFrameNanos { it }
                val dt = ((now - last) / 1e9f).coerceIn(0f, .1f); last = now
                val turn = 2000f * PI.toFloat()
                phase.floatValue = (phase.floatValue + dt * 2f * PI.toFloat() / period.coerceAtLeast(.2f)) % turn
                breathing.floatValue = (breathing.floatValue + dt * 2f * PI.toFloat() / breathPeriod.coerceAtLeast(.2f)) % turn
                time.floatValue += dt
                if (time.floatValue - revealFrom.floatValue > WAVE_REVEAL_EVERY) revealFrom.floatValue = time.floatValue
            }
        }
    }
    // A new mood or a step forward shows the progress at once (unless it is being shown right now).
    LaunchedEffect(mood, (progress * 40).toInt()) {
        val since = time.floatValue - revealFrom.floatValue
        if (since > 3.2f) revealFrom.floatValue = time.floatValue
    }
    // The dot lands with a little bounce on every change of mood.
    val landing = remember { Animatable(1f) }
    LaunchedEffect(mood) { if (!quiet) { landing.snapTo(1.6f); landing.animateTo(1f, spring(dampingRatio = .35f, stiffness = 260f)) } }
    val track = MaterialTheme.colorScheme.onSurfaceVariant
    val pattern = look.pattern
    Canvas(modifier.fillMaxWidth().height(WaveHeight).progressSemantics(progress.coerceIn(0f, 1f))
        .semantics { stateDescription = look.label }.testTag("journey-wave-${mood.name.lowercase()}")) {
        val cy = size.height / 2f
        val t = time.floatValue
        val sinceReveal = t - revealFrom.floatValue
        val reveal = if (quiet || pattern == WavePattern.STILL) 1f else progressReveal(sinceReveal)
        // The breath: a heartbeat for the urgent moods, an uneven one when late, otherwise a calm sine.
        val b = when {
            pattern == WavePattern.PULSE -> heartbeat(breathing.floatValue / (2f * PI.toFloat())) * 2f - .4f
            mood == WaveMood.RIDING_LATE -> .7f * sin(breathing.floatValue) + .3f * sin(breathing.floatValue * 2.7f + 1.3f)
            else -> sin(breathing.floatValue)
        }
        val width = thickness.toPx() * (1f + .3f * breath * b)
        val room = cy - width / 2f - 1.dp.toPx()
        val amp = (amplitude.toPx() * (1f + breath * b)).coerceIn(0f, room)
        val lambda = wavelength.toPx()
        val stop = size.width - 4.dp.toPx()
        val drift = t * .7f
        val jagged = pattern == WavePattern.JAGGED
        fun yAt(x: Float): Float {
            // Swells run along the wave: some stretches grow while others calm down.
            val swellFactor = 1f - swell * .5f * (1f + sin(x / (3.3f * lambda) * 2f * PI.toFloat() - t * 1.1f))
            // Soft ends; arriving, the wave flattens over a longer stretch into the destination.
            val envelope = (x / (lambda * .5f)).coerceIn(0f, 1f) * ((stop - x) / (lambda * (.6f + 1.2f * landingCalm))).coerceIn(0f, 1f)
            return cy + amp * envelope * swellFactor * waveShape(x, lambda, phase.floatValue, drift, jagged)
        }
        val path = Path()
        val step = 1.5.dp.toPx()
        var x = 0f
        while (x < stop) { if (x == 0f) path.moveTo(x, yAt(x)) else path.lineTo(x, yAt(x)); x += step }
        path.lineTo(stop, yAt(stop))
        val flicker = if (jagged) .75f + .25f * sin(t * 9f) * sin(t * 2.3f) else 1f
        val effect = when (pattern) {
            // Steps like footprints, moving forward with the walk.
            WavePattern.STEPS -> PathEffect.dashPathEffect(floatArrayOf(width * 2.2f, width * 1.6f), -phase.floatValue / (2f * PI.toFloat()) * lambda)
            WavePattern.STILL -> PathEffect.dashPathEffect(floatArrayOf(width * 3f, width * 2f), 0f)
            else -> null
        }
        val head = stop * shown
        if (glow > 0f) drawPath(path, color.copy(alpha = glow * flicker * (1f + .5f * breath * b).coerceAtLeast(.3f)),
            style = Stroke(width * 3.2f, cap = StrokeCap.Round))
        // The stretch travelled in full colour; the one ahead a little softer, and clearly back while the progress shows.
        clipRect(right = head) { drawPath(path, color.copy(alpha = flicker), style = Stroke(width, cap = StrokeCap.Round, pathEffect = effect)) }
        clipRect(left = head) {
            drawPath(path, color.copy(alpha = flicker * (.78f - .5f * reveal)),
                style = Stroke(width * (1f - .25f * reveal), cap = StrokeCap.Round, pathEffect = effect))
        }
        // The destination: a small stop at the end, filled when it is near.
        val end = Offset(stop, cy)
        drawCircle(if (mood == WaveMood.ARRIVING) color else track.copy(alpha = .45f), 3.dp.toPx(), end)
        if (reveal <= .01f) return@Canvas
        // A gleam runs up to where the journey stands.
        if (!quiet && sinceReveal in 0f..1.3f && head > 4.dp.toPx()) {
            val k = sinceReveal / 1.3f
            val gx = head * (1f - (1f - k).pow(2))
            val band = 34.dp.toPx()
            val gleam = lerp(color, Color.White, .65f).copy(alpha = .85f * (1f - k * k))
            clipRect(right = head + width) {
                drawPath(path, Brush.horizontalGradient(listOf(Color.Transparent, gleam, Color.Transparent), startX = gx - band, endX = gx + band),
                    style = Stroke(width * 1.25f, cap = StrokeCap.Round))
            }
        }
        // The dot: on the wave, where the journey stands now.
        val dot = Offset(head.coerceIn(width, stop), yAt(head.coerceIn(width, stop)))
        val pop = reveal * landing.value
        drawCircle(color.copy(alpha = .22f * reveal), (width * 1.6f + 3.5.dp.toPx()) * pop, dot)
        drawCircle(color, (width * .85f + 2.dp.toPx()) * pop.coerceAtMost(1.2f), dot)
        drawCircle(lerp(color, Color.White, .8f).copy(alpha = reveal), (width * .3f + .8.dp.toPx()) * pop.coerceAtMost(1.2f), dot)
    }
}

/** The wave's mood for a pinned journey right now (cards and floating card use the same). */
fun pinnedWaveMood(session: dev.ambivalenton.openv.data.model.PinnedJourneySession, focus: dev.ambivalenton.openv.data.model.JourneyFocus,
    brief: dev.ambivalenton.openv.data.model.PinnedJourneyBrief, status: PinnedTrackingStatus, imminent: Boolean, now: Long): WaveMood {
    val segments = session.trip.segments
    val disrupted = session.trip.cancelled || session.missedBoarding != null ||
        dev.ambivalenton.openv.data.model.unreachablePinnedTransfer(session.trip, session.routing.ignoreWalking, session.routing.walkingSpeed) != null
    // Walking to a ride: the minutes left once the rest of the walk is done.
    val slack = focus.legIndex?.let { index ->
        val walk = segments.getOrNull(index)?.takeIf { it.isWalk } ?: return@let null
        val ride = segments.drop(index + 1).firstOrNull { !it.isWalk } ?: return@let null
        val walkLeft = (walk.endTime - maxOf(now, walk.startTime)).coerceAtLeast(0)
        ((ride.effectiveDeparture() - now - walkLeft) / 60_000L).toInt()
    }
    return journeyWaveMood(brief.kind, status, imminent, disrupted, brief.delayMinutes, slack, focus.progress)
}
