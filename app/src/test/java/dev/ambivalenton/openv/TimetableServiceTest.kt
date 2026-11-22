// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.widget.WidgetRefreshScheduler
import dev.ambivalenton.openv.widget.widgetRequestAllowed
import dev.ambivalenton.openv.widget.widgetStandStale
import org.junit.Assert.*
import org.junit.Test

/** The timetable service is one switchable description; widgets ask it only on demand. */
class TimetableServiceTest {
    @Test fun theDefaultBuildTalksToVrrOpenService() {
        val active = TimetableServices.active
        assertEquals("vrr", BuildConfig.TIMETABLE_SERVICE)
        assertSame(TimetableServices.vrr, active)
        assertEquals(TimetableProtocol.EFA_JSON, active.protocol)
        assertEquals("https://openservice.vrr.de/openservice/", active.endpoint)
        assertEquals(active.endpoint, TimetableProfile.endpoint)
        assertEquals("VRR", TimetableProfile.shortName(Engine.VRR))
        assertEquals(active.coverage, TimetableProfile.coverage(Engine.VRR))
    }

    @Test fun storedSettingsReturnToTheServiceOfThisBuild() {
        val restored = AppPublication.settings(AppSettings(engine = Engine.DELFI, efaEndpoint = "https://old.example/"))
        assertEquals(TimetableServices.active.engine, restored.engine)
        assertEquals(TimetableServices.active.endpoint, restored.efaEndpoint)
    }

    @Test fun delfiNaviTakesItsInterfaceFromTheBuildAndHasItsOwnAttribution() {
        val delfi = TimetableServices.byId("delfi")
        assertEquals("efa", BuildConfig.TIMETABLE_PROTOCOL)
        assertEquals(TimetableProtocol.EFA_JSON, delfi.protocol)
        assertEquals(TimetableProtocol.TRIAS, TimetableProtocol.of("trias"))
        assertEquals(TimetableProtocol.EFA_JSON, TimetableProtocol.of("efa"))
        assertEquals(Engine.DELFI, delfi.engine)
        assertEquals("DELFI", TimetableProfile.shortName(Engine.DELFI))
        assertNotEquals(TimetableServices.vrr.attribution, delfi.attribution)
        // Every service has a unique id and engine; an unknown id falls back to VRR.
        assertEquals(TimetableServices.all.size, TimetableServices.all.map { it.id }.toSet().size)
        assertEquals(TimetableServices.all.size, TimetableServices.all.map { it.engine }.toSet().size)
        assertSame(TimetableServices.vrr, TimetableServices.byId("unknown"))
        assertTrue(TimetableServices.all.all { it.sourceUrl.startsWith("https://") })
    }

    @Test fun theWidgetClockAsksTheServiceOnlyWhileTheScreenIsOn() {
        assertTrue(widgetRequestAllowed(periodic = true, screenOn = true))
        assertFalse(widgetRequestAllowed(periodic = true, screenOn = false))
        // A refresh the person starts is never skipped.
        assertTrue(widgetRequestAllowed(periodic = false, screenOn = false))
    }

    @Test fun unlockingRefreshesOnlyAStandOlderThanFifteenMinutes() {
        val now = 1_800_000_000_000L
        assertFalse(widgetStandStale(now - 5 * 60_000L, now))
        assertTrue(widgetStandStale(now - WidgetRefreshScheduler.STALE_AFTER_MILLIS, now))
        assertTrue(widgetStandStale(0L, now))
        // A clock set back counts as stale rather than blocking refreshes.
        assertTrue(widgetStandStale(now + 60_000L, now))
    }
}
