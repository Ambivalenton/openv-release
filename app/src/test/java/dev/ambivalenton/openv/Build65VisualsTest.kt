// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import dev.ambivalenton.openv.ui.theme.*
import org.junit.Assert.*
import org.junit.Test

/** Acceptance group D (OSM contrast) of build 65: black or white by composed luminance. */
class Build65VisualsTest {
    @Test fun inkFollowsTheComposedBackground() {
        assertEquals(Color.Black,contrastInk(Color.White))
        assertEquals(Color.White,contrastInk(Color.Black))
        // AMOLED: pure black surfaces.
        assertEquals(Color.White,contrastInk(Color(0xFF000000)))
        // A light translucent pill over an unknown map still needs dark ink.
        assertEquals(Color.Black,contrastInk(Color(0xFFFDF8F6).copy(alpha=.94f)))
        assertEquals(Color.White,contrastInk(Color(0xFF141218).copy(alpha=.94f)))
    }
    @Test fun theOutlineIsAlwaysOppositeToTheInk() {
        assertTrue(relativeLuminance(contrastOutline(Color.Black))>.9)
        assertTrue(relativeLuminance(contrastOutline(Color.White))<.01)
        // A light grey theme ink (dark mode onSurfaceVariant) receives a dark edge.
        assertTrue(relativeLuminance(contrastOutline(Color(0xFFCAC4D0)))<.01)
    }
    @Test fun translucencyIsComposedBeforeLuminanceIsMeasured() {
        val half=Color(1f,1f,1f,.5f)
        assertEquals(.5f,half.compositeOver(Color.Black).red,.01f)
        assertEquals(21.0,contrastRatio(Color.Black,Color.White),1e-6)
        // The stable 94 % pill keeps at least WCAG AA contrast over any map pixel beneath it.
        for(pill in listOf(Color(0xFFFDF8F6),Color(0xFF141218),Color(0xFF000000),Color(0xFFEEEEEE),Color(0xFF1E1E1E))) {
            val translucent=pill.copy(alpha=.94f);val ink=contrastInk(translucent)
            val worst=minOf(contrastRatio(ink,translucent.compositeOver(Color.Black)),contrastRatio(ink,translucent.compositeOver(Color.White)))
            assertTrue("pill $pill worst contrast $worst",worst>=4.5)
        }
    }
}
