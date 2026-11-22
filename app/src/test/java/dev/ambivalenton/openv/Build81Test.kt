// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import android.app.Application
import androidx.core.graphics.ColorUtils
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.localization.englishStrings
import dev.ambivalenton.openv.rendering.*
import dev.ambivalenton.openv.ui.components.colorfulCapsuleTheme
import dev.ambivalenton.openv.ui.theme.appThemeScheme
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Build81Test {
    @Test fun everyDisplayContrastsWithItsAppThemeAndStaysLegible() {
        for (theme in AppTheme.entries) {
            val d = displayProfile(theme)
            assertEquals("${theme.name}: light themes get dark screens and vice versa", theme.dark, d.light)
            assertTrue(theme.name, ColorUtils.calculateContrast(d.ink, d.background) >= 4.5)
            assertTrue(theme.name, ColorUtils.calculateContrast(d.accent, d.background) >= 4.5)
        }
        // Art becomes painting, retro becomes a game, systems keep their window.
        assertEquals(DisplayMaterial.PAINTING, displayProfile(AppTheme.REN_LUMEN).material)
        assertEquals(DisplayMaterial.GAME, displayProfile(AppTheme.POCKET_CONSOLE).material)
        assertEquals(DisplayMaterial.OS_WINDOW, displayProfile(AppTheme.OS_MAC_LEOPARD).material)
        assertTrue(AppTheme.entries.map { displayProfile(it).material }.toSet().size >= 14)
    }

    @Test fun customThemeColoursTintTheDisplay() {
        val theme = AppTheme.CLASSIC_LIGHT
        val custom = theme.defaultColors().copy(accent = 0xFFB3261E)
        assertNotEquals(displayProfile(theme).accent, displayProfile(theme, custom).accent)
    }

    @Test fun bothFollowingBoardsUseTheirOwnColoursAndRender() {
        for (theme in listOf(AppTheme.CLASSIC_LIGHT, AppTheme.FROSTED_BERRY, AppTheme.OS_WIN_95, AppTheme.VINTAGE_TRANSIT)) for (design in listOf(BoardDesign.APP_THEME, BoardDesign.APP_INTEGRATED)) {
            val state = StoredState(settings = AppSettings(appTheme = theme, board = BoardSettings(design = design)))
            val m = boardPresentation(state, appThemeScheme(theme))
            assertEquals(design, m.design); assertTrue(design.followsAppTheme)
            assertEquals(design == BoardDesign.APP_INTEGRATED, m.appPalette != null)
            assertTrue(ColorUtils.calculateContrast(m.colors.foreground.toInt(), m.colors.background.toInt()) >= 4.5)
            val b = BoardRenderer.render(m, 340f, 270f); assertEquals(255, android.graphics.Color.alpha(b.getPixel(170, 135))); b.recycle()
        }
        assertEquals("Display", BoardDesign.APP_THEME.title); assertEquals("App-Theme", BoardDesign.APP_INTEGRATED.title)
        assertNotNull(englishStrings["Display"]); assertNotNull(englishStrings[NetworkPlanDesign.APP_THEME.descriptionKey])
    }

    @Test fun everyBundledFontHasACategoryAndCategoriesKeepTheirOrder() {
        val grouped = groupedAppFonts()
        assertEquals(appFonts.size, grouped.sumOf { it.second.size })
        assertEquals(AppFontCategory.entries.toList(), grouped.map { it.first })
        assertEquals(AppFontCategory.PIXEL, appFontCategory("pressstart2p"))
        assertEquals(AppFontCategory.SANS, appFontCategory("unknown-future-font"))
        AppFontCategory.entries.forEach { assertNotNull(it.name, englishStrings[it.title]) }
    }

    @Test fun onlyColourfulThemesTintTheCapsulePerDestination() {
        assertTrue(colorfulCapsuleTheme(AppTheme.CONFETTI_STUDIO)); assertTrue(colorfulCapsuleTheme(AppTheme.RAINBOW_CLASSIC))
        assertFalse(colorfulCapsuleTheme(AppTheme.CLASSIC_DARK)); assertFalse(colorfulCapsuleTheme(AppTheme.OS_WIN_XP))
        assertFalse(colorfulCapsuleTheme(null))
    }

    @Test fun analogClockTakesTheShortWayAndPlacesBothHands() {
        assertEquals(1440f, dev.ambivalenton.openv.ui.components.nearestClockMinutes(1439f, 0), .001f)
        assertEquals(-1f, dev.ambivalenton.openv.ui.components.nearestClockMinutes(5f, 1439), .001f)
        assertEquals(870f, dev.ambivalenton.openv.ui.components.nearestClockMinutes(480f, 870), .001f)
        val (hour, minute) = dev.ambivalenton.openv.ui.components.clockHandAngles(14 * 60 + 30f)
        assertEquals(75f, hour, .001f); assertEquals(180f, minute, .001f)
    }
}
