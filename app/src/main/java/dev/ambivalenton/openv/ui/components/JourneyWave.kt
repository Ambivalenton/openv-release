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

/** How a mood looks: stroke, wave height and length, seconds per wavelength, glow and pattern. */
private data class WaveLook(val color: Color, val thickness: Dp, val amplitude: Dp, val wavelength: Dp, val period: Float,
    val glow: Float, val pattern: WavePattern, val label: String)

@Composable private fun waveLook(mood: WaveMood): WaveLook {
    val s = MaterialTheme.colorScheme
    val mono = LocalAppSettings.current.monochrome
    val dark = s.background.luminance() < .5f
    fun tone(c: Color, weight: Float = .7f) = if (mono) (if (dark) Color.White else Color.Black) else lerp(s.primary, c, weight)
    val green = tone(Color(0xFF2E9D57)); val amber = tone(Color(0xFFE8890C), .8f); val red = tone(Color(0xFFD32F2F), .9f)
    return when (mood) {
        WaveMood.WAITING -> WaveLook(s.primary, 3.dp, 3.dp, 36.dp, 3.4f, .10f, WavePattern.SMOOTH, tr("Warten auf den Einstieg"))
        WaveMood.BOARD_NOW -> WaveLook(red, 8.dp, 6.5.dp, 26.dp, .85f, .24f, WavePattern.PULSE, tr("Jetzt einsteigen"))
        WaveMood.RIDING -> WaveLook(green, 2.5.dp, 2.5.dp, 46.dp, 6.2f, .07f, WavePattern.SMOOTH, tr("Unterwegs, pünktlich"))
        WaveMood.RIDING_LATE -> WaveLook(amber, 3.5.dp, 4.dp, 34.dp, 3.2f, .12f, WavePattern.SMOOTH, tr("Unterwegs, verspätet"))
        WaveMood.WALKING -> WaveLook(tone(s.tertiary, .55f), 3.dp, 2.dp, 16.dp, 1.4f, .08f, WavePattern.STEPS, tr("Zu Fuß unterwegs"))
        WaveMood.HURRY -> WaveLook(lerp(amber, red, .5f), 5.dp, 4.5.dp, 20.dp, .95f, .18f, WavePattern.PULSE, tr("Umstieg knapp"))
        WaveMood.ARRIVING -> WaveLook(green, 3.dp, 3.dp, 52.dp, 4.2f, .10f, WavePattern.SMOOTH, tr("Gleich am Ziel"))
        WaveMood.DISRUPTED -> WaveLook(red, 4.dp, 4.5.dp, 22.dp, 2.4f, .16f, WavePattern.JAGGED, tr("Störung"))
        WaveMood.PAUSED -> WaveLook(s.onSurfaceVariant, 2.dp, 0.dp, 40.dp, 9f, 0f, WavePattern.STILL, tr("Pausiert"))
    }
}

/** Height of the wave's slot (fixed, so a change of mood never moves the card). */
private val WaveHeight = 30.dp

/** A heartbeat: two quick beats, then rest ([t] in beats). */
internal fun heartbeat(t: Float): Float {
    val x = t - floor(t)
    fun beat(at: Float) = exp(-((x - at) * 14f).pow(2))
    return (beat(.12f) + .7f * beat(.32f)).coerceIn(0f, 1f)
}

/** The wave's height at [x] px for [shape] (sine or a sharper zigzag), between -1 and 1. */
private fun waveShape(x: Float, wavelength: Float, phase: Float, jagged: Boolean): Float {
    val u = x / wavelength * 2f * PI.toFloat() - phase
    if (!jagged) return sin(u)
    // A zigzag with a small random-looking kink: something is not running smoothly.
    val t = (u / (2f * PI.toFloat())).let { it - floor(it) }
    val zig = if (t < .5f) t * 4f - 1f else 3f - t * 4f
    return zig * .85f + .15f * sin(u * 3.3f)
}

/**
 * Build 123 · the journey's progress as a wave in the mood of the moment. [progress] 0..1 of the whole journey;
 * the part travelled waves, the rest is a quiet track that ends in a stop dot (Material 3 Expressive).
 */
