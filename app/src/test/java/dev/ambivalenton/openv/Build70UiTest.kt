// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.ui.NetworkCountdown
import dev.ambivalenton.openv.ui.network.*
import dev.ambivalenton.openv.ui.theme.OpenVTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Build 70 on real composables: the network plan design page, its vehicle switch and the designs. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = OpenVApp::class, qualifiers = "de-rDE-w411dp-h891dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Build70UiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val now = java.time.ZonedDateTime.now()

    @Test fun theDesignPageOpensWithTheVehicleSwitchOnTopAndEveryDesignSelectable() {
        val settings = mutableStateOf(AppSettings(quietMode = true))
        compose.setContent {
            OpenVTheme(settings.value) { Surface { NetworkPlanDesignSettings(settings.value) { change -> settings.value = change(settings.value) } } }
        }
        compose.onNodeWithTag("network-design-selector").assertIsDisplayed().assertTextContains("Netzplan-Design").assertTextContains("Öpen-V")
        compose.onNodeWithTag("network-design-selector").performClick()
        compose.onNodeWithTag("network-vehicles-switch").assertIsDisplayed()
        compose.onNodeWithText("Animierte Verkehrsmittel anzeigen").assertIsDisplayed()
        // The switch sits above the list.
        val switchTop = compose.onNodeWithTag("network-vehicles-switch").fetchSemanticsNode().boundsInRoot.top
        val firstDesignTop = compose.onNodeWithTag("network-design-OPENV").fetchSemanticsNode().boundsInRoot.top
        assertTrue(switchTop < firstDesignTop)
        compose.onNode(hasText("Animierte Verkehrsmittel anzeigen").and(isToggleable())).assertIsOn().performClick()
        compose.runOnIdle { assertFalse(settings.value.networkVehicles) }
        val list = compose.onNodeWithTag("network-design-picker")
        for (design in NetworkPlanDesign.entries) {
            list.performScrollToNode(hasTestTag("network-design-${design.name}"))
            compose.onNodeWithTag("network-design-${design.name}").assertTextContains(design.title).performClick()
            compose.runOnIdle { assertEquals(design, settings.value.networkPlanDesign) }
            compose.onNodeWithTag("network-design-${design.name}").assertIsSelected()
        }
    }

    @Test fun aPreviewTakesNoTouchesAndHidesTheSampleFromTalkBack() {
        var touched = false
        compose.setContent {
            OpenVTheme(AppSettings(quietMode = true)) { Surface {
                NetworkPlan(networkPreviewStore, now, false, null, NetworkCountdown(), null,
                    NetworkPlanActions(onNodeTap = { touched = true }, onEdgeTap = { touched = true }), Modifier.width(360.dp),
                    NetworkPlanPreview(NetworkPlanDesign.LONDON, vehicles = true, animateVehicles = false))
            } }
        }
        // TalkBack reads the merged tree: the sample's favourites and their actions are not in it.
        compose.onAllNodesWithTag("network-node-preview-home").assertCountEquals(0)
        compose.onAllNodes(hasContentDescription("Zuhause")).assertCountEquals(0)
        compose.onNodeWithTag("network-plan").performTouchInput { click(center) }
        compose.runOnIdle { assertFalse(touched) }
    }

    @Test fun theSwitchRemovesTheVehiclesFromTheLivePlan() {
        val settings = mutableStateOf(AppSettings(networkVehicles = false))
        compose.setContent {
            OpenVTheme(settings.value) { Surface {
                NetworkPlan(networkPreviewStore, now, false, null, NetworkCountdown(), null, NetworkPlanActions(), Modifier.width(360.dp))
            } }
        }
        compose.onAllNodesWithTag("network-vehicles", useUnmergedTree = true).assertCountEquals(0)
        compose.runOnIdle { settings.value = settings.value.copy(networkVehicles = true) }
        compose.waitForIdle()
        // Shown only where the system allows animations (Robolectric may switch them off).
        val expected = if (android.animation.ValueAnimator.areAnimatorsEnabled()) 1 else 0
        compose.onAllNodesWithTag("network-vehicles", useUnmergedTree = true).assertCountEquals(expected)
    }

    /** One composition per test: design and theme change through state. */
    private val shown = mutableStateOf(NetworkPlanDesign.LONDON to ThemeMode.LIGHT)
    private fun showPlan() = compose.setContent {
        val (design, theme) = shown.value
        OpenVTheme(AppSettings(quietMode = false, themeMode = theme)) { Surface {
            NetworkPlan(networkPreviewStore, now, false, null, NetworkCountdown(), null, NetworkPlanActions(), Modifier.width(360.dp),
                NetworkPlanPreview(design, vehicles = false, animateVehicles = false))
        } }
    }
    private fun windowBitmap(): android.graphics.Bitmap {
        val view = compose.activity.window.decorView
        return android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
            .also { view.draw(android.graphics.Canvas(it)) }
    }
    private fun sheetPixel(design: NetworkPlanDesign, theme: ThemeMode): Color {
        compose.runOnIdle { shown.value = design to theme }
        compose.waitForIdle()
        val plan = compose.onNodeWithTag("network-plan").fetchSemanticsNode().boundsInWindow
        return compose.runOnIdle {
            val bitmap = windowBitmap()
            // Centre of the empty field in the middle of the 6 × 3 sample ring.
            val pitch = plan.width / 6
            Color(bitmap.getPixel((plan.left + pitch * 3.5f).toInt(), (plan.top + pitch * 1.5f).toInt())).also { bitmap.recycle() }
        }
    }

    /** Evidence images of every design, light and dark, for manual review; audit/ is not part of the repository. */
    @Test fun designGalleryForReview() {
        val theme = mutableStateOf(ThemeMode.LIGHT)
        compose.setContent {
            OpenVTheme(AppSettings(quietMode = false, themeMode = theme.value)) { Surface(Modifier.fillMaxSize()) {
                Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    NetworkPlanDesign.entries.chunked(2).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            pair.forEach { design ->
                                Column(Modifier.width(193.dp)) {
                                    Text(design.title, style = MaterialTheme.typography.labelLarge)
                                    NetworkPlanDesignPreview(design, vehicles = true, animate = false)
                                }
                            }
                        }
                    }
                }
            } }
        }
        for (mode in listOf(ThemeMode.LIGHT, ThemeMode.DARK)) {
            compose.runOnIdle { theme.value = mode }
            compose.waitForIdle()
            compose.onAllNodesWithTag("network-design-preview-SINGAPORE").assertCountEquals(1)
            compose.runOnIdle {
                val bitmap = windowBitmap()
                val file = java.io.File("../audit/build70/images/network-designs-${mode.name.lowercase()}.png"); file.parentFile!!.mkdirs()
                file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
            }
        }
    }

    @Test fun printedMapDesignsUseTheirPaperInLightModeAndADarkSheetInDarkMode() {
        showPlan()
        val london = sheetPixel(NetworkPlanDesign.LONDON, ThemeMode.LIGHT)
        assertTrue("London paper is white: $london", london.red > .97f && london.green > .97f && london.blue > .97f)
        val paris = sheetPixel(NetworkPlanDesign.PARIS, ThemeMode.LIGHT)
        assertTrue("Paris paper is cream: $paris", paris.red > paris.blue + .03f)
        val dark = sheetPixel(NetworkPlanDesign.LONDON, ThemeMode.DARK)
        assertTrue("dark mode never shows a glaring sheet: $dark", dark.red < .3f && dark.green < .3f && dark.blue < .3f)
    }
}
