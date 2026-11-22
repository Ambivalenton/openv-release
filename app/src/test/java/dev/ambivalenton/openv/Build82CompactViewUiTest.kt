// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.ui.components.*
import dev.ambivalenton.openv.ui.theme.OpenVTheme
import java.io.File
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * "Kompaktansicht" instead of sharing. The former share card opens on a full-height sheet,
 * centred, with source and data timestamp at the bottom; swiping the handle down closes it. No image
 * or intent leaves the app.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=OpenVApp::class,qualifiers="de-rDE-w411dp-h891dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Build82CompactViewUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val overlay=AppOverlayState()

    private fun trip():Trip {
        val now=Fixture45.now
        val bus=Fixture45.ride(LineType.BUS,now+600_000).copy(id="bus",lineName="107",realtimeReceivedAt=now)
        return Trip("compact",listOf(bus),"VRR / EFA · NRW",now)
    }

    private fun capture(name:String)=compose.runOnIdle {
        val view=compose.activity.window.decorView
        val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val file=File("../audit/theme-catalog/$name.png");file.parentFile!!.mkdirs()
        file.outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
    }

    private var theme by mutableStateOf<AppTheme?>(null)
    private var opening by mutableStateOf(0)

    private fun host() {
        val journey=trip()
        compose.setContent {
            OpenVTheme(AppSettings(appTheme=theme,powerSaving=true)) {
                CompositionLocalProvider(LocalAppOverlay provides overlay) {
                    AppOverlayHost(overlay,Modifier.fillMaxSize()) {
                        Text("Öpen-V",Modifier.padding(24.dp))
                        key(opening) {
                            val open=rememberJourneyCompactView()
                            LaunchedEffect(Unit) {open(journey)}
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    @Test fun compactCardIsCentredShowsItsSourceAndClosesBySwiping() {
        host()
        for(next in listOf(null,AppTheme.BRUTAL_POSTER,AppTheme.LINE_PENCIL,AppTheme.GLOW_VIOLET)) {
            compose.runOnIdle {theme=next;opening++}
            compose.waitUntil(5_000) {compose.onAllNodesWithTag("journey-compact-card").fetchSemanticsNodes().isNotEmpty()}
            val root=compose.onRoot().fetchSemanticsNode().boundsInRoot
            val card=compose.onNodeWithTag("journey-compact-card").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            val handle=compose.onNodeWithTag("app-bottom-sheet-drag-handle").fetchSemanticsNode().boundsInRoot
            val source=compose.onNodeWithTag("journey-compact-source").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            // Same compact card as the former share image: full width minus a margin, never stretched.
            assertTrue("card width ${card.width}",card.width<=root.width-23f && card.width>=root.width-26f)
            assertEquals("horizontally centred",root.center.x,card.center.x,2f)
            // The source line keeps 8 dp padding above its text; the centring area ends there.
            assertEquals("vertically centred between handle and source",(handle.bottom+source.top-8f)/2f,card.center.y,2f)
            assertTrue("source line at the very bottom",source.bottom>=root.bottom-60f && source.top>card.bottom)
            val text=compose.onNodeWithTag("journey-compact-source").fetchSemanticsNode().config
                .getOrElse(androidx.compose.ui.semantics.SemanticsProperties.Text) {emptyList()}.joinToString {it.text}
            assertTrue(text,text.contains("VRR / EFA · NRW") && text.contains("Stand"))
            capture("compact-view-${next?.name?.lowercase() ?: "system"}")
            // Nothing is exported: no chooser, no send intent, no cached image.
            assertNull(shadowOf(compose.activity.application).nextStartedActivity)
            assertFalse(File(compose.activity.cacheDir,"journey-shares").exists())
            compose.onNodeWithTag("app-bottom-sheet-drag-handle").performTouchInput {swipe(center,center+Offset(0f,500f),300)}
            compose.waitUntil(5_000) {compose.onAllNodesWithTag("journey-compact-view").fetchSemanticsNodes().isEmpty()}
        }
    }

    @Test fun sourceNamesProviderTimestampsAndMissingRealtime() {
        val now=Fixture45.now
        val zone=ZoneId.of("Europe/Berlin")
        val plain=Trip("t",listOf(Fixture45.ride(LineType.BUS,now)),"VRR / EFA · NRW",now)
        val text=journeyCompactSource(plain,zone)
        assertTrue(text,text.startsWith("Daten: VRR / EFA · NRW · Stand: "))
        val noRealtime=plain.copy(segments=plain.segments.map {it.copy(realtimeDeparture=false,realtimeReceivedAt=null)})
        assertTrue(journeyCompactSource(noRealtime,zone).endsWith("ohne Echtzeitangaben"))
        val provider=plain.copy(segments=plain.segments.map {it.copy(realtimeGeneratedAt=now-60_000)})
        assertTrue(journeyCompactSource(provider,zone).contains("Echtzeit des Anbieters: "))
    }
}
