// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.tutorial


import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.testTag
import dev.ambivalenton.openv.localization.tr

data class TutorialStep(val target:String,val titleKey:String,val bodyKey:String)
data class TutorialPath(val id:String,val steps:List<TutorialStep>)
object TutorialPaths {
    val overview=TutorialPath("overview-cards-v1",listOf(TutorialStep("overview-connection","Fahrt öffnen",
        "Tippe für die einzelne Fahrt. Halte den Verbindungstext gedrückt, um mehrere passende Fahrten im Balkendiagramm zu suchen.")))
    val timeline=TutorialPath("timeline-actions-v2",listOf(
        TutorialStep("timeline-waypoint","Zwischenstopp einfügen","Über das Plus wählst du einen Zwischenstopp. Mit dem Slider legst du fest, wie lange du dort bleiben möchtest."),
        TutorialStep("timeline-pin","Live begleiten","Pinne die Fahrt an: Fortschritt und Hinweise bleiben sichtbar."),
        journeyUtilityTutorial()))
    val search=TutorialPath("search-destination-v1",listOf(TutorialStep("search-destination","Ziel suchen",
        "Gib eine Haltestelle, Adresse oder einen Ort ein.")))
    val waypointStay=TutorialPath("waypoint-stay-v1",listOf(TutorialStep("waypoint-stay","Zeit am Zwischenstopp","Ziehe den Slider für deinen Aufenthalt: von 0 bis 120 Minuten. Danach berechnet Öpen-V auch die weiteren Verbindungen.")))
    val all=listOf(overview,timeline,search,waypointStay)
}

@Stable class TutorialState {
    var completed by mutableStateOf<Set<String>>(emptySet())
    var enabled by mutableStateOf(false)
    private val modalOwners=mutableStateMapOf<Any,Unit>()
    private val overlayOwners=mutableStateMapOf<Any,String>()
    private val dismissedForSession=mutableStateMapOf<String,Unit>()
    fun canStart(path:TutorialPath)=enabled && path.id !in completed && path.id !in dismissedForSession
    fun dismissForNavigation() {session?.let {dismissedForSession[it.id]=Unit;cancel(it.id)}}
    fun restart() {session?.let {cancel(it.id)};dismissedForSession.clear();completed=emptySet();lastPersisted=null}
    val popupActive get()=modalOwners.isNotEmpty()
    fun acquireModal(owner:Any) {modalOwners[owner]=Unit}
    fun releaseModal(owner:Any) {modalOwners.remove(owner);overlayOwners.remove(owner)}
    fun overlayVisible(owner:Any,pathId:String,visible:Boolean) {
        if(visible && session?.id==pathId) overlayOwners[owner]=pathId else overlayOwners.remove(owner)
    }
    fun releaseOverlay(owner:Any) {overlayOwners.remove(owner)}
    fun isBlocking(pathId:String)=session?.id==pathId && overlayOwners.values.any {it==pathId}
    fun needsTarget(key:String)=enabled && (TutorialPaths.all.none {it.steps.any {s->s.target==key}} ||
        TutorialPaths.all.any {it.id !in completed && it.steps.any {s->s.target==key}})

    var session by mutableStateOf<TutorialPath?>(null)
        private set
    var stepIndex by mutableIntStateOf(0)
        private set
    var saving by mutableStateOf(false)
        private set
    var saveError by mutableStateOf(false)
        private set
    private var generation=0
    private var lastPersisted:Set<String>?=null
    private val owners=mutableMapOf<String,Any>()
    private var persist: suspend (String)->Boolean={false}
    val targets=mutableStateMapOf<String,Rect>()
    val step get()=session?.steps?.getOrNull(stepIndex)
    fun configure(completed:Set<String>,enabled:Boolean,persist:suspend (String)->Boolean) {
        if(lastPersisted!=completed) {this.completed=completed;lastPersisted=completed}
        this.enabled=enabled;this.persist=persist
        if(!enabled) session?.let {cancel(it.id)}
    }
    fun begin(path:TutorialPath) {
        if(!canStart(path) || session!=null) return
        session=path;stepIndex=0;saveError=false;generation++
    }
    fun cancel(pathId:String) {if(session?.id==pathId) {session=null;stepIndex=0;overlayOwners.clear();generation++}}
    fun register(key:String,owner:Any,rect:Rect) {owners[key]=owner;targets[key]=rect}
    fun unregister(key:String,owner:Any) {if(owners[key]===owner) {owners.remove(key);targets.remove(key)}}
    suspend fun advance(skip:Boolean=false) {
        val path=session ?: return
        if(saving) return
        if(!skip && stepIndex<path.steps.lastIndex) {stepIndex++;saveError=false;return}
        saving=true;saveError=false;val token=generation
        try {
            if(persist(path.id)) {
                completed=completed+path.id
                if(token==generation) {session=null;stepIndex=0;overlayOwners.clear()}
            } else if(token==generation) saveError=true
        } finally {saving=false}
    }
}
val LocalTutorialState=staticCompositionLocalOf<TutorialState?> {null}
val LocalTutorialSurfaceActive=staticCompositionLocalOf {true}

@Composable fun rememberTutorialState(completed:Set<String>,enabled:Boolean,persist:suspend (String)->Boolean):TutorialState {
    val state=remember {TutorialState()}
    SideEffect {state.configure(completed,enabled,persist)}
    return state
}

/** Registers actual window coordinates, including scroll, insets and popup windows. */
fun Modifier.tutorialTarget(key:String):Modifier = composed {
    val state=LocalTutorialState.current
    val owner=remember {Any()}
    DisposableEffect(state,key,state?.needsTarget(key)) {onDispose {state?.unregister(key,owner)}}
    if(state==null || !state.needsTarget(key)) return@composed this
    this.onGloballyPositioned {coordinates->
        if(coordinates.isAttached) {
            val position=coordinates.positionInWindow()
            state.register(key,owner,Rect(position.x,position.y,position.x+coordinates.size.width,position.y+coordinates.size.height))
        }
    }
}
