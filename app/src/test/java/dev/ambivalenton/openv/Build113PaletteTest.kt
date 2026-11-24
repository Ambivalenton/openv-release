// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import androidx.core.graphics.ColorUtils
import dev.ambivalenton.openv.data.model.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Build 113 · every design has a colour world of its own, usable as it is on its own ground. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class Build113PaletteTest {
    private val designs = groupedAppThemes().flatMap { it.second }
    private fun contrast(a: Long, b: Long) = ColorUtils.calculateContrast(a.toInt() or android.graphics.Color.BLACK, b.toInt() or android.graphics.Color.BLACK)

    @Test fun everyDesignOffersTenDistinctAccentsReadableOnItsGround() {
        designs.forEach { t ->
            val accents = ThemeColorWorld.accents(t)
            assertEquals(t.name, 10, accents.size)
            assertEquals(t.name, 10, accents.distinct().size)
            accents.forEach { assertTrue("${t.name} ${"%08X".format(it)} on ground ${contrast(it, t.background)}", contrast(it, t.background) >= 3.0) }
            // Clearly different from each other, not ten shades of one.
            val close = accents.indices.flatMap { i -> (i + 1 until accents.size).map { j -> ThemeColorWorld.distance(accents[i], accents[j]) } }.min()
            assertTrue("${t.name} closest pair $close", close >= 3.5)
        }
    }

    @Test fun worldsAreTheDesignsOwnNotShared() {
        // Before: fourteen families for all designs. Now nearly every design has its own set.
        val worlds = designs.map { ThemeColorWorld.accents(it).toSet() }
        assertTrue("distinct worlds ${worlds.distinct().size} of ${designs.size}", worlds.distinct().size >= designs.size - 5)
        // The design's own accent hue leads its world (its tone may move to read on the ground).
        designs.filter { ThemeColorWorld.chroma(it.accent) > 20 }.forEach { t ->
            val first = ThemeColorWorld.accents(t).first()
            val a = dev.ambivalenton.openv.color.utilities.Hct.fromInt(t.accent.toInt()).hue
            val f = dev.ambivalenton.openv.color.utilities.Hct.fromInt(first.toInt()).hue
            assertTrue(t.name, kotlin.math.abs(((a - f + 540.0) % 360.0) - 180.0) < 20.0)
        }
    }

    @Test fun groundsKeepTheDesignsBrightnessAndInksStayReadable() {
        designs.forEach { t ->
            for (role in listOf(DesignColorRole.BACKGROUND, DesignColorRole.SURFACE)) {
                val base = if (role == DesignColorRole.SURFACE) t.surface else t.background
                val set = ThemeColorWorld.set(t, role, base)
                assertEquals(t.name, 5, set.swatches.size)
                assertEquals("${t.name} default first", base or 0xff000000L, set.swatches.first().color)
                set.swatches.drop(1).forEach { assertEquals(t.name, ThemeColorWorld.tone(base), ThemeColorWorld.tone(it.color), 7.0) }
            }
            val inks = ThemeColorWorld.set(t, DesignColorRole.TEXT, 0).swatches.map { it.color }
            assertEquals(t.name, 10, inks.distinct().size)
            inks.forEach { assertTrue("${t.name} ink ${contrast(it, t.background)}", contrast(it, t.background) >= 7.0) }
        }
    }

    @Test fun favouritesAreTwentyDistinctColoursOfTheDesign() {
        designs.forEach { t ->
            val favourites = ThemeColorWorld.favorites(t)
            assertEquals(t.name, 20, favourites.size)
            assertEquals(t.name, 20, favourites.distinct().size)
            assertEquals(t.name, ThemeColorWorld.accents(t), favourites.take(10))
        }
    }
}
