// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
// Modified for the Öpen-V 1.0 FOSS release (2026-10-27).

package dev.ambivalenton.openv.data.repository

import dev.ambivalenton.openv.localization.tr

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.data.preferences.*
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retryWhen
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement

// K02: a damaged file is only replaced on an explicit decision (confirmed import or empty start).
private val Context.openVStore by dataStore("openv-v1.json", serializer = StateSerializer,
    corruptionHandler = androidx.datastore.core.handlers.ReplaceFileCorruptionHandler { StoreRecovery.gate.replacementFor(it) })

/** K02: the app's store file (DataStore keeps it under files/datastore). */
fun storeFile(context: Context) = java.io.File(context.applicationContext.filesDir, "datastore/openv-v1.json")

object StateSerializer : Serializer<StoredState> {
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
    }
    override val defaultValue = StoredState()

    override suspend fun readFrom(input: InputStream): StoredState =
        try {
            val bytes = input.readBytes()
            require(bytes.size <= 2_000_000)
            val document = migrateFavoriteShapes(migrateDesignOptions(json.parseToJsonElement(bytes.decodeToString())))
            json.decodeFromJsonElement<StoredState>(document)
                .let { state ->
                    // An existing install has already been introduced; new stores retain the default 0.
                    val keys=(document as? kotlinx.serialization.json.JsonObject)?.keys.orEmpty()
                    val introduced=if ("onboardingVersion" !in keys) state.copy(onboardingVersion = 1) else state
                    // Before build 66 the board projected favourite places; copy them once (migrateBoardStops).
                    val board=if ("boardStopsDecoupled" !in keys) introduced.copy(boardStopsDecoupled = false) else introduced
                    // Before build 67 the plan always used the full 12 × 12 grid: existing users keep it
                    // with every line until they change the setting themselves; 6 × 6 is for new installs.
                    if (legacyGrid(document)) board.copy(network = board.network.copy(columns = NETWORK_GRID_SIZE, rows = NETWORK_GRID_SIZE)) else board
                }
                .let { it.withUniqueFavoriteIds().withUniqueSchedules(notify = true) }
                .also { state ->
                    require(state.schemaVersion in 1..10)
                    require(state.personalThemes.size<=MAX_PERSONAL_THEMES)
                    require(state.personalThemes.all{validPersonalThemeName(it.name)})
                    require(state.personalThemes.map{it.id}.distinct().size==state.personalThemes.size)
                    require(state.favorites.size <= if(state.favorites.any {it.counterpole!=null})40 else 80)
                    // An earlier version copied former favourite places (also addresses) into the board list once.
                    require(state.boardStops.size <= MAX_BOARD_PLACES && state.boardStops.all { it.routable })
                    require(state.favorites.map { it.id }.distinct().size == state.favorites.size)
                    // Legacy pairs are converted (invalid ones are skipped), network lines are repaired.
                    require(state.favoritePairs.size<=40)
                    require(
                        state.favorites.all {
                            it.hasValidRoutingOptions() && it.label.isNotBlank() &&
                                it.location.routable &&
                                it.hiddenRules.size <= 8
                        }
                    )
                }
                .let { state ->
                    state.copy(
                        settings = normalizePreferences(state.settings),
                        personalThemes = state.personalThemes.map { it.copy(design = it.design.copy(appearance = it.design.appearance.curatedThemes())) },
                        onboardingDraft = state.onboardingDraft?.let {draft ->
                            require(draft.favorites.size + state.favorites.size <= 80)
                            require(draft.favorites.all {it.hasValidRoutingOptions() && it.label.isNotBlank() && it.label.length <= 80 && it.location.routable})
                            require(draft.favorites.map {it.id}.distinct().size == draft.favorites.size)
                            draft.copy(step=draft.step.coerceIn(0,ONBOARDING_STEPS-1),settings=normalizePreferences(draft.settings))
                        },
                        history = mergeSearchHistory(state.history),
                    ).migrateToNetwork().migrateBoardStops().normalizedOverride().copy(schemaVersion = 10)
                }
        } catch (e: SerializationException) {
            throw CorruptionException(
                tr("Gespeicherte Daten sind beschädigt. Sie wurden nicht überschrieben."),
                e,
            )
        } catch (e: IllegalArgumentException) {
            throw CorruptionException(
                tr("Gespeicherte Daten sind ungültig. Sie wurden nicht überschrieben."),
                e,
            )
        }

    /** True for a stored document (not a new, empty store) whose network has no grid size yet. */
    private fun legacyGrid(document: kotlinx.serialization.json.JsonElement): Boolean {
        val root = document as? kotlinx.serialization.json.JsonObject ?: return false
        if (root.isEmpty()) return false
        val network = root["network"] as? kotlinx.serialization.json.JsonObject
        return network == null || "columns" !in network.keys
    }

    override suspend fun writeTo(t: StoredState, output: OutputStream) {
        val bytes=json.encodeToString(t).encodeToByteArray()
        require(bytes.size<=2_000_000) {tr("Der lokale Speicher ist voll. Entferne eine alte Vormerkung.")}
        output.write(bytes)
    }
}

