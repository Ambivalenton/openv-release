// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.data.repository.StateSerializer
import dev.ambivalenton.openv.ui.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream

class Sprint64FavoritesTest {
    private val here=Location("Herne",latitude=51.54,longitude=7.22)
    private fun favorite(id:String,lat:Double)=Favorite(id=id,label=id,location=here.copy(name=id,latitude=lat))
    private fun state(favorites:List<Favorite>)=StoredState(favorites=favorites,
        settings=AppSettings(originMode=OriginMode.MANUAL,manualOrigin=here))



    @Test fun noPositionOrEqualDistancesPreservesSavedOrderAndUnknownDistancesComeLast() {
        val a=favorite("a",51.56);val b=a.copy(id="b",label="b")
        val unknown=Favorite(id="u",label="Ohne Koordinaten",location=Location("Halt",id="u",provider="TEST"))
        val store=state(listOf(unknown,b,a))
        assertEquals(listOf("u","b","a"),store.favoriteManagementEntries(null).map {it.id})
        assertEquals(listOf("b","a","u"),store.favoriteManagementEntries(here).map {it.id})
    }

    @Test fun identicalPlacesKeepSeparateIdsLabelsColorsAndRulesAcrossStorage() = runBlocking {
        val a=favorite("a",51.56).copy(label="Arbeit",accentSeed=0xff156b56,
            rule=HighlightRule((1..7).toSet(),0,0))
        val b=a.copy(id="b",label="Besuch",accentSeed=0xff926126,rule=null)
        val bytes=ByteArrayOutputStream().also {StateSerializer.writeTo(state(listOf(a,b)),it)}.toByteArray()
        val restored=StateSerializer.readFrom(bytes.inputStream())
        assertEquals(listOf(a,b),restored.favorites)
        assertEquals(2,restored.network.nodes.size)
        assertEquals(2,restored.favoriteManagementEntries().size)
    }

    @Test fun legacyRepeatedIdsAreRepairedWithoutDroppingFavoritesOrLineReferences() = runBlocking {
        val a=favorite("a",51.56);val b=favorite("b",51.90)
        val original=state(listOf(a,b)).copy(favoritePairs=listOf(FavoritePair(id="pair",firstId=a.id,secondId=b.id)))
        val duplicate=original.copy(favorites=original.favorites+ a.copy(label="Noch einmal"))
        val bytes=ByteArrayOutputStream().also {StateSerializer.writeTo(duplicate,it)}.toByteArray()
        val restored=StateSerializer.readFrom(bytes.inputStream())
        assertEquals(3,restored.favorites.size)
        assertEquals(3,restored.favorites.map {it.id}.toSet().size)
        assertEquals(a,restored.favorites[0]);assertEquals(b,restored.favorites[1])
        assertEquals("Noch einmal",restored.favorites[2].label)
        assertEquals(a.location,restored.favorites[2].location)
        // The legacy pair became a network line with the same identity (build 66).
        assertTrue(restored.favoritePairs.isEmpty())
        assertEquals(listOf("pair" to (a.id to b.id)),restored.network.edges.map {it.id to (it.aId to it.bId)})
        assertEquals(restored,StateSerializer.readFrom(bytes.inputStream()))
        val again=ByteArrayOutputStream().also {StateSerializer.writeTo(restored,it)}.toByteArray()
        assertEquals(restored,StateSerializer.readFrom(again.inputStream()))
    }

    @Test fun overviewKeysNeverCollapseRepeatedPlacesTripsOrLegacyIds() {
        val a=favorite("transit-header",51.56)
        val rows=listOf(a,a.copy(label="Zweiter Name"),a.copy(id="transit-header:1"))
            .map {OverviewEntry(it,0)}
        val keys=overviewRowKeys(rows)
        assertEquals(3,keys.toSet().size)
        assertFalse("transit-header" in keys)
        assertEquals(keys,overviewRowKeys(rows.map {it.copy(loading=false)}))
    }
}
