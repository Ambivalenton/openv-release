// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.repository

import android.content.Context
import android.os.SystemClock
import android.util.AtomicFile
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.localization.tr
import dev.ambivalenton.openv.location.CurrentPositionSource
import java.io.File
import java.io.IOException
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Process-wide owner shared by Compose, the service and notification deep links.
 * One cancellable polling loop lives in the service. Session persistence uses atomic app-private I/O.
 */
class PinnedJourneyController(
    context: Context,
    private val provider: (AppSettings) -> TransitRepository,
    private val positions: CurrentPositionSource,
    private val weather: ArrivalWeatherSource? = null,
    private val now: () -> Long = System::currentTimeMillis,
    private val elapsed: () -> Long = SystemClock::elapsedRealtime,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val file = AtomicFile(File(context.filesDir, "pinned-journey.json"))
    private val recoveryFile = AtomicFile(File(context.filesDir,"recoverable-pins.json"))
    private val recovered = MutableStateFlow<List<RecoverablePin>>(emptyList())
    val recoverable = recovered.asStateFlow()
    private var observations = emptyList<DevicePresence>()
    @Volatile private var lastPollAttemptAt=0L
    private val arrivalPicker = IconArrivalPicker()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val lock = Mutex()
    private val refreshLock = Mutex()
    private val powerProbeLock = Mutex()
    private var lastPowerProbeAt:Long?=null
    private var lastAlternativeSearchAt:Long?=null
    private var lastObservedRideSearchAt:Long?=null
    private var lastTelemetrySaveAt=0L
    private var lastWeatherAt=0L
    private var liveFailureCount=0
    private val wakeup = Channel<Unit>(Channel.CONFLATED)
    @Volatile private var restored = false
    private val mutable = MutableStateFlow(PinnedJourneyState())
    val state = mutable.asStateFlow()
    private inline fun publish(transform:(PinnedJourneyState)->PinnedJourneyState) {
        mutable.update {previous->stabilizedAssistantState(previous,transform(previous),now())}
    }
    private val mutablePower=MutableStateFlow(false)
    val powerMode=mutablePower.asStateFlow()
    @Volatile private var powerOverride:Boolean?=null
    val powerSavingEnabled:Boolean get()=powerOverride?:mutable.value.session?.routing?.powerSaving?:false

    private val locationAvailable:Boolean get()=positions.hasPermission() && positions.locationEnabled()
    private val serverModeEnabled:Boolean get()=powerSavingEnabled || !locationAvailable

    suspend fun setPowerSaving(enabled:Boolean) {
        powerOverride=enabled
        mutablePower.value=enabled
        lock.withLock {
            val current=mutable.value.session?:return@withLock
            // The settings observer can repeat the existing choice after process startup.
            // Do not hold the session lock for another disk write or reset its alert slots.
            if(current.routing.powerSaving==enabled)return@withLock
            val changed=current.withPowerSaving(enabled).withLocationAvailability(locationAvailable)
            if(enabled)observations=emptyList()
            val storageError=save(changed)
            publish {it.copy(session=changed,presence=if(enabled)null else it.presence,
                weather=if(enabled)null else it.weather,alert=null,powerLocationCheck=null,
                arrivalPhase=if(enabled)ArrivalPhase.TRACKING else it.arrivalPhase,
                visualProgress=resolvedJourneyProgress(changed,now(),if(enabled)null else it.presence),storageError=storageError)}
        }
        wakeup.trySend(Unit)
    }

    suspend fun setWalkingPreferences(speed:WalkingSpeed,ignore:Boolean) {
        lock.withLock {
            val current=mutable.value.session ?: return@withLock
            if(current.routing.walkingSpeed==speed && current.routing.ignoreWalking==ignore)return@withLock
            val routing=current.routing.copy(walkingSpeed=speed,ignoreWalking=ignore)
            val changed=current.copy(routing=routing,query=current.query.withWalking(routing),
                alternative=null,alternativeQuery=null,routeRevision=current.routeRevision+1)
            val error=save(changed)
            publish {it.copy(session=changed,storageError=error,alert=null)}
        }
        wakeup.trySend(Unit)
    }

    /** Forecast ticks stay cheap; only one bounded location check is allowed per five minutes. */
    suspend fun forecastTick() {
        checkPowerLocation()
        lock.withLock {
        val current=mutable.value.session?:return@withLock
        if(!serverModeEnabled)return@withLock
        observations=emptyList()
        val session=current.withPowerSaving(powerSavingEnabled).withLocationAvailability(locationAvailable);val progress=resolvedJourneyProgress(session,now(),null)
        publish {it.copy(session=session,presence=null,visualProgress=progress,
            alert=smartJourneyAlert(session,now(),null,progress),arrivalPhase=ArrivalPhase.TRACKING)}
        }
    }

    private suspend fun checkPowerLocation() {
        if(!powerSavingEnabled || !powerProbeLock.tryLock())return
        try {
            val snapshot=mutable.value.session?:return
            val timestamp=now();val last=lastPowerProbeAt
            if(last!=null && timestamp-last in 0 until POWER_GPS_CHECK_INTERVAL_MS)return
            lastPowerProbeAt=timestamp
            val fix=if(positions.hasPermission() && positions.locationEnabled())try {
                withTimeoutOrNull(5_500){positions.currentFresh()}
                currentJourneyGps(positions.lastPresence,now())
            }catch(e:CancellationException){throw e}catch(_:Exception){null} else null
            lock.withLock {
                val current=mutable.value.session?:return@withLock
                if(current.id!=snapshot.id || !powerSavingEnabled)return@withLock
                val checked=powerLocationCheck(current,now(),fix)
                publish {it.copy(powerLocationCheck=checked)}
                if(checked.verdict==PowerLocationVerdict.POSSIBLE_DEVIATION && now()-lastPollAttemptAt>=30_000)wakeup.trySend(Unit)
            }
        }finally {powerProbeLock.unlock()}
    }
    val hasSession: Boolean get() = mutable.value.session != null || file.baseFile.exists() || File(file.baseFile.path + ".bak").exists()

    val focusIsOpen: Boolean get() = mutable.value.session?.let { !it.minimized } ?: (!restored && hasSession)

    suspend fun restore() = lock.withLock {
        if (restored) return@withLock
        restored = true
        val saved = withContext(ioDispatcher) {
            if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return@withContext null
            try {
                val bytes = file.openRead().use { it.readBytes() }
                require(bytes.size <= 1_500_000)
                json.decodeFromString<PinnedJourneySession>(bytes.decodeToString()).also {
                    require(it.trip.segments.isNotEmpty() && it.query.destination.routable)
                }
            } catch (_: IOException) { null }
            catch (_: IllegalArgumentException) { file.delete(); null }
        }
        if (saved != null) {val effective=saved.withPowerSaving(powerOverride?:saved.routing.powerSaving).withLocationAvailability(locationAvailable);mutablePower.value=effective.routing.powerSaving;publish {PinnedJourneyState(session = effective)}}
        var pruneRecovery=false
        recovered.value=withContext(ioDispatcher) {
            if (!recoveryFile.baseFile.exists() && !File(recoveryFile.baseFile.path+".bak").exists()) emptyList()
            else try {
                val bytes=recoveryFile.openRead().use { it.readBytes() };require(bytes.size<=3_000_000)
                val rows=json.decodeFromString<List<RecoverablePin>>(bytes.decodeToString())
                val usable=rows.take(8).filter {now()-it.closedAt in 0..6*3_600_000L && it.session.trip.endTime>now()}
                pruneRecovery=usable.size!=rows.size
                usable
            } catch (_: IOException) { emptyList() } catch (_: IllegalArgumentException) { recoveryFile.delete();emptyList() }
        }
        if(pruneRecovery) saveRecoveries(recovered.value)
    }

    suspend fun pin(favorite: Favorite, query: JourneyQuery, trip: Trip, routing: AppSettings,
        expectedCurrentId: String?=null,enforceExpected: Boolean=false,recoveryId: String?=null): Boolean {
        restore()
        return lock.withLock {
        require(trip.segments.isNotEmpty())
        val old=mutable.value.session
        if(recoveryId!=null) {
            val candidate=recovered.value.firstOrNull {it.session.id==recoveryId} ?: return@withLock false
            if(!samePinnedJourney(candidate.session.trip,trip) || !recoverablePinIsUsable(candidate,now(),positions.lastPresence ?: candidate.presence)) return@withLock false
        }
        if (enforceExpected && old?.id!=expectedCurrentId) return@withLock false
        if (old!=null && samePinnedJourney(old.trip,trip)) {
            // Pinning again is an explicit request to follow the journey, also as a bubble.
            val reopened=old.copy(minimized=false,bubbleDismissed=false)
            val error=save(reopened)
            if(error!=null) { publish { it.copy(storageError=error) };return@withLock false }
            publish { it.copy(session=reopened,storageError=null) };return@withLock true
        }
        // H09: never start a companion for a known cancellation; an active one stays untouched.
        if (pinRejection(trip,now())!=null) return@withLock false
        if (old!=null && !archive(old,mutable.value.presence)) return@withLock false
        val session = PinnedJourneySession(favorite = favorite, query = query, initialTrip = trip, routing = routing).withPowerSaving(powerOverride?:routing.powerSaving).withLocationAvailability(locationAvailable)
        mutablePower.value=session.routing.powerSaving
        val error=save(session)
        if(error!=null) { publish { it.copy(storageError=error) };return@withLock false }
        restored = true
        val fix=if(session.usesServerProgress)null else crediblePresence(positions.lastPresence,now())
        observations=listOfNotNull(fix)
        lastPowerProbeAt=null;lastAlternativeSearchAt=null
        publish {PinnedJourneyState(session, PinnedTrackingStatus.STARTING,presence=fix)}
        if(recoveryId!=null) saveRecoveries(recovered.value.filterNot { it.session.id==recoveryId })
        true
        }
    }

    suspend fun setMinimized(minimized: Boolean) = lock.withLock {
        val session = mutable.value.session ?: return@withLock
        val updated = session.copy(minimized = minimized,assistantNotice=session.assistantNotice?.let {notice->
            if(!minimized && session.minimized)notice.copy(remainingViews=(notice.remainingViews-1).coerceAtLeast(0))else notice})
        publish { it.copy(session = updated) }
        val error = save(updated)
        publish { it.copy(storageError = error) }
    }

    /** Sparse manual corrections of the alighting hypothesis ("still on board" / "I got off"). */
    suspend fun correctAlighting(expectedId:String,alighted:Boolean) = lock.withLock {
        val session=mutable.value.session?.takeIf {it.id==expectedId} ?: return@withLock false
        val timestamp=now()
        val updated=if(alighted)alightManually(session,timestamp) else stayAboardManually(session,timestamp)
        val progress=resolvedJourneyProgress(updated,timestamp,mutable.value.presence)
        publish {it.copy(session=updated,visualProgress=progress)}
        val error=save(updated)
        publish {it.copy(storageError=error)}
        true
    }

    /** Overlay visibility only: the pin and the journey companion stay as they are. */
    suspend fun setBubbleDismissed(expectedId:String,dismissed:Boolean) = lock.withLock {
        val session=mutable.value.session?.takeIf {it.id==expectedId} ?: return@withLock false
        if(session.bubbleDismissed==dismissed)return@withLock true
        val updated=session.copy(bubbleDismissed=dismissed)
        publish {it.copy(session=updated)}
        val error=save(updated)
        publish {it.copy(storageError=error)}
        true
    }

    suspend fun close(expectedId: String? = null): Boolean {
        restore()
        return lock.withLock {
        if (expectedId != null && mutable.value.session?.id != expectedId) return@withLock false
        val current=mutable.value.session
        if(current!=null && !archive(current,mutable.value.presence)) return@withLock false
        restored = true
        observations=emptyList()
        lastPowerProbeAt=null;lastAlternativeSearchAt=null
        withContext(ioDispatcher) { file.delete() }
        publish {PinnedJourneyState()}
        true
        }
    }

    suspend fun clearRecoveries(): Boolean = lock.withLock {saveRecoveries(emptyList())}

    fun requestRefresh() { wakeup.trySend(Unit) }

    fun dismissArrival(expectedId: String) {
        publish { if (it.arrival?.sessionId == expectedId) it.copy(arrival = null) else it }
    }

    fun running() { publish { it.copy(status = PinnedTrackingStatus.RUNNING, error = null) } }
    fun pause(message: String? = null) {
        publish { it.copy(status = PinnedTrackingStatus.PAUSED, refreshing = false, alert=null,error = message ?: it.error) }
    }

    /**
     * H10: the result tells the screen which journey is active afterwards: [AlternativeAdoption.Adopted]
     * with the new trip, or [AlternativeAdoption.Expired] when the offer ended between opening and
     * confirming (the previous journey stays active), or [AlternativeAdoption.Failed].
     */
    suspend fun adoptAlternative(expectedSessionId:String?=null,expectedAlternativeKey:String?=null):AlternativeAdoption = lock.withLock {
        val current = mutable.value.session ?: return@withLock AlternativeAdoption.Failed
        if(expectedSessionId!=null && current.id!=expectedSessionId)return@withLock AlternativeAdoption.Expired
        if(expectedAlternativeKey!=null && current.alternative?.let(::alternativeJourneyKey)!=expectedAlternativeKey)return@withLock AlternativeAdoption.Expired
        val query=current.alternativeQuery ?: return@withLock AlternativeAdoption.Expired
        val trip=availablePinnedAlternative(current,now(),mutable.value.presence) ?: return@withLock AlternativeAdoption.Expired
        replaceJourney(current,query,trip)
    }

    /**
     * Build 117: the person chose one of the offered connections ([JourneyChoices]) at a station. It is
     * taken only while it is still reachable (fresh, boarding ahead); otherwise [AlternativeAdoption.Expired].
     */
    suspend fun adoptChoice(expectedSessionId:String,key:String):AlternativeAdoption = lock.withLock {
        val current = mutable.value.session ?: return@withLock AlternativeAdoption.Failed
        if(current.id!=expectedSessionId)return@withLock AlternativeAdoption.Expired
        val offered=mutable.value.choices?.takeIf {it.sessionId==current.id} ?: return@withLock AlternativeAdoption.Expired
        val raw=offered.trips.firstOrNull {alternativeJourneyKey(it)==key} ?: return@withLock AlternativeAdoption.Expired
        val trip=reachableAssistantAlternative(current,offered.query,raw,now(),mutable.value.presence) ?: return@withLock AlternativeAdoption.Expired
        replaceJourney(current,offered.query,trip)
    }

    /** Called under [lock]: archives [current] and continues with [trip] from [query]'s origin. */
    private suspend fun replaceJourney(current:PinnedJourneySession,query:JourneyQuery,trip:Trip):AlternativeAdoption {
        val replacement=adoptPinnedAlternative(current,query,trip,now())?.copy(query=current.query)?:return AlternativeAdoption.Expired
        if(!archive(current,mutable.value.presence)) return AlternativeAdoption.Failed
        observations=emptyList()
        // New generation prevents an in-flight response for the previous chain from replacing this one.
        lastAlternativeSearchAt=null
        val error=save(replacement)
        if(error!=null){publish {it.copy(storageError=error)};return AlternativeAdoption.Failed}
        publish {it.copy(session = replacement, weather = null, error = null,
            refreshing = false, visualProgress=null,storageError = null,choices=null)}
        wakeup.trySend(Unit)
        return AlternativeAdoption.Adopted(replacement.trip)
    }

    suspend fun noteAlternativePrompt(expectedId:String)=lock.withLock {
        val current=mutable.value.session?.takeIf {it.id==expectedId} ?: return@withLock
        val changed=current.copy(lastAlternativePromptAt=now(),acknowledgedArrivalDelay=maxOf(current.acknowledgedArrivalDelay,arrivalChangeMinutes(current,now())))
        val error=save(changed);publish {it.copy(session=changed,storageError=error)}
    }

    suspend fun dismissAlternative(expectedId:String,key:String) = lock.withLock {
        val current=mutable.value.session?.takeIf {it.id==expectedId} ?: return@withLock
        val changed=current.copy(dismissedAlternativeKeys=(current.dismissedAlternativeKeys+key).toList().takeLast(64).toSet(),lastAlternativePromptAt=now(),
            acknowledgedArrivalDelay=maxOf(current.acknowledgedArrivalDelay,arrivalChangeMinutes(current,now())))
        val error=save(changed);publish {it.copy(session=changed,storageError=error)}
    }

    suspend fun acknowledgeArrivalChange(expectedId:String) = lock.withLock {
        val current=mutable.value.session?.takeIf {it.id==expectedId} ?: return@withLock
        val changed=current.copy(acknowledgedArrivalDelay=arrivalChangeMinutes(current,now()))
        val error=save(changed);publish {it.copy(session=changed,storageError=error)}
    }

    suspend fun track() {
        running()
        while (currentCoroutineContext().isActive && mutable.value.session != null) {
            val started = elapsed()
            try {
                withTimeout(50_000) { withContext(transitRequestBudget(16)) { refreshOnce() } }
            } catch (_: TimeoutCancellationException) {
                publish { it.copy(refreshing = false,
                    error = quietLiveWarning(mutable.value.session)) }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                publish { it.copy(refreshing = false,
                    error = quietLiveWarning(mutable.value.session)) }
            }
            if (mutable.value.session == null) break
            // Start-to-start cadence, rather than adding a full minute after a slow request.
            val poll=serverPollMillis(powerSavingEnabled)*(1L shl liveFailureCount.coerceIn(0,3))
            val remaining = (poll.coerceAtMost(300_000) - (elapsed() - started)).coerceAtLeast(0)
            if (remaining > 0) withTimeoutOrNull(remaining) { wakeup.receive() }
        }
    }

    suspend fun refreshOnce() = refreshLock.withLock {
        val session = mutable.value.session?.withPowerSaving(powerSavingEnabled)?.withLocationAvailability(locationAvailable) ?: return@withLock
        if(session.routing.powerSaving)checkPowerLocation()
        lastPollAttemptAt=now()
        publish { it.copy(refreshing = true) }
        val engine = provider(session.routing)
        val refreshStarted=elapsed()
        val refreshed=withContext(FreshTransitResponse) {engine.refreshAssistantJourney(session,now())}
        liveFailureCount=if(refreshed.fresh)0 else (liveFailureCount+1).coerceAtMost(3)
        val trip=refreshed.trip
        if(!serverModeEnabled && !positions.continuousTracking && positions.hasPermission() && positions.locationEnabled())try {
            withTimeoutOrNull(5_500){positions.currentFresh()}
        }catch(e:CancellationException){throw e}catch(_:Exception){}
        val timestamp=now()
        val presence=if(session.usesServerProgress)null else currentJourneyGps(positions.lastPresence,timestamp)
        var next=session.copy(trip=trip,lastSuccessAt=if(refreshed.fresh && refreshed.complete)timestamp else session.lastSuccessAt,
            alternative=availablePinnedAlternative(session,timestamp,presence))
        if(!samePinnedJourney(session.trip,trip))next=next.copy(routeRevision=session.routeRevision+1,
            assistantNotice=AssistantNotice(tr("Anschluss angepasst · Ankunft %1\$s",clockTime(trip.effectiveArrival())),timestamp))
        if(!session.usesServerProgress)next=advanceAssistantJourney(next,presence,observations+listOfNotNull(presence),positions.lastActivity,timestamp)
        if(!session.usesServerProgress)next=observeMissedBoarding(next,timestamp,presence,refreshed.fresh)
        val needsRecovery=trip.cancelled || !assistantTransfersReachable(next,trip,timestamp) || unreachablePinnedTransfer(trip,next.routing.ignoreWalking,next.routing.walkingSpeed)!=null || !refreshed.fresh ||
            upcomingRideUnreachableFromGps(next,timestamp,presence) || next.missedBoarding!=null ||
            mutable.value.powerLocationCheck?.verdict==PowerLocationVerdict.POSSIBLE_DEVIATION
        // Another service than the pinned one was observed. Look for that service first;
        // an ordinary alternative search for the outdated plan would be wasted.
        val deviation=if(session.usesServerProgress)null else confirmedRideDeviation(next,timestamp)
        var observedRide:ObservedRideMatch?=null
        if(deviation!=null && lastObservedRideSearchAt.let {it==null || timestamp-it !in 0 until RideDeviationPolicy.SEARCH_INTERVAL_MILLIS}) {
            lastObservedRideSearchAt=timestamp
            observedRide=try {withContext(FreshTransitResponse) {engine.findObservedRide(next,deviation)}}
                catch(e:CancellationException) {throw e} catch(_:Exception) {null}
        }
        val sinceSearch=lastAlternativeSearchAt?.let {timestamp-it}
        val searchInterval=if(needsRecovery && !session.usesServerProgress)45_000L else alternativeSearchIntervalMillis(session.routing.powerSaving)
        var searchResult:AlternativeSearchResult?=null
        if(deviation==null && liveFailureCount<=1 && (sinceSearch==null || sinceSearch<0 || sinceSearch>=searchInterval)) {
            lastAlternativeSearchAt=timestamp
            val query=pinnedRecoveryQuery(next,timestamp,presence)
            try {
                searchResult=withContext(FreshTransitResponse) {engine.findReachableAlternative(next,timestamp,presence,query,
                    maxRequests=if(needsRecovery)5 else if(session.routing.powerSaving)2 else 3)}
            } catch(e:CancellationException) {throw e} catch(_:Exception) { /* Retry with bounded cadence; keep useful travel information. */ }
        }
        val forecast = if (session.routing.overview.showWeather && !powerSavingEnabled && timestamp-lastWeatherAt>=15*60_000 && elapsed()-refreshStarted<35_000) try {
            lastWeatherAt=timestamp
            withTimeoutOrNull(6_000) { weather?.atArrival(session.query.destination, trip.endTime) }
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { null } else mutable.value.weather
        currentCoroutineContext().ensureActive()
        lock.withLock commit@ {
            if (mutable.value.session?.id != session.id || mutable.value.session?.routeRevision!=session.routeRevision) return@commit
            // A live response must not overwrite a minimize/reopen action made during I/O.
            var committed = next.copy(minimized = mutable.value.session!!.minimized,
                bubbleDismissed=mutable.value.session!!.bubbleDismissed,
                provisionalAlighting=mutable.value.session!!.provisionalAlighting,stayAboard=mutable.value.session!!.stayAboard,
                assistantNotice=if(next.routeRevision==session.routeRevision)mutable.value.session!!.assistantNotice else next.assistantNotice,
                leftDestinationFence = mutable.value.session!!.leftDestinationFence,
                acknowledgedArrivalDelay=mutable.value.session!!.acknowledgedArrivalDelay,
                dismissedAlternativeKeys=mutable.value.session!!.dismissedAlternativeKeys,lastAlternativePromptAt=mutable.value.session!!.lastAlternativePromptAt,
                visitedWaypoints=mutable.value.session!!.visitedWaypoints,observedCalls=mutable.value.session!!.observedCalls,
                stationArrival=mutable.value.session!!.stationArrival,
                movement=mutable.value.session!!.movement,completedRideKeys=mutable.value.session!!.completedRideKeys,activeLegAnchor=mutable.value.session!!.activeLegAnchor,gpsCursor=mutable.value.session!!.gpsCursor,lastTrackedPresence=mutable.value.session!!.lastTrackedPresence,
                rideDeviation=mutable.value.session!!.rideDeviation)
            val timestamp=now()
            val latest=if(!serverModeEnabled) listOfNotNull(presence,mutable.value.presence,positions.lastPresence).maxByOrNull { it.capturedAt }?.let {currentJourneyGps(it,timestamp)} else null
            committed=committed.withPowerSaving(powerSavingEnabled).withLocationAvailability(locationAvailable)
            if(serverModeEnabled)observations=emptyList()
            if(latest!=null) observations=(observations+latest).distinctBy { it.capturedAt }.filter { timestamp-it.capturedAt in 0..120_000 }.takeLast(16)
            committed = advanceAssistantJourney(rememberDeparture(committed,latest,timestamp),latest,observations,positions.lastActivity,timestamp)
            // The observed service replaces the pinned ride only while the observation still holds.
            observedRide?.takeIf {match->confirmedRideDeviation(committed,timestamp)?.rideKey==match.observation.rideKey}?.let {match->
                committed=advanceAssistantJourney(adoptObservedRide(committed,match,timestamp),latest,observations,positions.lastActivity,timestamp)
                searchResult=null
            }
            searchResult?.let {result->
                val candidate=result.trip?.let {reachableAssistantAlternative(committed,result.query,it,timestamp,latest)}
                val complete=candidate?.let {adoptPinnedAlternative(committed,result.query,it,timestamp)}
                if(candidate!=null && complete!=null) {
                    val first=candidate.segments.firstOrNull {!it.isWalk}
                    val frequent=first?.let {frequentLocalService(it,result.board)}==true
                    when(assistantRouteDecision(committed,complete.trip,timestamp,frequent)) {
                        AssistantRouteDecision.UPDATE_CONNECTION->{
                            committed=complete.copy(id=committed.id,query=session.query,initialTrip=session.initialTrip,
                                assistantNotice=AssistantNotice(tr("Anschluss angepasst · Ankunft %1\$s",clockTime(complete.trip.effectiveArrival())),timestamp),
                                acknowledgedArrivalDelay=committed.acknowledgedArrivalDelay,
                                dismissedAlternativeKeys=committed.dismissedAlternativeKeys,lastAlternativePromptAt=committed.lastAlternativePromptAt,
                                visitedWaypoints=committed.visitedWaypoints)
                            committed=advanceAssistantJourney(committed,latest,observations,positions.lastActivity,timestamp)
                        }
                        AssistantRouteDecision.OFFER->committed=committed.copy(alternative=candidate,alternativeQuery=result.query)
                        AssistantRouteDecision.KEEP->committed=committed.copy(alternative=null,alternativeQuery=null)
                    }
                }else if(!result.incomplete)committed=committed.copy(alternative=null,alternativeQuery=null)
            }
            // Build 117: the other reachable connections of the same search, for the choice buttons (no extra request).
            val choices=searchResult?.let {result->JourneyChoices(committed.id,result.query,result.choices.mapNotNull {
                reachableAssistantAlternative(committed,result.query,it,timestamp,latest)}.filterNot {samePinnedJourney(it,committed.trip)})}
            if (completeArrival(committed, timestamp)) return@commit
            val progress=resolvedJourneyProgress(committed,timestamp,latest)
            val storageError = save(committed)
            publish {it.copy(session = committed, presence = latest, weather = forecast,
                visualProgress=progress,choices=choices ?: it.choices?.takeIf {old->old.sessionId==committed.id},
                alert=smartJourneyAlert(committed,timestamp,latest,progress),
                arrivalPhase=pinnedArrivalPhase(committed,timestamp,observations),
                refreshing = false, error = quietLiveWarning(committed), storageError = storageError)}
        }
    }

    /** GPS cadence is independent of provider polling; no routing request is made here. */
    suspend fun telemetryTick() {
        if(serverModeEnabled){forecastTick();return}
        val id=mutable.value.session?.id ?: return
        val fix=if(positions.hasPermission() && positions.locationEnabled()) try {
            if(!positions.continuousTracking)withTimeoutOrNull(6_000) { positions.currentFresh() }
            positions.lastPresence
        } catch(e: CancellationException) { throw e } catch(_: Exception) { null } else null
        lock.withLock {
            val current=mutable.value.session?.withLocationAvailability(locationAvailable) ?: return@withLock
            if(current.id!=id) return@withLock
            // A request started in normal mode may complete after the mode switch.
            if(serverModeEnabled) {
                observations=emptyList()
                val forecast=current.withPowerSaving(powerSavingEnabled).withLocationAvailability(locationAvailable);val timestamp=now()
                val progress=resolvedJourneyProgress(forecast,timestamp,null)
                publish {it.copy(session=forecast,presence=null,visualProgress=progress,
                    arrivalPhase=ArrivalPhase.TRACKING,alert=smartJourneyAlert(forecast,timestamp,null,progress))}
                return@withLock
            }
            val timestamp=now()
            if(fix!=null) observations=(observations+fix).distinctBy { it.capturedAt }.filter { timestamp-it.capturedAt in 0..120_000 }.takeLast(16)
            var updated = advanceAssistantJourney(rememberDeparture(current,fix,timestamp),fix,observations,positions.lastActivity,timestamp)
            if(missedBoardingRide(updated,timestamp,fix)==null) updated=updated.copy(missedBoarding=null)
            if (completeArrival(updated, timestamp)) return@withLock
            val progress=resolvedJourneyProgress(updated,timestamp,fix)
            if(upcomingRideUnreachableFromGps(updated,timestamp,fix) && timestamp-lastPollAttemptAt>=30_000) wakeup.trySend(Unit)
            val deviationConfirmed=confirmedRideDeviation(updated,timestamp)!=null && confirmedRideDeviation(current,timestamp)==null
            if(deviationConfirmed) wakeup.trySend(Unit)
            val transition=updated.gpsCursor?.legKey!=current.gpsCursor?.legKey || updated.gpsCursor?.departed!=current.gpsCursor?.departed || updated.completedRideKeys!=current.completedRideKeys ||
                deviationConfirmed
            val storageError = if (updated != current && (transition || timestamp-lastTelemetrySaveAt>=30_000)) {
                lastTelemetrySaveAt=timestamp;save(updated)
            } else mutable.value.storageError
            publish { it.copy(session=updated,presence=currentJourneyGps(fix,timestamp),
                arrivalPhase=pinnedArrivalPhase(updated,timestamp,observations),storageError=storageError,
                visualProgress=progress,
                alert=smartJourneyAlert(updated,timestamp,fix,progress)) }
        }
    }

    /** Old data is labelled with its actual receipt time, per relevant leg, not per HTTP success. */
    private fun quietLiveWarning(session:PinnedJourneySession?):String? {
        session ?: return null
        val now=now()
        staleRealtimeSince(session,now)?.let {return tr("Live-Stand von %1\$s Uhr. Neuere Daten werden abgerufen.",clockTime(it))}
        return session.takeIf {now-it.lastSuccessAt>10*60_000}
            ?.let {tr("Live-Zeiten werden aktualisiert. Die letzte Ankunftsprognose bleibt sichtbar.")}
    }

    private fun rememberDeparture(session: PinnedJourneySession, fix: DevicePresence?, timestamp: Long): PinnedJourneySession =
        if (!session.leftDestinationFence && timestamp >= session.trip.startTime &&
            crediblePresence(fix,timestamp) != null &&
            (distanceMeters(fix!!.location,session.query.destination) ?: 0.0) > ARRIVAL_DESTINATION_RADIUS_METERS + fix.accuracyMeters)
            session.copy(leftDestinationFence=true) else session

    /** Caller holds the session mutex. A completed pin is never offered as a recovery. */
    private suspend fun completeArrival(session: PinnedJourneySession, timestamp: Long): Boolean {
        if(session.usesServerProgress)return false
        if (pinnedArrivalPhase(session,timestamp,observations) != ArrivalPhase.CONFIRMED) return false
        withContext(ioDispatcher) { file.delete() }
        if (file.baseFile.exists() || File(file.baseFile.path+".bak").exists()) {
            publish { it.copy(storageError=tr("Die angepinnte Verbindung konnte nicht dauerhaft geschlossen werden.")) }
            return false
        }
        observations=emptyList()
        publish {PinnedJourneyState(arrivalPhase=ArrivalPhase.CONFIRMED,
            arrival=JourneyArrival(session.id,arrivalPicker.next(session.favorite.icon),timestamp))}
        saveRecoveries(recovered.value.filterNot { samePinnedJourney(it.session.trip,session.trip) })
        wakeup.trySend(Unit)
        return true
    }

    private suspend fun archive(session: PinnedJourneySession,presence: DevicePresence?): Boolean {
        val candidate=RecoverablePin(session.copy(minimized=true),now(),crediblePresence(presence,now(),90_000))
        val rows=(listOf(candidate)+recovered.value.filterNot { samePinnedJourney(it.session.trip,session.trip) })
            .filter { it.session.trip.endTime>now() && now()-it.closedAt in 0..6*3_600_000L }.take(8)
        return saveRecoveries(rows)
    }

    private suspend fun saveRecoveries(rows: List<RecoverablePin>): Boolean = withContext(ioDispatcher) {
        try {
            var bounded=rows.take(8)
            var bytes=json.encodeToString(bounded).encodeToByteArray()
            while(bytes.size>3_000_000 && bounded.size>1) {bounded=bounded.dropLast(1);bytes=json.encodeToString(bounded).encodeToByteArray()}
            require(bytes.size<=3_000_000)
            val output=recoveryFile.startWrite()
            try { output.write(bytes);recoveryFile.finishWrite(output) }
            catch(e: Exception) {recoveryFile.failWrite(output);throw e}
            recovered.value=bounded
            true
        } catch(e: CancellationException) {throw e} catch(_: Exception) {
            publish { it.copy(storageError=tr("Die Verbindung konnte nicht für die Wiederherstellung gespeichert werden.")) }
            false
        }
    }

    private suspend fun save(session: PinnedJourneySession): String? = withContext(ioDispatcher) {
        try {
            val bytes=json.encodeToString(session).encodeToByteArray()
            require(bytes.size<=1_500_000)
            val output = file.startWrite()
            try {
                output.write(bytes)
                file.finishWrite(output)
            } catch (e: Exception) { file.failWrite(output); throw e }
            null
        } catch(e: CancellationException) {throw e} catch (_: Exception) {
            tr("Die angepinnte Verbindung konnte nicht dauerhaft gespeichert werden.")
        }
    }
}

/** H10: outcome of taking over an alternative journey. */
sealed interface AlternativeAdoption {
    data class Adopted(val trip: Trip) : AlternativeAdoption
    /** The offer is no longer available; the previous journey stays active. */
    data object Expired : AlternativeAdoption
    data object Failed : AlternativeAdoption
}
