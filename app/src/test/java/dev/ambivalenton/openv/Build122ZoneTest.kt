// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import dev.ambivalenton.openv.data.model.clockTime
import dev.ambivalenton.openv.data.model.transitZone
import dev.ambivalenton.openv.data.model.transitZoneNote
import org.junit.Assert.*
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.TimeZone

/** Build 122 · test report: a German departure at 00:43 showed as 23:43 of the day before on a phone set to London. */
class Build122ZoneTest {
    private val departure = ZonedDateTime.of(2026, 11, 25, 0, 43, 0, 0, ZoneId.of("Europe/Berlin")).toInstant().toEpochMilli()

    @Test fun timesAreTheStopsLocalTimeWhateverThePhoneSays() {
        val before = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Europe/London"))
            assertEquals("00:43", clockTime(departure))
            assertEquals("Ortszeit Deutschland · Mi., 25.11.", transitZoneNote(departure, ZoneId.of("Europe/London")))
        } finally { TimeZone.setDefault(before) }
        assertNull("same zone: no note", transitZoneNote(departure, transitZone))
        // Summer: still the same offset in Berlin and Vienna – no note either.
        assertNull(transitZoneNote(departure, ZoneId.of("Europe/Vienna")))
    }
}
