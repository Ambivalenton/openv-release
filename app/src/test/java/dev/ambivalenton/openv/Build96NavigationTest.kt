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

/** Every tap on a tab of the navigation pill changes the page at once, in every order. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=OpenVApp::class,qualifiers="de-rDE-w411dp-h891dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Build96NavigationTest {
    private val home=Location("Essen Hbf","e",51.45,7.01,"VRR",LocationKind.STOP)
    private val state=MutableStateFlow(StoredState(onboardingVersion=1,completedTutorials=TutorialPaths.all.map {it.id}.toSet(),
        settings=AppSettings(originMode=OriginMode.MANUAL,manualOrigin=Location("Essen Hbf","e",51.45,7.01,"VRR",LocationKind.STOP),autoRefresh=false,manualOriginChangedAt=System.currentTimeMillis()),
        favorites=listOf(Favorite("w","Arbeit",Location("Bochum Hbf","b",51.48,7.22,"VRR",LocationKind.STOP))),
        network=TransitNetwork(listOf(NetworkNode("w",GridPoint(1,1))),emptyList())))
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
    private fun page()=compose.onNodeWithTag("main-tabs-pager").fetchSemanticsNode().let {node->
        val range=node.config[SemanticsProperties.HorizontalScrollAxisRange]
        Math.round(range.value()/(range.maxValue()/2f))
    }

    @Test fun everyTapChangesThePageTheFirstTime() {
        compose.waitForIdle()
        compose.waitUntil(10_000) {compose.onAllNodesWithTag("main-tabs-pager").fetchSemanticsNodes().isNotEmpty()}
        compose.waitForIdle()
        val order=listOf("Suchen" to 1,"Abfahrten" to 2,"Übersicht" to 0,"Abfahrten" to 2,"Suchen" to 1,"Übersicht" to 0,"Suchen" to 1)
        for((name,expected) in order) {
            tab(name).performTouchInput {down(center);advanceEventTime(60);up()}
            compose.waitForIdle()
            assertEquals("after one tap on $name",expected,page())
            tab(name).assertIsSelected()
        }
    }

    private fun start() {
        compose.waitForIdle()
        compose.waitUntil(10_000) {compose.onAllNodesWithTag("main-tabs-pager").fetchSemanticsNodes().isNotEmpty()}
        compose.waitForIdle()
    }
    private fun tapAndCheck(name:String,expected:Int) {
        tab(name).performTouchInput {down(center);advanceEventTime(60);up()}
        compose.waitForIdle()
        compose.waitUntil(3_000) {compose.onAllNodesWithTag("main-tabs-pager").fetchSemanticsNodes().isNotEmpty()}
        compose.waitForIdle()
        assertEquals("after one tap on $name",expected,page())
        tab(name).assertIsSelected()
    }

    /** Build 107: the settings hide the pill – except "Design & Darstellung" (App-Design), where a tab tap still opens its page at once. */
    @Test fun aTabTapFromTheSettingsOpensThatPageAtOnce() {
        start()
        for((name,expected) in listOf("Abfahrten" to 2,"Übersicht" to 0,"Suchen" to 1)) {
            compose.onNodeWithContentDescription("Einstellungen öffnen").performClick()
            compose.waitForIdle()
            compose.waitUntil(3_000) {compose.onAllNodesWithTag("expressive-pill-navigation").fetchSemanticsNodes().isEmpty()}
            compose.onNodeWithTag("settings-DESIGN").performScrollTo().performClick()
            compose.waitForIdle()
            compose.waitUntil(3_000) {compose.onAllNodesWithTag("expressive-pill-navigation").fetchSemanticsNodes().isNotEmpty()}
            tapAndCheck(name,expected)
        }
    }

    @Test fun aTabTapFromTheResultsOpensThatPageAtOnce() {
        start()
        for((name,expected) in listOf("Übersicht" to 0,"Abfahrten" to 2)) {
            tapAndCheck("Suchen",1)
            compose.onNodeWithTag("network-node-w").performClick()
            compose.waitForIdle()
            compose.waitUntil(5_000) {compose.onAllNodesWithTag("main-tabs-pager").fetchSemanticsNodes().isEmpty()}
            tapAndCheck(name,expected)
        }
    }

    /** Frame by frame as on a device: the animation started by the tap reaches its page without being cut short. */
    @Test fun aTapAnimatesToItsPageFrameByFrame() {
        start()
        compose.mainClock.autoAdvance=false
        for((name,expected) in listOf("Suchen" to 1,"Abfahrten" to 2,"Übersicht" to 0,"Suchen" to 1,"Abfahrten" to 2)) {
            tab(name).performTouchInput {down(center);advanceEventTime(70);up()}
            val positions=mutableListOf<Float>()
            repeat(90) {
                compose.mainClock.advanceTimeByFrame()
                positions+=compose.onNodeWithTag("main-tabs-pager").fetchSemanticsNode().config[SemanticsProperties.HorizontalScrollAxisRange].value()
            }
            assertEquals("after one tap on $name: ${positions.distinct().take(40)}",expected,page())
        }
        compose.mainClock.autoAdvance=true
    }

    private fun framesWith(change:(AppSettings)->AppSettings) {
        compose.runOnIdle {state.value=state.value.copy(settings=change(state.value.settings))}
        start()
        compose.mainClock.autoAdvance=false
        for((name,expected) in listOf("Suchen" to 1,"Abfahrten" to 2,"Übersicht" to 0,"Suchen" to 1,"Abfahrten" to 2,"Suchen" to 1)) {
            tab(name).performTouchInput {down(center);advanceEventTime(70);up()}
            repeat(90) {compose.mainClock.advanceTimeByFrame()}
            assertEquals("after one tap on $name",expected,page())
        }
        compose.mainClock.autoAdvance=true
    }
    @Test fun rainbowModeKeepsTheTap()=framesWith {it.copy(rainbowMode=true)}
    @Test fun aDarkThemeWithDayAndNightKeepsTheTap()=framesWith {it.copy(appTheme=AppTheme.entries.first {t->t.dark},
        dayNight=it.dayNight.copy(schedule=DayNightSchedule.SUN))}
    @Test fun quietAndPowerSavingKeepTheTap()=framesWith {it.copy(quietMode=true)}
}
