// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.data.repository.*
import java.io.File
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

/**
 * Build 101: a fresh install starts with the first view in front of everything. Since build 96 the
 * first view was composed below the main screen and its opaque background, so it could neither be
 * seen nor tapped – the setup never finished and tutorials, the navigation and the GPS start stayed off.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=OpenVApp::class,qualifiers="de-rDE-w411dp-h891dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Build101OnboardingTest {
    private val state=MutableStateFlow(StoredState())
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

    private fun capture(name:String)=compose.runOnIdle {
        val view=compose.activity.window.decorView
        val image=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
        view.draw(Canvas(image))
        val file=File("../audit/build101/$name.png");file.parentFile!!.mkdirs()
        file.outputStream().use {image.compress(Bitmap.CompressFormat.PNG,100,it)};image.recycle()
    }

    private fun awaitTag(tag:String)=compose.waitUntil(10_000) {compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()}

    @Test fun aFreshInstallShowsTheFirstViewInFrontAndItAnswersATap() {
        awaitTag("intro-search")
        capture("fresh-start")
        // A real touch at the button's position: it reaches the button only when nothing lies above it.
        compose.onNodeWithTag("intro-search").performTouchInput {click(center)}
        compose.waitUntil(5_000) {state.value.onboardingVersion==1}
        assertEquals(1,state.value.onboardingVersion)
    }

    @Test fun setUpOpensTheGuidedSetupAboveTheApp() {
        awaitTag("intro-setup")
        compose.onNodeWithTag("intro-setup").performTouchInput {click(center)}
        awaitTag("first-launch-onboarding")
        compose.onNodeWithTag("first-launch-onboarding").assertIsDisplayed()
    }
}
