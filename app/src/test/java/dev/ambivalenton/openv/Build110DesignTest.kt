// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.rendering.DisplayMaterial
import dev.ambivalenton.openv.rendering.displayProfile
import dev.ambivalenton.openv.ui.network.PlanFinish
import dev.ambivalenton.openv.ui.network.planFinishFor
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Build 110 · collections build the app, the plan and the "Display" board from their own material. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class Build110DesignTest {
    private fun collection(prefix: String) = groupedAppThemes().flatMap { it.second }.filter { it.name.startsWith(prefix) }

    @Test fun everyCollectionHasItsOwnConstruction() {
        val languages = mapOf("COSMOS_" to DesignLanguage.GLASS, "MINERAL_" to DesignLanguage.CHROME, "TEXTILE_" to DesignLanguage.STITCH,
            "ARCH_" to DesignLanguage.SWISS, "CAFE_" to DesignLanguage.SOFT, "PRINT_" to DesignLanguage.EDITORIAL,
            "GAME_" to DesignLanguage.PIXEL, "PET_" to DesignLanguage.CLAY)
        languages.forEach { (prefix, language) ->
            val themes = collection(prefix)
            assertTrue(prefix, themes.isNotEmpty())
            themes.forEach { assertEquals(it.name, language, it.language) }
        }
        // Eight collections, eight different plan materials.
        assertEquals(8, languages.keys.map { planFinishFor(collection(it).first()) }.distinct().size)
        assertEquals(PlanFinish.STITCH, planFinishFor(AppTheme.TEXTILE_TARTAN))
        assertEquals(PlanFinish.PLAIN, planFinishFor(null))
        assertEquals(PlanFinish.PLAIN, planFinishFor(AppTheme.CLASSIC_LIGHT))
    }

    @Test fun collectionScreensAreMadeOfTheirMaterial() {
        collection("TEXTILE_").forEach { assertEquals(it.name, DisplayMaterial.EMBROIDERY, displayProfile(it).material) }
        collection("MINERAL_").forEach { assertEquals(it.name, DisplayMaterial.ENGRAVED, displayProfile(it).material) }
        collection("PET_").forEach { assertEquals(it.name, DisplayMaterial.LETTERBOARD, displayProfile(it).material) }
        collection("GAME_").forEach { assertEquals(it.name, DisplayMaterial.GAME, displayProfile(it).material) }
        // Ten games, at least five different screens, each in counter-brightness to its design.
        assertTrue(collection("GAME_").map { displayProfile(it).motif }.distinct().size >= 5)
        (collection("TEXTILE_") + collection("MINERAL_") + collection("PET_") + collection("GAME_"))
            .forEach { assertEquals(it.name, it.dark, displayProfile(it).light) }
    }

    @Test fun collectionScreensStayReadable() {
        (collection("TEXTILE_") + collection("MINERAL_") + collection("PET_") + collection("GAME_")).forEach { t ->
            val d = displayProfile(t)
            assertTrue("${t.name} ink", ColorUtils.calculateContrast(d.ink, d.background or android.graphics.Color.BLACK) >= 4.5)
            assertTrue("${t.name} accent", ColorUtils.calculateContrast(d.accent, d.background or android.graphics.Color.BLACK) >= 4.5)
        }
    }
}

/** Build 111 · the coloured light behind glass keeps every text on the ground readable. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class Build111GlassTest {
    @Test fun glassLightKeepsTextReadable() {
        val glass = groupedAppThemes().flatMap { it.second }.filter { it.language == DesignLanguage.GLASS }
        assertTrue(glass.size >= 10)
        glass.forEach { t ->
            val s = dev.ambivalenton.openv.ui.theme.appThemeScheme(t)
            val lights = dev.ambivalenton.openv.ui.theme.glassFieldColors(s.background, s.primary, s.secondary, t.dark)
            val strength = dev.ambivalenton.openv.ui.theme.glassFieldStrength(t.dark)
            lights.forEach { light ->
                val ground = androidx.compose.ui.graphics.lerp(s.background, light, strength)
                fun ratio(a: androidx.compose.ui.graphics.Color) = ColorUtils.calculateContrast(a.toArgb() or android.graphics.Color.BLACK,
                    ground.toArgb() or android.graphics.Color.BLACK)
                assertTrue("${t.name} text ${ratio(s.onBackground)}", ratio(s.onBackground) >= 4.5)
                assertTrue("${t.name} accent ${ratio(s.primary)}", ratio(s.primary) >= 3.0)
            }
        }
    }
}
