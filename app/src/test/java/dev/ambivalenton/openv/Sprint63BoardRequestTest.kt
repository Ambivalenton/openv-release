// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import androidx.datastore.core.DataStore
import androidx.lifecycle.ViewModelStore
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.data.preferences.UserPreferencesRepository
import dev.ambivalenton.openv.data.repository.*
import dev.ambivalenton.openv.ui.AppViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=OpenVApp::class)
@OptIn(ExperimentalCoroutinesApi::class)
class Sprint63BoardRequestTest {
    private class Memory(initial:StoredState):DataStore<StoredState> {
        override val data=MutableStateFlow(initial);private val lock=Mutex()
        override suspend fun updateData(transform:suspend(StoredState)->StoredState)=lock.withLock {
            transform(data.value).also {data.value=it}
        }
    }
    @Test fun aNewGpsFixCancelsAnInFlightOldNearestRequestEvenWhenAllPreferencesAreUnchanged()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler));val holder=ViewModelStore()
        try {
            val a=Location("Herne Bf","a",51.54,7.22,"TEST")
            val b=Location("Dortmund Hbf","b",51.51,7.46,"TEST")
            val now=System.currentTimeMillis()
            val locations=LocationRepository(Memory(StoredState(boardStops=listOf(a,b),presence=DevicePresence(a,now,5f))))
            val entered=CompletableDeferred<Unit>();val blocked=CompletableDeferred<Unit>();var cancelled=false
            val provider=object:TransitRepository {
                override val name="TEST"
                override suspend fun locations(query:String)=listOf(a,b)
                override suspend fun nearby(latitude:Double,longitude:Double,radiusMeters:Int)=listOf(a,b)
                override suspend fun departures(stop:Location):List<Departure> {
                    if(stop==a){entered.complete(Unit);try {blocked.await()}catch(e:CancellationException){cancelled=true;throw e}}
                    return listOf(Departure("b",b,"S2",LineType.SUBURBAN,"Zentrum",now+180_000,now+180_000,DelayInfo()))
                }
                override suspend fun journeyPage(query:JourneyQuery,cursor:String?):JourneyPage=error("Unused")
                override suspend fun refresh(trip:Trip)=trip
            }
            val app=RuntimeEnvironment.getApplication() as OpenVApp
            val vm=AppViewModel(locations,app.deviceLocation,app.koin.get(),app.koin.get(),app.koin.get(),UserPreferencesRepository(locations),
                boardRepository=DepartureBoardRepository(locations){provider})
            holder.put("app",vm);vm.state.first {it.ready}
            vm.refreshBoard();entered.await()
            locations.recordPresence(DevicePresence(b,now,5f));vm.state.first {it.store.presence?.location==b}
            vm.refreshBoard()
            val result=locations.state.first {it.departureBoard.stop==b}.departureBoard
            assertTrue(cancelled);assertNull(result.error);assertEquals(b,result.departures.single().stop)
        } finally {holder.clear();Dispatchers.resetMain()}
    }
}
