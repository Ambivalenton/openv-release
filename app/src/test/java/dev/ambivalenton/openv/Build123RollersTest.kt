// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.ui.components.*
import dev.ambivalenton.openv.ui.theme.OpenVTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

/** Build 123 · the date and time rollers of the search: turn, carry over, tap, spool back, three looks. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = OpenVApp::class, qualifiers = "de-rDE-w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Build123RollersTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val zone = ZoneId.of("Europe/Berlin")

    @Test fun theRollerArithmetic() {
        val today = LocalDate.of(2026, 11, 25)
        val t = wheelTime(1440 + 23 * 60 + 59, today, zone)
        assertEquals(1440 + 23 * 60 + 59, wheelTotal(t, today, zone))
        assertEquals(LocalDate.of(2026, 11, 26), java.time.Instant.ofEpochMilli(t).atZone(zone).toLocalDate())
        // No needless full turns: the index nearest to the roller's place that shows the value.
        assertEquals(61, nearestIndex(59.6f, 1, 60))
        assertEquals(-1, nearestIndex(0.2f, 59, 60))
        assertEquals(WheelLook.MECHANICAL, wheelLook(AppTheme.VINTAGE_TRANSIT))
        assertEquals(WheelLook.DIGITAL, wheelLook(AppTheme.SPACE_TERMINAL))
        assertEquals(WheelLook.MODERN, wheelLook(null))
    }

    private fun host(theme: AppTheme?, time: MutableState<Long?>, picked: MutableList<String>) {
        compose.setContent { OpenVTheme(AppSettings(appTheme = theme)) { Surface(Modifier.fillMaxSize()) {
            Column(Modifier.padding(20.dp)) {
                Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    TimeRollers(time.value, { time.value = it }, { picked += "date" }, { picked += "time" }, Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                }
            }
        } } }
        compose.waitForIdle()
    }

    private fun minutesOf(time: Long) = java.time.Instant.ofEpochMilli(time).atZone(zone).let { it.hour * 60 + it.minute }

    @Test fun turningTheMinuteRollerChoosesATimeAndCarriesIntoTheHour() {
        val start = LocalDate.now(zone).atTime(9, 58).atZone(zone).toInstant().toEpochMilli()
        val time = mutableStateOf<Long?>(start); val picked = mutableListOf<String>()
        host(null, time, picked)
        // Up by about three values: three minutes later, past the full hour – the hour roller turns on.
        // Slowly (no fling): the finger moves the drum by exactly three rows.
        compose.onNodeWithTag("roller-minute").performTouchInput {
            val row = rollerRowPx(height.toFloat())
            down(center)
            repeat(12) { advanceEventTime(80); moveBy(Offset(0f, -row * 3f / 12f)) }
            advanceEventTime(400); moveBy(Offset(0f, 0f)); up()
        }
        compose.mainClock.advanceTimeBy(1_500); compose.waitForIdle()
        val chosen = minutesOf(time.value!!)
        assertTrue("three minutes on, past the hour: $chosen", chosen in 10 * 60 + 0..10 * 60 + 2)
        compose.onNodeWithTag("roller-hour").assertContentDescriptionEquals("Stunde")
        val hour = compose.onNodeWithTag("roller-hour").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.StateDescription]
        assertEquals("the hour carried over", "10", hour)
        save("modern-turned")
        assertTrue(picked.isEmpty())
    }

    @Test fun tappingTheDayOpensTheCalendarAndTheTimeTheTypedEntry() {
        val time = mutableStateOf<Long?>(null); val picked = mutableListOf<String>()
        host(null, time, picked)
        compose.onNodeWithTag("roller-day").performClick(); compose.waitForIdle()
        compose.onNodeWithTag("roller-minute").performClick(); compose.waitForIdle()
        assertEquals(listOf("date", "time"), picked)
        // TalkBack can step a value without dragging.
        compose.runOnIdle {
            compose.onNodeWithTag("roller-day").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsActions.CustomActions]
                .first { it.label == "Einen Tag später" }.action()
        }
        compose.waitForIdle()
        assertNotNull(time.value)
        assertEquals(LocalDate.now(zone).plusDays(1), java.time.Instant.ofEpochMilli(time.value!!).atZone(zone).toLocalDate())
    }

    @Test fun nowSpoolsTheRollersBack() {
        val later = LocalDate.now(zone).plusDays(1).atTime(18, 30).atZone(zone).toInstant().toEpochMilli()
        val time = mutableStateOf<Long?>(later); val picked = mutableListOf<String>()
        host(null, time, picked)
        compose.onNodeWithTag("roller-day").assertExists()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { time.value = null }
        compose.mainClock.advanceTimeBy(300)
        save("modern-spooling")
        compose.mainClock.advanceTimeBy(2_000)
        compose.mainClock.autoAdvance = true; compose.waitForIdle()
        val day = compose.onNodeWithTag("roller-day").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.StateDescription]
        assertTrue(day, day.startsWith("Heute"))
    }

    @Test fun theLooksOfTheDesigns() {
        var theme by mutableStateOf<AppTheme?>(null)
        val time = LocalDate.now(zone).plusDays(2).atTime(7, 43).atZone(zone).toInstant().toEpochMilli()
        compose.setContent { OpenVTheme(AppSettings(appTheme = theme)) { Surface(Modifier.fillMaxSize()) {
            Column(Modifier.padding(20.dp)) {
                Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    TimeRollers(time, {}, {}, {}, Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                }
            }
        } } }
        for ((t, name) in listOf(null to "modern", AppTheme.VINTAGE_TRANSIT to "mechanical", AppTheme.SPACE_TERMINAL to "digital",
            AppTheme.SYNTHWAVE to "synthwave", AppTheme.CLASSIC_DARK to "classic-dark")) {
            compose.runOnIdle { theme = t }
            compose.waitForIdle()
            compose.onNodeWithTag("time-rollers").assertExists()
            save("look-$name")
        }
    }

    private fun save(name: String) = compose.runOnIdle {
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(android.graphics.Canvas(it)) }
        val file = File("../audit/build123/$name.png"); file.parentFile!!.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
    }
}
