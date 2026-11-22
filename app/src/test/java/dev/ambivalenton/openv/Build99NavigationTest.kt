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

/** A tap back to the page being left counts at once, not only after the pages have come to rest. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=OpenVApp::class,qualifiers="de-rDE-w411dp-h891dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Build99NavigationTest {
    private val state=MutableStateFlow(StoredState(onboardingVersion=1,completedTutorials=TutorialPaths.all.map {it.id}.toSet(),
        settings=AppSettings(originMode=OriginMode.MANUAL,manualOrigin=Location("Essen Hbf","e",51.45,7.01,"VRR",LocationKind.STOP),autoRefresh=false,manualOriginChangedAt=System.currentTimeMillis())))
    private val compose=createAndroidComposeRule<MainActivity>()
    private val fixture=object:ExternalResource() {
        override fun before() {
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
    private fun tab(name:String)=compose.onNode(hasText(name) and SemanticsMatcher.expectValue(SemanticsProperties.Role,Role.Tab))
    /** Pager position in pages (0 … 2). */
    private fun position()=compose.onNodeWithTag("main-tabs-pager").fetchSemanticsNode().let {node->
        val range=node.config[SemanticsProperties.HorizontalScrollAxisRange]
        range.value()/(range.maxValue()/2f)
    }
    private fun tap(name:String)=tab(name).performTouchInput {down(center);advanceEventTime(70);up()}

    private fun start() {
        compose.waitForIdle()
        compose.waitUntil(10_000) {compose.onAllNodesWithTag("main-tabs-pager").fetchSemanticsNodes().isNotEmpty()}
        tap("Suchen");compose.waitForIdle()
        assertEquals(1f,position(),.001f)
    }

    @Test fun tappingBackWhileThePagesStillMoveReturnsAtOnce() {
        start()
        compose.mainClock.autoAdvance=false
        tap("Übersicht")
        repeat(14) {compose.mainClock.advanceTimeByFrame()}
        // The page "Übersicht" is visibly almost there, but not yet at rest.
        val left=position()
        assertTrue("on its way to Übersicht: $left",left<.5f)
        tap("Suchen")
        repeat(6) {compose.mainClock.advanceTimeByFrame()}
        assertTrue("turned back towards Suchen: $left -> ${position()}",position()>left)
        repeat(60) {compose.mainClock.advanceTimeByFrame()}
        assertEquals(1f,position(),.001f)
        tab("Suchen").assertIsSelected()
        compose.mainClock.autoAdvance=true
    }

    @Test fun aTappedPageRestsExactlyAndSoonAfterItArrived() {
        start()
        compose.mainClock.autoAdvance=false
        tap("Abfahrten")
        var frames=0
        while(position()!=2f && frames<120) {compose.mainClock.advanceTimeByFrame();frames++}
        // Exactly on the page (no pixel short), about half a second after the tap.
        assertEquals(2f,position(),0f)
        assertTrue("at rest after $frames frames",frames<=36)
        compose.mainClock.autoAdvance=true
    }

    @Test fun theOpenTabFollowsTheRunningChange() {
        // Still a no-op for the page that is open and at rest, and for the page a tap is heading to.
        assertEquals(1,activeHomeTab("search",1,scrolling=false,target=null))
        assertEquals(0,activeHomeTab("search",1,scrolling=true,target=0))
        // A moving pager without a tapped target (a swipe) counts no tab as open.
        assertEquals(-1,activeHomeTab("search",1,scrolling=true,target=null))
        assertEquals(1,activeHomeTab("results",0,scrolling=false,target=null))
        assertEquals(-1,activeHomeTab("settings",0,scrolling=false,target=null))
    }
}
