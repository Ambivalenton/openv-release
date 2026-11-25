// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import dev.ambivalenton.openv.data.repository.StaticStopCatalog
import dev.ambivalenton.openv.data.repository.catalogMatch
import dev.ambivalenton.openv.data.repository.rankSearchedPlaces
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Build 122 · test report: the offline stop search understands other spellings of the same station. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = OpenVApp::class)
class Build122StopSearchTest {
    private val catalog by lazy { StaticStopCatalog(RuntimeEnvironment.getApplication()) }
    private fun first(query: String) = runBlocking { catalog.search(query) }.firstOrNull()?.name.orEmpty()

    @Test fun theQueryKeepsTheTypedFormAndAddsTheOthers() {
        assertEquals("(duesseldorf* OR dusseldorf*) (hauptbahnhof* OR hbf* OR hauptbf*)", catalogMatch("Duesseldorf Hbf"))
        assertEquals("duisburg* (hauptbahnhof* OR hbf* OR hauptbf*)", catalogMatch("Duisburg central station"))
        assertEquals("dortmund* (hauptbahnhof* OR hbf* OR hauptbf*)", catalogMatch("Dortmund Hauptbanhof"))
        // A real "oe" (Oelde) is still searched as written.
        assertTrue(catalogMatch("Oelde")!!.contains("oelde*"))
    }

    @Test fun theCatalogFindsTheStations() {
        for ((query, expected) in listOf("Duesseldorf Hbf" to "Düsseldorf", "Koeln Hbf" to "Köln", "Muenster Hbf" to "Münster",
            "Duisburg central station" to "Duisburg", "Dortmund main station" to "Dortmund", "Dortmund Hauptbanhof" to "Dortmund",
            "Oelde" to "Oelde")) {
            val name = first(query)
            assertTrue("$query → $name", name.contains(expected))
        }
        assertTrue("main station", first("Duisburg central station").contains("Hbf") || first("Duisburg central station").contains("Hauptbahnhof"))
    }

    /** The search field ranks local and remote rows again with the typed text: they must survive it. */
    @Test fun theRankingKeepsTheseHits() {
        for (query in listOf("Duesseldorf Hbf", "Duisburg central station", "Dortmund Hauptbanhof")) {
            val rows = runBlocking { catalog.search(query) }
            assertTrue(query, rankSearchedPlaces(query, rows, directory = rows).isNotEmpty())
        }
    }
}
