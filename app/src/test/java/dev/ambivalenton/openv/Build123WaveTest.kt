// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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

/** Build 123 · the pinned journey's progress wave: its mood follows the situation. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = OpenVApp::class, qualifiers = "de-rDE-w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Build123WaveTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val running = PinnedTrackingStatus.RUNNING

    @Test fun theMoodFollowsTheSituation() {
        assertEquals(WaveMood.WAITING, journeyWaveMood(BriefStepKind.BOARD, running, false, false, 0, null, .1f))
        assertEquals(WaveMood.BOARD_NOW, journeyWaveMood(BriefStepKind.BOARD, running, true, false, 0, null, .1f))
        assertEquals(WaveMood.RIDING, journeyWaveMood(BriefStepKind.ALIGHT, running, false, false, 0, null, .4f))
        assertEquals(WaveMood.RIDING_LATE, journeyWaveMood(BriefStepKind.ALIGHT, running, false, false, 6, null, .4f))
        assertEquals(WaveMood.WALKING, journeyWaveMood(BriefStepKind.WALK, running, false, false, 0, 9, .5f))
        assertEquals(WaveMood.HURRY, journeyWaveMood(BriefStepKind.WALK, running, false, false, 0, 2, .5f))
        assertEquals(WaveMood.ARRIVING, journeyWaveMood(BriefStepKind.ALIGHT, running, false, false, 0, null, .95f))
        assertEquals(WaveMood.DISRUPTED, journeyWaveMood(BriefStepKind.ALIGHT, running, true, true, 0, null, .4f))
        assertEquals(WaveMood.PAUSED, journeyWaveMood(BriefStepKind.ALIGHT, PinnedTrackingStatus.PAUSED, true, true, 0, null, .4f))
        // Two beats, then rest.
        assertTrue(heartbeat(.12f) > .9f && heartbeat(.7f) < .05f)
    }

    @Test fun everyMoodDrawn() {
        val moods = WaveMood.entries
        compose.setContent { OpenVTheme(AppSettings()) { Surface(Modifier.fillMaxSize()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                moods.forEachIndexed { i, mood ->
                    Text(mood.name)
                    JourneyWave(.25f + i * .07f, mood)
                }
            }
        } } }
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(1_400)
        moods.forEach { compose.onNodeWithTag("journey-wave-${it.name.lowercase()}").assertExists() }
        save("moods")
        compose.mainClock.advanceTimeBy(350)
        save("moods-later")
        compose.mainClock.autoAdvance = true
    }

    private fun save(name: String) = compose.runOnIdle {
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(android.graphics.Canvas(it)) }
        val file = File("../audit/build123/wave-$name.png"); file.parentFile!!.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
    }
}
