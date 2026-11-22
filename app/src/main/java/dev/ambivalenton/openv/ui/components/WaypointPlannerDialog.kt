// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components

import dev.ambivalenton.openv.ui.components.ThemedButton

import dev.ambivalenton.openv.ui.components.AppDialog as Dialog

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import dev.ambivalenton.openv.R
import dev.ambivalenton.openv.ui.tutorial.*
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.data.repository.StateSerializer
import dev.ambivalenton.openv.localization.tr
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.last

/** Local transaction: a failed, cancelled or superseded request cannot replace the displayed route. */
@Composable fun WaypointPlannerDialog(query:JourneyQuery,trip:Trip,gateway:WaypointRoutingGateway,
    onDismiss:()->Unit,onRouted:(JourneyQuery,Trip)->Unit) {
    var savedStops by rememberSaveable(trip.id) {mutableStateOf(StateSerializer.json.encodeToString(trip.waypoints))}
    val stops=remember(savedStops) {runCatching {StateSerializer.json.decodeFromString<List<Location>>(savedStops)}.getOrDefault(emptyList())}
    var savedDwell by rememberSaveable(trip.id) {mutableStateOf(StateSerializer.json.encodeToString(trip.waypointDwellMinutes))}
    val dwell=remember(savedDwell) {runCatching {StateSerializer.json.decodeFromString<Map<String,Float>>(savedDwell)}.getOrDefault(emptyMap())}
    var selectedStay by rememberSaveable(trip.id) {mutableFloatStateOf(0f)}
    // M13: optimised order is the default; "Meine Reihenfolge verwenden" keeps the list as shown.
    var order by rememberSaveable(trip.id) {mutableStateOf(query.waypointOrder)}
    var editing by rememberSaveable(trip.id) {mutableStateOf<String?>(null)}
    var selected by rememberSaveable(trip.id,stateSaver=locationSaver) {mutableStateOf<Location?>(null)}
    var loading by remember {mutableStateOf(false)}
    var problem by remember {mutableStateOf<String?>(null)}
    val scope=rememberCoroutineScope()
    var request by remember {mutableStateOf<Job?>(null)}
    val search=LocalPlaceSearchGateway.current
    val latestRouted by rememberUpdatedState(onRouted)
    fun dismiss() {request?.cancel();onDismiss()}
    Dialog(onDismissRequest={dismiss()},properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
        BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars).imePadding(),contentAlignment=Alignment.Center) {
        ReleaseDialogWindowOnDispose()
        Surface(Modifier.fillMaxWidth(.94f).widthIn(max=560.dp)
            .height(maxHeight*.94f).testTag("waypoint-planner"),
            shape=MaterialTheme.shapes.large,color=MaterialTheme.colorScheme.surfaceContainerLow) {
            FirstUseTutorialHost(if(selected!=null) TutorialPaths.waypointStay else null,Modifier.fillMaxSize(),modal=true) {
            Column(Modifier.fillMaxSize().padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    FittedText(tr("Zwischenstopps"),Modifier.weight(1f),style=MaterialTheme.typography.titleLarge)
                    IconButton({dismiss()}) {AppIcon(R.drawable.ic_close,tr("Zwischenstopps schließen"))}
                }
                Column(Modifier.weight(1f).padding(top=4.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Text(tr("%1\$s → %2\$s",query.origin.name,query.destination.name),Modifier.testTag("waypoint-route-context"),
                        style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    PlaceSearchField(tr("Adresse oder Haltestelle als Zwischenstopp"),selected,"waypoint-${query.origin.provider}",
                        search={text->search?.updates?.invoke(text)?.last() ?: emptyList()},
                        onSelected={selected=it;problem=null},enabled=!loading,expressive=true,externalLabel=true,
                        modifier=Modifier.testTag("waypoint-search"))
                    WaypointOrderChoice(order,{order=it;problem=null},!loading)
                    if(selected!=null) WaypointStaySlider(selectedStay,{selectedStay=it},!loading,"waypoint-stay-new")
                    stops.forEachIndexed {index,place->
                        val identity=waypointIdentity(place)
                        fun move(to:Int) {
                            savedStops=StateSerializer.json.encodeToString(stops.toMutableList().apply {add(to,removeAt(index))})
                            order=WaypointOrder.MANUAL;problem=null
                        }
                        FlowRow(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.Center,
                            horizontalArrangement=Arrangement.spacedBy(4.dp),itemVerticalAlignment=Alignment.CenterVertically) {
                            Text(tr("%1\$s. %2\$s",index+1,place.name),Modifier.weight(1f).widthIn(min=120.dp),style=MaterialTheme.typography.bodyLarge)
                            Row(verticalAlignment=Alignment.CenterVertically) {
                                TextButton({editing=if(editing==identity) null else identity},Modifier.heightIn(min=48.dp),enabled=!loading) {
                                    Text(waypointMinutesLabel(dwell[identity] ?: 0f))
                                }
                                // M13: moving a stop by hand switches to the manual order.
                                if(order==WaypointOrder.MANUAL && stops.size>1) {
                                    IconButton({move(index-1)},enabled=!loading && index>0,modifier=Modifier.testTag("waypoint-up-$index")) {
                                        AppIcon(R.drawable.ic_expand,tr("%1\$s früher anfahren",place.name),Modifier.rotate(180f))
                                    }
                                    IconButton({move(index+1)},enabled=!loading && index<stops.lastIndex,modifier=Modifier.testTag("waypoint-down-$index")) {
                                        AppIcon(R.drawable.ic_expand,tr("%1\$s später anfahren",place.name))
                                    }
                                }
                                IconButton({savedStops=StateSerializer.json.encodeToString(stops.filterIndexed {i,_->i!=index});
                                    savedDwell=StateSerializer.json.encodeToString(dwell-identity);problem=null},enabled=!loading) {
                                    AppIcon(R.drawable.ic_close,tr("Zwischenstopp %1\$s entfernen",place.name))
                                }
                            }
                        }
                        if(editing==identity) WaypointStaySlider(dwell[identity] ?: 0f,
                            {savedDwell=StateSerializer.json.encodeToString(dwell+(identity to it))},!loading,"waypoint-stay-$index")
                    }
                    // M13: order and stays are visible before anything is calculated.
                    val planned=remember(stops,selected,order,query.origin,query.destination) {
                        plannedWaypointOrder(query.origin,query.destination,stops+listOfNotNull(selected),order)
                    }
                    if(stops.size+(if(selected!=null) 1 else 0)>0)
                        Text(planned?.let {list->waypointOrderPreview(query.origin,query.destination,list,dwell+(selected?.let {mapOf(waypointIdentity(it) to selectedStay)} ?: emptyMap()))}
                            ?: tr("Die Reihenfolge wird bei der Berechnung in Fahrtrichtung bestimmt."),
                            Modifier.testTag("waypoint-order-preview"),style=MaterialTheme.typography.bodyMedium,
                            color=MaterialTheme.colorScheme.onSurfaceVariant)
                    if(stops.isEmpty() && selected==null) Text(tr("Suche deinen ersten Zwischenstopp."),style=MaterialTheme.typography.bodyMedium)
                    if(loading) {
                        OverviewDottedDivider(isLoading=true)
                        Text(tr("Deine Verbindung über die Zwischenstopps wird berechnet …"),Modifier.semantics {liveRegion=LiveRegionMode.Polite})
                    }
                    problem?.let {Text(it,Modifier.semantics {liveRegion=LiveRegionMode.Polite},color=MaterialTheme.colorScheme.error)}
                }
                ThemedButton(onClick={
                    val candidate=selected
                    val desired=stops+listOfNotNull(candidate)
                    if(desired.size!=distinctWaypoints(desired).size) {
                        problem=tr("Dieser Zwischenstopp ist bereits enthalten.");return@ThemedButton
                    }
                    if(candidate!=null && (sameLocation(candidate,query.origin) || sameLocation(candidate,query.destination))) {
                        problem=tr("Dieser Ort ist bereits Start oder Ziel deiner Verbindung.");return@ThemedButton
                    }
                    loading=true;problem=null;request?.cancel()
                    request=scope.launch {
                        try {
                            val nextDwell=dwell+if(candidate!=null) mapOf(waypointIdentity(candidate) to selectedStay) else emptyMap()
                            val nextQuery=query.copy(waypoints=desired,waypointDwellMinutes=nextDwell,waypointOrder=order)
                            val result=gateway.route(nextQuery)
                            ensureActive()
                            latestRouted(nextQuery.copy(waypoints=result.waypoints,waypointDwellMinutes=result.waypointDwellMinutes),result)
                        } catch(e:CancellationException) {throw e}
                        catch(e:Exception) {problem=e.message ?: tr("Die Verbindung konnte nicht neu berechnet werden. Deine bisherige Fahrt bleibt erhalten.")}
                        finally {loading=false}
                    }
                },enabled=!loading && (selected!=null || stops!=trip.waypoints || dwell!=trip.waypointDwellMinutes || order!=query.waypointOrder),
                    modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("waypoint-recalculate")) {
                    Text(tr("Route berechnen"))
                }
                // M13: cancelling a running calculation keeps the dialog and the previous journey.
                TextButton({
                    if(loading) {request?.cancel();request=null;loading=false
                        problem=tr("Berechnung abgebrochen. Deine bisherige Fahrt bleibt erhalten.")}
                    else dismiss()
                },Modifier.align(Alignment.End).heightIn(min=48.dp).testTag("waypoint-cancel")) {Text(if(loading) tr("Berechnung abbrechen") else tr("Abbrechen"))}
            }
        }
            }
        }
    }
}

