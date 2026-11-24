// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Build 118 · a capsule dragged like a mechanical switch. The finger moves a hidden lever ([raw], in
 * slots); the capsule only leans a little towards it ([lean]) until the lever passes the middle between
 * two positions plus a small hysteresis – then it clicks over to the next position ([committed]) and
 * leans from there. Fast drags may click over several positions; the ends give way softly. Pure, so the
 * detents are checked in a unit test.
 */
internal class DetentDrag(private val count: Int, start: Int) {
    var committed = start.coerceIn(0, count - 1); private set
    var raw = committed.toFloat(); private set

    /** Moves the lever by [slots]; returns the new position when it clicked over, else null. */
    fun drag(slots: Float): Int? {
        raw = (raw + slots).coerceIn(-OVERDRAW, count - 1 + OVERDRAW)
        val before = committed
        while (raw - committed > CLICK_OVER && committed < count - 1) committed++
        while (committed - raw > CLICK_OVER && committed > 0) committed--
        return committed.takeIf { it != before }
    }

    /** How far the capsule leans out of its position towards the finger (in slots), softly limited. */
    val lean: Float get() {
        val pull = raw - committed
        return LEAN_REACH * kotlin.math.tanh(pull * LEAN_GAIN / LEAN_REACH)
    }

    /** On release a strong fling towards a neighbour that the lever already leans to clicks over once more. */
    fun release(velocitySlotsPerSecond: Float): Int? {
        val toward = raw - committed
        val next = when {
            velocitySlotsPerSecond > FLING && toward > .2f && committed < count - 1 -> committed + 1
            velocitySlotsPerSecond < -FLING && toward < -.2f && committed > 0 -> committed - 1
            else -> null
        }
        next?.let { committed = it }
        raw = committed.toFloat()
        return next
    }

    companion object {
        /** Past the middle plus a little: no flutter when the finger rests on the boundary. */
        const val CLICK_OVER = .56f
        const val OVERDRAW = .35f
        const val LEAN_GAIN = .45f
        const val LEAN_REACH = .26f
        /** Slots per second that count as a deliberate flick. */
        const val FLING = 3.2f
    }
}

/**
 * Horizontal drag on a row of [count] equal slots: [onLean] follows the finger (0 at rest), [onCommit]
 * fires at every detent the capsule clicks into. Taps keep working: the drag only starts past the touch
 * slop and then takes the gesture (the press of a tapped slot is cancelled).
 */
internal fun Modifier.detentSwipe(enabled: Boolean, count: Int, current: () -> Int, slotPx: () -> Float, rtl: Boolean,
    onLean: (Float) -> Unit, onCommit: (Int) -> Unit): Modifier =
    if (!enabled || count < 2) this else composed {
        // The gesture outlives recompositions: it always reads the latest position and callbacks.
        val position by rememberUpdatedState(current)
        val slot by rememberUpdatedState(slotPx)
        val lean by rememberUpdatedState(onLean)
        val commit by rememberUpdatedState(onCommit)
        pointerInput(count, rtl) {
            var drag: DetentDrag? = null
            val velocity = androidx.compose.ui.input.pointer.util.VelocityTracker()
            fun end(fling: Float) {
                val d = drag ?: return
                drag = null
                d.release(fling)?.let(commit)
                lean(0f)
            }
            detectHorizontalDragGestures(
                onDragStart = { drag = DetentDrag(count, position()); velocity.resetTracking() },
                onDragEnd = {
                    val width = slot()
                    val v = if (width > 0f) velocity.calculateVelocity().x / width else 0f
                    end(if (rtl) -v else v)
                },
                onDragCancel = { end(0f) },
            ) { change, dx ->
                val d = drag ?: return@detectHorizontalDragGestures
                val width = slot()
                if (width <= 0f) return@detectHorizontalDragGestures
                change.consume()
                velocity.addPosition(change.uptimeMillis, change.position)
                d.drag((if (rtl) -dx else dx) / width)?.let(commit)
                // In slots of the row's own order (start → end), like the committed position.
                lean(d.lean)
            }
        }
    }
