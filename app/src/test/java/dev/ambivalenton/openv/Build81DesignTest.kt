// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import android.graphics.Color
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.localization.englishStrings
import dev.ambivalenton.openv.rendering.*
import dev.ambivalenton.openv.ui.network.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Redesigned groups with design languages, display technologies and network designs. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=OpenVApp::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Build81DesignTest {
    private val catalog=catalogThemeGroups.toSet()+AppThemeGroup.SYSTEMS
    // The ten ThemeLanguage collections (own frames, see Build81DesignLanguageTest) are not part of the redesign.
    private val redesigned=AppTheme.entries.filter {appThemeGroup(it) !in catalog && it.themeLanguage==ThemeLanguage.STANDARD} - quietAppThemes.toSet()

    @Test fun everyOldDesignSpeaksALanguageWithoutWallpaperAndWithBundledTypefaces() {
        assertEquals(120,redesigned.size)
        val fontIds=appFonts.map {it.id}.toSet()
        redesigned.forEach {t->
            assertNotEquals(t.name,DesignLanguage.MATERIAL,t.language)
            assertEquals(t.name,ThemeTexture.NONE,t.texture)
            assertTrue(t.name,t.headFont in fontIds && t.bodyFont in fontIds)
            assertNotNull("${t.name} title",englishStrings[t.titleKey]);assertNotNull("${t.name} description",englishStrings[t.detailKey])
        }
        // Catalog collections, operating systems and the quiet baseline keep their look.
        (AppTheme.entries-redesigned.toSet()).forEach {assertEquals(it.name,DesignLanguage.MATERIAL,it.language)}
    }

    @Test fun theRedesignedGroupsAreVariedAndEveryLanguageIsUsed() {
        val groups=groupedAppThemes().filter {(g,_)->g !in catalog && g !in designLanguageThemeGroups}
        groups.forEach {(g,themes)->
            assertTrue("${g.name} mixes languages",themes.map {it.language}.distinct().size>=2)
            assertTrue("${g.name} light and dark",themes.any {it.dark} && themes.any {!it.dark} || g in setOf(AppThemeGroup.BOLD,AppThemeGroup.SOFT))
        }
        assertEquals(DesignLanguage.entries.toSet()-DesignLanguage.MATERIAL,redesigned.map {it.language}.toSet())
        // Every theme is listed exactly once.
        val listed=groupedAppThemes().flatMap {it.second}
        assertEquals(AppTheme.entries.size,listed.size);assertEquals(AppTheme.entries.toSet(),listed.toSet())
        assertEquals(AppThemeGroup.CITY,appThemeGroup(AppTheme.SPACE_TERMINAL))
    }

    @Test fun boardsUseRealDisplayTechnologiesThatFitTheLanguage() {
        assertEquals(BoardDisplayTechnology.SPLIT_FLAP,boardDisplayTechnology(AppTheme.SWISS_METRO))
        assertEquals(BoardDisplayTechnology.FLIP_DOT,boardDisplayTechnology(AppTheme.BRUTALIST))
        assertEquals(BoardDisplayTechnology.E_INK,boardDisplayTechnology(AppTheme.PARCHMENT))
        assertEquals(BoardDisplayTechnology.REFLECTIVE_LCD,boardDisplayTechnology(AppTheme.POCKET_CONSOLE))
        assertEquals(BoardDisplayTechnology.NIXIE,boardDisplayTechnology(AppTheme.KRAFT_STUDIO))
        assertEquals(BoardDisplayTechnology.PLASMA,boardDisplayTechnology(AppTheme.COPPER_CIRCUIT))
        assertEquals(BoardDisplayTechnology.LED_MATRIX,boardDisplayTechnology(AppTheme.CYBERPUNK_NEON))
        assertEquals(BoardDisplayTechnology.SEGMENT_LED,boardDisplayTechnology(AppTheme.INDUSTRIAL))
        // Operating systems keep their era screens.
        assertEquals(BoardDisplayTechnology.COLOR_CRT,boardDisplayTechnology(AppTheme.OS_WIN_95))
        assertTrue(BoardDisplayTechnology.entries.all {tech->AppTheme.entries.any {boardDisplayTechnology(it)==tech}})
    }

    @Test fun everyTechnologyRendersALegibleDeterministicBoard() {
        val now=1_800_000_000_000L
        val stop=Location("Bochum Hbf","b",51.478,7.223,"TEST")
        val rows=listOf(Departure("1",stop,"RE 1",LineType.REGIONAL,"Aachen Hbf",now+240_000,now+240_000,DelayInfo(),"4"),
            Departure("2",stop,"U35",LineType.SUBWAY,"Herne Schloß Strünkede",now+420_000,now+540_000,DelayInfo(120,120),"1"))
        BoardDisplayTechnology.entries.forEach {tech->
            val theme=AppTheme.entries.first {boardDisplayTechnology(it)==tech}
            val colors=themedDisplayColors(theme,BoardColors(0xFF202020,theme.background,theme.accent))
            val model=BoardPresentation("Bochum Hbf",rows,BoardDesign.APP_THEME,colors,"Stand 14:28","",appStyle=theme)
            val a=BoardRenderer.render(model,340f,170f);val b=BoardRenderer.render(model,340f,170f)
            assertTrue(tech.name,a.sameAs(b))
            val bg=colors.background.toInt()
            val distinct=(0 until a.width step 3).sumOf {x->(0 until a.height step 3).count {y->
                val c=a.getPixel(x,y);Math.abs(Color.red(c)-Color.red(bg))+Math.abs(Color.green(c)-Color.green(bg))+Math.abs(Color.blue(c)-Color.blue(bg))>90}}
            assertTrue("${tech.name} draws visible text ($distinct)",distinct>120)
            a.recycle();b.recycle()
        }
    }

    @Test fun networkPlanOffersAnAppThemeDesignOnTopAndTenMoreWorldMaps() {
        assertEquals(NetworkPlanDesign.APP_THEME,NetworkPlanDesign.entries.first())
        assertTrue("ten or more new maps",NetworkPlanDesign.entries.size-NetworkPlanDesign.entries.indexOf(NetworkPlanDesign.SINGAPORE)-1>=10)
        NetworkPlanDesign.entries.forEach {d->
            assertNotNull(englishStrings[d.titleKey]);assertNotNull(englishStrings[d.descriptionKey])
            if(d!=NetworkPlanDesign.OPENV) assertTrue(d.name,d.look.palette!!.size>=6)
        }
        assertEquals(NetworkPlanDesign.entries.size,NetworkPlanDesign.entries.map {it.look}.distinct().size)
        // The app-theme look follows the language: graphic designs get hard corners, neon designs glow.
        val brutal=appThemeNetworkLook(AppTheme.BRUTALIST,AppTheme.BRUTALIST.accent,AppTheme.BRUTALIST.secondary,AppTheme.BRUTALIST.accent,false)
        val neon=appThemeNetworkLook(AppTheme.CYBERPUNK_NEON,AppTheme.CYBERPUNK_NEON.accent,AppTheme.CYBERPUNK_NEON.secondary,AppTheme.CYBERPUNK_NEON.accent,true)
        assertEquals(0f,brutal.corner);assertEquals(LineCasing.INK,brutal.casing)
        assertEquals(LineStyle.GLOW,neon.line)
        assertTrue("distinct app-theme line colours",brutal.palette!!.size>=6 && brutal.palette!!.distinct().size==brutal.palette!!.size)
    }
}