class LocationRepository(private val store: androidx.datastore.core.DataStore<StoredState>) {
    constructor(context: Context) : this(context.applicationContext.openVStore)
    private val rotatingStop=MutableStateFlow<Location?>(null)
    /** K02: why the store cannot be read right now; null while it can. */
    private val problem=MutableStateFlow<Throwable?>(null)
    val storageProblem: kotlinx.coroutines.flow.StateFlow<Throwable?> = problem
    private val reloads=MutableStateFlow(0)
    /**
     * K02: a read failure does not end the collectors. They wait (with [storageProblem] set) until
     * [reload] is requested and then read the file again, so a repaired store reaches every screen.
     */
    val state: Flow<StoredState> = combine(store.data,rotatingStop) {saved,rotating->
        validBoardState(saved.copy(rotatingBoardStop=rotating))
    }.onEach {problem.value=null}
        .retryWhen {cause,_->
            if(cause is kotlinx.coroutines.CancellationException || !(cause is CorruptionException || cause is java.io.IOException))false
            else {
                problem.value=cause
                val seen=reloads.value
                reloads.first {it!=seen}
                true
            }
        }
        .distinctUntilChanged()

    /** K02: "Erneut laden" – every waiting collector reads the file again. */
    fun reload() {reloads.value=reloads.value+1}

    /** K02: one direct read; it throws instead of waiting when the file cannot be read. */
    suspend fun readOnce(): StoredState = validBoardState(store.data.first().copy(rotatingBoardStop=rotatingStop.value))

    private suspend fun updateData(change:suspend (StoredState)->StoredState):StoredState {
        var expected:Location?=null
        var next:Location?=null
        val saved=store.updateData {raw->
            val current=rotatingStop.value
            // Every write reconciles the virtual location (build 69): switching to GPS or choosing
            // another manual start ends it at once, not only after the next app start.
            val changed=validBoardState(change(validBoardState(raw.copy(rotatingBoardStop=current))).normalizedOverride())
            expected=current;next=changed.rotatingBoardStop
            // Runtime rotation never participates in DataStore equality, disk writes or exports.
            changed.copy(rotatingBoardStop=null)
        }
        if(expected!=next)rotatingStop.compareAndSet(expected,next)
        return validBoardState(saved.copy(rotatingBoardStop=rotatingStop.value))
    }

    suspend fun updateSettings(change: (AppSettings) -> AppSettings) =
        updateData { old ->
            val changed=change(old.settings)
            val timed=if(changed.manualOrigin!=old.settings.manualOrigin && changed.manualOrigin?.let {it.routable && !it.isDevicePosition}==true)
                changed.copy(manualOriginChangedAt=System.currentTimeMillis()) else changed
            old.copy(settings = normalizePreferences(timed),rotatingBoardStop=old.rotatingBoardStop.takeIf {
                changed.board.selectedStop==old.settings.board.selectedStop &&
                    changed.board.rotationSeconds==old.settings.board.rotationSeconds
            })
        }

    suspend fun setManualOrigin(location:Location,now:Long=System.currentTimeMillis())=updateData {old->
        require(location.routable && !location.isDevicePosition)
        old.copy(settings=normalizePreferences(old.settings.copy(originMode=OriginMode.MANUAL,
            manualOrigin=location,manualOriginChangedAt=now)))
    }

    suspend fun activateManualOrigin()=updateData {old->
        // In the planner mode the missing access is intended; the chosen start mode stays for later.
        if(old.settings.plannerMode) return@updateData old
        val settings=old.settings.manualLocationFallback()
        if(settings==old.settings)old else old.copy(settings=settings)
    }

    suspend fun startUsageTipWeek(now:Long=System.currentTimeMillis())=updateData {old->
        if(old.usageTips.startedAt!=null)old else old.copy(usageTips=old.usageTips.copy(startedAt=now))
    }

