// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.ui.components.*
import dev.ambivalenton.openv.ui.theme.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

/** A rotating design sample fades in together with its shadow (no shadow popping in afterwards). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=OpenVApp::class,qualifiers="de-rDE-w411dp-h260dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Build81ThemePreviewUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()

    private fun luminanceBelowCard():Float = compose.runOnIdle {
        val view=compose.activity.window.decorView
        val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val card=compose.onNodeWithTag("theme-group-SOFT").fetchSemanticsNode().boundsInWindow
        val x=card.center.x.toInt()
        // Darkest pixel of a short band just below the card: the shadow of the incoming design.
        val result=(card.bottom.toInt()+2..card.bottom.toInt()+12).minOf {y->
            val c=bitmap.getPixel(x,y.coerceIn(0,bitmap.height-1));(Color.red(c)+Color.green(c)+Color.blue(c))/3f
        }
        bitmap.recycle();result
    }

    @Test fun theShadowOfTheNextDesignFadesInWithTheDesign() {
        val settings=AppSettings(appTheme=AppTheme.CLASSIC_LIGHT)
        val themes=groupedAppThemes().first {it.first==AppThemeGroup.SOFT}.second
        compose.mainClock.autoAdvance=false
        compose.setContent {OpenVTheme(settings){Column(Modifier.fillMaxSize().padding(28.dp)){
            ThemeGroupHeader(AppThemeGroup.SOFT,themes,settings,false,null){}
        }}}
        compose.mainClock.advanceTimeBy(500)
        val page=compose.runOnIdle {
            val bitmap=Bitmap.createBitmap(compose.activity.window.decorView.width,compose.activity.window.decorView.height,Bitmap.Config.ARGB_8888)
            compose.activity.window.decorView.draw(Canvas(bitmap))
            val c=bitmap.getPixel(4,4);bitmap.recycle();(Color.red(c)+Color.green(c)+Color.blue(c))/3f
        }
        assertTrue("the resting sample casts its shadow",luminanceBelowCard()<page-2f)
        // Into the cross-fade: both samples are drawn and the shadow is there, not only after the fade.
        compose.mainClock.advanceTimeBy(2_500+400)
        compose.onNodeWithTag("theme-group-sample-${themes[0].name}",useUnmergedTree=true).assertExists()
        compose.onNodeWithTag("theme-group-sample-${themes[1].name}",useUnmergedTree=true).assertExists()
        assertTrue("shadow visible during the fade",luminanceBelowCard()<page-2f)
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithTag("theme-group-sample-${themes[0].name}",useUnmergedTree=true).assertDoesNotExist()
    }
}
