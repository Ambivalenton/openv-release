// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import android.app.Application
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.ui.components.*
import dev.ambivalenton.openv.ui.screens.BackupTransferButtons
import dev.ambivalenton.openv.ui.theme.OpenVTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Notices in the timeline, platforms in the accent, the backup labels and the swap arrow's fixed light. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "de-rDE-w360dp-h780dp-mdpi")
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
class Build95UiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val now = System.currentTimeMillis() + 3_600_000
    private fun stop(name: String, id: String, lon: Double) = Location(name, id, 51.45, lon, "VRR", LocationKind.STOP)
    private val essen = stop("Essen Hbf", "e", 7.0); private val bochum = stop("Bochum Hbf", "b", 7.22)
    private val notice = "Essen Hbf: Haltestelle vorübergehend verlegt."
    private val trip = Trip("t", listOf(TripSegment("ride", essen, bochum, now, now + 900_000, LineType.REGIONAL, "RE 1",
        departurePlatform = "3", arrivalPlatform = "5", notices = listOf(notice))), "VRR", now)

    /** The card stands out; hiding it ends only the emphasis, the notice and its mark on the rail stay. */
    @Test fun aRecognisedNoticeHasACardAndAnExclamationMarkOnTheRail() {
        var dismissed by mutableStateOf(emptySet<String>())
        compose.setContent { OpenVTheme(AppSettings(quietMode = true)) { Surface(Modifier.fillMaxSize()) {
            CompositionLocalProvider(LocalJourneyNotices provides JourneyNoticeActions(dismissed) { hit -> dismissed = dismissed + hit.keys }) {
                Column(Modifier.verticalScroll(rememberScrollState())) { ModernRouteTimeline(trip) }
            }
        } } }
        compose.onNodeWithTag("journey-notice").assertIsDisplayed()
        compose.onNodeWithTag("notice-mark-ride").assertExists()
        compose.onNodeWithText("Hervorhebung ausblenden").performClick()
        compose.onNodeWithTag("journey-notice").assertDoesNotExist()
        compose.onNodeWithTag("notice-mark-ride").assertExists()
        compose.onNodeWithText("1 Hinweise").performScrollTo().performClick()
        compose.onNodeWithText(notice).assertExists()
    }

    /** "Abfahrt: Gleis 3": the platform in the theme's accent colour. */
    @Test fun platformsAreShownInTheAccentColour() {
        var accent = Color.Unspecified
        compose.setContent { OpenVTheme(AppSettings(quietMode = true)) { Surface(Modifier.fillMaxSize()) {
            accent = MaterialTheme.colorScheme.primary
            Column(Modifier.verticalScroll(rememberScrollState())) { ModernRouteTimeline(trip) }
        } } }
        for (tag in listOf("departure-platform", "arrival-platform")) {
            val text = compose.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsProperties.Text].first()
            val span = text.spanStyles.single()
            assertEquals(tag, accent, span.item.color)
            assertTrue(tag, text.text.substring(span.start, span.end).contains("3") || text.text.substring(span.start, span.end).contains("5"))
        }
    }

    /** The backup buttons break between words only ("Sicherun g" no more). */
    @Test fun theBackupLabelsNeverSplitAWord() {
        compose.setContent { OpenVTheme(AppSettings(quietMode = true)) { Surface { Box(Modifier.width(300.dp)) { BackupTransferButtons(false, {}, {}) } } } }
        for (label in listOf("Sicherung speichern", "Sicherung wiederherstellen")) {
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNode(hasText(label), useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertTrue(label, breaksOnlyBetweenWords(layouts.single()))
        }
    }

    /** The light does not turn with the arrow: half a turn looks exactly as before, a tilt is lit differently on each side. */
    @Test fun theSwapArrowKeepsItsLightWhileItTurns() {
        fun render(degrees: Float): IntArray {
            val bitmap = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888)
            val arrow = SpaceArrow(Size(120f, 120f))
            CanvasDrawScope().draw(Density(2f), LayoutDirection.Ltr, androidx.compose.ui.graphics.Canvas(bitmap.asImageBitmap()), Size(120f, 120f)) {
                arrow.draw(this, degrees, Color(0xFF1B5E86), Color.Black, false)
            }
            return IntArray(120 * 120).also { bitmap.getPixels(it, 0, 120, 0, 0, 120, 120) }
        }
        // Mean brightness of the arrow's own pixels in the upper-left and the lower-right half (the background is transparent).
        fun light(pixels: IntArray): Triple<Double, Double, Double> {
            var upper = 0.0; var nu = 0; var lower = 0.0; var nl = 0
            for (y in 0 until 120) for (x in 0 until 120) {
                val c = pixels[y * 120 + x]; if ((c ushr 24) < 250) continue
                val lum = .299 * (c shr 16 and 255) + .587 * (c shr 8 and 255) + .114 * (c and 255)
                if (x + y < 120) { upper += lum; nu++ } else { lower += lum; nl++ }
            }
            return Triple(upper / nu, lower / nl, (upper + lower) / (nu + nl))
        }
        val rest = light(render(0f)); val turned = light(render(180f))
        assertTrue("lit from the upper left at rest: $rest", rest.first > rest.second)
        assertTrue("still lit from the upper left after half a turn: $turned", turned.first > turned.second)
        assertEquals("the same brightness after half a turn", rest.third, turned.third, 4.0)
        val towards = light(render(60f)).third; val away = light(render(120f)).third
        assertTrue("a tilt towards and away from the light differ: $towards / $away", kotlin.math.abs(towards - away) > 8.0)
    }
}
