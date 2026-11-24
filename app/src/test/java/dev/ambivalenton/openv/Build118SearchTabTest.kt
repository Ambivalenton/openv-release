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

/** Build 118 · after removing the pinned journey every tab answers, "Suchen" included. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=OpenVApp::class,qualifiers="de-rDE-w411dp-h891dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Build118SearchTabTest {
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

    private fun pin(underWay:Boolean,minimized:Boolean=true) {
        val now=System.currentTimeMillis()
        val a=Location("Essen Hbf","e",51.45,7.01,"VRR",LocationKind.STOP)
        val b=Location("Bochum Hbf","b",51.478,7.223,"VRR",LocationKind.STOP)
        val start=if(underWay) now-5*60_000L else now+20*60_000L
        val trip=Trip("t",listOf(TripSegment("re",a,b,start,start+25*60_000L,LineType.REGIONAL,"RE1",departurePlatform="2",arrivalPlatform="5")),"VRR",now)
        runBlocking {
            val tracker=(RuntimeEnvironment.getApplication() as OpenVApp).koin.get<PinnedJourneyController>()
            assertTrue(tracker.pin(Favorite("f","Bochum",b),JourneyQuery(a,b,start),trip,AppSettings()))
            // As after leaving the journey's timeline: the card waits on the overview.
            if(minimized) tracker.setMinimized(true)
        }
    }

    private fun removeThenSearch(underWay:Boolean) {
        pin(underWay)
        compose.waitForIdle()
        compose.waitUntil(10_000) {compose.onAllNodesWithTag("main-tabs-pager").fetchSemanticsNodes().isNotEmpty()}
        tap(if(underWay) "Unterwegs" else "Übersicht");compose.waitForIdle()
        assertEquals(0f,position(),.001f)
        compose.waitUntil(5_000) {compose.onAllNodesWithTag("remove-pinned-card").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("remove-pinned-card").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Entfernen").performClick()
        compose.waitForIdle()
        compose.waitUntil(5_000) {compose.onAllNodesWithTag("remove-pinned-card").fetchSemanticsNodes().isEmpty()}
        tap("Suchen");compose.waitForIdle()
        assertEquals("Suchen answers after removing the journey",1f,position(),.001f)
        tab("Suchen").assertIsSelected()
        tap("Abfahrten");compose.waitForIdle()
        assertEquals(2f,position(),.001f)
        tap("Suchen");compose.waitForIdle()
        assertEquals(1f,position(),.001f)
    }

    /** The journey's timeline is open (just pinned) and is closed with "back": then every tab answers. */
    private fun closeTimelineThenSearch(underWay:Boolean,viaButton:Boolean=false) {
        compose.waitForIdle()
        compose.waitUntil(10_000) {compose.onAllNodesWithTag("main-tabs-pager").fetchSemanticsNodes().isNotEmpty()}
        tap("Suchen");compose.waitForIdle()
        pin(underWay,minimized=false)
        compose.waitForIdle()
        if(viaButton) compose.onNodeWithTag("close-pinned-journey").performClick()
        else compose.activityRule.scenario.onActivity {it.onBackPressedDispatcher.onBackPressed()}
        compose.waitForIdle()
        compose.waitUntil(5_000) {compose.onAllNodesWithTag("remove-pinned-card").fetchSemanticsNodes().isNotEmpty()}
        assertEquals(0f,position(),.001f)
        tap("Suchen");compose.waitForIdle()
        assertEquals("Suchen answers after closing the timeline",1f,position(),.001f)
        tap(if(underWay) "Unterwegs" else "Übersicht");compose.waitForIdle()
        compose.onNodeWithTag("remove-pinned-card").performClick();compose.waitForIdle()
        compose.onNodeWithText("Entfernen").performClick();compose.waitForIdle()
        tap("Suchen");compose.waitForIdle()
        assertEquals("Suchen answers after removing the journey",1f,position(),.001f)
    }

    @Test fun searchAnswersAfterClosingTheTimelineOfAJourneyUnderWay()=closeTimelineThenSearch(underWay=true)

    @Test fun searchAnswersAfterClosingTheTimelineWithItsButtonUnderWay()=closeTimelineThenSearch(underWay=true,viaButton=true)

    @Test fun searchAnswersAfterClosingTheTimelineWithItsButton()=closeTimelineThenSearch(underWay=false,viaButton=true)

    @Test fun searchAnswersAfterClosingTheTimelineOfAJourneyNotYetStarted()=closeTimelineThenSearch(underWay=false)

    /** The case of the screen video: minimalist mode with a board stop. "Suchen" must stay "Suchen". */
    @Test fun searchStaysWhereItWasTappedInMinimalistMode() {
        val dortmund=Location("Dortmund Hbf","d",51.517,7.459,"VRR",LocationKind.STOP)
        val herne=Location("Herne","h",51.54,7.22,"VRR",LocationKind.STOP)
        val muenster=Location("Münster Hbf","m",51.956,7.635,"VRR",LocationKind.STOP)
        val now=System.currentTimeMillis()
        val tomorrow=now+20*3_600_000L
        val saved=SavedJourney(favorite=Favorite("m","Münster",muenster),query=JourneyQuery(dortmund,muenster,tomorrow),
            trip=Trip("s",listOf(TripSegment("re",dortmund,muenster,tomorrow,tomorrow+80*60_000L,LineType.REGIONAL,"RE6")),"VRR",now))
        state.value=state.value.copy(settings=state.value.settings.copy(minimalistMode=true),boardStops=listOf(dortmund),
            favorites=listOf(Favorite("h","Zuhause",herne),Favorite("w","Arbeit",dortmund),Favorite("m","Münster",muenster)),
            network=TransitNetwork(nodes=listOf(NetworkNode("h",GridPoint(0,0)),NetworkNode("w",GridPoint(2,1)),NetworkNode("m",GridPoint(1,2))),
                edges=listOf(NetworkEdge(aId="h",bId="w"),NetworkEdge(aId="w",bId="m"))),
            savedJourneys=listOf(saved),gestureHints=GestureHintProgress(startedAt=now))
        compose.waitForIdle()
        compose.waitUntil(10_000) {compose.onAllNodesWithTag("main-tabs-pager").fetchSemanticsNodes().isNotEmpty()}
        tap("Übersicht");compose.waitForIdle()
        assertEquals(0f,position(),.001f)
        tap("Suchen");compose.waitForIdle()
        compose.mainClock.advanceTimeBy(3_000);compose.waitForIdle()
        assertEquals("Suchen from Übersicht",1f,position(),.001f)
        tap("Abfahrten");compose.waitForIdle()
        assertEquals(2f,position(),.001f)
        tap("Suchen");compose.waitForIdle()
        compose.mainClock.advanceTimeBy(3_000);compose.waitForIdle()
        assertEquals("Suchen from Abfahrten",1f,position(),.001f)
    }

    @Test fun searchAnswersAfterRemovingAJourneyNotYetStarted()=removeThenSearch(underWay=false)

    @Test fun searchAnswersAfterRemovingAJourneyUnderWay()=removeThenSearch(underWay=true)
}
