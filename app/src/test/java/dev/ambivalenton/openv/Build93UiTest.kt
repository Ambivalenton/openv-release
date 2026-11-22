// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.ui.components.*
import dev.ambivalenton.openv.ui.network.NetworkTargetChoiceDialog
import dev.ambivalenton.openv.ui.screens.PersonalThemeUiState
import dev.ambivalenton.openv.ui.screens.PersonalThemesMenu
import dev.ambivalenton.openv.ui.screens.SettingsPage
import dev.ambivalenton.openv.ui.screens.SettingsSearch
import dev.ambivalenton.openv.ui.screens.SettingsSearchEntry
import dev.ambivalenton.openv.ui.theme.OpenVTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The new controls of the UX report at their real composables. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "de-rDE-w360dp-h780dp-mdpi")
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
class Build93UiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun content(settings: AppSettings = AppSettings(quietMode = true), body: @Composable () -> Unit) =
        compose.setContent { OpenVTheme(settings) { Surface(Modifier.fillMaxSize()) { body() } } }

    /** M11: typed words find the setting, show its path and jump there. */
    @Test fun theSettingsSearchShowsThePathAndOpensTheHit() {
        var opened: SettingsSearchEntry? = null
        content { Column(Modifier.verticalScroll(rememberScrollState())) { SettingsSearch { opened = it } } }
        compose.onNodeWithTag("settings-search-field").performTextInput("Backup")
        compose.onNodeWithText("Einstellungen › Modi & Verhalten").assertIsDisplayed()
        compose.onNodeWithText("Sichern & Übertragen").performClick()
        compose.runOnIdle { assertEquals(SettingsPage.MODES, opened!!.page); assertEquals("backup", opened!!.anchor) }
        compose.onNodeWithTag("settings-search-field").performTextInput("Zzzz")
        compose.onNodeWithTag("settings-search-empty").assertExists()
    }

    /** 2.8: only an unbroken hold of three seconds opens the secret; a tap still opens the page. */
    @Test fun theSecretNeedsThreeSecondsAndSwallowsTheTap() {
        var taps = 0; var secrets = 0
        content {
            Box(Modifier.secretHold { secrets++ }) {
                Box(Modifier.size(200.dp, 80.dp).testTag("design-tile").clickable { taps++ })
            }
        }
        compose.onNodeWithTag("design-tile").performTouchInput { down(center); advanceEventTime(1_500); up() }
        compose.runOnIdle { assertEquals(1, taps); assertEquals(0, secrets) }
        compose.onNodeWithTag("design-tile").performTouchInput { down(center); advanceEventTime(3_100); up() }
        compose.runOnIdle { assertEquals("no extra tap after the secret", 1, taps); assertEquals(1, secrets) }
        // Moving away cancels the hold.
        compose.onNodeWithTag("design-tile").performTouchInput { down(center); moveBy(androidx.compose.ui.geometry.Offset(60f, 0f)); advanceEventTime(3_100); up() }
        compose.runOnIdle { assertEquals(1, secrets) }
    }

    /** 2.8: both switches start off and change independently. */
    @Test fun theSecretWindowHasTwoIndependentSwitches() {
        var settings by mutableStateOf(AppSettings(quietMode = true))
        var randomOff = 0
        content { SecretDesignDialog(settings, { change -> settings = change(settings) }, { randomOff++ }) {} }
        compose.onNodeWithText("Zufälliges Theme beim App-Start").assertIsDisplayed().performClick()
        compose.runOnIdle { assertTrue(settings.randomThemeOnStart); assertFalse(settings.rainbowMode) }
        compose.onNodeWithText("Regenbogen-Modus").performClick()
        compose.runOnIdle { assertTrue(settings.rainbowMode) }
        compose.onNodeWithText("Zufälliges Theme beim App-Start").performClick()
        compose.runOnIdle { assertFalse(settings.randomThemeOnStart); assertTrue(settings.rainbowMode); assertEquals(1, randomOff) }
    }

    /** 2.10: sun and moon say where the next tap leads. */
    @Test fun theDayNightButtonNamesTheNextChange() {
        var settings by mutableStateOf(AppSettings(quietMode = false, appTheme = AppTheme.CLASSIC_LIGHT))
        content { DayNightButton(settings, { settings = settings.withDayNight(settings.nextDayNightIsNight(false)) }) }
        compose.onNodeWithContentDescription("Zum Nacht-Theme wechseln").assertHasClickAction().performClick()
        compose.runOnIdle { assertTrue(settings.appTheme!!.dark) }
        compose.onNodeWithContentDescription("Zum Tag-Theme wechseln").performClick()
        compose.runOnIdle { assertEquals(AppTheme.CLASSIC_LIGHT, settings.appTheme) }
    }

    /** 2.10: "Nur von Hand" greys out the times; the sun schedule shows today's times. */
    @Test fun theDayNightFrameGreysOutTimesThatDoNotApply() {
        var settings by mutableStateOf(AppSettings(quietMode = true))
        val place = Location("Essen", latitude = 51.45, longitude = 7.01)
        content { Column(Modifier.verticalScroll(rememberScrollState())) { DayNightFrame(settings, { settings = it(settings) }, place) } }
        compose.onNodeWithTag("day-night-times").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Disabled))
        compose.onNodeWithText("Nur von Hand").performClick()
        compose.runOnIdle { assertEquals(DayNightSchedule.TIMES, settings.dayNight.schedule) }
        compose.onNodeWithTag("day-night-times").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Disabled))
        compose.onNodeWithText("Nach Sonnenauf- und -untergang").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(DayNightSchedule.SUN, settings.dayNight.schedule) }
        compose.onNodeWithTag("day-night-sun").assertExists()
    }

    /** 2.9: the floppy disk saves; folding stays with the title and the arrow. */
    @Test fun theFloppyDiskSavesWithoutFoldingTheMenu() {
        val state = PersonalThemeUiState()
        content { PersonalThemesMenu(StoredState(), state, AppLogo.ORIGINAL, {}, {}) }
        compose.onNodeWithContentDescription("Persönliches Theme speichern").assertIsDisplayed().performClick()
        compose.runOnIdle { assertTrue(state.dialogOpen); assertFalse(state.expanded) }
        state.dismiss()
        // The space around the button belongs to it as well.
        compose.onNodeWithTag("save-personal-theme-area").performTouchInput { click(androidx.compose.ui.geometry.Offset(4f, 4f)) }
        compose.runOnIdle { assertTrue(state.dialogOpen); assertFalse(state.expanded) }
        state.dismiss()
        compose.onNodeWithTag("personal-themes-toggle").performClick()
        compose.runOnIdle { assertTrue(state.expanded); assertFalse(state.dialogOpen) }
    }

    /** H13: a long title wraps between words instead of shrinking into illegibility. */
    @Test fun fittedTextWrapsBeforeItBecomesTiny() {
        var layout: TextLayoutResult? = null
        content { Column { Box(Modifier.width(150.dp)) {
            FittedText("Haltestellen in deiner Nähe", style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                onTextLayout = { layout = it }, modifier = Modifier.testTag("fitted"))
        } } }
        compose.runOnIdle {
            val result = layout!!
            assertEquals(2, result.lineCount)
            assertTrue("still readable: ${result.layoutInput.style.fontSize}", result.layoutInput.style.fontSize.value >= 16f)
            assertTrue("no break inside a word", breaksOnlyBetweenWords(result))
        }
    }

    /** Two words in a narrow button break between the words, never inside one. */
    @Test fun fittedTextNeverBreaksInsideAWord() {
        var layout: TextLayoutResult? = null
        content { Column { Box(Modifier.width(110.dp)) {
            FittedText("Sicherung wiederherstellen", style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                onTextLayout = { layout = it })
        } } }
        compose.runOnIdle {
            val result = layout!!
            assertTrue(breaksOnlyBetweenWords(result))
            assertEquals(2, result.lineCount)
            assertEquals("Sicherung ", result.layoutInput.text.text.substring(0, result.getLineEnd(0)))
        }
    }

    /** M13: both orders are named choices; the stay can be typed. */
    @Test fun waypointOrderAndTypedMinutes() {
        var order by mutableStateOf(WaypointOrder.OPTIMIZED); var stay by mutableFloatStateOf(0f)
        content { Column {
            WaypointOrderChoice(order, { order = it })
            WaypointStaySlider(stay, { stay = it }, tag = "stay")
        } }
        compose.onNodeWithText("Meine Reihenfolge verwenden").performClick()
        compose.runOnIdle { assertEquals(WaypointOrder.MANUAL, order) }
        compose.onNodeWithText("Die Stopps werden genau in dieser Reihenfolge angefahren, auch mit Umweg.").assertExists()
        compose.onNodeWithTag("stay-minutes").performTextReplacement("25")
        compose.runOnIdle { assertEquals(25f, stay, .01f) }
        compose.onNodeWithTag("stay-minutes").performTextReplacement("999")
        compose.runOnIdle { assertEquals("at most 120 minutes", 120f, stay, .01f) }
    }

    /** M17: close targets on a dense plan are offered by name instead of guessed. */
    @Test fun closeTargetsAreOfferedByName() {
        val a = Favorite("a", "Zuhause", Location("A", "a", 51.0, 7.0, "T")); val b = Favorite("b", "Arbeit", Location("B", "b", 51.1, 7.1, "T"))
        val store = StoredState(favorites = listOf(a, b), network = TransitNetwork(listOf(NetworkNode("a", GridPoint(0, 0)), NetworkNode("b", GridPoint(1, 0))),
            listOf(NetworkEdge("l", "a", "b"))))
        var picked: String? = null
        content { NetworkTargetChoiceDialog(store, listOf("a", "b"), emptyList(), { picked = it }, {}, {}) }
        compose.onNodeWithText("Welches Ziel meinst du?").assertIsDisplayed()
        compose.onNodeWithTag("network-choice-node-b").assertHeightIsAtLeast(48.dp).performClick()
        compose.runOnIdle { assertEquals("b", picked) }
    }
}
