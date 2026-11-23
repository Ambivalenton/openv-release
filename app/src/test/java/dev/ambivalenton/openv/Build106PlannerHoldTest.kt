// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.data.repository.*
import dev.ambivalenton.openv.ui.tutorial.TutorialPaths
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Build 106 · Planermodus: holding a favourite on the plan enters it as the start in the field above
 * (the default there); "In Übersicht anzeigen" is not offered, since the planner has no overview.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = OpenVApp::class, qualifiers = "de-rDE-w411dp-h891dp-xhdpi")
class Build106PlannerHoldTest {
    private val home = Favorite("home", "Zuhause", Location("Herne Bf", "h", 51.54, 7.22, "VRR", LocationKind.STOP), icon = FavoriteIcon.HOME, accentSeed = 0xFF245EA8)
    private val work = Favorite("work", "Arbeit", Location("Dortmund Hbf", "d", 51.517, 7.459, "VRR", LocationKind.STOP), icon = FavoriteIcon.WORK, accentSeed = 0xFFC2185B)
    private val state = MutableStateFlow(StoredState(onboardingVersion = 1, completedTutorials = TutorialPaths.all.map { it.id }.toSet(),
        favorites = listOf(home, work),
        network = TransitNetwork(listOf(NetworkNode("home", GridPoint(1, 3)), NetworkNode("work", GridPoint(4, 3))),
            listOf(NetworkEdge("line", "home", "work")), columns = 6, rows = 6),
        // A former "In Übersicht anzeigen" choice outside the planner must not reach into it.
        settings = AppSettings(plannerMode = true, quietMode = true, autoRefresh = false, networkHoldAction = NetworkHoldAction.OVERVIEW)))
    private val compose = createAndroidComposeRule<MainActivity>()
    private val fixture = object : ExternalResource() {
        override fun before() {
            val store = object : androidx.datastore.core.DataStore<StoredState> {
                override val data = state
                override suspend fun updateData(transform: suspend (StoredState) -> StoredState) = transform(state.value).also { state.value = it }
            }
            val client = OkHttpClient.Builder().addInterceptor { chain -> Response.Builder().request(chain.request())
                .protocol(Protocol.HTTP_1_1).code(200).message("Fixture").body("{}".toResponseBody()).build() }.build()
            (RuntimeEnvironment.getApplication() as OpenVApp).koin.loadModules(listOf(org.koin.dsl.module {
                single<LocationRepository> { LocationRepository(store) }
                single<NetworkClient> { NetworkClient(client) }
            }))
        }
    }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(fixture).around(compose)

    @Test fun thePlannerHasItsOwnHoldChoiceWithoutTheOverview() {
        // Default in the planner: the favourite becomes the start; outside it everything stays as before.
        assertEquals(NetworkHoldAction.LOCATION, AppSettings(plannerMode = true).holdAction)
        assertEquals(NetworkHoldAction.LOCATION, AppSettings(plannerMode = true, networkHoldAction = NetworkHoldAction.OVERVIEW).holdAction)
        assertEquals(NetworkHoldAction.LOCATION, AppSettings(plannerMode = true, plannerHoldAction = NetworkHoldAction.OVERVIEW).holdAction)
        assertEquals(NetworkHoldAction.PIN, AppSettings(plannerMode = true, plannerHoldAction = NetworkHoldAction.PIN).holdAction)
        assertEquals(NetworkHoldAction.MOVE, AppSettings().holdAction)
        assertEquals(NetworkHoldAction.OVERVIEW, AppSettings(networkHoldAction = NetworkHoldAction.OVERVIEW).holdAction)
        assertFalse(NetworkHoldAction.OVERVIEW in networkHoldChoices(true))
        assertEquals(NetworkHoldAction.entries, networkHoldChoices(false))
        assertEquals("Als Startpunkt setzen", NetworkHoldAction.LOCATION.titleFor(true))
        assertEquals(NetworkHoldAction.LOCATION.title, NetworkHoldAction.LOCATION.titleFor(false))
        // Older stores without the new field keep their own choice and get the planner default.
        val old = StateSerializer.json.decodeFromString(StoredState.serializer(), """{"settings":{"networkHoldAction":"PIN","plannerMode":true}}""")
        assertEquals(NetworkHoldAction.PIN, old.settings.networkHoldAction)
        assertEquals(NetworkHoldAction.LOCATION, old.settings.holdAction)
    }

    @Test fun holdingAFavouriteEntersItAsTheStart() {
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("network-plan").fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasText("Dortmund Hbf") and hasAnyAncestor(hasTestTag("planner-start-field")), useUnmergedTree = true).assertDoesNotExist()
        val plan = compose.onNodeWithTag("network-plan").fetchSemanticsNode().boundsInRoot
        val pitch = plan.width / 6f
        compose.onNodeWithTag("network-plan").performTouchInput { down(Offset(4.5f * pitch, 3.5f * pitch)); advanceEventTime(900); up() }
        compose.waitForIdle()
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasText("Dortmund Hbf") and hasAnyAncestor(hasTestTag("planner-start-field")), useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        // Nothing else was set: no location override, no overview pin, the plan stays as it is.
        assertNull(state.value.locationOverride)
        assertTrue(state.value.overviewPins.isEmpty())
        assertEquals(listOf("home", "work"), state.value.favorites.map { it.id })
    }
}
