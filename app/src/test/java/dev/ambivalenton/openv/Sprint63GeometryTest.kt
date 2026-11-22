// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import dev.ambivalenton.openv.ui.components.*
import dev.ambivalenton.openv.data.model.*
import org.junit.Assert.*
import org.junit.Test

class Sprint63GeometryTest {
    // Build 65 replaces the Build 63 dwell rule: strictly more than 60 % overlap with the dragged
    // centre inside the target offers pairing at once, and the target does not evade.
    @Test fun shortAndLongConnectorsAlwaysHaveEqualGapsAndNoOverlappingOrClippedCircles() {
        for(length in listOf(.2f,1f,2f,3f,4f,7f,8f,15f,21f,23f,31f,48f,99f,120f)) {
            val dots=uniformTimelineDots(length,1.5f,8f)
            assertTrue(dots.centers.isNotEmpty())
            assertTrue(dots.centers.first()-dots.radius>=-.001f)
            assertTrue(dots.centers.last()+dots.radius<=length+.001f)
            dots.centers.zipWithNext().forEach {(a,b)->assertEquals(8f,b-a,.001f);assertTrue(b-a>2*dots.radius)}
            assertEquals(length,dots.centers.first()+dots.centers.last(),.001f)
        }
        assertTrue(uniformTimelineDots(Float.NaN,1.5f,8f).centers.isEmpty())
    }
    @Test fun decadesAndHandheldsUseDistinctEraAppropriateScreenMaterials() {
        assertEquals(BoardDisplayTechnology.VFD,boardDisplayTechnology(AppTheme.DECADE_70))
        assertEquals(BoardDisplayTechnology.COLOR_CRT,boardDisplayTechnology(AppTheme.DECADE_80))
        assertEquals(BoardDisplayTechnology.COLOR_CRT,boardDisplayTechnology(AppTheme.DECADE_90))
        assertEquals(BoardDisplayTechnology.TFT_LCD,boardDisplayTechnology(AppTheme.DECADE_00))
        assertEquals(BoardDisplayTechnology.OLED,boardDisplayTechnology(AppTheme.DECADE_10))
        assertEquals(BoardDisplayTechnology.REFLECTIVE_LCD,boardDisplayTechnology(AppTheme.POCKET_CONSOLE))
        assertTrue(BoardDesign.RETRO_LCD in visibleBoardDesigns)
    }
}