    /** Atomic counters survive restarts and do not grow when the text header is hidden. */
    suspend fun nextUsageTip(now:Long=System.currentTimeMillis()):JourneyContent? {
        var selected:JourneyContent?=null
        updateData {old->advanceUsageTips(old.usageTips,now).let {step->
            selected=step.tip;old.copy(usageTips=step.progress)
        }}
        return selected
    }

    suspend fun saveFavorite(f: Favorite) =
        updateData { old ->
            require(f.hasValidRoutingOptions() && f.label.isNotBlank() && f.label.length <= 80 && f.location.routable)
            require(f.hiddenRules.size <= 8) { tr("Bis zu acht Zeitfenster pro Favorit.") }
            old.scheduleConflict(f.id,f.hiddenRules)?.let {other->throw IllegalArgumentException(tr("Zu dieser Zeit gibt bereits „%1\$s“ den Standort vor.",other.label))}
            require(old.favorites.size < 40 || old.favorites.any { it.id == f.id }) {
                tr("Maximal 40 Favoriten.")
            }
            // A moved place can make a line connect one place with itself; such lines are dropped.
            old.withFavorite(f.copy(counterpole=null)).let { it.copy(network = it.network.normalized(it.favorites)) }
        }

    /** "Neuer Favorit hier": the new favourite's node lands on the tapped free grid point. */
    suspend fun saveFavoriteAt(f: Favorite, at: GridPoint?) =
        updateData { old ->
            require(f.hasValidRoutingOptions() && f.label.isNotBlank() && f.label.length <= 80 && f.location.routable)
            require(f.hiddenRules.size <= 8) { tr("Bis zu acht Zeitfenster pro Favorit.") }
            old.scheduleConflict(f.id,f.hiddenRules)?.let {other->throw IllegalArgumentException(tr("Zu dieser Zeit gibt bereits „%1\$s“ den Standort vor.",other.label))}
            require(old.favorites.size < 40 || old.favorites.any { it.id == f.id }) { tr("Maximal 40 Favoriten.") }
            old.withFavorite(f.copy(counterpole=null)).withNetworkNodeFor(f.id, at).let { it.copy(network = it.network.normalized(it.favorites)) }
        }

    /** New grid size: lines are removed and all favourites are placed again from the top left. */
    suspend fun setGridSize(columns:Int,rows:Int)=updateData {it.withGridSize(columns,rows)}
    suspend fun moveNode(id:String,point:GridPoint)=updateData {it.withNodeMoved(id,point)}
    suspend fun nodeToTray(id:String)=updateData {it.withNodeToTray(id)}
    suspend fun setNodeRole(id:String,role:NodeRole)=updateData {it.withNodeRole(id,role)}
    suspend fun connectNodes(aId:String,bId:String,edgeId:String)=updateData {it.withEdge(aId,bId,edgeId)}
    suspend fun setEdgeDirection(id:String,mode:DirectionMode)=updateData {it.withEdgeDirection(id,mode)}
    suspend fun removeEdge(id:String)=updateData {it.withoutEdge(id)}
    suspend fun restoreEdge(edge:NetworkEdge,index:Int)=updateData {it.withRestoredEdge(edge,index)}
    suspend fun toggleLocationOverride(nodeId:String,now:Long=System.currentTimeMillis())=updateData {it.toggleLocationOverride(nodeId,now)}
    /** "In Übersicht anzeigen" – one favourite pinned to the overview (hold again releases it). */
    suspend fun setOverviewFavorite(id:String?)=updateData {it.withOverviewFavorite(id)}
    /** The whole list of favourites pinned to the overview (idempotent). */
    suspend fun setOverviewFavorites(ids:List<String>)=updateData {it.withOverviewFavorites(ids)}
    /** Explicit "Aus Favoriten hinzufügen" in the board list: copies the place, it does not couple. */
    suspend fun addFavoriteToBoard(id:String)=updateData {old->
        val place=old.favorites.firstOrNull {it.id==id}?.location?.takeIf {it.routable} ?: return@updateData old
        val key=boardLocationKey(place)
        if(old.boardStops.any {boardLocationKey(it)==key}) old.copy(boardDisabledKeys=old.boardDisabledKeys-key)
        else {
            require(old.boardStops.size < MAX_BOARD_PLACES) { tr("Die Haltestellen-Liste ist voll.") }
            old.copy(boardStops=old.boardStops+place,boardDisabledKeys=old.boardDisabledKeys-key)
        }
    }

