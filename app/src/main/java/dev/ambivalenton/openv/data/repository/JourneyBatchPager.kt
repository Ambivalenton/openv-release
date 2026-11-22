// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.repository

import dev.ambivalenton.openv.data.model.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Loading plan of the comparison chart. Configurable starting values, not measured optima:
 * a first small page renders quickly, two background waves complete the context, and further
 * batches follow only when the user scrolls towards an edge.
 *
 * "Primary" connections lie on the searched side of the reference: departing at/after it in
 * departure mode, arriving at/before it in arrival mode. "Secondary" connections lie on the other
 * side. The counts are connections (bars), never favourites or favourite pairs.
 */
data class ConnectionWave(val primary:Int,val secondary:Int)
val CONNECTION_WAVES=listOf(ConnectionWave(3,0),ConnectionWave(6,3),ConnectionWave(9,6))
/**
 * Order of the directions within each background wave. Earlier connections come first, so
 * a departure search shows the last few missed connections before it looks further (possibly hours)
 * ahead: three coming, three earlier, three coming, three earlier. An arrival search keeps its order,
 * its searched side already lies earlier.
 */
val CONNECTION_WAVE_ORDER=listOf(ConnectionDirection.EARLIER,ConnectionDirection.LATER)

/** Phases 2 and 3 after the first page: one [step] per wave and direction that still misses bars. */
suspend fun JourneyBatchPager.completeWaves(active:()->Boolean,step:suspend (ConnectionDirection,Int)->Unit) {
    for(wave in CONNECTION_WAVES.drop(1)) for(direction in CONNECTION_WAVE_ORDER) {
        val missing=missing(wave,direction)
        if(missing<=0 || exhausted(direction) || !active())continue
        step(direction,minOf(missing,CONNECTION_EDGE_BATCH))
    }
}
/** Connections added per edge batch in scroll direction. */
const val CONNECTION_EDGE_BATCH=3
/** Prefetch when the viewport has covered about 80 % of the loaded range towards an edge. */
const val CONNECTION_EDGE_PREFETCH_FRACTION=.8f
const val INITIAL_FUTURE_CONNECTIONS=3

enum class ConnectionDirection {EARLIER,LATER}

/**
 * K01: outcome of one revalidation pass over saved bars. [confirmed] bars received a new stand;
 * [failures] requests failed (radio gap, timeout, server error) and left their bars untouched.
 */
data class SeedRefresh(val attempted:Int,val confirmed:Int,val failures:Int,val lastError:Throwable?)

/** One owner per search. Provider overflow stays in RAM; only explicit demand exposes more bars.
 * Rows are keyed by the shared connection identity; the first-seen ID of a connection is kept. */
