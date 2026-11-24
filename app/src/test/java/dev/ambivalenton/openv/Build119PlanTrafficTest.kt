// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import androidx.compose.ui.geometry.Offset
import dev.ambivalenton.openv.ui.network.*
import org.junit.Assert.*
import org.junit.Test

/** Build 119 · crossings on the plan: the upper line is known, vehicles never meet in the middle. */
class Build119PlanTrafficTest {
    private fun straight(from: Offset, to: Offset) = sampleTrack((to - from).getDistance(), 4f) { d ->
        val dir = (to - from) / (to - from).getDistance(); from + dir * d
    }
    private val horizontal = straight(Offset(0f, 50f), Offset(200f, 50f))
    private val vertical = straight(Offset(100f, 0f), Offset(100f, 200f))

    @Test fun aCrossingKnowsWhichLineLiesOnTop() {
        val crossings = lineCrossings(listOf("h", "v"), mapOf("h" to horizontal, "v" to vertical), skip = 10f)
        assertEquals(1, crossings.size)
        val c = crossings.single()
        assertEquals("h", c.lower); assertEquals("v", c.upper)
        assertEquals(100f, c.lowerAt, 1f); assertEquals(50f, c.upperAt, 1f)
        assertEquals(Offset(100f, 50f), c.point)
        // Lines that only meet in a shared node do not cross.
        val other = straight(Offset(200f, 50f), Offset(200f, 180f))
        assertTrue(lineCrossings(listOf("h", "o"), mapOf("h" to horizontal, "o" to other), skip = 10f).isEmpty())
    }

    /** Front at [speed] px/s from the start of its line, starting [delay] s late. */
    private fun run(key: String, edge: String, speed: Float, delay: Float) =
        TrafficRun(key, edge, true, body = 24f) { clock -> (clock - delay).takeIf { it >= 0f }?.let { it * speed } }

    private fun inside(front: Float?, at: Float, zone: Float) = front != null && at in (front - 24f - zone)..(front + zone)

    @Test fun twoVehiclesHeadingForTheSameCrossingTakeTurns() {
        val crossings = lineCrossings(listOf("h", "v"), mapOf("h" to horizontal, "v" to vertical), skip = 10f)
        val a = run("a", "h", 80f, 0f)   // reaches the crossing (100 px) after 1.25 s
        val b = run("b", "v", 50f, .25f) // reaches it (50 px) after 1.25 s as well
        val traffic = PlanTraffic()
        val zone = 6f
        var slowest = 1f
        var both = 0
        var t = 0f
        traffic.advance(t, listOf(a, b), crossings, zone)
        while (t < 6f) {
            t += 1f / 60f
            traffic.advance(t, listOf(a, b), crossings, zone)
            val fa = a.frontAt(traffic.clock("a", t)); val fb = b.frontAt(traffic.clock("b", t))
            if (inside(fa, 100f, zone) && inside(fb, 50f, zone)) both++
            slowest = minOf(slowest, traffic.rate("a"), traffic.rate("b"))
        }
        assertEquals("never both in the crossing", 0, both)
        assertTrue("one of them braked: $slowest", slowest < .5f)
        assertEquals("and both run at full speed again", 1f, traffic.rate("a"), .05f)
        assertEquals(1f, traffic.rate("b"), .05f)
    }

    @Test fun vehiclesThatPassAtDifferentTimesKeepTheirSpeed() {
        val crossings = lineCrossings(listOf("h", "v"), mapOf("h" to horizontal, "v" to vertical), skip = 10f)
        val a = run("a", "h", 80f, 0f)
        val b = run("b", "v", 50f, 3f)
        val traffic = PlanTraffic()
        var t = 0f
        var slowest = 1f
        while (t < 6f) { traffic.advance(t, listOf(a, b), crossings, 6f); slowest = minOf(slowest, traffic.rate("a"), traffic.rate("b")); t += 1f / 60f }
        assertEquals(1f, slowest, .001f)
        assertEquals(6f, traffic.clock("a", t), .05f)
    }
}