fun waypointMinutesLabel(minutes:Float):String {
    val value=normalizedWaypointStay(minutes)
    val number=if(value%1f==0f) value.toInt().toString() else String.format(java.util.Locale.getDefault(),"%.1f",value)
    return tr("%1\$s Min",number)
}

/** M13: the planned order before calculating; null when the optimised order needs coordinates first. */
fun plannedWaypointOrder(origin:Location,destination:Location,stops:List<Location>,order:WaypointOrder):List<Location>? =
    if(order==WaypointOrder.MANUAL || distinctWaypoints(stops).size<=1) distinctWaypoints(stops)
    else runCatching {dev.ambivalenton.openv.data.repository.WaypointOrderOptimizer().order(origin,destination,stops)}.getOrNull()

fun waypointOrderPreview(origin:Location,destination:Location,stops:List<Location>,dwell:Map<String,Float>):String =
    tr("Reihenfolge: %1\$s",(listOf(origin.name)+stops.map {stop->
        val minutes=normalizedWaypointStay(dwell[waypointIdentity(stop)] ?: 0f)
        if(minutes>0f) tr("%1\$s (%2\$s)",stop.name,waypointMinutesLabel(minutes)) else stop.name
    }+destination.name).joinToString(" → "))

/** M13: two clearly named choices; the optimised order stays the default. */
@OptIn(ExperimentalLayoutApi::class)
@Composable fun WaypointOrderChoice(order:WaypointOrder,onChange:(WaypointOrder)->Unit,enabled:Boolean=true) {
    Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            listOf(WaypointOrder.OPTIMIZED to tr("Reihenfolge optimieren"),WaypointOrder.MANUAL to tr("Meine Reihenfolge verwenden")).forEach {(value,label)->
                FilterChip(order==value,{onChange(value)},label={Text(label)},enabled=enabled,
                    modifier=Modifier.heightIn(min=48.dp).testTag("waypoint-order-${value.name.lowercase()}")
                        .semantics {stateDescription=if(order==value) tr("Ausgewählt") else tr("Nicht ausgewählt")})
            }
        }
        Text(if(order==WaypointOrder.MANUAL) tr("Die Stopps werden genau in dieser Reihenfolge angefahren, auch mit Umweg.")
            else tr("Öpen-V sortiert die Stopps in Fahrtrichtung."),style=MaterialTheme.typography.bodySmall)
    }
}

