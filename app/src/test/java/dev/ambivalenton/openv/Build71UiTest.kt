// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.ui.screens.JourneyDetailsSheet
import dev.ambivalenton.openv.ui.theme.OpenVTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A connection opened from the overview is the only one there is. The arrow at the top
 * right led back to a list with that same single connection; it is gone whenever only one
 * connection can be shown. With several connections the arrow and the list stay.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = OpenVApp::class, qualifiers = "de-rDE-w411dp-h891dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Build71UiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val dortmund = Location("Dortmund Hbf", "d", 51.51, 7.46, "VRR")
    private val herne = Location("Herne Bf", "h", 51.54, 7.22, "VRR")
    private fun trip(id: String, minute: Int): Trip {
        val start = System.currentTimeMillis() + minute * 60_000L
        return Trip(id, listOf(TripSegment("r$id", dortmund, herne, start, start + 960_000, LineType.REGIONAL, "RE3", realtimeDeparture = true)), "TEST", start)
    }

    @Test fun theOnlyConnectionFromTheOverviewHasNoArrowAndATapOpensNoList() {
        var listRequests = 0
        val only = trip("a", 30)
        compose.setContent {
            OpenVTheme(AppSettings(quietMode = true)) {
                JourneyDetailsSheet(listOf(only), only.id, onDismiss = {}, onShowList = { listRequests++ },
                    detailsContext = JourneyDetailsContext.Overview("Zuhause"))
            }
        }
        compose.onNodeWithText("Verbindung nach Zuhause").assertIsDisplayed()
        compose.onAllNodesWithTag("collapse-connection-details").assertCountEquals(0)
        compose.onAllNodesWithContentDescription("Alle Verbindungen anzeigen").assertCountEquals(0)
        // The route stays open, and tapping the times does not switch to a list.
        compose.onAllNodesWithText("RE3", substring = true).onFirst().assertExists()
        compose.onAllNodesWithText(" – ", substring = true).onFirst().performClick()
        compose.runOnIdle { assertEquals(0, listRequests) }
        compose.onNodeWithTag("connection-list").assertDoesNotExist()
        // Sharing by long press is still offered to TalkBack.
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.OnLongClick) and hasAnyDescendant(hasText(" – ", substring = true)))
            .assertExists()
    }

    @Test fun severalConnectionsKeepTheArrowBackToTheList() {
        var listRequests = 0
        val trips = listOf(trip("a", 30), trip("b", 45))
        compose.setContent {
            OpenVTheme(AppSettings(quietMode = true)) {
                JourneyDetailsSheet(trips, trips.first().id, onDismiss = {}, onShowList = { listRequests++ })
            }
        }
        compose.onNodeWithText("Verbindung 1/2").assertIsDisplayed()
        compose.onAllNodesWithContentDescription("Alle Verbindungen anzeigen").onFirst().performClick()
        compose.runOnIdle { assertEquals(1, listRequests) }
    }
}
