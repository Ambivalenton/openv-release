// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

/**
 * Build 113 · text fits its box at every system font size: a single word shrinks and never breaks,
 * a long label takes a second line between words before it becomes hard to read, and nothing runs
 * out of its box or bends it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = OpenVApp::class, qualifiers = "de-rDE-w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Build113FontTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    /** Content as the system shows it with font size [scale] (2 = the largest Android offers). */
    private fun show(scale: Float, theme: AppTheme? = null, content: @Composable () -> Unit) {
        compose.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, scale)) {
                OpenVTheme(AppSettings(appTheme = theme, quietMode = true)) { Surface(Modifier.fillMaxSize()) { content() } }
            }
        }
        compose.waitForIdle()
    }

    private fun layout(tag: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!(results)
        return results.first()
    }

    private fun hasTestTagStartingWith(prefix: String) = SemanticsMatcher("tag starts with $prefix") {
        androidx.compose.ui.semantics.SemanticsProperties.TestTag in it.config && it.config[androidx.compose.ui.semantics.SemanticsProperties.TestTag].startsWith(prefix)
    }

    private fun assertFits(tag: String, lines: Int? = null) {
        val l = layout(tag)
        assertFalse("$tag runs out of its width", l.didOverflowWidth)
        assertFalse("$tag runs out of its height", l.didOverflowHeight)
        assertTrue("$tag breaks inside a word", breaksOnlyBetweenWords(l))
        lines?.let { assertEquals("$tag lines", it, l.lineCount) }
    }

    @Test fun aSingleWordShrinksButNeverBreaks() {
        show(2f) {
            Box(Modifier.width(96.dp).height(60.dp)) {
                FittedText("Haltestellenverzeichnis", Modifier.testTag("word"), style = TextStyle(fontSize = 14.sp), maxLines = 1)
            }
        }
        assertFits("word", lines = 1)
    }

    @Test fun aLongLabelTakesASecondLineBetweenWordsBeforeItGetsTooSmall() {
        val label = "Verbindung suchen, damit du am Mittwoch um 08:15 ankommst"
        show(1.6f) {
            Box(Modifier.width(260.dp).height(64.dp)) {
                FittedText(label, Modifier.testTag("label"), style = TextStyle(fontSize = 14.sp), maxLines = 1)
            }
        }
        assertFits("label", lines = 2)
        assertTrue("readable size", layout("label").layoutInput.style.fontSize.value >= readingFloor(14f))
    }

    @Test fun aShortLabelStaysOnOneLineAtItsFullSize() {
        show(1f) {
            Box(Modifier.width(260.dp).height(64.dp)) {
                FittedText("Verbindung suchen", Modifier.testTag("label"), style = TextStyle(fontSize = 14.sp), maxLines = 1)
            }
        }
        assertFits("label", lines = 1)
        assertEquals(14f, layout("label").layoutInput.style.fontSize.value, .01f)
    }

    @Test fun aBoxTooLowForTwoLinesKeepsOneLineInside() {
        show(2f) {
            Box(Modifier.width(160.dp).height(26.dp)) {
                FittedText("Nächste Verbindung suchen", Modifier.testTag("label"), style = TextStyle(fontSize = 14.sp), maxLines = 1)
            }
        }
        assertFits("label", lines = 1)
    }

    /** The screenshot case: with the largest font, "Übersicht" and "Abfahrten" stay inside the capsule. */
    @Test fun pillLabelsStayInsideTheCapsuleAtTheLargestFont() {
        val tabs = listOf(PillDestination("Übersicht", R.drawable.ic_overview), PillDestination("Suchen", R.drawable.ic_search),
            PillDestination("Abfahrten", R.drawable.ic_overview))
        var current by mutableStateOf(AppTheme.CLASSIC_LIGHT)
        compose.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, 2f)) {
                OpenVTheme(AppSettings(appTheme = current, quietMode = true)) {
                    Surface(Modifier.fillMaxSize()) {
                        val pager = rememberPagerState(0) { 3 }
                        Box(Modifier.fillMaxSize()) { Box(Modifier.align(Alignment.BottomCenter)) { ExpressivePillNavigation(pager, tabs, null) {} } }
                    }
                }
            }
        }
        for (theme in listOf(AppTheme.CLASSIC_LIGHT, AppTheme.GAME_PLATFORMER, AppTheme.BRUTAL_POSTER)) {
            compose.runOnIdle { current = theme }
            compose.waitForIdle()
            val bar = compose.onNodeWithTag("expressive-pill-navigation").fetchSemanticsNode().boundsInRoot
            val pad = with(compose.density) { 7.dp.toPx() }
            val slot = (bar.width - 2 * pad) / 3f
            // One size for all three; the capsule at rest is 70 % of the inner height, centred.
            val sizes = tabs.indices.map { layout("pill-label-$it").layoutInput.style.fontSize.value }
            assertEquals("${theme.name} one size $sizes", 1, sizes.distinct().size)
            val capsuleHalf = (bar.height - 2 * pad) * .70f / 2f
            for (i in tabs.indices) {
                val label = compose.onNodeWithTag("pill-label-$i", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                val icon = compose.onAllNodes(hasTestTagStartingWith("pill-icon-$i-"), useUnmergedTree = true).onFirst().fetchSemanticsNode().boundsInRoot
                assertTrue("${theme.name} tab $i symbol and label inside the capsule height",
                    icon.top >= bar.center.y - capsuleHalf - .5f && label.bottom <= bar.center.y + capsuleHalf + .5f)
                val centre = bar.left + pad + slot * (i + .5f)
                val capsule = slot * .82f
                assertTrue("${theme.name} tab $i width ${label.width} capsule $capsule", label.width <= capsule + .5f)
                assertTrue("${theme.name} tab $i centred ${label.center.x} vs $centre", kotlin.math.abs(label.center.x - centre) <= 2f)
                assertTrue("${theme.name} tab $i inside the bar", label.top >= bar.top - .5f && label.bottom <= bar.bottom + .5f)
                assertFits("pill-label-$i", lines = 1)
            }
        }
    }

    /** The choice bubbles of the settings ("Favoriten-Symbol frei bewegen") wrap between words, never inside one. */
    @Test fun aSettingsBubbleNeverBreaksAWord() {
        show(2f) {
            Column(Modifier.width(360.dp)) {
                StepBubbleSetting("Favorit im Netzplan gedrückt halten", listOf("Nichts", "Favoriten-Symbol frei bewegen", "Kompaktansicht öffnen"),
                    1, {}, "bubble")
            }
        }
        assertFits("bubble-value")
    }

    /** The search button keeps its shape; a long label takes two lines between words, nothing runs out. */
    @Test fun theSearchButtonKeepsItsShapeAtTheLargestFont() {
        val label = "Verbindung suchen, damit du am Mittwoch um 08:15 ankommst"
        var height = 0f
        show(2f) {
            height = with(LocalDensity.current) { searchButtonHeight().toPx() }
            Column(Modifier.width(380.dp)) { LiquidSearchAction(null, 1, true, false, {}, {}, label = label) }
        }
        val button = compose.onNodeWithTag("search-submit").fetchSemanticsNode().boundsInRoot
        val text = compose.onNodeWithTag("search-submit-label", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("button keeps its height ${button.height} vs $height", button.height <= height + 16f)
        assertTrue("label inside the button", text.left >= button.left && text.right <= button.right &&
            text.top >= button.top && text.bottom <= button.bottom)
        assertFits("search-submit-label", lines = 2)
    }
}
