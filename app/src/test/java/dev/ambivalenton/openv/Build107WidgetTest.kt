// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.compose
import androidx.glance.appwidget.provideContent
import androidx.test.core.app.ApplicationProvider
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.widget.*
import java.io.File
import java.time.ZonedDateTime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Build 107 · both widgets, rendered from their real RemoteViews at the sizes launchers offer. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "de-rDE-w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Build107WidgetTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val now = System.currentTimeMillis()
    private fun stop(name: String, id: String, lon: Double) = Location(name, id, 51.5, lon, "VRR", LocationKind.STOP, city = "Herne")
    private val herne = stop("Herne Bf", "h", 7.22)
    private val favorites = listOf(
        Favorite("home", "Zuhause", herne, icon = FavoriteIcon.HOME, accentSeed = 0xFF245EA8),
        Favorite("work", "Arbeit", stop("Dortmund Hbf", "d", 7.46), icon = FavoriteIcon.WORK, accentSeed = 0xFFC2185B, shape = IconStyle.CIRCLE),
        Favorite("uni", "Uni", stop("Bochum Ruhr-Universität", "u", 7.26), icon = FavoriteIcon.SCHOOL, accentSeed = 0xFF2E7D32),
        Favorite("gym", "Fitnessstudio", stop("Essen Hbf", "e", 7.01), icon = FavoriteIcon.FITNESS_CENTER, accentSeed = 0xFFEF6C00),
        Favorite("mum", "Eltern", stop("Gelsenkirchen Hbf", "g", 7.1), icon = FavoriteIcon.PARENTS, accentSeed = 0xFF6A1B9A),
    )
    private fun departure(i: Int, line: String, type: LineType, to: String, minutes: Int, delay: Int? = null, platform: String? = null,
        cancelled: Boolean = false) = Departure("d$i", herne, line, type, to, now + minutes * 60_000L, now + (minutes + (delay ?: 0)) * 60_000L,
        DelayInfo(departureSeconds = delay?.times(60), cancelled = cancelled), platform)
    private val departures = listOf(
        departure(1, "U35", LineType.SUBWAY, "Bochum Hustadt", 2, 0, "1"),
        departure(2, "RE3", LineType.REGIONAL, "Düsseldorf Hbf", 4, 3, "4"),
        departure(3, "311", LineType.BUS, "Recklinghausen Hbf", 6, null, "C"),
        departure(4, "S2", LineType.SUBURBAN, "Dortmund Hbf", 9, 0, "2"),
        departure(5, "SB20", LineType.BUS, "Herten Mitte", 12, 6, "A"),
        departure(6, "RB43", LineType.REGIONAL, "Dorsten", 15, null, "5", cancelled = true),
        departure(7, "323", LineType.BUS, "Wanne-Eickel Hbf", 18, 0, "B"),
        departure(8, "U35", LineType.SUBWAY, "Bochum Hustadt", 22, 0, "1"),
    )

    private fun state(count: Int = favorites.size, theme: AppTheme? = null): StoredState {
        val base = StoredState(favorites = favorites.take(count), settings = AppSettings(quietMode = true, appTheme = theme,
            originMode = OriginMode.MANUAL, manualOrigin = stop("Wanne-Eickel Hbf", "w", 7.17)))
        val key = dev.ambivalenton.openv.data.repository.boardConfigurationKey(base)
        return base.copy(departureBoard = DepartureBoardState(herne, departures, now - 60_000, now - 60_000, key))
    }

    private class Probe(val body: @Composable () -> Unit) : GlanceAppWidget() {
        override suspend fun provideGlance(context: Context, id: GlanceId) { provideContent { GlanceTheme { body() } } }
    }

    private fun inflate(width: Int, height: Int, body: @Composable () -> Unit): FrameLayout = runBlocking {
        val remote = Probe(body).compose(context, size = DpSize(width.dp, height.dp))
        FrameLayout(context).also { it.addView(remote.apply(context, it)) }
    }

    /** Every text the widget shows at this size (from its real RemoteViews). */
    private fun texts(width: Int, height: Int, body: @Composable () -> Unit): List<String> {
        val out = mutableListOf<String>()
        fun walk(v: View) { if (v is android.widget.TextView && v.visibility == View.VISIBLE) out += v.text.toString()
            if (v is android.view.ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i)) }
        walk(inflate(width, height, body)); return out
    }

    private fun render(width: Int, height: Int, body: @Composable () -> Unit): Bitmap {
        val container = inflate(width, height, body)
        val d = context.resources.displayMetrics.density
        val w = (width * d).toInt(); val h = (height * d).toInt()
        container.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        container.layout(0, 0, w, h)
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { container.draw(Canvas(it)) }
    }

    /** Launcher sizes: n cells ≈ n × 72 dp − 8 dp. */
    private val sizes = listOf(1 to 1, 2 to 1, 2 to 2, 3 to 2, 4 to 1, 4 to 2, 4 to 3, 5 to 5, 1 to 3, 2 to 4)
    private fun cells(n: Int) = n * 72 - 8

    private fun sheet(name: String, shots: List<Pair<String, Bitmap>>) {
        val d = context.resources.displayMetrics.density
        val gap = (12 * d).toInt()
        val width = shots.sumOf { it.second.width } + gap * (shots.size + 1)
        val height = shots.maxOf { it.second.height } + gap * 2
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(if (RuntimeEnvironment.getQualifiers().contains("-night")) 0xFF202124.toInt() else 0xFF8EA4B8.toInt())
        var x = gap
        shots.forEach { (_, b) -> canvas.drawBitmap(b, x.toFloat(), gap.toFloat(), null); x += b.width + gap }
        val file = File("../audit/build107/$name.png"); file.parentFile!!.mkdirs()
        file.outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun captureAll(suffix: String) {
        val s = state()
        sheet("favorites-$suffix", sizes.map { (c, r) -> "${c}x$r" to render(cells(c), cells(r)) { QuickWidgetContent(s, 1f, ZonedDateTime.now(), preview = true) } })
        sheet("favorites-four-$suffix", sizes.map { (c, r) -> "${c}x$r" to render(cells(c), cells(r)) { QuickWidgetContent(state(4), 1f, ZonedDateTime.now(), preview = true) } })
        sheet("favorites-few-$suffix", listOf(1 to 1, 2 to 1, 2 to 2, 4 to 1).map { (c, r) -> "${c}x$r" to
            render(cells(c), cells(r)) { QuickWidgetContent(state(2), 1f, ZonedDateTime.now()) } })
        sheet("board-$suffix", listOf(2 to 2, 3 to 2, 4 to 2, 4 to 3, 5 to 4, 2 to 4, 3 to 1, 5 to 2).map { (c, r) -> "${c}x$r" to
            render(cells(c), cells(r)) { DepartureBoardContent(s, 1f, now) } })
    }

    @Test fun captureLight() = captureAll("light")

    @Test @Config(qualifiers = "de-rDE-w411dp-h891dp-night-xxhdpi") fun captureDark() = captureAll("dark")

    /** Every launcher size: readable, tappable tiles; everything fits wherever it can; small widgets never scroll. */
    @Test fun theFavouritesFitEverySize() {
        for ((c, r) in sizes + listOf(3 to 3, 5 to 1, 1 to 5, 2 to 3)) for (n in listOf(1, 2, 4, 7, 12)) {
            val plan = favoritePlan(cells(c).toFloat(), cells(r).toFloat(), n)
            val at = "${c}x$r with $n"
            assertTrue("$at: tiles big enough to tap (${plan.tileWidth}×${plan.tileHeight})", plan.tileWidth >= 40f && plan.tileHeight >= 40f)
            assertTrue("$at: inside the widget", plan.columns * plan.tileWidth + (plan.columns - 1) * plan.gap <= cells(c) - 2 * plan.padding + .5f)
            if (plan.visibleRows == 1) assertFalse("$at: one row never scrolls", plan.scroll)
            if (n <= 4 && c >= 2 && r >= 2) assertFalse("$at: four favourites need no scrolling", plan.scroll)
        }
        // 1×1: one favourite with its name; flat: a row of named tiles; tall and narrow: wide tiles with name and stop.
        assertEquals(1, favoritePlan(64f, 64f, 4).columns)
        assertEquals(FavoriteTile.STACKED, favoritePlan(64f, 64f, 4).tile)
        favoritePlan(cells(4).toFloat(), cells(1).toFloat(), 4).let { assertEquals(4, it.columns); assertEquals(FavoriteTile.STACKED, it.tile) }
        assertEquals(FavoriteTile.WIDE, favoritePlan(cells(2).toFloat(), cells(4).toFloat(), 4).tile)
        assertEquals(FavoriteTile.WIDE, favoritePlan(cells(4).toFloat(), cells(2).toFloat(), 4).tile)
        // A big widget: large tiles and the search button above them.
        favoritePlan(cells(5).toFloat(), cells(5).toFloat(), 4).let { assertTrue(it.header > 0f); assertTrue(it.tileWidth > 140f) }
    }

    @Test fun theDeparturesFitEverySize() {
        for ((c, r) in listOf(2 to 1, 3 to 1, 2 to 2, 3 to 2, 4 to 2, 5 to 2, 4 to 3, 5 to 4, 2 to 4, 4 to 5)) {
            val plan = boardWidgetPlan(cells(c).toFloat(), cells(r).toFloat())
            val used = 2 * plan.padding + plan.header + (if (plan.single) 0f else headerGap(plan.compactHeader)) + plan.rows * plan.rowHeight + plan.footer
            assertTrue("${c}x$r fits: $used", used <= cells(r) + .5f)
            assertTrue("${c}x$r rows tall enough: ${plan.rowHeight}", plan.rowHeight >= (if (plan.narrow && !plan.single) 40f else 24f))
            if (r >= 2) assertTrue("${c}x$r shows at least two departures", plan.rows >= 2)
            if (r >= 3 && c >= 4) assertTrue("${c}x$r shows at least four", plan.rows >= 4)
        }
        assertTrue(boardWidgetPlan(cells(3).toFloat(), cells(1).toFloat()).single)
        assertTrue(boardWidgetPlan(cells(5).toFloat(), cells(4).toFloat()).twoLine)
        assertTrue(boardWidgetPlan(cells(2).toFloat(), cells(4).toFloat()).narrow)
    }

    /** From the real RemoteViews: names, stop, lines, times, delay, cancellation and the source line. */
    @Test fun theWidgetsShowWhatMatters() {
        val s = state()
        val favourites = texts(cells(4), cells(2)) { QuickWidgetContent(state(4), 1f, ZonedDateTime.now()) }
        assertTrue(favourites.toString(), listOf("Zuhause", "Arbeit", "Uni", "Fitnessstudio").all { it in favourites })
        assertTrue("the stop of a wide tile: $favourites", "Dortmund Hbf" in favourites)
        val big = texts(cells(5), cells(4)) { DepartureBoardContent(s, 1f, now) }
        for (expected in listOf("Herne Bf", "U35", "Bochum Hustadt", "RE3", "Düsseldorf Hbf", "+3", "Gleis 4", "Fahrplandaten: VRR"))
            assertTrue("$expected in $big", expected in big)
        assertTrue(big.any { it.startsWith("Stand ") })
        val tall = texts(cells(2), cells(5)) { DepartureBoardContent(s, 1f, now) }
        assertTrue("cancelled: $tall", "Dorsten · fällt aus" in tall)
        val flat = texts(cells(3), cells(1)) { DepartureBoardContent(s, 1f, now) }
        assertTrue("next departure and source in a flat widget: $flat", "Bochum Hustadt" in flat && flat.any { "Fahrplandaten: VRR" in it })
        // No favourites: a clear way to add one.
        val empty = texts(cells(2), cells(2)) { QuickWidgetContent(s.copy(favorites = emptyList()), 1f, ZonedDateTime.now()) }
        assertTrue(empty.toString(), "Favorit hinzufügen" in empty)
    }
}
