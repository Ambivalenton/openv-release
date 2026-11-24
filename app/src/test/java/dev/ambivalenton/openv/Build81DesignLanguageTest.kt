// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.localization.englishStrings
import dev.ambivalenton.openv.rendering.*
import dev.ambivalenton.openv.ui.components.AppChartArtwork
import dev.ambivalenton.openv.ui.components.drawAppChartBar
import dev.ambivalenton.openv.ui.theme.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.io.File

/** Ten design-language collections between "Klar & reduziert" and "Glas & Licht". */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Build81DesignLanguageTest {
    private val languageThemes=AppTheme.entries.filter {it.themeLanguage!=ThemeLanguage.STANDARD}
    private fun save(bitmap:Bitmap,name:String){
        val file=File("../audit/theme-catalog/$name.png");file.parentFile!!.mkdirs()
        file.outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
    }
    private fun pixels(bitmap:Bitmap)=IntArray(bitmap.width*bitmap.height).also {bitmap.getPixels(it,0,bitmap.width,0,0,bitmap.width,bitmap.height)}
    private fun ratio(a:androidx.compose.ui.graphics.Color,b:androidx.compose.ui.graphics.Color)=contrastRatio(a,b)

    @Test fun tenCollectionsOfTenSitBetweenCleanAndGlassAndOldThemesKeepTheirLanguage() {
        val order=groupedAppThemes().map {it.first}
        val clean=order.indexOf(AppThemeGroup.CLEAN)
        assertEquals(designLanguageThemeGroups,order.subList(clean+1,clean+11))
        // The redesigned "Weich & taktil" group of the parallel line also sits before "Glas & Licht".
        assertTrue(order.indexOf(AppThemeGroup.GLASS)>clean+10)
        for(group in designLanguageThemeGroups) {
            val themes=groupedAppThemes().single {it.first==group}.second
            // Build 109: six to eight of each ten remain in the picker; the rest moved to a sibling.
            assertEquals(group.name,AppTheme.entries.count {it.themeLanguage.themeGroup==group && !it.retired},themes.size)
            assertTrue(group.name,themes.size in 6..8)
            assertEquals(group.name,1,themes.map {it.themeLanguage}.distinct().size)
            assertEquals(group,themes.first().themeLanguage.themeGroup)
            assertTrue(group.name,themes.any {it.dark})
            if(group!=AppThemeGroup.GLOW_EDGE) assertTrue(group.name,themes.any {!it.dark})
        }
        assertEquals(100,languageThemes.size)
        // The 201 themes released before build 81 keep their language, group and texture rules.
        AppTheme.entries.take(201).forEach {assertEquals(it.name,ThemeLanguage.STANDARD,it.themeLanguage)}
        // The redesign of the older groups moved Clay-Mosaik to "Natur & Weite".
        assertEquals(AppThemeGroup.NATURE,appThemeGroup(AppTheme.CLAY_MOSAIC))
        assertTrue(languageThemes.all {it.texture==ThemeTexture.NONE})
        // Quiet mode still offers only its three calm designs.
        assertEquals(quietAppThemes.toSet(),groupedAppThemes(true).flatMap {it.second}.toSet())
    }

    @Test fun namesTitlesFontsTranslationsAndPalettesAreCompleteAndDistinct() {
        assertEquals(languageThemes.size,languageThemes.map {it.title}.distinct().size)
        languageThemes.forEach {t->assertEquals(t.name,1,AppTheme.entries.count {it.titleKey==t.titleKey})}
        assertEquals(languageThemes.size,languageThemes.map {it.defaultColors()}.distinct().size)
        val fonts=appFonts.map {it.id}.toSet()
        for(theme in languageThemes) {
            assertNotNull(theme.name,theme.typefaceId)
            assertTrue("${theme.name} font ${theme.typefaceId}",theme.typefaceId in fonts)
            assertNotNull("${theme.name} title",englishStrings[theme.titleKey])
            assertNotNull("${theme.name} description",englishStrings[theme.detailKey])
            assertEquals(10,appColorFamily(theme).colors.size)
            assertTrue(theme.radiusScale in 0f..2f)
            val encoded=Json.encodeToString(theme)
            assertEquals("\"${theme.name}\"",encoded)
            assertEquals(theme,Json.decodeFromString<AppTheme>(encoded))
        }
        // Collection titles and descriptions are translated (the test locale may show either language).
        for(group in designLanguageThemeGroups) for(text in listOf(group.title,group.description))
            assertTrue("${group.name}: $text",englishStrings.containsKey(text) || englishStrings.containsValue(text))
    }

    @Test fun everyRoleOfEveryLanguageThemeIsReadable() {
        val failures=mutableListOf<String>()
        for(theme in languageThemes) {
            val s=appThemeScheme(theme)
            fun check(label:String,a:androidx.compose.ui.graphics.Color,b:androidx.compose.ui.graphics.Color,min:Double=4.5) {
                val r=ratio(a,b.copy(alpha=1f));if(r<min) failures+="${theme.name} $label ${"%.2f".format(r)}"
            }
            check("onBackground",s.onBackground,s.background)
            listOf(s.surfaceContainerLow,s.surfaceContainer,s.surfaceContainerHigh,s.surfaceContainerHighest).forEachIndexed {i,c->check("onSurface/$i",s.onSurface,c)}
            check("onPrimaryContainer",s.onPrimaryContainer,s.primaryContainer)
            check("onSecondaryContainer",s.onSecondaryContainer,s.secondaryContainer)
            check("onTertiaryContainer",s.onTertiaryContainer,s.tertiaryContainer)
            check("primary text",s.primary,s.background)
            check("secondary text",s.secondary,s.background)
            check("onPrimary",s.onPrimary,s.primary)
            val palette=createTimelinePalette(TimelineTheme.APP_THEME,s,appTheme=theme)
            for(type in LineType.entries) check("ride $type",palette[type].content,palette[type].container)
            // High contrast keeps working on top of every language.
            val strong=highContrastScheme(s)
            check("high contrast onSurface",strong.onSurface,strong.surfaceContainer,7.0)
            check("high contrast primary",strong.primary,strong.background,7.0)
        }
        assertTrue(failures.joinToString("\n"),failures.isEmpty())
    }

    @Test fun squircleAndPebbleStayConvexInsideTheirBounds() {
        val density=Density(2f)
        for(theme in listOf(AppTheme.BENTO_PORCELAIN,AppTheme.ORGANIC_PEBBLE,AppTheme.SOFT_PEARL,AppTheme.GLOW_VIOLET)) {
            for(size in listOf(Size(320f,180f),Size(48f,48f),Size(24f,300f),Size(300f,16f))) {
                val outline=AppVisuals(theme).corner(24.dp).createOutline(size,LayoutDirection.Ltr,density)
                val path=Path().apply {addOutline(outline)}
                assertTrue("${theme.name} $size convex",path.isConvex)
                val box=path.getBounds()
                assertTrue("${theme.name} $size bounds $box",box.left>=-.01f && box.top>=-.01f && box.right<=size.width+.01f && box.bottom<=size.height+.01f)
            }
        }
        assertNotEquals(SquircleShape(24.dp),androidx.compose.foundation.shape.RoundedCornerShape(24.dp))
    }

    private val rows=(0..5).map {i->Departure("81-$i",Fixture45.a,"${i+1}",LineType.entries[i],if(i==0)"Sehr langer Haltestellenname in der Innenstadt" else "Mitte",
        Fixture45.now+i*60_000,Fixture45.now+i*60_000,when(i){1->DelayInfo(420);2->DelayInfo(cancelled=true);else->DelayInfo()},"B$i")}
    // The language board is the "App-Theme" board (APP_INTEGRATED); "Display" shows real screens.
    private fun model(t:AppTheme)=boardPresentation(StoredState(settings=AppSettings(appTheme=t,board=BoardSettings(design=BoardDesign.APP_INTEGRATED))),appThemeScheme(t))
        .copy(title="Bochum Hbf",rows=rows,caption="Stand 12:34",emptyMessage="Keine Abfahrten")

    @Test fun boardsFollowTheLanguageInAllLayoutsDeterministically() {
        for(group in designLanguageThemeGroups) {
            val themes=AppTheme.entries.filter {appThemeGroup(it)==group}
            val atlas=Bitmap.createBitmap(1500,520,Bitmap.Config.ARGB_8888);atlas.eraseColor(0xff30343a.toInt())
            val out=Canvas(atlas);val hashes=mutableSetOf<Int>()
            themes.forEachIndexed {i,t->
                val m=model(t)
                assertEquals(t,m.appStyle);assertNotNull(t.name,m.appRoles)
                assertEquals(appThemeScheme(t).background.toArgb().toLong() and 0xffffffffL,m.colors.background)
                val a=BoardRenderer.render(m,288f,236f,1f);val b=BoardRenderer.render(m,288f,236f,1f)
                assertArrayEquals("${t.name} deterministic",pixels(a),pixels(b));hashes+=pixels(a).contentHashCode()
                out.drawBitmap(a,null,RectF(i%5*300f+6f,i/5*260f+6f,i%5*300f+294f,i/5*260f+242f),null)
                out.drawText(t.titleKey,i%5*300f+8f,i/5*260f+256f,Paint(Paint.ANTI_ALIAS_FLAG).apply {color=Color.WHITE;textSize=12f})
                a.recycle();b.recycle()
                for((w,h,font) in listOf(Triple(150f,240f,1.5f),Triple(300f,160f,2f),Triple(320f,64f,1f))) {
                    val normal=BoardRenderer.render(m.copy(rows=listOf(rows.first())),w,h,font)
                    for(delay in listOf(DelayInfo(420),DelayInfo(cancelled=true))) {
                        val changed=BoardRenderer.render(m.copy(rows=listOf(rows.first().copy(delay=delay))),w,h,font)
                        assertNotEquals("${t.name} $delay at $w/$h",pixels(normal).contentHashCode(),pixels(changed).contentHashCode());changed.recycle()
                    }
                    normal.recycle();BoardRenderer.render(m.copy(rows=emptyList()),w,h,font).recycle()
                    BoardRenderer.render(m.copy(isDesignPreview=true),w,h,font).recycle()
                }
            }
            assertEquals(group.name,themes.size,hashes.size)
            save(atlas,"design-language-boards-${group.name.lowercase()}");atlas.recycle()
        }
    }

    @Test fun languageBoardsAreNotLitDisplays() {
        // Printed in the app's own colours: no OLED re-colouring and no glow except glow-edge designs.
        for(theme in languageThemes) {
            val scheme=appThemeScheme(theme)
            val m=model(theme)
            assertEquals(theme.name,scheme.onSurface.toArgb().toLong() and 0xffffffffL,m.colors.foreground)
        }
        val classic=boardPresentation(StoredState(settings=AppSettings(appTheme=AppTheme.CLASSIC_LIGHT,board=BoardSettings(design=BoardDesign.APP_THEME))),appThemeScheme(AppTheme.CLASSIC_LIGHT))
        assertNull(classic.appRoles)
    }

    @Test fun rideBarsUseTheLanguageMaterial() {
        val atlas=Bitmap.createBitmap(520,1090,Bitmap.Config.ARGB_8888);atlas.eraseColor(0xffe9eaec.toInt())
        val out=Canvas(atlas)
        designLanguageThemeGroups.forEachIndexed {g,group->
            AppTheme.entries.filter {appThemeGroup(it)==group}.forEachIndexed {i,theme->
                val scheme=appThemeScheme(theme)
                val art=AppChartArtwork(AppVisuals(theme),scheme)
                val palette=createTimelinePalette(TimelineTheme.APP_THEME,scheme,appTheme=theme)
                val bitmap=Bitmap.createBitmap(40,100,Bitmap.Config.ARGB_8888)
                CanvasDrawScope().draw(Density(1.5f),LayoutDirection.Ltr,androidx.compose.ui.graphics.Canvas(Canvas(bitmap)),Size(40f,100f)) {
                    drawRect(scheme.background)
                    drawAppChartBar(palette[listOf(LineType.SUBURBAN,LineType.TRAM,LineType.FERRY)[i%3]],art)
                }
                val first=pixels(bitmap)
                assertTrue(theme.name,first.toSet().size>2)
                out.drawBitmap(bitmap,i*50f+12f,g*108f+6f,null)
                bitmap.recycle()
            }
        }
        save(atlas,"design-language-ride-bars");atlas.recycle()
        // Classic themes keep their own bar: the language painter is never reached for them.
        val classic=createTimelinePalette(TimelineTheme.APP_THEME,appThemeScheme(AppTheme.CLASSIC_DARK),appTheme=AppTheme.CLASSIC_DARK)
        assertEquals(createTimelinePalette(TimelineTheme.APP_THEME,appThemeScheme(AppTheme.CLASSIC_DARK))[LineType.BUS],classic[LineType.BUS])
    }

    @Test fun framesMatchTheLanguage() {
        val brutal=appFrameFor(AppTheme.BRUTAL_POSTER,appThemeScheme(AppTheme.BRUTAL_POSTER))
        assertTrue(brutal.shadow is HardShadow);assertEquals(2.5.dp,brutal.border!!.width);assertTrue(brutal.replacesElevation)
        assertTrue(appFrameFor(AppTheme.SOFT_PEARL,appThemeScheme(AppTheme.SOFT_PEARL)).shadow is ReliefShadow)
        assertTrue(appFrameFor(AppTheme.KNETE_BUBBLEGUM,appThemeScheme(AppTheme.KNETE_BUBBLEGUM)).shadow is HaloShadow)
        assertNull(appFrameFor(AppTheme.LINE_PENCIL,appThemeScheme(AppTheme.LINE_PENCIL)).shadow)
        assertNull(appFrameFor(AppTheme.BLOCK_COBALT,appThemeScheme(AppTheme.BLOCK_COBALT)).border)
        // Dark neo-brutalism casts its hard shadow in the bright fill colour.
        val night=appThemeScheme(AppTheme.BRUTAL_NIGHTSHIFT)
        assertEquals(night.primaryContainer,(appFrameFor(AppTheme.BRUTAL_NIGHTSHIFT,night).shadow as HardShadow).color)
        // Classic themes have no frame at all, so their cards render exactly as before.
        val classic=appFrameFor(AppTheme.CLASSIC_LIGHT,appThemeScheme(AppTheme.CLASSIC_LIGHT))
        assertNull(classic.border);assertNull(classic.shadow);assertFalse(classic.replacesElevation)
        // Outline designs never fill their containers.
        val line=appThemeScheme(AppTheme.LINE_PENCIL)
        assertEquals(line.background,line.surfaceContainerHighest)
    }
}