    suspend fun saveJourney(candidate:SavedJourney,now:Long=System.currentTimeMillis())=updateData {it.withSavedJourney(candidate,now)}
    suspend fun removeSavedJourney(id:String)=updateData {old->old.copy(savedJourneys=old.savedJourneys.filterNot {it.id==id})}
    suspend fun expireSavedJourneys(now:Long=System.currentTimeMillis())=updateData {old->
        val available=old.savedJourneys.filter {it.available(now)}
        if(available.size==old.savedJourneys.size)old else old.copy(savedJourneys=available)
    }

    suspend fun deleteFavorite(id: String) =
        updateData { it.withoutFavorite(id) }

    /** H12: undo of a deleted favourite including its lines, field, role and overview pin. */
    suspend fun restoreFavorite(removal: FavoriteRemoval) =
        updateData { it.withRestoredFavorite(removal) }

    suspend fun moveFavorite(id: String, direction: Int) =
        updateData { old ->
            val list = old.favorites.toMutableList()
            val index = list.indexOfFirst { it.id == id }
            val to = (index + direction).coerceIn(0, (list.size - 1).coerceAtLeast(0))
            if (index >= 0 && index != to) list.add(to, list.removeAt(index))
            old.copy(favorites = list)
        }

    suspend fun remember(query: JourneyQuery) =
        updateData { old ->
            old.copy(
                history =
                    mergeSearchHistory(
                        old.history + SearchHistory(query, System.currentTimeMillis())
                    )
            )
        }

    suspend fun addBoardStop(stop: Location) =
        updateData { old ->
            require(stop.kind == LocationKind.STOP && stop.routable) {
                tr("Bitte eine Haltestelle auswählen.")
            }
            val key = boardLocationKey(stop)
            if (old.boardStops.any { boardLocationKey(it) == key })
                old.copy(boardExcludedKeys = old.boardExcludedKeys - key, boardDisabledKeys = old.boardDisabledKeys - key)
            else {
                require(old.boardStops.size < MAX_BOARD_PLACES) { tr("Die Haltestellen-Liste ist voll.") }
                old.copy(boardStops = old.boardStops + stop, boardExcludedKeys = old.boardExcludedKeys - key, boardDisabledKeys = old.boardDisabledKeys - key)
            }
        }

    suspend fun selectBoardStop(stop:Location?) = updateData {old->
        if(stop==null)return@updateData old.copy(rotatingBoardStop=null,
            settings=old.settings.copy(board=old.settings.board.copy(selectedStop=null)))
        require(stop.routable)
        val key=boardLocationKey(stop)
        val has=old.boardStops.any {boardLocationKey(it)==key}
        require(has || stop.kind==LocationKind.STOP)
        require(has||old.boardStops.size<MAX_BOARD_PLACES)
        old.copy(rotatingBoardStop=null,boardStops=if(has)old.boardStops else old.boardStops+stop,
            boardExcludedKeys=old.boardExcludedKeys-key,boardDisabledKeys=old.boardDisabledKeys-key,
            settings=old.settings.copy(board=old.settings.board.copy(selectedStop=stop)))
    }

    suspend fun advanceBoardRotation() {
        val current=state.first()
        if(current.settings.board.rotationSeconds>0)rotatingStop.value=nextRotatingBoardStop(current)
    }

    suspend fun resetBoardRotation() {rotatingStop.value=null}

    suspend fun removeBoardStop(stop: Location) =
        updateData { it.withoutBoardPlace(stop) }

    suspend fun setBoardStopEnabled(stop: Location, enabled: Boolean) =
        updateData { it.withBoardPlaceEnabled(stop, enabled) }

    suspend fun saveDepartureBoard(board: DepartureBoardState) =
        updateData {if(board.configurationKey==boardConfigurationKey(it))it.copy(departureBoard=board) else it}

    suspend fun rememberColor(color: Long) =
        updateData { old ->
            old.copy(
                colorHistory =
                    old.colorHistory.copy(
                        recent = (listOf(color) + old.colorHistory.recent).distinct().take(12)
                    )
            )
        }

    suspend fun toggleFavoriteColor(color: Long) =
        updateData { old ->
            val favorites = old.colorHistory.favorites
            old.copy(
                colorHistory =
                    old.colorHistory.copy(
                        favorites =
                            if (color in favorites) favorites - color
                            else (favorites + color).takeLast(20)
                    )
            )
        }

