// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.ui.components.*
import dev.ambivalenton.openv.ui.theme.OpenVTheme
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Build 105 · the timeline's rail runs from point to point: no line past a stop, footprints between the points. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "de-rDE-w400dp-h2400dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Build105TimelineTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val now = System.currentTimeMillis() + 3_600_000
    private val min = 60_000L
    private fun stop(name: String, id: String, lon: Double) = Location(name, id, 51.5, lon, "VRR", LocationKind.STOP, city = "Dortmund")
    private val street = Location("Hacheney (Dortmund), Hacheneyer Straße 182", "a", 51.48, 7.48, "VRR", LocationKind.ADDRESS)
    private val hacheney = stop("Dortmund Hacheney", "h", 7.49)
    private val hbf = stop("Dortmund Hbf", "d", 7.46)
    private val julia = stop("Herne Juliastr./Großmarkt", "j", 7.21)
    private val home = Location("Holsterhausen (Herne), Paderborner Straße 34", "p", 51.55, 7.2, "VRR", LocationKind.ADDRESS)
    private fun stops(from: Location, to: Location, start: Long, count: Int) = (0..count + 1).map { k ->
        val at = start + k * 90_000L
        Stopover(if (k == 0) from else if (k == count + 1) to else stop("Halt $k", "x$k", 7.3 + k / 100.0), at, at)
    }

    /** Walk → U49 → transfer → bus → walk, as in the reported screenshots. */
    private val journey = Trip("j", listOf(
        TripSegment("walk-in", street, hacheney, now, now + 7 * min, LineType.WALK, "Fußweg"),
        TripSegment("u49", hacheney, hbf, now + 7 * min, now + 17 * min, LineType.SUBWAY, "U49",
            departurePlatform = "1", stops = stops(hacheney, hbf, now + 7 * min, 6), direction = "Dortmund Hafen"),
        TripSegment("bus", hbf, julia, now + 25 * min, now + 61 * min, LineType.BUS, "SB20",
            arrivalPlatform = "01", stops = stops(hbf, julia, now + 25 * min, 4), direction = "Herne"),
        TripSegment("walk-out", julia, home, now + 61 * min, now + 72 * min, LineType.WALK, "Fußweg")), "VRR", now)

    /** One tram with notices before its first stop, as in the first screenshot. */
    private val tram = Trip("t", listOf(TripSegment("tram", stop("Dortmund Rombergpark", "r", 7.47), stop("Dortmund Hörde Bf", "hb", 7.5),
        now, now + 8 * min, LineType.TRAM, "440", arrivalPlatform = "1", direction = "Dortmund, Aplerbeck",
        stops = stops(stop("Dortmund Rombergpark", "r", 7.47), stop("Dortmund Hörde Bf", "hb", 7.5), now, 6),
        notices = (1..10).map { "Hinweis $it: Umleitung wegen Bauarbeiten." })), "VRR", now)

    /** Recognised notices (stop relocated) before the first ride and before the ride after the transfer. */
    private val relocated = journey.copy(id = "r", segments = journey.segments.mapIndexed { i, s -> when (i) {
        1 -> s.copy(notices = listOf("Dortmund Hacheney: Haltestelle vorübergehend verlegt."))
        2 -> s.copy(notices = listOf("Dortmund Hbf: Haltestelle vorübergehend verlegt.", "Aufzug außer Betrieb."))
        else -> s } })

    private var shown by mutableStateOf<Pair<Trip, AppTheme?>?>(null)
    private var fontScale by mutableStateOf(1f)
    private var started = false

    private fun render(trip: Trip, name: String, theme: AppTheme? = null, quiet: Boolean = true, scale: Float = 1f) {
        shown = trip to theme; fontScale = scale
        if (!started) {
            started = true
            compose.setContent {
                val (t, th) = shown ?: return@setContent
                val base = androidx.compose.ui.platform.LocalDensity.current
                CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(base.density, fontScale)) {
                OpenVTheme(AppSettings(quietMode = quiet, appTheme = th)) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 24.dp)) {
                            key(t.id, th, fontScale) { ModernRouteTimeline(t) }
                        }
                    }
                }
                }
            }
        }
        compose.waitForIdle()
        snapshot(name)
    }

    private fun snapshot(name: String) {
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val image = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(image))
            val file = File("../audit/build105/$name.png"); file.parentFile!!.mkdirs()
            file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }; image.recycle()
        }
    }

    @Test fun capture() {
        render(journey, "journey-light")
        render(journey, "journey-dark", AppTheme.CLASSIC_DARK)
        render(tram, "tram-dark", AppTheme.CLASSIC_DARK)
        compose.onNodeWithTag("intermediate-stops-tram").performClick()
        render(tram, "tram-dark-stops", AppTheme.CLASSIC_DARK)
        render(journey, "journey-synthwave", AppTheme.SYNTHWAVE)
        render(relocated, "notices-recognised")
        render(journey, "journey-large-font", scale = 1.6f)
    }

    /** While the rail draws itself in, the points pop and the rows glide up as the line reaches them. */
    @Test fun theRailDrawsItselfIn() {
        compose.mainClock.autoAdvance = false
        render(journey, "reveal-0", quiet = false)
        var at = 0L
        for (ms in listOf(100L, 250L, 450L, 1000L)) {
            compose.mainClock.advanceTimeBy(ms - at); at = ms
            snapshot("reveal-$ms")
        }
    }

    /** The plan: each line runs between its own points; walks join or continue a ride at the ride's point. */
    @Test fun theRailPlanJoinsPointsOnly() {
        val plan = railPlan(journey, emptySet())
        val links = plan.links.map { Triple(it.from, it.to, it.style) }
        assertEquals(listOf(
            Triple("start:0", "start:1", RailStyle.WALK),   // the walk ends at the U49's boarding point
            Triple("start:1", "end:1", RailStyle.RIDE),
            Triple("end:1", "start:2", RailStyle.WAIT),     // the transfer at Dortmund Hbf
            Triple("start:2", "end:2", RailStyle.RIDE),
            Triple("end:2", "end:3", RailStyle.WALK),       // the walk starts where the bus arrived
        ), links)
        assertEquals(RailMark.TERMINUS, plan.mark("start:0")); assertEquals(RailMark.TERMINUS, plan.mark("end:3"))
        assertEquals(RailMark.STOP, plan.mark("end:1"))
        // A ride's notices before its first stop: no line leads into that stop.
        val first = railPlan(tram, emptySet())
        assertEquals(listOf(Triple("start:0", "end:0", RailStyle.RIDE)), first.links.map { Triple(it.from, it.to, it.style) })
        // A recognised notice sits on the way to the stop, never on the ride's own line.
        val flagged = railPlan(journey.copy(segments = journey.segments.mapIndexed { i, s -> if (i == 2) s.copy(notices = listOf("Achtung")) else s }), setOf("Achtung"))
        assertTrue(flagged.links.any { it.from == "end:1" && it.to == "notice:2" && it.style == RailStyle.WAIT })
        assertTrue(flagged.links.any { it.from == "notice:2" && it.to == "start:2" && it.style == RailStyle.WAIT })
    }

    /** Drawn: no line runs on above a ride's first stop or below its last one (the reported screenshots). */
    @Test fun noLineRunsPastItsStop() {
        fun near(a: Int, b: Int) = listOf(16, 8, 0).all { sh -> kotlin.math.abs(((a shr sh) and 0xff) - ((b shr sh) and 0xff)) < 28 }
        render(journey, "check-journey")
        val image = screen()
        val d = compose.density.density
        val x = ((16 + 50 + 26) * d).toInt()
        fun pointY(text: String) = (compose.onNodeWithText(text).fetchSemanticsNode().boundsInRoot.top + 11 * d).toInt()
        // The bus line's colour, sampled in the middle of its ride.
        val busStart = compose.onAllNodesWithText("Dortmund Hbf").fetchSemanticsNodes()[1].boundsInRoot.top + 11 * d
        val busColor = image.getPixel(x, (busStart + 40 * d).toInt())
        val arrival = pointY("Herne Juliastr./Großmarkt")
        // Below the arrival point (outside its ring) the bus colour does not continue.
        for (dy in 11..24) assertFalse("bus line below its last stop at +$dy dp", near(image.getPixel(x, arrival + (dy * d).toInt()), busColor))
        // Above the U49's boarding point there is no U49 colour.
        val u49Start = pointY("Dortmund Hacheney")
        val u49Color = image.getPixel(x, u49Start + (30 * d).toInt())
        for (dy in 11..24) assertFalse("U49 line above its first stop at -$dy dp", near(image.getPixel(x, u49Start - (dy * d).toInt()), u49Color))
        assertFalse("sanity: the bus colour is not the card's", near(busColor, image.getPixel(image.width - 4, arrival)))
        image.recycle()
    }

    private fun screen(): Bitmap = compose.runOnIdle {
        val view = compose.activity.window.decorView
        Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
    }

    /** "Abfahrt/Ankunft" coming back: the capsule swings fluidly – lags, stretches, swings on, swings back, rests. */
    @Test fun theCapsuleSwingsLikeALiquidDrop() {
        val sway = dev.ambivalenton.openv.ui.components.CapsuleSway()
        val stiffness = 260f; val damping = .58f
        var x = -392f; var v = 0f; val dt = 1f / 120f
        val centre = mutableListOf<Float>(); val width = mutableListOf<Float>()
        var restedAt = -1f
        for (frame in 0 until 300) {
            val a = -stiffness * x - 2f * damping * kotlin.math.sqrt(stiffness) * v
            sway.step(dt, v, a)
            v += a * dt; x += v * dt
            centre += (sway.left + sway.right) / 2; width += sway.right - sway.left
            if (restedAt < 0 && frame > 30 && sway.resting) restedAt = frame * dt
        }
        // Lags behind while it is pulled in, then swings on past its place when the row brakes.
        assertTrue("lag ${centre.min()}", centre.min() < -8f)
        assertTrue("swing ${centre.max()}", centre.max() > 8f)
        // Not a block: it stretches while moving and swings back at least once more.
        assertTrue("stretch ${width.max()}", width.max() > 5f)
        val swings = centre.zipWithNext().count { (p, q) -> (p < -.5f && q >= -.5f) || (p > .5f && q <= .5f) }
        assertTrue("swings $swings", swings >= 3)
        // Never far from its place, and at rest after about a second and a half.
        assertTrue(centre.all { kotlin.math.abs(it) < 22f })
        assertTrue("rests at $restedAt s", restedAt in 0f..2.2f)
    }

    /** Audit: the pill coming back, frame by frame (Abfahrt and Ankunft). */
    @Test fun captureTheCapsuleComingBack() {
        var away by mutableStateOf(false)
        var mode by mutableStateOf(TimeMode.ARRIVAL)
        compose.setContent {
            OpenVTheme(AppSettings(quietMode = false)) {
                Surface(Modifier.fillMaxSize()) {
                    Box(Modifier.padding(16.dp).width(380.dp)) { ScheduleControls(null, {}, mode, { mode = it }, {}, modeAway = away) }
                }
            }
        }
        compose.waitForIdle()
        for (m in listOf(TimeMode.ARRIVAL, TimeMode.DEPARTURE)) {
            mode = m; away = true; compose.waitForIdle()
            compose.mainClock.autoAdvance = false
            away = false
            val frames = mutableListOf<Bitmap>()
            repeat(28) { compose.mainClock.advanceTimeBy(40); frames += screen() }
            compose.mainClock.autoAdvance = true; compose.waitForIdle()
            val pill = compose.onNodeWithTag("time-mode-pill").fetchSemanticsNode().boundsInRoot
            val top = (pill.top - 6).toInt().coerceAtLeast(0); val h = (pill.height + 12).toInt()
            val w = frames[0].width
            val strip = Bitmap.createBitmap(w, h * frames.size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(strip)
            frames.forEachIndexed { i, f -> canvas.drawBitmap(Bitmap.createBitmap(f, 0, top, w, h), 0f, (i * h).toFloat(), null); f.recycle() }
            val file = File("../audit/build105/capsule-${m.name.lowercase()}.png"); file.parentFile!!.mkdirs()
            file.outputStream().use { strip.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    /** A recognised notice (stop relocated) keeps its exclamation mark on the rail, in the ride's colour, before the boarding point. */
    @Test fun aRecognisedNoticeKeepsItsExclamationMark() {
        fun near(a: Int, b: Int) = listOf(16, 8, 0).all { sh -> kotlin.math.abs(((a shr sh) and 0xff) - ((b shr sh) and 0xff)) < 28 }
        render(relocated, "check-notices")
        val image = screen()
        val d = compose.density.density
        val x = (16 + 50 + 26) * d
        for ((segment, boarding) in listOf("u49" to "Dortmund Hacheney", "bus" to "Dortmund Hbf")) {
            val row = compose.onNodeWithTag("notice-mark-$segment").fetchSemanticsNode().boundsInRoot
            val centre = row.top + 30 * d
            val stop = compose.onAllNodesWithText(boarding).fetchSemanticsNodes().last().boundsInRoot
            assertTrue("$segment: the mark comes before the boarding point", centre < stop.top)
            val line = image.getPixel(x.toInt(), (stop.top + 11 * d + 30 * d).toInt())
            // The disc (left of the cut-out bar) is in the ride's colour, the bar itself shows the background.
            assertTrue("$segment disc", near(image.getPixel((x - 6 * d).toInt(), centre.toInt()), line))
            assertFalse("$segment bar", near(image.getPixel(x.toInt(), (centre - 2 * d).toInt()), line))
        }
        compose.onNodeWithText("1 Hinweis").assertExists()
        compose.onNodeWithText("2 Hinweise").assertExists()
        image.recycle()
    }
}
