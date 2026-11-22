// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

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
import org.robolectric.annotation.GraphicsMode

/** Without a start, the overview's two buttons lead somewhere. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=OpenVApp::class,qualifiers="de-rDE-w411dp-h891dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Build98OverviewStartTest {
    private val state=MutableStateFlow(StoredState(onboardingVersion=1,completedTutorials=TutorialPaths.all.map {it.id}.toSet(),
        settings=AppSettings(originMode=OriginMode.MANUAL,manualOrigin=null,autoRefresh=false,manualOriginChangedAt=System.currentTimeMillis()),
        favorites=listOf(Favorite("w","Arbeit",Location("Bochum Hbf","b",51.48,7.22,"VRR",LocationKind.STOP))),
        network=TransitNetwork(listOf(NetworkNode("w",GridPoint(1,1))),emptyList())))
    private val compose=createAndroidComposeRule<MainActivity>()
    private val fixture=object:ExternalResource() {
        override fun before() {
            val app=RuntimeEnvironment.getApplication()
            org.robolectric.Shadows.shadowOf(app).grantPermissions(android.Manifest.permission.ACCESS_FINE_LOCATION,android.Manifest.permission.ACCESS_COARSE_LOCATION)
            org.robolectric.Shadows.shadowOf(app.getSystemService(android.location.LocationManager::class.java)).setLocationEnabled(true)
            val store=object:androidx.datastore.core.DataStore<StoredState> {
                override val data=state
                override suspend fun updateData(transform:suspend (StoredState)->StoredState)=transform(state.value).also {state.value=it}
            }
            val client=OkHttpClient.Builder().addInterceptor {chain->Response.Builder().request(chain.request())
                .protocol(Protocol.HTTP_1_1).code(200).message("Fixture").body("{}".toResponseBody()).build()}.build()
            (RuntimeEnvironment.getApplication() as OpenVApp).koin.loadModules(listOf(org.koin.dsl.module {
                single<LocationRepository> {LocationRepository(store)}
                single<NetworkClient> {NetworkClient(client)}
            }))
        }
    }
    @get:Rule val rules:RuleChain=RuleChain.outerRule(fixture).around(compose)

    private fun start() {
        compose.waitForIdle()
        compose.waitUntil(10_000) {compose.onAllNodesWithTag("overview-start-card").fetchSemanticsNodes().isNotEmpty()}
        compose.waitForIdle()
    }

    /** A calm card with two full-width buttons, no retry and no red error panel. */
    @Test fun withoutAStartTheOverviewOffersBothWays() {
        start()
        compose.onNodeWithText("Wo startest du?").assertExists()
        compose.onAllNodesWithText("Erneut versuchen").assertCountEquals(0)
        val card = compose.onNodeWithTag("overview-start-card").fetchSemanticsNode().boundsInRoot
        val choose = compose.onNodeWithTag("overview-choose-start").fetchSemanticsNode().boundsInRoot
        assertTrue("full width: $choose in $card", choose.width > card.width * .8f)
        compose.onNodeWithTag("overview-choose-start").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("manual-location-dialog").assertExists()
    }

    /** With access and location on, "Meinen Standort nutzen" switches to the automatic start. */
    @Test fun myLocationSwitchesToTheAutomaticStart() {
        start()
        compose.onNodeWithTag("overview-use-location").performClick()
        compose.waitForIdle()
        compose.waitUntil(5_000) {state.value.settings.originMode==OriginMode.AUTOMATIC}
        assertEquals(OriginMode.AUTOMATIC, state.value.settings.originMode)
    }
}
