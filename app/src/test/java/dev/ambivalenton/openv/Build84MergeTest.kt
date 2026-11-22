// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import android.app.Application
import android.graphics.Bitmap
import androidx.core.graphics.ColorUtils
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.rendering.*
import dev.ambivalenton.openv.ui.network.PlanStroke
import dev.ambivalenton.openv.ui.network.appThemePlanLook
import dev.ambivalenton.openv.ui.theme.appThemeScheme
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Build 84 joins three lines that all started on build 80: the ten ThemeLanguage collections, the
 * redesigned older groups (DesignLanguage) and the Display / App-Theme boards with the new search.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Build84MergeTest {
    @Test fun everyThemeBelongsToAtMostOneDesignSystem() {
        for (theme in AppTheme.entries)
            assertFalse(theme.name, theme.themeLanguage != ThemeLanguage.STANDARD && theme.language != DesignLanguage.MATERIAL)
        assertEquals(100, AppTheme.entries.count { it.themeLanguage != ThemeLanguage.STANDARD })
        // Picker order: the ten collections directly after "Klar & reduziert", "Glas & Licht" after them.
        val order = groupedAppThemes().map { it.first }
        val clean = order.indexOf(AppThemeGroup.CLEAN)
        assertEquals(designLanguageThemeGroups, order.subList(clean + 1, clean + 11))
        assertTrue(order.indexOf(AppThemeGroup.GLASS) > clean + 10)
        // Faces: one face for the ten collections, head and body face for the redesigned groups.
        assertEquals(AppTheme.BRUTAL_POSTER.typefaceId, AppTheme.BRUTAL_POSTER.headFace)
        assertEquals(AppTheme.BRUTAL_POSTER.typefaceId, AppTheme.BRUTAL_POSTER.bodyFace)
        val redesigned = AppTheme.entries.first { it.language != DesignLanguage.MATERIAL }
        assertEquals(redesigned.headFont, redesigned.headFace)
    }

    @Test fun displayIsACuratedScreenOrARealTechnologyInCounterBrightness() {
        var technologies = 0
        for (theme in AppTheme.entries) {
            val technology = displayTechnologyFor(theme)
            if (technology == null) {
                val d = displayProfile(theme)
                assertEquals("${theme.name}: light designs get dark screens and vice versa", theme.dark, d.light)
            } else {
                technologies++
                assertFalse("${theme.name} curated", hasCuratedDisplay(theme))
                val lightScreen = technology == BoardDisplayTechnology.E_INK || technology == BoardDisplayTechnology.REFLECTIVE_LCD
                assertEquals("${theme.name} $technology", theme.dark, lightScreen)
            }
        }
        // Real technologies stay in use, including for the ten collections.
        assertTrue("technologies in use: $technologies", technologies >= 40)
        assertEquals(BoardDisplayTechnology.FLIP_DOT, displayTechnologyFor(AppTheme.BRUTAL_POSTER))
        assertEquals(BoardDisplayTechnology.E_INK, displayTechnologyFor(AppTheme.GLOW_VIOLET))
        // Curated screens of the later request win: art paints, retro plays, systems show windows.
        assertNull(displayTechnologyFor(AppTheme.REN_LUMEN)); assertNull(displayTechnologyFor(AppTheme.POCKET_CONSOLE))
        assertNull(displayTechnologyFor(AppTheme.OS_WIN_95))
    }

    private fun board(theme: AppTheme, design: BoardDesign): BoardPresentation {
        val now = 1_800_000_000_000L
        val stop = Location("Bochum Hbf", "b", 51.478, 7.223, "TEST")
        val rows = listOf(Departure("1", stop, "RE 1", LineType.REGIONAL, "Aachen Hbf", now + 240_000, now + 240_000, DelayInfo(), "4"),
            Departure("2", stop, "U35", LineType.SUBWAY, "Herne Schloß Strünkede", now + 420_000, now + 540_000, DelayInfo(120, 120), "1"))
        return boardPresentation(StoredState(settings = AppSettings(appTheme = theme, board = BoardSettings(design = design))), appThemeScheme(theme))
            .copy(title = "Bochum Hbf", rows = rows, caption = "Stand 14:28", emptyMessage = "")
    }

    private fun pixels(b: Bitmap) = IntArray(b.width * b.height).also { b.getPixels(it, 0, b.width, 0, 0, b.width, b.height) }

    @Test fun bothFollowingBoardsRenderForEveryDesignSystem() {
        val themes = listOf(AppTheme.BRUTAL_POSTER, AppTheme.GLOW_VIOLET, AppTheme.KNETE_NIGHT, AppTheme.SWISS_METRO,
            AppTheme.RAINBOW_CITRUS, AppTheme.REN_LUMEN, AppTheme.CLASSIC_LIGHT, AppTheme.OS_WIN_XP, AppTheme.COSMOS_PULSAR)
        for (theme in themes) for (design in listOf(BoardDesign.APP_THEME, BoardDesign.APP_INTEGRATED)) {
            val m = board(theme, design)
            assertTrue("${theme.name} $design", ColorUtils.calculateContrast(m.colors.foreground.toInt(), m.colors.background.toInt()) >= 4.5)
            // The language board (own frames and badges) belongs to "App-Theme" only.
            assertEquals("${theme.name} $design", design == BoardDesign.APP_INTEGRATED && theme.themeLanguage != ThemeLanguage.STANDARD, m.appRoles != null)
            val a = BoardRenderer.render(m, 340f, 220f, 1f); val b = BoardRenderer.render(m, 340f, 220f, 1f)
            assertArrayEquals("${theme.name} $design deterministic", pixels(a), pixels(b))
            assertTrue("${theme.name} $design draws", pixels(a).toSet().size > 8)
            a.recycle(); b.recycle()
        }
    }

    @Test fun appThemeNetworkPlanUsesTheThemesTechniqueAcrossBothSystems() {
        assertEquals(PlanStroke.BRUSH, appThemePlanLook(AppTheme.REN_LUMEN).stroke)
        assertEquals(PlanStroke.PIXEL, appThemePlanLook(AppTheme.POCKET_CONSOLE).stroke)
        assertEquals(PlanStroke.INK, appThemePlanLook(AppTheme.JAPANDI_OAK).stroke)
        assertEquals(PlanStroke.GLOW, appThemePlanLook(AppTheme.GLOW_CYAN).stroke)
        assertEquals(PlanStroke.SKETCH, appThemePlanLook(AppTheme.LINE_PENCIL).stroke)
        // Plain lines speak the language: ink casing and square stations for neo-brutalism.
        val poster = appThemePlanLook(AppTheme.BRUTAL_POSTER)
        assertEquals(PlanStroke.SOLID, poster.stroke); assertEquals(0f, poster.corner)
        // Every theme gets a look with a palette of the theme's own colour family.
        AppTheme.entries.forEach { assertTrue(it.name, appThemePlanLook(it).palette!!.size >= 6) }
    }
}
