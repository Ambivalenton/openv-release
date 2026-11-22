// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.ui.AppUiState
import dev.ambivalenton.openv.ui.AppViewModel
import dev.ambivalenton.openv.ui.components.*
import dev.ambivalenton.openv.ui.screens.SearchScreen
import dev.ambivalenton.openv.ui.theme.OpenVTheme
import org.junit.Assert.*
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=OpenVApp::class,qualifiers="de-rDE-w411dp-h891dp-mdpi")
class SearchLayoutUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    @After fun clearAnchor()=ProximityMemory.reset()
    @Test fun nearbyHintFollowsThePlanAndTheOverviewPinDoesNotAddATextBlock() {
        ProximityMemory.reset()
        val home=Favorite("home","Zuhause",Location("Herne","h",51.54,7.22,"test"))
        val work=Favorite("work","Arbeit",Location("Dortmund","w",51.51,7.46,"test"))
        val settings=AppSettings(quietMode=true,originMode=OriginMode.AUTOMATIC,networkHoldAction=NetworkHoldAction.OVERVIEW)
        val store=StoredState(favorites=listOf(home,work),settings=settings,overviewFavoriteId=home.id,
            presence=DevicePresence(home.location,System.currentTimeMillis(),8f),network=TransitNetwork(
                nodes=listOf(NetworkNode(home.id,GridPoint(0,1)),NetworkNode(work.id,GridPoint(5,1))),
                edges=listOf(NetworkEdge("line",home.id,work.id)),rows=3))
        val vm=(compose.activity.application as OpenVApp).koin.get<AppViewModel>()
        compose.setContent {
            val overlays=remember {AppOverlayState()}
            CompositionLocalProvider(LocalAppOverlay provides overlays) {
                OpenVTheme(settings) {AppOverlayHost(overlays,Modifier.fillMaxSize()) {Surface {
                    SearchScreen(AppUiState(store=store,ready=true),vm,{_,_,_->},{_,_,_->},{})
                }}}
            }
        }
        compose.onNodeWithTag("network-start-hint").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("exact-location").assertIsDisplayed()
        val hint=compose.onNodeWithTag("network-start-hint").fetchSemanticsNode().boundsInRoot
        val plan=compose.onNodeWithTag("network-plan").fetchSemanticsNode().boundsInRoot
        assertTrue("the hint sits below the map",hint.top>=plan.bottom)
        compose.onNodeWithText("Zuhause ist in der Übersicht angeheftet.").assertDoesNotExist()
        compose.onNodeWithTag("overview-pin-clear").assertDoesNotExist()
        assertEquals(home.id,store.overviewFavoriteId)
    }
}
