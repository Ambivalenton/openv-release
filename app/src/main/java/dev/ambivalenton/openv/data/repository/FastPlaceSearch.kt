// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.repository

import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.localization.serviceLanguage
import dev.ambivalenton.openv.localization.tr
import java.text.Normalizer
import java.util.Locale
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal fun searchText(value: String): String = Normalizer.normalize(value,Normalizer.Form.NFKD)
    .replace(Regex("\\p{M}+"),"").lowercase(Locale.ROOT).replace("ß","ss")
    .replace(Regex("[^\\p{L}\\p{N}]+")," ").trim()

private suspend fun <T> placeSearchDeadline(operation:suspend ()->T):T = try {withTimeout(12_000) {operation()}}
    catch(e:TimeoutCancellationException) {
        currentCoroutineContext().ensureActive()
        throw TransitException(tr("Die Ortssuche dauert zu lange. Bitte erneut versuchen."), FailureKind.TIMEOUT)
    }

/** Small bounded token-prefix trie: favourites and recent routes need no database or network. */
class LocalPlaceIndex {
    private class Node {val children=mutableMapOf<Char,Node>();val matches=linkedSetOf<Int>()}
    private var root=Node()
    private var records=emptyList<Pair<String,Location>>()
    @Synchronized fun update(state: StoredState) {
        val next=(state.favorites.map {it.label to it.location.copy(favoriteLabel=it.label,searchTerms=listOfNotNull(it.location.searchTerms,it.label).joinToString(" "))}+
            state.history.sortedByDescending {it.searchedAt}.flatMap {listOf(it.query.destination.name to it.query.destination,it.query.origin.name to it.query.origin)}+
            state.boardStops.map {it.name to it}).filter {it.second.routable}.distinctBy {it.first to placeIdentity(it.second)}.take(200)
        if(records==next) return
        records=next;root=Node()
        next.forEachIndexed {index,(alias,location)->
            placeWords("$alias ${location.name} ${location.city.orEmpty()}").distinct().forEach {token->
                var node=root
                token.forEach {c->node=node.children.getOrPut(c){Node()};node.matches.add(index)}
            }
        }
    }
    @Synchronized fun search(query: String): List<Location> {
        val terms=placeWords(query);if(terms.isEmpty()) return emptyList()
        val ids=terms.map {term->
            var node:Node?=root
            term.forEach {c->node=node?.children?.get(c)}
            node?.matches?.toSet().orEmpty()
        }.reduce {a,b->a.intersect(b)}
        val direct=ids.sorted().map {records[it].second}.distinctBy(::placeIdentity)
        if(direct.isNotEmpty())return direct
        return records.filter {(alias,p)->placeMatch(query,p,listOf(alias))!=null}.map {it.second}.distinctBy(::placeIdentity)
    }
}

internal fun placeIdentity(p: Location): String = when {
    p.kind==LocationKind.STOP && !p.dhid.isNullOrBlank() -> "dhid:${p.dhid}"
    p.id!=null && p.provider!="OSM" -> "${p.provider}:${p.id}"
    else -> "${searchText(p.name)}:${p.latitude}:${p.longitude}"
}

internal fun mergePlaceRows(rows:List<Location>):List<Location> {
    val merged=mutableListOf<Location>()
    val names=rows.associateWith {searchText(it.name)}
    val citiesByPlace=rows.associateWith {searchText(it.city.orEmpty())}
    fun priority(place:Location)=when {
        place.provider==StaticStopCatalog.PROVIDER -> 1
        place.provider!=null && place.provider!="OSM" && !place.id.isNullOrBlank() -> 2
        else -> 0
    }
    fun sameStop(a:Location,b:Location):Boolean {
        if(a.kind!=LocationKind.STOP || b.kind!=LocationKind.STOP) return false
        if(a.dhid!=null && b.dhid!=null && a.dhid!=b.dhid) return false
        val aCity=citiesByPlace.getValue(a);val bCity=citiesByPlace.getValue(b)
        if(aCity.isNotBlank() && bCity.isNotBlank() && aCity!=bCity) return false
        val cities=listOf(aCity,bCity).filter {it.isNotBlank()}.sortedByDescending {it.length}
        fun name(p:Location):String {
            val name=names.getValue(p)
            return cities.firstOrNull {name.startsWith("$it ")}?.let {name.removePrefix("$it ")} ?: name
        }
        return name(a)==name(b) && (distanceMeters(a,b)?.let {it<=50.0}==true)
    }
    rows.forEach { row ->
        val exact=merged.indexOfFirst {placeIdentity(it)==placeIdentity(row)}
        val candidates=if(exact<0) merged.indices.filter {sameStop(merged[it],row)} else emptyList()
        // A coordinate/name match must be unambiguous. Different proven DHIDs always stay separate.
        val index=if(exact>=0) exact else candidates.singleOrNull() ?: -1
        if(index<0) merged.add(row)
        else if(priority(row)>priority(merged[index]) || (priority(row)==2 && priority(merged[index])==2))
            merged[index]=row
    }
    return merged
}