class JourneyBatchPager(private val provider:TransitRepository,val query:JourneyQuery,seed:List<Trip> = emptyList(),
    initialCount:Int=INITIAL_FUTURE_CONNECTIONS,private val clock:()->Long=System::currentTimeMillis,
    /** A fresh pre-search seeds confirmed bars; a saved snapshot seeds unconfirmed ones. */
    seedConfirmedAt:Long?=null) {
    private val initialSize=initialCount.coerceIn(1,12)
    private val lock=Mutex()
    private val rows=linkedMapOf<String,Trip>()
    private val pending=mutableListOf<Trip>()
    private val verified=mutableSetOf<String>()
    /** K01: identities that came from a saved snapshot; only a provider answer confirms them again. */
    private val seeded=mutableSetOf<String>()
    /** K01: time of the newest successful provider answer of this pager; null before the first one. */
    var confirmedAt:Long?=null;private set
    /** K01: the last failed request of this pager; a later complete success clears it. */
    var lastFailure:Throwable?=null;private set
    private var cursor:String?=null
    private val cursors=mutableSetOf<String>()
    /** Identities a comparable, clearly better connection made superfluous; they stay hidden for this search. */
    private val hiddenKeys=mutableSetOf<String>()
    private fun remember(page:JourneyPage):List<Trip> {
        page.trips.filter {it.id in page.hidden}.forEach {hiddenKeys+=historicalIdentity(it)}
        return page.shown.filter {historicalIdentity(it) !in hiddenKeys}
    }
    var pastExhausted=false;private set
    var futureExhausted=false;private set
    private val forward=query.timeMode==TimeMode.DEPARTURE
    val primaryDirection=if(forward)ConnectionDirection.LATER else ConnectionDirection.EARLIER
    val secondaryDirection=if(forward)ConnectionDirection.EARLIER else ConnectionDirection.LATER

    init {
        // Restored snapshots may contain duplicates or repeated IDs from earlier versions.
        mergeDuplicateConnections(seed).forEach {
            val identity=historicalIdentity(it);rows[identity]=it
            if(seedConfirmedAt!=null) verified+=identity else seeded+=identity
        }
        confirmedAt=seedConfirmedAt
    }

    private fun boardable(trip:Trip,now:Long)=(trip.segments.firstOrNull {!it.isWalk}?.effectiveDeparture() ?: trip.startTime)>=now-30_000
    /**
     * K01: saved bars that no answer of this pager has confirmed and that are still boardable. They
     * keep their saved stand ([Trip.fetchedAt]) and stay visibly marked until a response confirms them.
     */
    fun unconfirmedIds(now:Long=clock()):Set<String> =
        rows.filter {(identity,trip)->identity in seeded && identity !in verified && boardable(trip,now)}.values.mapTo(LinkedHashSet()) {it.id}
    /** K01: true when this pager started from saved bars (a reopened cache or a refresh of shown results). */
    val seededFromSnapshot:Boolean get()=seeded.isNotEmpty()
    private fun succeeded() {confirmedAt=clock()}

    private fun onPrimarySide(trip:Trip)=if(forward)trip.queryStart(query.ignoreWalking)>=query.time
        else trip.queryEnd(query.ignoreWalking)<=query.time
    fun primaryCount()=rows.values.count(::onPrimarySide)
    fun secondaryCount()=rows.size-primaryCount()
    fun missing(wave:ConnectionWave,direction:ConnectionDirection):Int=
        if(direction==primaryDirection)wave.primary-primaryCount() else wave.secondary-secondaryCount()
    fun exhausted(direction:ConnectionDirection)=if(direction==ConnectionDirection.LATER)futureExhausted else pastExhausted
    suspend fun load(direction:ConnectionDirection,count:Int=CONNECTION_EDGE_BATCH):JourneyWindow=
        if(direction==ConnectionDirection.LATER)future(count) else past(count)

    private fun uniqueId(trip:Trip,identity:String):String {
        val taken=rows.values.mapTo(HashSet()) {it.id}
        if(trip.id !in taken)return trip.id
        val base="${trip.id}~${identityDigest(identity)}"
        var candidate=base;var n=2
        while(candidate in taken)candidate="$base~${n++}"
        return candidate
    }
    private fun accept(trip:Trip) {
        val identity=historicalIdentity(trip);val old=rows[identity]
        rows[identity]=if(old==null)trip.copy(id=uniqueId(trip,identity)) else fresherConnection(old,trip).copy(id=old.id)
        verified+=identity
    }
    fun snapshot(problem:String?=null)=rows.values.sortedBy {it.startTime}.let {trips->JourneyWindow(withUniqueTripIds(trips),
        trips.minOfOrNull {it.startTime} ?: query.time,trips.maxOfOrNull {it.endTime} ?: query.time,
        coveredFraction=1f,pages=1,finished=true,complete=problem==null,problem=problem)}
    /** Automatic updates replace loaded identities; new bars remain an explicit paging action. */
    suspend fun applyLoadedUpdates(updates:List<Trip>):JourneyWindow=lock.withLock {
        for(fresh in updates) if(rows.containsKey(historicalIdentity(fresh)))accept(fresh)
        succeeded();lastFailure=null
        snapshot()
    }
    /** K01: an automatic refresh failed; every bar keeps its stand, the failure is reported. */
    fun noteFailure(error:Throwable) {lastFailure=error}
    /** Revalidate every still boardable cached service, including bars loaded by earlier swipes. */
    suspend fun refreshSeed(now:Long,onUpdate:(JourneyWindow,Set<String>)->Unit):SeedRefresh = lock.withLock {
        val stale=rows.filter {(identity,trip)->identity !in verified && boardable(trip,now)}
            .keys.toMutableSet()
        fun staleIds()=stale.mapNotNull {rows[it]?.id}.toSet()
        var attempted=0;var confirmed=0;var failures=0;var error:Throwable?=null
        for(identity in rows.keys.toList()) {
            if(identity !in stale)continue
            val old=rows[identity] ?: continue
            currentCoroutineContext().ensureActive()
            attempted++
            try {
                // EFA has no refresh token. Requery a small departure page instead; it can
                // revalidate several cached bars at once without scanning a fixed time window.
                val updates=if(old.refreshToken!=null)listOf(provider.refresh(old)) else
                    provider.journeyPage(query.copy(time=old.queryStart(query.ignoreWalking),timeMode=TimeMode.DEPARTURE,pageSize=3)).trips
                succeeded()
                for(fresh in updates) {
                    val key=historicalIdentity(fresh)
                    if(key in stale && rows.containsKey(key)) {accept(fresh);stale.remove(key);confirmed++}
                }
            }
            catch(e:kotlinx.coroutines.CancellationException){throw e}
            // K01: a failed request changes neither the bar nor its stand. A bar missing from one
            // answer is not evidence that it is gone (one page or direction proves nothing).
            catch(e:Exception){failures++;error=e}
            onUpdate(snapshot(),staleIds())
        }
        if(error!=null)lastFailure=error else if(attempted>0 && stale.isEmpty())lastFailure=null
        SeedRefresh(attempted,confirmed,failures,error)
    }
    suspend fun initial():JourneyWindow=lock.withLock {
        val found=linkedMapOf<String,Trip>();var anchor=query.time;var native:String?=null
        val seen=mutableSetOf<String>();var problem:String?=null
        for(attempt in 0 until 8) {
            currentCoroutineContext().ensureActive()
            val page=try {provider.journeyPage(query.copy(time=anchor,pageSize=initialSize),native).also {succeeded()}}
                catch(e:kotlinx.coroutines.CancellationException){throw e}
                catch(e:Exception){if(found.isEmpty() && rows.isEmpty())throw e;problem=e.message;lastFailure=e;break}
            val before=found.size
            remember(page).filter {valid(it) && if(forward)it.queryStart(query.ignoreWalking)>=query.time else it.queryEnd(query.ignoreWalking)<=query.time}
                .forEach {trip->val key=historicalIdentity(trip);found[key]=found[key]?.let {fresherConnection(it,trip)} ?: trip}
            val next=page.nextCursor
            val positions=page.trips.map {if(forward)it.queryStart(query.ignoreWalking)else it.queryEnd(query.ignoreWalking)}
            if(forward)cursor=next
            if(found.size>=initialSize)break
            if(!next.isNullOrBlank() && seen.add(next))native=next
            else {
                native=null
                val edge=if(forward)positions.maxOrNull()else positions.minOrNull()
                if(page.trips.isEmpty())break
                // Query an overlapping minute; a provider may cap the requested page at three.
                // If it repeats the same page, advance a minute rather than spinning indefinitely.
                val candidate=edge?.let {Math.floorDiv(it,60_000)*60_000} ?: anchor
                anchor=if(forward)maxOf(candidate,anchor+if(found.size==before)60_000 else 0)
                    else minOf(candidate,anchor-if(found.size==before)60_000 else 0)
            }
        }
        val candidates=found.values.sortedBy {it.startTime}
        val initial=if(forward)candidates.take(initialSize)else candidates.takeLast(initialSize)
        initial.forEach(::accept)
        if(forward)pending+=candidates.drop(initialSize)
        if(problem==null)lastFailure=null
        snapshot(problem)
    }
    suspend fun past(count:Int=CONNECTION_EDGE_BATCH):JourneyWindow=lock.withLock {
        val before=rows.values.minOfOrNull {it.startTime} ?: query.time
        val previous=provider.fetchHistoricalConnections(query,before,count,rows.keys).also {succeeded()}
            .filter {valid(it) && historicalIdentity(it) !in rows}
        previous.forEach(::accept)
        // A short or repeated answer ends this direction instead of retrying on every scroll.
        pastExhausted=previous.isEmpty()
        snapshot()
    }
    suspend fun future(count:Int=CONNECTION_EDGE_BATCH):JourneyWindow=lock.withLock {
        val wanted=count.coerceIn(1,6);var added=0
        var anchor=Math.floorDiv(rows.values.maxOfOrNull {it.queryStart(query.ignoreWalking)} ?: query.time,60_000)*60_000
        // Bounded retries handle overlapping minute pages and providers ignoring product filters.
        for(attempt in 0 until 6) {
            currentCoroutineContext().ensureActive()
            val queued=pending.filter {historicalIdentity(it) !in rows}.distinctBy(::historicalIdentity).sortedBy {it.startTime}
            val take=queued.take(wanted-added)
            take.forEach {accept(it);added++}
            pending.removeAll {historicalIdentity(it) in rows}
            if(added>=wanted)break
            val overlap=if(cursor==null)rows.values.count {Math.floorDiv(it.queryStart(query.ignoreWalking),60_000)==Math.floorDiv(anchor,60_000)}else 0
            val page=provider.journeyPage(query.copy(time=anchor,timeMode=TimeMode.DEPARTURE,pageSize=(wanted-added+overlap).coerceAtMost(12)),cursor).also {succeeded()}
            val candidates=remember(page).filter {valid(it) && it.queryStart(query.ignoreWalking)>=anchor && historicalIdentity(it) !in rows}
            pending+=candidates
            val next=page.nextCursor
            if(!next.isNullOrBlank() && cursors.add(next))cursor=next
            else {
                cursor=null
                val furthest=page.trips.maxOfOrNull {it.queryStart(query.ignoreWalking)}
                anchor=if(furthest!=null && furthest>=anchor) Math.floorDiv(furthest,60_000)*60_000+60_000 else anchor+15*60_000
                if(page.trips.isEmpty()) {futureExhausted=true;break}
            }
        }
        // Consume a final received page without another request.
        pending.filter {historicalIdentity(it) !in rows}.distinctBy(::historicalIdentity).sortedBy {it.startTime}.take(wanted-added).forEach {accept(it);added++}
        pending.removeAll {historicalIdentity(it) in rows}
        if(added==0)futureExhausted=true
        snapshot()
    }
    private fun valid(trip:Trip)=trip.segments.isNotEmpty() && trip.matchesTransportFilter(query.transportFilter) &&
        trip.hasUsableTransfers(query) && trip.segments.all {it.isWalk || it.lineType in query.products && (LineType.ICE in query.products || !it.isLongDistance())}
}
