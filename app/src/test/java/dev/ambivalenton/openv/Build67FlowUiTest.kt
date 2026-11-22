// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import android.Manifest
import android.location.LocationManager
import android.os.SystemClock
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
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The real activity after the setup with one favourite. A second and a third favourite,
 * lines in every direction, tab changes, an activity restart and the new edit bar must work without
 * a crash, with and without the first-use hints. Every write is read back through the real
 * serializer, so a state that could not be opened after a restart fails here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=OpenVApp::class,qualifiers="de-rDE-w411dp-h891dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Build67FlowUiTest {
    private val state=MutableStateFlow(StoredState())
    private lateinit var repo:LocationRepository
    private val provider=OverviewTestProvider()
    private val compose=createAndroidComposeRule<MainActivity>()
    private var tutorials=false
    private var lat=51.5401;private var lon=7.2201
    private val fixture=object:ExternalResource() {
        override fun apply(base:org.junit.runners.model.Statement,description:org.junit.runner.Description):org.junit.runners.model.Statement {
            tutorials=description.methodName.startsWith("hints");return super.apply(base,description)
        }
        override fun before() {
            val app=RuntimeEnvironment.getApplication() as OpenVApp
            shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION)
            val manager=app.getSystemService(LocationManager::class.java)
            shadowOf(manager).setLocationEnabled(true);shadowOf(manager).setProviderEnabled("gps",true)
            val store=object:androidx.datastore.core.DataStore<StoredState> {
                override val data=state
                override suspend fun updateData(transform:suspend (StoredState)->StoredState):StoredState {
                    val next=transform(state.value)
                    val out=java.io.ByteArrayOutputStream();StateSerializer.writeTo(next,out)
                    // Must not throw: this is what the next app start reads.
                    val read=StateSerializer.readFrom(out.toByteArray().inputStream())
                    assertEquals(next.favorites,read.favorites);assertEquals(next.network,read.network)
                    state.value=next
                    return next
                }
            }
            repo=LocationRepository(store)
            runBlocking {
                repo.updateOnboarding {it.copy(favorites=listOf(Favorite("home","Zuhause",Location("Herne Bf","h",51.54,7.22,"T",kind=LocationKind.STOP),icon=FavoriteIcon.HOME)))}
                repo.completeOnboarding()
            }
            if(!tutorials) state.value=state.value.copy(completedTutorials=TutorialPaths.all.map {it.id}.toSet())
            val client=OkHttpClient.Builder().addInterceptor {chain->Response.Builder().request(chain.request())
                .protocol(Protocol.HTTP_1_1).code(200).message("Fixture").body("{}".toResponseBody()).build()}.build()
            app.koin.loadModules(listOf(org.koin.dsl.module {
                single<LocationRepository> {repo}
                single<NetworkClient> {NetworkClient(client)}
                single {OverviewRepository({provider})}
            }))
        }
    }
    @get:Rule val rules:RuleChain=RuleChain.outerRule(fixture).around(compose)
    private fun tab(name:String)=compose.onNode(hasText(name) and SemanticsMatcher.expectValue(SemanticsProperties.Role,Role.Tab))
    private fun fix() {
        val manager=RuntimeEnvironment.getApplication().getSystemService(LocationManager::class.java)
        shadowOf(manager).simulateLocation(android.location.Location("gps").apply {
            latitude=lat;longitude=lon;accuracy=8f
            elapsedRealtimeNanos=SystemClock.elapsedRealtimeNanos();time=System.currentTimeMillis()
        })
    }
    /** Lets time pass with fresh GPS fixes and draws the whole window once (software canvas). */
    private fun settle(millis:Long=2_000) {
        repeat((millis/250).toInt()) {fix();compose.mainClock.advanceTimeBy(250);compose.waitForIdle()}
        compose.runOnIdle {
            val view=compose.activity.window.decorView
            val b=android.graphics.Bitmap.createBitmap(view.width,view.height,android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(b));b.recycle()
        }
    }
    private fun ready() {
        compose.waitUntil(10_000) {compose.onAllNodes(hasText("Suchen") and SemanticsMatcher.expectValue(SemanticsProperties.Role,Role.Tab)).fetchSemanticsNodes().isNotEmpty()}
        settle()
    }
    private val work=Favorite("work","Arbeit",Location("Dortmund Hbf","w",51.51,7.46,"T",kind=LocationKind.STOP),icon=FavoriteIcon.WORK)
    private val gym=Favorite("gym","Sport",Location("Bochum Hbf","g",51.478,7.223,"T",kind=LocationKind.STOP),icon=FavoriteIcon.FITNESS_CENTER)

    private fun moreFavourites(connect:Boolean) {
        ready()
        assertEquals("the setup favourite sits in the first field",GridPoint(0,0),state.value.network.node("home")?.point)
        runBlocking {repo.saveFavorite(work)};settle()
        assertEquals("a new favourite takes the next free field",GridPoint(1,0),state.value.network.node("work")?.point)
        if(connect) runBlocking {repo.connectNodes("home","work","e1")}
        settle()
        tab("Übersicht").performClick();settle(4_000)
        tab("Suchen").performClick();settle(4_000)
        tab("Abfahrten").performClick();settle()
        tab("Suchen").performClick();settle()
        compose.onNodeWithTag("network-edit").performScrollTo().performClick();settle()
        if(connect) {
            runBlocking {repo.setEdgeDirection("e1",DirectionMode.A_TO_B)};settle()
            runBlocking {repo.setEdgeDirection("e1",DirectionMode.B_TO_A)};settle()
        }
        compose.onNodeWithTag("network-done").performScrollTo().performClick();settle()
        runBlocking {repo.saveFavorite(gym)};settle()
        if(connect) runBlocking {repo.connectNodes("work","gym","e2")}
        settle();tab("Übersicht").performClick();settle(4_000)
        compose.activityRule.scenario.recreate();settle(4_000)
        tab("Suchen").performClick();settle(4_000)
        assertEquals(listOf("home","work","gym"),state.value.favorites.map {it.id})
    }

    @Test fun aSecondAndThirdFavouriteWithLinesSurviveTabsAndARestart() {moreFavourites(true)}
    @Test fun unconnectedFavouritesFarFromThePersonWork() {lat=48.137;lon=11.575;moreFavourites(false)}
    @Test fun hintsDoNotBreakTheFlowWithLines() {moreFavourites(true)}

    @Test fun theEditBarOffersPlusAndGearInsteadOfTheList() {
        ready()
        runBlocking {repo.saveFavorite(work);repo.connectNodes("home","work","e1")}
        tab("Suchen").performClick();settle()
        compose.onNodeWithTag("network-edit").performScrollTo().performClick();settle()
        compose.onNodeWithTag("network-list").assertDoesNotExist()
        compose.onNodeWithTag("network-add").assertHasClickAction()
        // The gear opens the favourite settings as a sheet with both new settings.
        compose.onNodeWithTag("network-settings").performClick();settle()
        compose.onNodeWithTag("favorite-settings-sheet").assertExists()
        compose.onNodeWithTag("favorite-radius-setting").assertExists()
        compose.onNodeWithTag("network-grid-setting").assertExists()
        compose.onNodeWithTag("network-grid-value").assertTextEquals("6 × 6")
        // H12: a smaller grid that still holds every favourite keeps positions and lines.
        // Dragging the rectangle is covered by Build67SettingsUiTest; here the same commit path runs
        // through the accessibility action inside the real sheet.
        compose.onNodeWithTag("network-grid-picker").performScrollTo()
        val before=state.value.network.nodes.map {it.point}
        val actions=compose.onNodeWithTag("network-grid-picker").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsActions.CustomActions]
        compose.runOnIdle {actions.first {it.label=="Spalte entfernen"}.action()}
        compose.waitForIdle();settle()
        compose.onNodeWithTag("network-grid-confirm").assertDoesNotExist()
        assertEquals(5 to 6,state.value.network.columns to state.value.network.rows)
        assertEquals(listOf("e1"),state.value.network.edges.map {it.id})
        assertEquals(before,state.value.network.nodes.map {it.point})
        compose.activityRule.scenario.onActivity {it.onBackPressedDispatcher.onBackPressed()};settle()
        // The plus opens "Favorit hinzufügen" directly.
        compose.onNodeWithTag("network-add").performScrollTo().performClick();settle()
        compose.onNodeWithTag("favorite-editor-sheet").assertExists()
        compose.onNodeWithText("Favorit hinzufügen").assertExists()
    }
}
