// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.ui.components.LocalFavoritePlaces
import dev.ambivalenton.openv.ui.components.PlaceSearchField
import dev.ambivalenton.openv.ui.dialogs.FavoriteEditDialog
import dev.ambivalenton.openv.ui.dialogs.FavoriteSavedStep
import dev.ambivalenton.openv.ui.theme.OpenVTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The new add-favourite flow, walked through as three people would (one task each):
 * Pendlerin (a recent destination, two taps), Student (role first, then the place) and a person with
 * large system font (everything reachable, nothing cut). Plus the search field details and the banner.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "de-rDE-w380dp-h1600dp-mdpi")
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
class Build98FavoriteFlowTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val home = Favorite("h", "Zuhause", Location("Herne Bf", "h", 51.54, 7.22, "VRR", LocationKind.STOP), icon = FavoriteIcon.HOME, accentSeed = 0xFFD32F2FL)
    private val uni = Location("Bochum Ruhr-Universität", "r", 51.44, 7.26, "VRR", LocationKind.STOP)
    private val zoo = Location("Gelsenkirchen, Zoom Erlebniswelt", "z", 51.54, 7.06, "VRR", LocationKind.STOP)

    private fun open(onSave: (Favorite) -> Unit, search: suspend (String) -> List<Location> = { listOf(zoo) }) = compose.setContent {
        OpenVTheme(AppSettings(quietMode = true)) {
            CompositionLocalProvider(LocalFavoritePlaces provides listOf(home)) {
                Surface(Modifier.fillMaxSize()) {
                    FavoriteEditDialog(null, "VRR", search, onSave, {},
                        suggestions = listOf(FavoritePlaceSuggestion(uni, 3)), otherFavorites = listOf(home))
                }
            }
        }
    }

    /** Pendlerin: a destination she searched before is one tap, saving the second. */
    @Test fun aRecentDestinationIsTwoTapsAway() {
        var saved: Favorite? = null
        open({ saved = it })
        compose.waitForIdle()
        // The other favourites are no suggestions here; the recent destination is.
        compose.onAllNodesWithText("Herne Bf").assertCountEquals(0)
        compose.onNodeWithTag("favorite-suggestion-0").assertExists().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("favorite-hero-place").assertExists()
        compose.onNodeWithTag("favorite-save").assertIsEnabled().performClick()
        compose.runOnIdle {
            val favorite = saved!!
            assertEquals("Bochum Ruhr-Universität", favorite.label)
            assertEquals(FavoriteIcon.SCHOOL, favorite.icon)
            assertNotNull("a colour is chosen right away", favorite.accentSeed)
            assertNotEquals("apart from Zuhause", home.accentSeed, favorite.accentSeed)
        }
    }

    /** Student: picks "Arbeit" first, then searches the place; the role stays the name. */
    @Test fun aRoleChosenFirstKeepsItsName() {
        var saved: Favorite? = null
        open({ saved = it })
        // Build 101: saving belongs to the second beat; the search comes first.
        compose.onNodeWithTag("favorite-save").assertDoesNotExist()
        compose.onNodeWithTag("favorite-intent-WORK").performClick()
        compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("favorite-place-search"))).performTextInput("Zoom")
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Gelsenkirchen, Zoom Erlebniswelt").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText("Gelsenkirchen, Zoom Erlebniswelt").onFirst().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("favorite-save").performClick()
        compose.runOnIdle {
            assertEquals("Arbeit", saved!!.label)
            assertEquals(FavoriteIcon.WORK, saved!!.icon)
        }
    }

    /** Everything else waits behind "Weitere Optionen"; "Mehr" at the colours opens it. */
    @Test fun moreOptionsStayOutOfTheWayButAreReachable() {
        open({})
        compose.onNodeWithTag("favorite-suggestion-0").performClick()
        compose.waitForIdle()
        compose.onAllNodesWithText("Eigene Farbe auswählen").assertCountEquals(0)
        compose.onNodeWithTag("favorite-color-more").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Eigene Farbe auswählen").assertExists()
    }

    /** Build 101: after saving, the saved moment offers the next step – a line to "Zuhause", another favourite, done. */
    @Test fun theSavedMomentOffersTheNextStep() {
        val added = Favorite("a", "Arbeit", zoo, icon = FavoriteIcon.WORK)
        var connected: Favorite? = null
        var another = 0
        var done = 0
        var partner by mutableStateOf<Favorite?>(home)
        var joined by mutableStateOf(false)
        compose.setContent {
            OpenVTheme(AppSettings(quietMode = true)) {
                Column { FavoriteSavedStep(added, partner, joined, { connected = it; joined = true }, { another++ }, { done++ }) }
            }
        }
        compose.onNodeWithText("„Arbeit“ ist gespeichert").assertExists()
        compose.onNodeWithTag("new-favorite-connect").assertTextContains("Mit „Zuhause“ verbinden", substring = true).performClick()
        compose.runOnIdle { assertEquals(home, connected) }
        compose.onNodeWithText("Verbunden. Die Übersicht zeigt jetzt die Fahrt zwischen beiden.").assertExists()
        compose.onNodeWithTag("new-favorite-connect").assertDoesNotExist()
        compose.onNodeWithTag("new-favorite-add-another").performClick()
        compose.onNodeWithTag("new-favorite-done").performClick()
        compose.runOnIdle { assertEquals(1, another); assertEquals(1, done) }
        // Without a partner (the very first favourite) only "Noch einen Favoriten" and "Fertig" remain.
        joined = false; partner = null
        compose.onNodeWithTag("new-favorite-connect").assertDoesNotExist()
        compose.onNodeWithTag("new-favorite-add-another").assertExists()
    }

    /** The pull gesture's request empties the field once per opening, also when the field is new. */
    @Test fun aClearRequestEmptiesTheFieldReliably() {
        var request by mutableIntStateOf(0)
        var shown by mutableStateOf(true)
        compose.setContent {
            OpenVTheme(AppSettings(quietMode = true)) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    if (shown) PlaceSearchField("Start", home.location, "VRR", { emptyList() }, {}, clearText = request)
                }
            }
        }
        compose.onNodeWithText("Herne Bf").assertExists()
        request = 1; compose.waitForIdle()
        compose.onAllNodesWithText("Herne Bf").assertCountEquals(0)
        // Closed (request 0) and pulled again: empty again – also for a newly shown field.
        request = 0; shown = false; compose.waitForIdle()
        request = 1; shown = true; compose.waitForIdle()
        compose.onAllNodesWithText("Herne Bf").assertCountEquals(0)
        // Opened by tap (request 0) keeps the place.
        shown = false; request = 0; compose.waitForIdle(); shown = true; compose.waitForIdle()
        compose.onNodeWithText("Herne Bf").assertExists()
    }
}