@Composable fun WaypointStaySlider(value:Float,onChange:(Float)->Unit,enabled:Boolean=true,tag:String="waypoint-stay-slider") {
    val normalized=normalizedWaypointStay(value)
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
            FittedText(tr("Aufenthalt"),Modifier.weight(1f),style=MaterialTheme.typography.titleSmall)
            // M13: the stay can also be typed in minutes.
            var typed by remember(normalized) {mutableStateOf(if(normalized%1f==0f) normalized.toInt().toString() else waypointMinutesLabel(normalized).filter {it.isDigit() || it==',' || it=='.'})}
            OutlinedTextField(typed,{text->
                typed=text.filter(Char::isDigit).take(3)
                typed.toIntOrNull()?.let {onChange(normalizedWaypointStay(it.toFloat()))}
            },Modifier.width(112.dp).testTag("$tag-minutes"),enabled=enabled,singleLine=true,
                suffix={Text(tr("Min"))},
                keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(keyboardType=androidx.compose.ui.text.input.KeyboardType.Number,
                    imeAction=androidx.compose.ui.text.input.ImeAction.Done),
                label=null,textStyle=MaterialTheme.typography.titleSmall)
        }
        Slider(normalized,{onChange(normalizedWaypointStay(it))},valueRange=0f..120f,steps=0,
            enabled=enabled,modifier=Modifier.testTag(tag).then(if(tag=="waypoint-stay-new") Modifier.tutorialTarget("waypoint-stay") else Modifier).semantics {
                contentDescription=tr("Aufenthaltsdauer am Zwischenstopp")
                stateDescription=waypointMinutesLabel(normalized)
            })
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            Text(tr("Ohne Aufenthalt"),style=MaterialTheme.typography.labelSmall)
            Text(tr("120 Min"),style=MaterialTheme.typography.labelSmall)
        }
    }
}
