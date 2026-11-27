// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.repository

import dev.ambivalenton.openv.data.model.*
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.abs

const val JOURNEY_CACHE_TTL=30*60_000L
const val JOURNEY_CACHE_ORIGIN_METERS=200.0
const val JOURNEY_WINDOW_MILLIS=6*60*60_000L
fun isCurrentDeparture(time:Long,mode:TimeMode,now:Long=System.currentTimeMillis())=
    mode==TimeMode.DEPARTURE && abs(time-now)<=90_000
// The preferences shape the shown connections, so a snapshot belongs to them as well.
fun journeyCacheProvider(settings:AppSettings):String=MessageDigest.getInstance("SHA-256")
    .digest((TimetableProfile.identity(settings)+"|"+settings.overview.routingPreferences.routingIdentity()).toByteArray())
    .joinToString("") {"%02x".format(it)}
private fun exactPlace(a:Location,b:Location):Boolean =
    if(a.kind==LocationKind.STOP && b.kind==LocationKind.STOP && !a.id.isNullOrBlank()) a.id==b.id && a.provider==b.provider
    else if(a.hasCoordinates && b.hasCoordinates) (distanceMeters(a,b) ?: Double.MAX_VALUE)<=5.0
    else a==b

/**
 * One saved search. K01: [savedAt] is the last *confirmed* stand of the snapshot and anchors its
 * expiry; a failed or partial refresh never moves it. [lastAttemptAt]/[lastAttemptFailed] record the
 * latest refresh attempt separately. Each bar keeps its own confirmed stand in [Trip.fetchedAt].
 */
@Serializable data class CachedJourneySearch(val id:String,val query:JourneyQuery,val provider:String,
    val deviceOrigin:Boolean,val currentDeparture:Boolean,val savedAt:Long,val viewedAt:Long,
    val trips:List<Trip>,val from:Long,val until:Long,val coveredFraction:Float,val pages:Int,
    val finished:Boolean,val complete:Boolean,val viewIndex:Int=0,val viewOffset:Int=0,
    val lastAttemptAt:Long?=null,val lastAttemptFailed:Boolean=false,val lastBoardCheckAt:Long?=null) {
    /** K01: when this snapshot stops being offered (unchanged by failed refreshes). */
    val expiresAt:Long get()=savedAt+JOURNEY_CACHE_TTL
    fun valid(now:Long)=now-savedAt in 0 until JOURNEY_CACHE_TTL && trips.isNotEmpty() && until>=from &&
        trips.size<=2048 && trips.all {it.segments.isNotEmpty() && it.segments.size<=64 && it.segments.all {leg->leg.endTime>=leg.startTime}}
    fun window()=JourneyWindow(trips,from,until,coveredFraction,pages,finished,complete)
    fun matches(candidate:JourneyQuery,settings:AppSettings,isDevice:Boolean,isNow:Boolean,now:Long):Boolean {
        if(!valid(now) || provider!=journeyCacheProvider(settings) || deviceOrigin!=isDevice || currentDeparture!=isNow) return false
        if(query.walkingSpeed!=candidate.walkingSpeed || query.ignoreWalking!=candidate.ignoreWalking || query.transportFilter!=candidate.transportFilter)return false
        if(query.timeMode!=candidate.timeMode || query.products!=candidate.products || !exactPlace(query.destination,candidate.destination)) return false
        if(query.waypoints.size!=candidate.waypoints.size || !query.waypoints.zip(candidate.waypoints).all {(a,b)->exactPlace(a,b)} ||
            query.waypointDwellMinutes!=candidate.waypointDwellMinutes || query.waypointOrder!=candidate.waypointOrder) return false
        if(!isNow && query.time!=candidate.time) return false
        return if(isDevice) (distanceMeters(query.origin,candidate.origin) ?: Double.MAX_VALUE)<=JOURNEY_CACHE_ORIGIN_METERS
            else exactPlace(query.origin,candidate.origin)
    }
}

/** Bounded local, short-lived snapshots. UI reads RAM; only the private cache file uses IO.
 * No server requests or location acquisition occur here. The app can replay after process death. */
