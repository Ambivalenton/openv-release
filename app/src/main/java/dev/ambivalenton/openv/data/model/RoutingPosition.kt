// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.model

fun AppSettings.manualLocationFallback():AppSettings = copy(originMode=OriginMode.MANUAL,
    manualOrigin=manualOrigin?.takeIf {it.routable && !it.isDevicePosition})

const val MANUAL_LOCATION_PROMPT_INTERVAL=60*60_000L
fun AppSettings.manualLocationPromptDue(openedAt:Long):Boolean =
    manualOrigin?.let {it.routable && !it.isDevicePosition}!=true || manualOriginChangedAt<=0L ||
        openedAt-manualOriginChangedAt>=MANUAL_LOCATION_PROMPT_INTERVAL

/*
 * Time-dependent location (build 67; it replaces "Zeitlich ausblenden"). During one of its time
 * windows a favourite's place stands in for the person's position. It is meant for people who do
 * not share their location or have it switched off, and as a fallback while there is no GPS fix.
 * Priority of the start position (after a virtual location set by holding a node):
 *   1. fresh GPS fix   2. manually chosen start   3. time-dependent location
 * Only one favourite can own a minute of the week (see [scheduleConflict]).
 */
private fun StoredState.manualMode()=settings.originMode==OriginMode.MANUAL && settings.manualOrigin?.isDevicePosition!=true

/** Favourite whose time window is active now (first in list order, should windows ever overlap). */
fun StoredState.scheduledFavorite(now:Long=System.currentTimeMillis()):Favorite? =
    if(favorites.none {it.hiddenRules.isNotEmpty()})null else scheduledFavorite(zonedNow(now))

/** Wall-clock variant: the windows are meant in the device's time zone. */
fun StoredState.scheduledFavorite(clock:java.time.ZonedDateTime):Favorite? =
    favorites.firstOrNull {f->f.location.routable && f.hiddenRules.any {it.matches(clock)}}

/** The time-dependent location if it is the start right now (see the order above), else null. */
fun StoredState.scheduledStart(now:Long=System.currentTimeMillis()):Favorite? {
    if(locationOverride!=null && manualMode() && settings.manualOrigin==locationOverride.location)return null
    val scheduled=scheduledFavorite(now) ?: return null
    return if(manualMode()) scheduled.takeIf {settings.manualOrigin?.routable!=true}
    else scheduled.takeIf {presence?.isFresh(now)!=true}
}

/** Start of a calculation with a just measured device position ([device] null: none available). */
fun StoredState.startPosition(now:Long,device:Location?):Location? =
    if(manualMode()) settings.manualOrigin?.takeIf {it.routable} ?: scheduledFavorite(now)?.location
    else device ?: presence?.takeIf {it.isFresh(now)}?.location ?: scheduledFavorite(now)?.location

/** The other favourite whose time window overlaps one of [windows], or null. */
fun StoredState.scheduleConflict(favoriteId:String?,windows:List<HighlightRule>):Favorite? =
    favorites.firstOrNull {other->other.id!=favoriteId && other.hiddenRules.any {theirs->windows.any {it.overlaps(theirs)}}}

/** [rule] without the minutes in [taken] (minute ranges of the week), as at most [limit] rules. */
internal fun HighlightRule.without(taken:List<IntRange>,limit:Int=8):List<HighlightRule> {
    if(taken.none {t->weekRanges().any {it.first<=t.last && t.first<=it.last}})return listOf(this)
    // Minute ranges of the week that remain, each one within the day piece it came from.
    val pieces=weekRanges().flatMap {range->
        var parts=listOf(range)
        taken.forEach {t->parts=parts.flatMap {p->
            if(t.last<p.first || t.first>p.last) listOf(p)
            else listOfNotNull((p.first until t.first).takeIf {!it.isEmpty()},(t.last+1..p.last).takeIf {!it.isEmpty()})
        }}
        parts
    }
    // A piece starts on its own day; "until" is taken modulo one day (an overnight piece wraps).
    val rules=pieces.map {p->Triple(p.first/1440+1,p.first%1440,(p.last+1)%1440)}
        .groupBy({(_,from,until)->from to until},{it.first})
        .map {(times,days)->HighlightRule(days.toSet(),times.first,times.second)}
    return rules.take(limit)
}

/** On read: a minute of the week belongs to the first favourite (list order) that claims it; the
 * others lose only the overlapping part. [notify] records them for a one-time notice. */
fun StoredState.withUniqueSchedules(notify:Boolean=false):StoredState {
    if(favorites.count {it.hiddenRules.isNotEmpty()}<2)return this
    val taken=mutableListOf<IntRange>()
    val shortened=mutableListOf<String>()
    val repaired=favorites.map {f->
        val kept=f.hiddenRules.flatMap {it.without(taken)}.distinct().take(8)
        kept.forEach {taken+=it.weekRanges()}
        if(kept==f.hiddenRules)f else {shortened+=f.label;f.copy(hiddenRules=kept)}
    }
    if(repaired==favorites)return this
    return copy(favorites=repaired,scheduleNotice=if(notify)(scheduleNotice+shortened).distinct().take(40) else scheduleNotice)
}

/** A selected manual origin takes precedence over GPS for routing and presentation.
 * This never replaces the real device fix used to detect physical travel. */
fun StoredState.routingPosition(now:Long=System.currentTimeMillis()):Location? =
    scheduledStart(now)?.location ?: if(manualMode()) settings.manualOrigin
    else presence?.takeIf {it.isFresh(now)}?.location

/** Transient visibility input only; a manual or time-dependent position is never persisted as GPS. */
fun StoredState.routingPresence(now:Long=System.currentTimeMillis()):DevicePresence? =
    scheduledStart(now)?.location?.takeIf {it.hasCoordinates}?.let {DevicePresence(it,now,0f)}
        ?: if(manualMode()) settings.manualOrigin?.takeIf {it.hasCoordinates}?.let {DevicePresence(it,now,0f)}
    else presence