    suspend fun setStopColor(stop: Location, color: Long?) =
        updateData { old ->
            val key = boardLocationKey(stop)
            old.copy(
                stopColors =
                    if (color == null) old.stopColors - key else old.stopColors + (key to color)
            )
        }

    suspend fun launchFeedback(feedback: LaunchFeedback?) =
        updateData { it.copy(launchFeedback = feedback) }

    suspend fun updateOnboarding(change: (OnboardingDraft) -> OnboardingDraft) = updateData { old ->
        if (!needsFirstLaunchSetup(old)) old else {
            val next = change(old.onboardingDraft ?: OnboardingDraft(settings = old.settings))
            require(next.step in 0 until ONBOARDING_STEPS)
            require(next.favorites.size + old.favorites.size <= 40) { tr("Maximal 40 Favoriten.") }
            require(next.favorites.all { it.hasValidRoutingOptions() && it.label.isNotBlank() && it.label.length <= 80 && it.location.routable })
            require((old.favorites+next.favorites).map {it.id}.distinct().size==old.favorites.size+next.favorites.size)
            old.copy(onboardingDraft = next.copy(settings = normalizePreferences(next.settings)))
        }
    }

    suspend fun completeOnboarding() = updateData { old ->
        if (!needsFirstLaunchSetup(old)) return@updateData old
        val next = old.finishFirstLaunchSetup()
        (old.onboardingDraft?.favorites ?: emptyList()).fold(next.copy(favorites=old.favorites)) { state, favorite -> state.withFavorite(favorite) }
    }

    suspend fun savePersonalTheme(name:String,logo:AppLogo):PersonalTheme {
        var saved:PersonalTheme?=null
        updateData {old->old.withPersonalTheme(name,logo,System.currentTimeMillis()).let{(state,theme)->saved=theme;state}}
        return requireNotNull(saved)
    }
    suspend fun removePersonalTheme(id:String)=updateData {old->old.copy(personalThemes=old.personalThemes.filterNot{it.id==id})}
    suspend fun applyPersonalTheme(id:String)=updateData {old->
        val theme=old.personalThemes.first{it.id==id}
        old.copy(settings=normalizePreferences(old.settings.withPersonalDesign(theme.design)),launcherLogo=theme.design.logo.currentReplacement())
    }
    suspend fun restoreDesign(design:PersonalDesign)=updateData{old->old.copy(settings=normalizePreferences(old.settings.withPersonalDesign(design)),launcherLogo=design.logo.currentReplacement())}
    suspend fun setLauncherLogo(logo:AppLogo)=updateData{it.copy(launcherLogo=logo.currentReplacement())}
    suspend fun replaceFromBackup(imported:StoredState,token:String)=updateData {old->
        imported.copy(presence=old.presence,departureBoard=DepartureBoardState(),launchFeedback=null,
            lastImportToken=token,settings=normalizePreferences(imported.settings))
    }

    suspend fun clearHistory() = updateData { it.copy(history = emptyList()) }

    /** 2.5: hide exactly these notice texts for their provider and stop (bounded, local). */
    suspend fun dismissNotices(keys: Collection<String>) = updateData {
        it.copy(dismissedNotices = (it.dismissedNotices + keys).toList().takeLast(500).toSet())
    }
    /** 2.5: "Rückgängig" or showing hidden notices again (all when [keys] is null). */
    suspend fun restoreNotices(keys: Collection<String>?) = updateData {
        it.copy(dismissedNotices = if (keys == null) emptySet() else it.dismissedNotices - keys.toSet())
    }
    suspend fun clearScheduleNotice() = updateData { if (it.scheduleNotice.isEmpty()) it else it.copy(scheduleNotice = emptyList()) }

    suspend fun completeTutorial(id:String) = updateData {old->
        require(id.matches(Regex("[a-z0-9-]{1,80}")))
        old.copy(completedTutorials=old.completedTutorials+id)
    }
    suspend fun resetTutorials() = updateData {it.copy(completedTutorials=emptySet())}
    suspend fun restartOnboarding() = updateData {it.copy(onboardingVersion=0,onboardingDraft=null)}

    suspend fun recordPresence(fix: DevicePresence) =
        updateData { old ->
            require(
                fix.location.hasCoordinates &&
                    fix.accuracyMeters.isFinite() &&
                    fix.accuracyMeters >= 0
            )
            if (old.presence == fix) old else old.copy(presence = fix)
        }

    suspend fun clearPresence() = updateData { it.copy(presence = null) }
}