/** Worth asking another address source: at least four characters with a letter, not a coordinate. */
internal fun wantsAddressFallback(query:String):Boolean = query.trim().length>=4 && query.any(Char::isLetter) && parseCoordinateInput(query)==null

/** Something the user can take: a real place (no street stand-in) that matches without guessing. */
internal fun hasUsablePlace(query:String,rows:List<Location>):Boolean =
    rows.any {it.note==null && placeMatch(query,it,listOfNotNull(it.favoriteLabel))?.fuzzy==false}

/** A street offered "without house number" only stays while no real address matches the same query. */
internal fun withoutStreetFallbacks(query:String,rows:List<Location>):List<Location> {
    if(rows.none {it.note!=null}) return rows
    val real=rows.any {it.note==null && it.kind!=LocationKind.STOP && placeMatch(query,it)?.fuzzy==false}
    return if(real) rows.filter {it.note==null} else rows
}

class FastPlaceSearch(
    private val transit: suspend (String,AppSettings)->List<Location>,
    private val addresses: suspend (String,Location?,String)->List<Location>,
    private val clock: ()->Long={System.nanoTime()/1_000_000},
    private val offline: suspend (String,Location?)->List<Location> = {_,_->emptyList()},
    private val networkDebounceMillis:Long=0,
    /** Extra address source (VRR/EFA address directory). Only asked when nothing usable was found. */
    private val fallback: (suspend (String,AppSettings)->List<Location>)? = null,
    private val fallbackDelayMillis:Long=1_200,
) {
    private data class Entry(val at:Long,val rows:List<Location>,val ttl:Long)
    private val cache=object:LinkedHashMap<String,Entry>(160,.75f,true) {
        override fun removeEldestEntry(eldest:MutableMap.MutableEntry<String,Entry>?)=size>160
    }
    private val index=LocalPlaceIndex()
    private fun aliases(state:StoredState)=state.favorites.groupBy {placeIdentity(it.location)}.mapValues {(_,rows)->rows.map {it.label}}
    fun local(query:String,state:StoredState):List<Location> {
        index.update(state)
        val rows=index.search(query).filter {query.trim().length>=2 || it.favoriteLabel!=null}
        return rankSearchedPlaces(query,mergePlaceRows(rows),stored=rows,aliases=aliases(state),order=state.settings.searchResultOrder).take(12)
    }
    private fun key(q:String,s:AppSettings,b:Location?)=listOf(TimetableProfile.identity(s),s.geocodingEndpoint,
        serviceLanguage(),"DE-v3",searchText(q),b?.latitude?.let {(it*100).toInt()},b?.longitude?.let {(it*100).toInt()}).joinToString("|")
    @Synchronized private fun cached(key:String)=cache[key]?.takeIf {clock()-it.at in 0 until it.ttl}?.rows
    @Synchronized private fun put(key:String,rows:List<Location>) {cache[key]=Entry(clock(),rows,if(rows.isEmpty()) 15_000 else 600_000)}
    fun stopSuggestions(query:String,state:StoredState):Flow<List<Location>> = suggestionsInternal(query,state,null,true)
    fun suggestions(query:String,state:StoredState,bias:Location?):Flow<List<Location>> = suggestionsInternal(query,state,bias,false)
    private fun suggestionsInternal(query:String,state:StoredState,bias:Location?,stopsOnly:Boolean):Flow<List<Location>> = channelFlow {
        if(!stopsOnly) parseCoordinateInput(query)?.let {send(listOf(it));return@channelFlow}
        val input=query.trim();if(input.length !in 1..160) {send(emptyList());return@channelFlow}
        if(input.length==1) {send(local(input,state).filter {!stopsOnly || it.kind==LocationKind.STOP});return@channelFlow}
        index.update(state)
        val local=index.search(input).filter { !stopsOnly || it.kind==LocationKind.STOP }
        val key=(if(stopsOnly) "stops|" else "")+key(input,state.settings,bias)
        val cachedRemote=cached(key)
        fun merge(remote:List<Location>,directory:List<Location>)=rankSearchedPlaces(input,
            withoutStreetFallbacks(input,mergePlaceRows(local+remote+directory)).map {row->
                val favorite=state.favorites.firstOrNull {placeIdentity(it.location)==placeIdentity(row) || sameSearchPlace(it.location,row)}
                if(favorite==null)row else row.copy(favoriteLabel=favorite.label,searchTerms=listOfNotNull(row.searchTerms,favorite.label).joinToString(" "))
            }.filter {it.routable && (!stopsOnly || it.kind==LocationKind.STOP)},
            directory=directory,stored=local,bias=bias,aliases=aliases(state),order=state.settings.searchResultOrder).take(12)
        if(local.isNotEmpty()) send(merge(emptyList(),emptyList()))
        if(cachedRemote!=null) send(merge(cachedRemote,emptyList()))
        // 0 = stops of the timetable service, 1 = Photon, 2 = offline stop directory, 3 = address fallback.
        val lock=Mutex();val results=arrayOfNulls<Result<List<Location>>>(4)
        if(stopsOnly) results[1]=Result.success(emptyList())
        if(cachedRemote!=null) {results[0]=Result.success(cachedRemote);results[1]=Result.success(emptyList())}
        fun remote()=listOf(0,1,3).flatMap {results[it]?.getOrDefault(emptyList()).orEmpty()}
        fun directory()=results[2]?.getOrDefault(emptyList()).orEmpty()
        var usable=hasUsablePlace(input,merge(remote(),emptyList()))
        val fallbackSource=fallback?.takeIf {!stopsOnly && cachedRemote==null && wantsAddressFallback(input)}
        supervisorScope {
            suspend fun receive(index:Int,operation:suspend ()->List<Location>) {
                val result=try {Result.success(if(index==2) operation() else placeSearchDeadline {operation()})}
                    catch(e:CancellationException) {throw e}
                    catch(e:Exception) {Result.failure(e)}
                lock.withLock {
                    results[index]=result
                    val rows=merge(remote(),directory())
                    if(hasUsablePlace(input,rows)) usable=true
                    if(rows.isNotEmpty()) send(rows)
                }
            }
            val fast=if(cachedRemote==null) listOfNotNull(
                launch {delay(networkDebounceMillis);receive(0){transit(input,state.settings)}},
                if(!stopsOnly) launch {delay(networkDebounceMillis);receive(1){addresses(input,bias,state.settings.geocodingEndpoint)}} else null,
            ) else emptyList()
            launch {receive(2){offline(input,bias)}}
            // The fast sources keep their speed. The extra source only starts when they found nothing
            // usable: right after they finished, or once the search takes longer than the grace period.
            if(fallbackSource!=null) launch {
                withTimeoutOrNull(networkDebounceMillis+fallbackDelayMillis) {fast.joinAll()}
                if(lock.withLock {usable}) {lock.withLock {results[3]=Result.success(emptyList())};return@launch}
                receive(3){fallbackSource(input,state.settings)}
            }
        }
        currentCoroutineContext().ensureActive()
        val rows=merge(remote(),directory())
        if(cachedRemote==null && results.take(2).all {it?.isSuccess==true} && results[3]?.isFailure!=true) put(key,remote())
        if(rows.isEmpty()) listOf(0,1,3).firstNotNullOfOrNull {results[it]?.exceptionOrNull()}?.let {throw it}
        send(rows)
    }.distinctUntilChanged()
}
