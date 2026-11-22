// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.rendering.*
import dev.ambivalenton.openv.ui.components.*
import dev.ambivalenton.openv.ui.theme.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.io.File

/**
 * Every design-language theme rendered as a small app screen with real components (top bar,
 * card, buttons, App-Theme ride bars, adaptive departure board and navigation bar). The PNGs are
 * uploaded by CI as `app-design-previews`; they do not replace a check on a physical device.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=OpenVApp::class,qualifiers="de-rDE-w1650dp-h1200dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Build81DesignLanguageUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()

    private fun capture(name:String)=compose.runOnIdle {
        val view=android.view.inspector.WindowInspector.getGlobalWindowViews()
            .lastOrNull {it.isShown&&it.width>0&&it.height>0}?:compose.activity.window.decorView
        val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val file=File("../audit/theme-catalog/$name.png");file.parentFile!!.mkdirs()
        file.outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
        bitmap
    }

    private val rows=(0..4).map {i->Departure("81-$i",Fixture45.a,listOf("S1","U47","RE 6","107","ICE 9")[i],
        listOf(LineType.SUBURBAN,LineType.SUBWAY,LineType.REGIONAL,LineType.BUS,LineType.ICE)[i],
        listOf("Essen Hbf","Ruhr-Universität","Köln/Bonn Flughafen","Stadtmitte","Berlin Hbf")[i],
        Fixture45.now+i*240_000,Fixture45.now+i*240_000,if(i==2) DelayInfo(300) else DelayInfo(),"${i+3}")}

    @Composable private fun MiniScreen(theme:AppTheme) {
        val settings=AppSettings(appTheme=theme,powerSaving=true,board=BoardSettings(design=BoardDesign.APP_INTEGRATED))
        OpenVTheme(settings) {
            val scheme=MaterialTheme.colorScheme
            val frame=appFrame()
            val board=remember(theme,scheme) {
                val model=boardPresentation(StoredState(settings=settings),scheme).copy(title="Bochum Hbf",rows=rows,caption="Stand 12:34",emptyMessage="–")
                BoardRenderer.render(model,284f,150f,1f).asImageBitmap()
            }
            Column(Modifier.size(314.dp,580.dp).appThemeBackground().testTag("mini-${theme.name}")) {
                Row(Modifier.fillMaxWidth().appSystemHeader().padding(horizontal=14.dp,vertical=10.dp)) {
                    Text(theme.title,style=MaterialTheme.typography.titleLarge,maxLines=1)
                }
                Column(Modifier.padding(horizontal=12.dp,vertical=10.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Section("Essen → Düsseldorf",initiallyExpanded=true) {
                        Text("Nächste Fahrt 12:48 · Gleis 4",style=MaterialTheme.typography.bodyMedium)
                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            ThemedButton({}) {Text("Suchen")}
                            ThemedTonalButton({}) {Text("Später")}
                        }
                    }
                    Row(Modifier.height(64.dp),horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.CenterVertically) {
                        val palette=timelineColorPalette(TimelineTheme.APP_THEME)
                        listOf(LineType.SUBURBAN,LineType.TRAM,LineType.BUS,LineType.ICE).forEachIndexed {i,type->
                            TimelineBar(TimelineBarStyle.APP_THEME,palette[type],listOf("S1","T","107","ICE")[i],
                                Modifier.width(38.dp).height((40+i*8).dp))
                        }
                        ThemedOutlinedButton({}) {Text("Info")}
                    }
                    val shape=appCorner(24.dp)
                    Card(shape=shape,modifier=Modifier.fillMaxWidth().appFrameShadow(frame,shape),elevation=frame.cardElevation(),border=frame.border) {
                        Image(board,null,Modifier.fillMaxWidth().height(150.dp),contentScale=ContentScale.FillBounds)
                    }
                }
                Spacer(Modifier.weight(1f))
                val pager=rememberPagerState {3}
                ExpressivePillNavigation(pager,listOf(PillDestination("Übersicht",R.drawable.ic_home),
                    PillDestination("Suchen",R.drawable.ic_search),PillDestination("Abfahrten",R.drawable.ic_train)),null) {}
            }
        }
    }

    @Test fun everyDesignLanguageRendersTenThemesWithFramesBarsBoardsAndNavigation() {
        var group by mutableStateOf(designLanguageThemeGroups.first())
        compose.setContent {
            val themes=AppTheme.entries.filter {appThemeGroup(it)==group}
            Column(Modifier.fillMaxSize().padding(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                themes.chunked(5).forEach {line->
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {line.forEach {MiniScreen(it)}}
                }
            }
        }
        for(next in designLanguageThemeGroups) {
            compose.runOnIdle {group=next}
            val themes=AppTheme.entries.filter {appThemeGroup(it)==next}
            assertEquals(next.name,10,themes.size)
            themes.forEach {compose.onNodeWithTag("mini-${it.name}").assertExists()}
            capture("design-language-${next.name.lowercase()}").recycle()
        }
    }
}
