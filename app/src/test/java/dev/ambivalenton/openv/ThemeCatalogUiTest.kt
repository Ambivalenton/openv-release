// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.*
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.ui.components.*
import dev.ambivalenton.openv.ui.theme.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=OpenVApp::class,qualifiers="de-rDE-w411dp-h891dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ThemeCatalogUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private fun capture(name:String)=compose.runOnIdle {
        val view=android.view.inspector.WindowInspector.getGlobalWindowViews()
            .lastOrNull {it.isShown&&it.width>0&&it.height>0}?:compose.activity.window.decorView
        val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val file=File("../audit/theme-catalog/$name.png");file.parentFile!!.mkdirs()
        file.outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
    }
    @Test fun aNewCollectionOpensAndItsLastThemeRemainsSelectableWithLargeType(){
        var settings by mutableStateOf(AppSettings(appTheme=AppTheme.CLASSIC_LIGHT,powerSaving=true))
        compose.setContent {CompositionLocalProvider(LocalDensity provides Density(1f,1.5f)){
            OpenVTheme(settings){AppThemePicker(settings.appTheme,{settings=settings.copy(appTheme=it)},{},settings)}
        }}
        compose.onNodeWithTag("app-theme-picker").performScrollToNode(hasTestTag("theme-group-TEXTILES"))
        compose.onNodeWithTag("theme-group-TEXTILES").assertIsDisplayed().performClick()
        compose.onNodeWithTag("app-theme-group-popup").performScrollToNode(hasTestTag("theme-TEXTILE_SILK"))
        compose.onNodeWithTag("theme-TEXTILE_SILK").performClick().assertIsSelected()
        compose.runOnIdle {assertEquals(AppTheme.TEXTILE_SILK,settings.appTheme)}
        capture("textile-picker-large-type")
    }
    @Test fun systemButtonsRetainNativeClicksDisabledStateAndReadableLayout(){
        var theme by mutableStateOf(AppTheme.OS_WIN_95);var clicks=0
        compose.setContent {OpenVTheme(AppSettings(appTheme=theme,powerSaving=true)){
            Column(Modifier.fillMaxSize().appThemeBackground().padding(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
                Text("Öpen-V",Modifier.fillMaxWidth().appSystemHeader().padding(16.dp),color=systemHeaderInk())
                Column(Modifier.fillMaxWidth().clip(appCorner(24.dp)).appPanelDecoration().padding(16.dp)){
                    Text("Essen → Düsseldorf",style=MaterialTheme.typography.titleLarge)
                    ThemedButton({clicks++},Modifier.testTag("system-action")){Text("Verbindung suchen")}
                    ThemedTonalButton({clicks++},Modifier.testTag("system-disabled"),enabled=false){Text("Bitte warten")}
                }
            }
        }}
        for(next in listOf(AppTheme.OS_WIN_95,AppTheme.OS_WIN_XP,AppTheme.OS_MAC_AQUA,AppTheme.OS_IOS_GLASS,AppTheme.OS_ANDROID_HOLO,AppTheme.OS_GNOME)){
            compose.runOnIdle {theme=next}
            compose.onNodeWithTag("system-action").assertIsDisplayed().performClick()
            compose.onNodeWithTag("system-disabled").assertIsNotEnabled()
            capture("system-${next.name.lowercase()}")
        }
        compose.runOnIdle {assertEquals(6,clicks)}
    }
}
