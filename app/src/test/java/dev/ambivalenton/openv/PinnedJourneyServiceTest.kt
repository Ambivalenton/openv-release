// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import android.app.Notification
import android.content.pm.ServiceInfo
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.data.repository.PinnedJourneyController
import dev.ambivalenton.openv.tracking.PinnedJourneyService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = OpenVApp::class)
class PinnedJourneyServiceTest {
    private val now = System.currentTimeMillis()
    private val a = Location("Start", "a", 51.5, 7.2, "Test")
    private val b = Location("Ziel", "b", 51.6, 7.3, "Test")
    private val trip = Trip("service-test", listOf(TripSegment("one", a, b,
        now + 600_000, now + 1_200_000, LineType.BUS, "312")), "Test", now)

    @Test(timeout=30_000) fun ongoingPrivateNotificationReopensTheExactPersistedSession() = runBlocking {
        val app = RuntimeEnvironment.getApplication() as OpenVApp
        val tracker = app.koin.get<PinnedJourneyController>()
        tracker.close()
        tracker.pin(Favorite("f", "Zuhause", b), JourneyQuery(a, b, now), trip, AppSettings())
        val handle = Robolectric.buildService(PinnedJourneyService::class.java).create()
        try {
            val notification = handle.get().notification(tracker.state.value)
            assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
            assertEquals(Notification.VISIBILITY_PRIVATE, notification.visibility)
            assertTrue(notification.contentIntent.isImmutable)
            val intent = Shadows.shadowOf(notification.contentIntent).savedIntent
            val command = parseLink(intent, 7)!!
            assertEquals(tracker.state.value.session!!.id, command.pinnedId)
            assertTrue(command.overview)
            assertEquals(MainActivity::class.java.name, intent.component!!.className)
        } finally { handle.destroy(); tracker.close() }
    }

    @Test(timeout=30_000) fun androidTimeoutStopsTrackingAndKeepsTheJourneyAvailableToResume() = runBlocking {
        val app = RuntimeEnvironment.getApplication() as OpenVApp
        val tracker = app.koin.get<PinnedJourneyController>()
        tracker.close()
        tracker.pin(Favorite("f", "Zuhause", b), JourneyQuery(a, b, now), trip, AppSettings())
        tracker.running()
        val handle = Robolectric.buildService(PinnedJourneyService::class.java).create()
        try {
            handle.get().onTimeout(1, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            assertEquals(PinnedTrackingStatus.PAUSED, tracker.state.value.status)
            assertNotNull(tracker.state.value.session)
            assertNotNull(tracker.state.value.error)
        } finally { handle.destroy(); tracker.close() }
    }
    @Test(timeout=30_000) fun notificationStopEndsTheMatchingJourneyWithoutStartingTracking() = runBlocking {
        val app = RuntimeEnvironment.getApplication() as OpenVApp
        val tracker = app.koin.get<PinnedJourneyController>()
        tracker.close()
        tracker.pin(Favorite("f", "Zuhause", b), JourneyQuery(a, b, now), trip, AppSettings())
        val handle = Robolectric.buildService(PinnedJourneyService::class.java).create()
        try {
            val actions = handle.get().notification(tracker.state.value).actions
            assertEquals(listOf("Reise beenden") + if (TimetableProfile.sourceNotice) listOf("Fahrplandaten: VRR") else emptyList(),
                actions.map { it.title.toString() })
            val stop = actions.first().actionIntent
            assertTrue(stop.isImmutable)
            val intent = Shadows.shadowOf(stop).savedIntent
            assertEquals(PinnedJourneyService.ACTION_STOP_JOURNEY, intent.action)
            assertEquals(tracker.state.value.session!!.id, intent.getStringExtra(PinnedJourneyService.EXTRA_PINNED_ID))
            handle.get().onStartCommand(intent, 0, 1)
            val deadline = System.currentTimeMillis() + 10_000
            while (tracker.state.value.session != null && System.currentTimeMillis() < deadline) {
                Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
                Thread.sleep(10)
            }
            assertNull(tracker.state.value.session)
        } finally { handle.destroy(); tracker.close() }
    }

}
