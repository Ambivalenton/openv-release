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

/**
 * Build 122 · test report: after swapping start and destination and choosing "my location", the search said
 * "Start und Ziel sind gleich" although GPS put the person elsewhere. A favourite's time window took over the
 * start while the last fix was a few minutes old. With the location in use it no longer stands in.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=OpenVApp::class,qualifiers="de-rDE-w411dp-h891dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Build122WindowStartTest {
    private val home=Favorite("h","Zuhause",Location("Herne Bf","h",51.54,7.22,"VRR",LocationKind.STOP),
        hiddenRules=listOf(HighlightRule(days=(1..7).toSet(),fromMinute=0,untilMinute=0)))
    private val state=MutableStateFlow(StoredState(onboardingVersion=1,completedTutorials=TutorialPaths.all.map {it.id}.toSet(),
        favorites=listOf(home),settings=AppSettings(originMode=OriginMode.AUTOMATIC,autoRefresh=false),
        // The last fix is ten minutes old: no longer "fresh".
        presence=DevicePresence(Location("Bochum",null,51.48,7.22),System.currentTimeMillis()-10*60_000L,12f)))
    private val compose=createAndroidComposeRule<MainActivity>()
    private val fixture=object:ExternalResource() {
        override fun before() {
            val app=RuntimeEnvironment.getApplication()
            org.robolectric.Shadows.shadowOf(app).grantPermissions(android.Manifest.permission.ACCESS_FINE_LOCATION,android.Manifest.permission.ACCESS_COARSE_LOCATION)
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

    @Test fun withTheLocationInUseTheTimeWindowDoesNotTakeOverTheStart() {
        compose.waitForIdle()
        compose.waitUntil(10_000) {compose.onAllNodesWithTag("main-tabs-pager").fetchSemanticsNodes().isNotEmpty()}
        tab("Suchen").performClick();compose.waitForIdle()
        compose.onAllNodesWithText("Zuhause (Zeitfenster)",substring=true).assertCountEquals(0)
        compose.onAllNodesWithText("Dein Standort",substring=true).fetchSemanticsNodes().let {assertTrue("start shows the own location",it.isNotEmpty())}
    }

    @Test fun withoutTheLocationTheTimeWindowStillStandsIn() {
        state.value=state.value.copy(settings=state.value.settings.copy(originMode=OriginMode.MANUAL,manualOrigin=null))
        compose.waitForIdle()
        compose.waitUntil(10_000) {compose.onAllNodesWithTag("main-tabs-pager").fetchSemanticsNodes().isNotEmpty()}
        tab("Suchen").performClick();compose.waitForIdle()
        compose.onAllNodesWithText("Zuhause (Zeitfenster)",substring=true).fetchSemanticsNodes().let {assertTrue(it.isNotEmpty())}
    }
}