@Composable fun JourneyWave(progress: Float, mood: WaveMood, modifier: Modifier = Modifier) {
    val quiet = LocalAppSettings.current.reduceMotion || !ValueAnimator.areAnimatorsEnabled()
    val look = waveLook(mood)
    val springy = spring<Float>(dampingRatio = .5f, stiffness = Spring.StiffnessLow)
    val springyDp = spring<Dp>(dampingRatio = .5f, stiffness = Spring.StiffnessLow)
    val color by animateColorAsState(look.color, spring(stiffness = Spring.StiffnessLow), label = "wave-color")
    val thickness by animateDpAsState(look.thickness, springyDp, label = "wave-thickness")
    val amplitude by animateDpAsState(if (quiet) 0.dp else look.amplitude, springyDp, label = "wave-amplitude")
    val wavelength by animateDpAsState(look.wavelength, spring(stiffness = Spring.StiffnessVeryLow), label = "wave-length")
    val glow by animateFloatAsState(look.glow, springy, label = "wave-glow")
    val shown by animateFloatAsState(progress.coerceIn(0f, 1f), spring(dampingRatio = .8f, stiffness = Spring.StiffnessVeryLow), label = "wave-progress")
    val period by rememberUpdatedState(look.period)
    // One continuous phase: a change of tempo never makes the wave jump.
    val phase = remember { mutableFloatStateOf(0f) }
    val time = remember { mutableFloatStateOf(0f) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val moving = !quiet && look.pattern != WavePattern.STILL
    LaunchedEffect(moving, lifecycle) {
        if (!moving) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var last = withInfiniteAnimationFrameNanos { it }
            while (true) {
                val now = withInfiniteAnimationFrameNanos { it }
                val dt = ((now - last) / 1e9f).coerceIn(0f, .1f); last = now
                phase.floatValue = (phase.floatValue + dt * 2f * PI.toFloat() / period.coerceAtLeast(.2f)) % (2000f * PI.toFloat())
                time.floatValue += dt
            }
        }
    }
    // The head – where the journey stands – lands with a little bounce on every change of mood.
    val landing = remember { Animatable(1f) }
    LaunchedEffect(mood) { if (!quiet) { landing.snapTo(1.6f); landing.animateTo(1f, spring(dampingRatio = .35f, stiffness = 260f)) } }
    val track = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .22f)
    val pattern = look.pattern
    Canvas(modifier.fillMaxWidth().height(WaveHeight).progressSemantics(progress.coerceIn(0f, 1f))
        .semantics { stateDescription = look.label }.testTag("journey-wave-${mood.name.lowercase()}")) {
        val y = size.height / 2f
        val stroke = thickness.toPx()
        val beat = if (pattern == WavePattern.PULSE) heartbeat(time.floatValue / (period * 1.6f)) else 0f
        val width = stroke * (1f + .45f * beat)
        val amp = amplitude.toPx() * (1f + .35f * beat)
        val lambda = wavelength.toPx()
        val end = size.width * shown
        val gap = 6.dp.toPx()
        // The track after the head, with a stop dot at the very end.
        if (end + gap < size.width - stroke) {
            drawLine(track, Offset(end + gap, y), Offset(size.width - stroke, y), 3.dp.toPx(), StrokeCap.Round)
        }
        drawCircle(track.copy(alpha = .5f), 2.5.dp.toPx(), Offset(size.width - 2.5.dp.toPx(), y))
        if (end <= 1f) return@Canvas
        // The travelled part: a wave that fades in from the start and settles into the head (no hard edges).
        val path = Path()
        val step = 1.5.dp.toPx()
        var x = 0f
        while (x <= end) {
            val envelope = (x / (lambda * .6f)).coerceIn(0f, 1f) * ((end - x) / (lambda * .5f)).coerceIn(0f, 1f)
            val dy = amp * envelope * waveShape(x, lambda, phase.floatValue, pattern == WavePattern.JAGGED)
            if (x == 0f) path.moveTo(x, y + dy) else path.lineTo(x, y + dy)
            x += step
        }
        path.lineTo(end, y)
        val flicker = if (pattern == WavePattern.JAGGED) .75f + .25f * sin(time.floatValue * 9f) else 1f
        if (glow > 0f) drawPath(path, color.copy(alpha = glow * flicker), style = Stroke(width * 3.2f, cap = StrokeCap.Round))
        val effect = when (pattern) {
            // Steps like footprints, moving forward with the walk.
            WavePattern.STEPS -> PathEffect.dashPathEffect(floatArrayOf(width * 2.2f, width * 1.6f), -phase.floatValue / (2f * PI.toFloat()) * lambda)
            WavePattern.STILL -> PathEffect.dashPathEffect(floatArrayOf(width * 3f, width * 2f), 0f)
            else -> null
        }
        drawPath(path, color.copy(alpha = flicker), style = Stroke(width, cap = StrokeCap.Round, pathEffect = effect))
        // The head: where the journey stands now.
        val head = Offset(end, y)
        drawCircle(color.copy(alpha = .22f), (width * 1.6f + 3.dp.toPx()) * landing.value, head)
        drawCircle(color, (width * .85f + 1.5.dp.toPx()) * (.85f + .15f * landing.value), head)
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
