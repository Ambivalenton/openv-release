// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.ui.components.*
import dev.ambivalenton.openv.ui.theme.OpenVTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.math.sqrt

/** The capsules of the navigation blend with a design change instead of jumping. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=OpenVApp::class,qualifiers="de-rDE-w411dp-h891dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Build99UiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val tabs=listOf(PillDestination("Übersicht",R.drawable.ic_overview),PillDestination("Suchen",R.drawable.ic_search),
        PillDestination("Abfahrten",R.drawable.ic_train))

    private fun distance(a:Int,b:Int):Float {
        val r=((a shr 16) and 255)-((b shr 16) and 255);val g=((a shr 8) and 255)-((b shr 8) and 255);val bl=(a and 255)-(b and 255)
        return sqrt((r*r+g*g+bl*bl).toFloat())
    }

    /** Capsule (selected "Suchen") and background, every frame of a design change. */
    private fun blend(from:AppTheme,to:AppTheme):List<Pair<Int,Int>> {
        var settings by mutableStateOf(AppSettings(appTheme=from))
        compose.setContent {OpenVTheme(settings) {
            val pager=rememberPagerState(1) {3}
            Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background) {
                Box(Modifier.fillMaxSize()) {Box(Modifier.align(Alignment.BottomCenter)) {ExpressivePillNavigation(pager,tabs,null) {}}}
            }
        }}
        compose.waitForIdle()
        val bar=compose.onNodeWithTag("expressive-pill-navigation").fetchSemanticsNode().boundsInRoot
        val slot=(bar.width-14f)/3f;val y=bar.center.y.toInt();val x=(bar.left+7f+slot*1.75f).toInt()
        fun sample():Pair<Int,Int> {
            val view=compose.activity.window.decorView;val image=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
            view.draw(Canvas(image));val result=image.getPixel(x,y) to image.getPixel(10,10);image.recycle();return result
        }
        val frames=mutableListOf(sample())
        compose.mainClock.autoAdvance=false
        compose.runOnUiThread {settings=AppSettings(appTheme=to)}
        repeat(36) {compose.mainClock.advanceTimeByFrame();frames+=sample()}
        compose.mainClock.autoAdvance=true
        return frames
    }

    private fun assertBlendsWithTheDesign(from:AppTheme,to:AppTheme) {
        val frames=blend(from,to)
        val (capsuleStart,screenStart)=frames.first();val (capsuleEnd,screenEnd)=frames.last()
        assertNotEquals(capsuleStart,capsuleEnd)
        val log=frames.joinToString {(c,s)->"${Integer.toHexString(c)}/${Integer.toHexString(s)}"}
        var previous=0f
        for((capsule,screen) in frames) {
            val c=distance(capsule,capsuleStart)/distance(capsuleEnd,capsuleStart)
            val s=distance(screen,screenStart)/distance(screenEnd,screenStart)
            // The capsule is as far through the change as the screen behind it …
            assertTrue("$from -> $to: capsule ${"%.2f".format(c)} vs screen ${"%.2f".format(s)}: $log",abs(c-s)<=.2f)
            // … and never jumps ahead or back.
            assertTrue("$from -> $to: capsule moved back: $log",c>=previous-.03f)
            previous=c
        }
    }

    @Test fun aCapsuleBlendsIntoAColourfulDesign()=assertBlendsWithTheDesign(AppTheme.CLASSIC_LIGHT,AppTheme.SYNTHWAVE)
    @Test fun aCapsuleBlendsBetweenTwoPosterDesigns()=assertBlendsWithTheDesign(AppTheme.BRUTAL_POSTER,AppTheme.BRUTAL_KIOSK)
    @Test fun aCapsuleBlendsFromLightToDark()=assertBlendsWithTheDesign(AppTheme.CLASSIC_LIGHT,AppTheme.CLASSIC_DARK)

    @Test fun withReducedMotionTheCapsuleChangesAtOnce() {
        var settings by mutableStateOf(AppSettings(appTheme=AppTheme.CLASSIC_LIGHT,quietMode=true))
        compose.setContent {OpenVTheme(settings) {
            val pager=rememberPagerState(1) {3}
            Surface(Modifier.fillMaxSize()) {Box(Modifier.fillMaxSize()) {Box(Modifier.align(Alignment.BottomCenter)) {ExpressivePillNavigation(pager,tabs,null) {}}}}
        }}
        compose.waitForIdle()
        val bar=compose.onNodeWithTag("expressive-pill-navigation").fetchSemanticsNode().boundsInRoot
        val slot=(bar.width-14f)/3f;val x=(bar.left+7f+slot*1.75f).toInt();val y=bar.center.y.toInt()
        fun capsule():Int {val v=compose.activity.window.decorView;val b=Bitmap.createBitmap(v.width,v.height,Bitmap.Config.ARGB_8888)
            v.draw(Canvas(b));return b.getPixel(x,y).also {b.recycle()}}
        val before=capsule()
        compose.mainClock.autoAdvance=false
        compose.runOnUiThread {settings=settings.copy(appTheme=AppTheme.CLASSIC_DARK)}
        // The first frame that shows a change already shows the new capsule, no blend in between.
        var first=before;var frames=0
        while(first==before && frames<10) {compose.mainClock.advanceTimeByFrame();frames++;first=capsule()}
        compose.mainClock.autoAdvance=true;compose.waitForIdle()
        assertNotEquals(before,first)
        assertEquals(capsule(),first)
    }
}
