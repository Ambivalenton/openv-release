// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import android.app.Application
import androidx.datastore.core.DataStore
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.data.repository.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * "Automatically the nearest stop": frequent GPS fixes during an active journey must not discard a
 * board request whose choice (nearest place, pair direction) they do not change.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
@OptIn(ExperimentalCoroutinesApi::class)
class Build65BoardChoiceTest {
    private class Memory(initial:StoredState):DataStore<StoredState> {
        override val data=MutableStateFlow(initial);private val lock=Mutex()
        override suspend fun updateData(transform:suspend(StoredState)->StoredState)=lock.withLock {transform(data.value).also {data.value=it}}
    }
    private val now=System.currentTimeMillis()
    private val a=Location("Herne Bf","a",51.54,7.22,"TEST")
    private val b=Location("Dortmund Hbf","b",51.51,7.46,"TEST")
    private inner class Slow(val gate:CompletableDeferred<Unit>,val entered:CompletableDeferred<Unit>):TransitRepository {
        override val name="TEST"
        override suspend fun locations(query:String)=listOf(a,b)
        override suspend fun nearby(latitude:Double,longitude:Double,radiusMeters:Int)=listOf(a,b)
        override suspend fun departures(stop:Location):List<Departure> {
            entered.complete(Unit);gate.await()
            return listOf(Departure("d-${stop.id}",stop,"S2",LineType.SUBURBAN,"Zentrum",now+180_000,now+180_000,DelayInfo()))
        }
        override suspend fun journeyPage(query:JourneyQuery,cursor:String?):JourneyPage=error("Unused")
        override suspend fun refresh(trip:Trip)=trip
    }

    @Test fun aFixThatKeepsTheNearestStopKeepsTheAutomaticBoard()=runTest {
        val locations=LocationRepository(Memory(StoredState(boardStops=listOf(a,b),presence=DevicePresence(a,now,5f))))
        val gate=CompletableDeferred<Unit>();val entered=CompletableDeferred<Unit>()
        val repository=DepartureBoardRepository(locations) {Slow(gate,entered)}
        val before=boardChoiceSignature(locations.state.first(),now)
        val request=launch {repository.refresh(force=true,now=now)}
        entered.await()
        // 30 m further on, still nearest to the same stop: a typical update while travelling.
        locations.recordPresence(DevicePresence(Location("GPS",latitude=51.5403,longitude=7.2201),now+5_000,8f))
        assertEquals(before,boardChoiceSignature(locations.state.first(),now+5_000))
        gate.complete(Unit);request.join()
        val board=locations.state.first().departureBoard
        assertNull(board.error);assertEquals("a",board.stop?.id);assertEquals(1,board.departures.size)
        // The same choice within a minute needs no new request.
        val repeated=CompletableDeferred<Unit>()
        DepartureBoardRepository(locations) {Slow(CompletableDeferred(Unit),repeated)}.refresh(now=now+20_000)
        assertFalse(repeated.isCompleted)
    }

    @Test fun aFixNearAnotherStopStillDiscardsTheOldChoice()=runTest {
        val locations=LocationRepository(Memory(StoredState(boardStops=listOf(a,b),presence=DevicePresence(a,now,5f))))
        val gate=CompletableDeferred<Unit>();val entered=CompletableDeferred<Unit>()
        val repository=DepartureBoardRepository(locations) {Slow(gate,entered)}
        val request=launch {repository.refresh(force=true,now=now)}
        entered.await()
        locations.recordPresence(DevicePresence(b,now+5_000,5f))
        gate.complete(Unit);request.join()
        assertTrue("an answer for the old nearest stop is not shown",locations.state.first().departureBoard.departures.isEmpty())
    }
}
