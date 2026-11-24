// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.data.preferences.normalizePreferences
import org.junit.Assert.*
import org.junit.Test

/** Build 109 · the curated design catalogue: fewer near copies, every stored choice still lands well. */
class Build109CurationTest {
    private val shown = groupedAppThemes().flatMap { it.second }

    @Test fun retiredDesignsLeaveThePickerAndTheirSuccessorsStay() {
        assertTrue(retiredAppThemes.keys.none { it in shown })
        retiredAppThemes.forEach { (old, new) ->
            assertFalse("$old → $new is retired itself", new.retired)
            assertEquals("$old → $new keeps day or night", old.dark, new.dark)
            assertTrue("$new is shown", new in shown)
        }
        assertEquals(AppTheme.entries.size - retiredAppThemes.size, shown.distinct().size)
        // Quality over quantity, but every collection still offers a real choice of light and dark.
        groupedAppThemes().forEach { (group, themes) ->
            assertTrue("$group has ${themes.size}", themes.size >= 4)
            if (group !in setOf(AppThemeGroup.SYSTEMS, AppThemeGroup.BOLD, AppThemeGroup.GLOW_EDGE)) assertTrue("$group needs light and dark", themes.any { it.dark } && themes.any { !it.dark })
        }
        assertFalse("Weich & taktil merged into relief and clay", groupedAppThemes().any { it.first == AppThemeGroup.SOFT })
    }

    @Test fun noBrandNamesInThePicker() {
        val brands = Regex("\\b(Windows|iOS|Mac|Macintosh|Android|Ubuntu|GNOME|Amiga|Atari|BeOS|NeXTSTEP|OS/2|Unix|Aqua|Aero|Holo)\\b")
        shown.forEach { assertFalse(it.title, brands.containsMatchIn(it.title)) }
        val eras = groupedAppThemes().first { it.first == AppThemeGroup.SYSTEMS }.second.map { it.title.take(4).toInt() }
        assertEquals("in the order of their year", eras.sorted(), eras)
    }

    @Test fun storedChoicesMoveToTheirSuccessor() {
        val own = AppThemeColors(0xFF112233, 0xFF223344, 0xFF336699, 0xFF996633, 0xFFF0F0F0)
        val theirs = AppThemeColors(0xFF000000, 0xFF111111, 0xFFFFFFFF, 0xFF777777, 0xFFEEEEEE)
        val stored = AppSettings(appTheme = AppTheme.COOL_GRAPHITE, lastRandomTheme = AppTheme.PET_PUPPY,
            appThemeColors = mapOf(AppTheme.COOL_GRAPHITE to own, AppTheme.GAME_VR to theirs),
            dayNight = DayNightSettings(other = AppearanceProfile(appTheme = AppTheme.OS_MAC_LEOPARD)))
        val loaded = normalizePreferences(stored)
        assertEquals(AppTheme.MONOCHROME, loaded.appTheme)
        assertEquals("the active design keeps its own colours", own, loaded.appThemeColors[AppTheme.MONOCHROME])
        assertFalse(loaded.appThemeColors.keys.any { it.retired })
        assertEquals(AppTheme.PET_DOG_GOLDEN, loaded.lastRandomTheme)
        assertEquals(AppTheme.OS_WIN_8, loaded.dayNight.other?.appTheme)
        // A kept design and its own colours are untouched; the successor's own colours win.
        val kept = AppSettings(appTheme = AppTheme.COOL_GRAPHITE, appThemeColors = mapOf(AppTheme.COOL_GRAPHITE to own, AppTheme.MONOCHROME to theirs))
        assertEquals(theirs, normalizePreferences(kept).appThemeColors[AppTheme.MONOCHROME])
        val untouched = AppSettings(appTheme = AppTheme.FOREST)
        assertEquals(AppTheme.FOREST, normalizePreferences(untouched).appTheme)
    }

    @Test fun nightCounterpartAndRandomDrawOnlyUseKeptDesigns() {
        AppTheme.entries.filter { !it.retired }.forEach {
            assertFalse(counterpartTheme(it, !it.dark).retired)
        }
        repeat(200) { assertFalse(dev.ambivalenton.openv.ui.theme.drawRandomTheme(AppSettings(), kotlin.random.Random(it))!!.retired) }
    }
}
