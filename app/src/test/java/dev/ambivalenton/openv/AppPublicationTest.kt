// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.data.preferences.normalizePreferences
import dev.ambivalenton.openv.data.repository.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test

class AppPublicationTest {
    @Test fun storedSettingsUseTheConfiguredSourceAndCompactView() {
        val restored = normalizePreferences(AppSettings(efaEndpoint = "https://old.example/",
            chartLongPress = ChartLongPressAction.SHARE, powerSaving = true))
        assertEquals(Engine.VRR, restored.engine)
        assertEquals(TimetableServices.vrr.endpoint, restored.efaEndpoint)
        // The hold action stays; it opens the compact view (no image export of timetable data).
        assertEquals(ChartLongPressAction.SHARE, restored.chartLongPress)
        assertEquals(dev.ambivalenton.openv.localization.tr("Kompaktansicht öffnen"), ChartLongPressAction.SHARE.title)
        assertTrue(restored.powerSaving)
    }

    @Test fun backupKeepsTheUsersPlanButNeverTheProvidersFullJourneyResponse() = runBlocking {
        val f = Fixture45
        val query = JourneyQuery(f.a, f.c, f.now + 600_000)
        val trip = f.trip(f.ride(start = f.now + 600_000)).copy(id = "api-response-must-not-export")
        val favorite = Favorite(label = "Zuhause", location = f.c)
        val state = StoredState(favorites = listOf(favorite),
            savedJourneys = listOf(SavedJourney(favorite = favorite, query = query, trip = trip, savedAt = f.now)))
        val bytes = AppBackupCodec.encode(state, AppLogo.ORIGINAL, emptyList(), f.now)
        assertFalse(bytes.decodeToString().contains(trip.id))
        val decoded = AppBackupCodec.decode(bytes).state
        assertEquals(state.favorites, decoded.favorites)
        assertTrue(decoded.savedJourneys.isEmpty())
        assertEquals(query, decoded.history.single().query)
        // Existing backups remain readable without importing full provider responses.
        val legacy = AppBackupCodec.json.encodeToString(AppBackupDocument(exportedAt = f.now,
            state = state, launcherLogo = AppLogo.ORIGINAL))
        val imported = AppBackupCodec.decode(legacy.encodeToByteArray()).state
        assertTrue(imported.savedJourneys.isEmpty())
        assertEquals(query, imported.history.single().query)
    }
}
