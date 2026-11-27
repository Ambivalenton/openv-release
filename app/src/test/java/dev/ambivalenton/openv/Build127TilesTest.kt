// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import androidx.core.graphics.ColorUtils
import androidx.compose.ui.graphics.toArgb
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.localization.englishStrings
import dev.ambivalenton.openv.ui.theme.appThemeScheme
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Build 127 · "Kacheln & Farbe": colourful, square, minimal – flat planes, right angles, no texture, light and dark. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = OpenVApp::class)
class Build127TilesTest {
    private val tiles = AppTheme.entries.filter { appThemeGroup(it) == AppThemeGroup.TILES }

    @Test fun theTileCollectionIsFlatSquareAndColourful() {
        assertTrue(tiles.size >= 8)
        assertTrue(tiles.any { it.dark } && tiles.any { !it.dark })
        tiles.forEach { t ->
            assertEquals(t.name, ThemeShape.SHARP, t.shapeStyle)
            assertEquals(t.name, 0f, t.radiusScale)
            assertEquals(t.name, DesignLanguage.FLAT, t.language)
            assertEquals(t.name, ThemeTexture.NONE, t.texture)
            assertNotNull(englishStrings[t.titleKey]); assertNotNull(englishStrings[t.detailKey])
            // Neither name nor description borrows a product name.
            listOf(t.titleKey, t.detailKey).forEach { assertFalse(it, Regex("(?i)metro|windows").containsMatchIn(it)) }
            // Readable: text on the ground and the accent on the ground.
            val s = appThemeScheme(t)
            assertTrue("${t.name} text", ColorUtils.calculateContrast(s.onBackground.toArgb(), s.background.toArgb()) >= 7.0)
            assertTrue("${t.name} accent", ColorUtils.calculateContrast(s.primary.toArgb(), s.background.toArgb()) >= 3.0)
        }
        // Colourful: every design has its own accent.
        assertEquals(tiles.size, tiles.map { it.accent }.distinct().size)
        assertNotNull(englishStrings["Kacheln & Farbe"])
        assertTrue(groupedAppThemes().any { it.first == AppThemeGroup.TILES && it.second.size == tiles.size })
    }
}
