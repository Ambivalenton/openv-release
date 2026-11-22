// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.*
import androidx.compose.ui.zIndex
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.ui.*
import dev.ambivalenton.openv.ui.components.*
import dev.ambivalenton.openv.ui.onboarding.*
import dev.ambivalenton.openv.ui.screens.*
import dev.ambivalenton.openv.ui.theme.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=OpenVApp::class,qualifiers="de-rDE-w360dp-h740dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Sprint59UiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val a=Favorite(id="a",label="Bochum Hbf",location=Location("Bochum Hauptbahnhof","a",51.478,7.222,"VRR"),accentSeed=0xff156b56)
    private val b=a.copy(id="b",label="Dortmund Hbf",location=Location("Dortmund Hauptbahnhof","b",51.518,7.459,"VRR"),accentSeed=0xff926126)
    private val c=a.copy(id="c",label="Freizeit",accentSeed=0xff7259a6)
    private fun capture(name:String)=compose.runOnIdle {
        val view=android.view.inspector.WindowInspector.getGlobalWindowViews().lastOrNull {it.isShown && it.width>0} ?: compose.activity.window.decorView
        val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888);view.draw(Canvas(bitmap))
        val file=File("../audit/build59/images/$name.png");file.parentFile!!.mkdirs()
        file.outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
    }
    @Test fun theNetworkPlanGuideIsASeparateScrollableStepAndDoesNotRequestLocation() {
        var requests=0;var finished=0
        var store by mutableStateOf(StoredState(onboardingDraft=OnboardingDraft(step=3,settings=AppSettings(quietMode=true))))
        compose.setContent {CompositionLocalProvider(LocalDensity provides Density(1f,1.5f)) {
            OnboardingWizard(store,SetupPermissions(requestLocation={requests++}),
                {change->store=store.copy(onboardingDraft=change(store.onboardingDraft!!));true},{finished++;true},{emptyList()})
        }}
        compose.onNodeWithTag("onboarding-next").assertIsDisplayed().performClick()
        compose.onNodeWithText("Dein Netzplan").assertIsDisplayed()
        compose.onNodeWithTag("onboarding-network-plan").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Unterwegs").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("In der Nähe eines Favoriten").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("onboarding-next").assertIsDisplayed().performClick()
        compose.onNodeWithText("Bereit für deine Wege").assertIsDisplayed()
        compose.onNodeWithTag("onboarding-next").performClick()
        compose.runOnIdle {assertEquals(0,requests);assertEquals(1,finished)}
    }
}