class JourneySearchCache(private val file:File?=null,private val now:()->Long=System::currentTimeMillis,
    private val limit:Int=8) {
    private val mutable=MutableStateFlow<List<CachedJourneySearch>>(emptyList())
    val entries=mutable.asStateFlow()
    private val io=Mutex();private var initialized=file==null;private var writer:Job?=null
    private val json=Json {ignoreUnknownKeys=true;encodeDefaults=true}
    suspend fun initialize()=io.withLock {
        if(initialized) return@withLock
        val loaded=withContext(Dispatchers.IO) {try {
            if(file?.isFile==true && file.length()<=8L*1024*1024) json.decodeFromString<List<CachedJourneySearch>>(file.readText())
            else emptyList()
        } catch(e:CancellationException) {throw e} catch(_:Exception) {emptyList()}}
        mutable.value=loaded.filter {it.valid(now())}.distinctBy {it.id}.sortedByDescending {it.viewedAt}.take(limit.coerceIn(1,12))
        initialized=true
    }
    @Synchronized fun find(query:JourneyQuery,settings:AppSettings,device:Boolean,current:Boolean):CachedJourneySearch? {
        prune();return mutable.value.firstOrNull {it.matches(query,settings,device,current,now())}
    }
    @Synchronized fun get(id:String):CachedJourneySearch? {prune();return mutable.value.firstOrNull {it.id==id}}
    @Synchronized fun prune() {mutable.value=mutable.value.filter {it.valid(now())}}
    /**
     * K01: [retainSavedAt] keeps the confirmed stand (and so the expiry) of a snapshot whose bars were
     * not all confirmed again; null means this window was confirmed now. [attemptFailed] records that
     * the latest refresh attempt failed, without touching the stand.
     */
    @Synchronized fun put(query:JourneyQuery,settings:AppSettings,device:Boolean,current:Boolean,window:JourneyWindow,
        id:String?=null,retainSavedAt:Long?=null,attemptFailed:Boolean=false,boardCheckedAt:Long?=null):CachedJourneySearch? {
        if(window.trips.isEmpty() || window.trips.size>2048) return null
        val existing=id?.let {key->mutable.value.firstOrNull {it.id==key}} ?: find(query,settings,device,current)
        // A confirmed stand never moves backwards, and an older retained stand never extends a snapshot.
        val stand=retainSavedAt?.let {kept->existing?.savedAt?.let {minOf(it,kept)} ?: kept} ?: now()
        val entry=CachedJourneySearch(existing?.id ?: newId(),query,journeyCacheProvider(settings),device,current,
            stand,now(),window.trips,window.from,window.until,
            window.coveredFraction,window.pages,window.finished,window.complete,existing?.viewIndex ?: 0,existing?.viewOffset ?: 0,
            lastAttemptAt=now(),lastAttemptFailed=attemptFailed,lastBoardCheckAt=boardCheckedAt ?: existing?.lastBoardCheckAt)
        if(!entry.valid(now())) return null
        mutable.value=(listOf(entry)+mutable.value.filter {it.id!=entry.id && it.valid(now())}).take(limit.coerceIn(1,12))
        return entry
    }
    @Synchronized fun viewport(id:String,index:Int,offset:Int) {
        mutable.value=mutable.value.map {if(it.id==id) it.copy(viewIndex=index.coerceIn(0,(it.trips.size-1).coerceAtLeast(0)),viewOffset=offset.coerceAtLeast(0)) else it}
    }
    @Synchronized fun touch(id:String) {
        val entry=get(id) ?: return
        mutable.value=listOf(entry.copy(viewedAt=now()))+mutable.value.filterNot {it.id==id}
    }
    @Synchronized fun remove(id:String) {mutable.value=mutable.value.filterNot {it.id==id}}
    @Synchronized fun clear() {mutable.value=emptyList()}
    fun persist(scope:CoroutineScope,immediate:Boolean=false) {
        if(file==null) return
        writer?.cancel();writer=scope.launch {if(!immediate) delay(600);flush()}
    }
    suspend fun flush() {
        if(file==null) return
        io.withLock {withContext(Dispatchers.IO) {
            var snapshot=entries.value.filter {it.valid(now())}
            var text=json.encodeToString(snapshot)
            while(text.toByteArray().size>8*1024*1024 && snapshot.isNotEmpty()) {
                snapshot=snapshot.dropLast(1);text=json.encodeToString(snapshot)
            }
            try {
                file.parentFile?.mkdirs();val temp=File(file.parentFile,file.name+".tmp");temp.writeText(text)
                try {Files.move(temp.toPath(),file.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING)}
                catch(_:java.nio.file.AtomicMoveNotSupportedException) {Files.move(temp.toPath(),file.toPath(),StandardCopyOption.REPLACE_EXISTING)}
            } catch(e:CancellationException) {throw e} catch(_:Exception) { /* Cache failure never blocks live routing. */ }
        }}
    }
}

/** Keep all unverified bars while a cumulative provider window is loading, with stable IDs.
 * Only a successful complete window may remove missing services. Partial failures retain them. */
class JourneyRevalidation(private val previous:List<Trip>,private val originalWindow:JourneyWindow?=null) {
    var staleIds:Set<String> = previous.map {it.id}.toSet();private set
    fun accept(window:JourneyWindow):JourneyWindow {
        val fresh=window.trips.map {candidate->
            // Exact connection identity only: a walk-insensitive match could hand one bar's ID to
            // a different connection on the same route and hide or duplicate it.
            val old=previous.firstOrNull {historicalIdentity(it)==historicalIdentity(candidate)}
            if(old!=null) candidate.copy(id=old.id) else candidate
        }
        val ids=fresh.map {it.id}.toSet()
        val remaining=previous.filter {it.id !in ids}
        staleIds=if(window.complete) emptySet() else remaining.map {it.id}.toSet()
        val combined=if(window.complete) fresh else fresh+remaining
        return window.copy(trips=combined.distinctBy {it.id}.sortedBy {it.startTime})
    }
}
