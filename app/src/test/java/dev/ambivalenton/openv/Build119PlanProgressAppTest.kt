// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.data.repository.*
import dev.ambivalenton.openv.ui.tutorial.TutorialPaths
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
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

/** Build 119 · in the app: a pinned journey between two favourites shows its single progress train on the plan. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=OpenVApp::class,qualifiers="de-rDE-w411dp-h891dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Build119PlanProgressAppTest {
    private val state=MutableStateFlow(StoredState(onboardingVersion=1,completedTutorials=TutorialPaths.all.map {it.id}.toSet(),
        settings=AppSettings(originMode=OriginMode.MANUAL,manualOrigin=Location("Essen Hbf","e",51.45,7.01,"VRR",LocationKind.STOP),autoRefresh=false,
            manualOriginChangedAt=System.currentTimeMillis())))
    private val compose=createAndroidComposeRule<MainActivity>()
    private val fixture=object:ExternalResource() {
        override fun before() {
            val app=RuntimeEnvironment.getApplication()
            runCatching {androidx.work.WorkManager.initialize(app,androidx.work.Configuration.Builder().build())}
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
        override fun after() {runBlocking {(RuntimeEnvironment.getApplication() as OpenVApp).koin.get<PinnedJourneyController>().close()}}
    }
    @get:Rule val rules:RuleChain=RuleChain.outerRule(fixture).around(compose)
    private fun tab(name:String)=compose.onNode(hasText(name) and SemanticsMatcher.expectValue(SemanticsProperties.Role,Role.Tab))
    private fun position()=compose.onNodeWithTag("main-tabs-pager").fetchSemanticsNode().let {node->
        val range=node.config[SemanticsProperties.HorizontalScrollAxisRange]
        range.value()/(range.maxValue()/2f)
    }
    private fun tap(name:String)=tab(name).performTouchInput {down(center);advanceEventTime(70);up()}

    @Test fun theSearchPageShowsTheProgressTrainOfThePinnedJourney() {
        val now=System.currentTimeMillis()
        val hbf=Location("Dortmund Hbf","de:05913:2410",51.5176,7.4590,"VRR",LocationKind.STOP)
        val home=Location("Zuhause, Bahnhofstr. 12, Herne",null,51.5392,7.2210,kind=LocationKind.ADDRESS)
        val herne=Location("Herne Bf","de:05916:5001",51.5440,7.2190,"VRR",LocationKind.STOP)
        state.value=state.value.copy(favorites=listOf(Favorite("hbf","Dortmund Hbf",hbf),Favorite("home","Zuhause",home)),
            network=TransitNetwork(nodes=listOf(NetworkNode("hbf",GridPoint(0,0)),NetworkNode("home",GridPoint(2,1))),edges=listOf(NetworkEdge(id="e",aId="hbf",bId="home"))))
        val platform=Location("Dortmund Hbf","de:05913:2410:12:3",51.5168,7.4592,"VRR",LocationKind.STOP)
        val here=Location("Mein Standort",null,51.5180,7.4600,kind=LocationKind.COORDINATE,isDevicePosition=true)
        val start=now-10*60_000L
        val trip=Trip("t",listOf(
            TripSegment("w1",here,platform,start,start+4*60_000L,LineType.WALK,""),
            TripSegment("re",platform,herne,start+6*60_000L,start+30*60_000L,LineType.REGIONAL,"RE3",
                stops=listOf(Stopover(platform,start+6*60_000L,start+6*60_000L),Stopover(herne,start+30*60_000L,start+30*60_000L))),
            TripSegment("w2",herne,home,start+30*60_000L,start+40*60_000L,LineType.WALK,"")),"VRR",now)
        compose.waitForIdle()
        compose.waitUntil(10_000) {compose.onAllNodesWithTag("main-tabs-pager").fetchSemanticsNodes().isNotEmpty()}
        runBlocking {
            val tracker=(RuntimeEnvironment.getApplication() as OpenVApp).koin.get<PinnedJourneyController>()
            assertTrue(tracker.pin(Favorite("home","Zuhause",home),JourneyQuery(here,home,start),trip,AppSettings()))
            tracker.setMinimized(true)
        }
        tap("Suchen");compose.waitForIdle()
        compose.waitUntil(5_000) {compose.onAllNodesWithTag("network-pinned-vehicle",useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("network-pinned-vehicle",useUnmergedTree=true).assertExists()
        compose.onAllNodesWithTag("network-vehicles",useUnmergedTree=true).assertCountEquals(0)
    }
}
