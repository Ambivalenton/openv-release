// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import dev.ambivalenton.openv.ui.screens.SettingsPage
import dev.ambivalenton.openv.ui.screens.searchSettings
import dev.ambivalenton.openv.ui.screens.settingsExcerpt
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Build 121 · the settings search finds every word that is written on a settings page. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "de-rDE")
class Build121SettingsSearchTest {
    private fun hits(query: String) = searchSettings(query)

    @Test fun fussFindsTheFootprintsAndTheWalkingPace() {
        val hits = hits("Fuß")
        assertTrue(hits.joinToString { "${it.page}:${it.titleKey}" }, hits.any { it.page == SettingsPage.TIMELINE && it.titleKey == "Fußabdrücke" })
        assertTrue(hits.any { it.page == SettingsPage.WALKING })
        // Spelled without ß, too.
        assertTrue(hits("fuss").any { it.page == SettingsPage.TIMELINE })
    }

    @Test fun wordsInsideCompoundsAndDescriptionsAreFound() {
        assertTrue(hits("abdruck").any { it.page == SettingsPage.TIMELINE })
        assertTrue(hits("Punktlinie").any { it.page == SettingsPage.TIMELINE })
        // A long description is shown as a passage under the page's name.
        val akku = hits("Akku")
        assertTrue(akku.any { it.page == SettingsPage.MODES })
        assertTrue(hits("Eckradien").any { it.page == SettingsPage.TYPOGRAPHY })
        assertTrue(hits("Animierte Verkehrsmittel").any { it.page == SettingsPage.NETWORK_PLAN })
    }

    @Test fun namesAndSynonymsStayFirst() {
        assertEquals(SettingsPage.TYPOGRAPHY, hits("Schrift").first().page)
        assertEquals("backup", hits("Backup").first().anchor)
        assertEquals("Planermodus", hits("ohne Standort").first().titleKey)
        assertTrue(hits("Zzyzx").isEmpty())
    }

    @Test fun anExcerptKeepsTheWordAndCutsAtSpaces() {
        val text = "Schont den Akku: Standort nur etwa alle fünf Minuten, keine Animationen und weniger automatische Aktualisierungen im Hintergrund."
        val cut = settingsExcerpt(text, "aktualisierungen")
        assertTrue(cut, cut.contains("Aktualisierungen"))
        assertTrue(cut.length <= 76)
        assertTrue(cut.startsWith("…"))
    }
}
