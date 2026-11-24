// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.data.repository.WalkingDirectionsSource
import dev.ambivalenton.openv.localization.tr
import dev.ambivalenton.openv.ui.theme.LocalAppSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import androidx.compose.ui.platform.LocalConfiguration
import kotlin.math.*

val LocalWalkingDirectionsSource=staticCompositionLocalOf<WalkingDirectionsSource?> {null}
sealed interface WalkingDirectionsState {
    data object Loading:WalkingDirectionsState
    data class Ready(val route:WalkingDirections):WalkingDirectionsState
    data object Unavailable:WalkingDirectionsState
}

/** Open pinned focus is the sole request owner. No geocoding, GPS polling or background requests.
 * A stable geometry key prevents live timetable refreshes restarting work. */
@Composable fun rememberPinnedWalkingDirections(trip:Trip,retry:Int):Map<String,WalkingDirectionsState> {
    val settings=LocalAppSettings.current
    val source=LocalWalkingDirectionsSource.current
    val language=if(LocalConfiguration.current.locales[0].language=="de") "de-DE" else "en-US"
    val walks=trip.segments.withIndex().filter {it.value.isWalk}.map {"${it.index}:${it.value.id}" to it.value}
    val geometryKey=walks.map {(id,leg)->listOf(id,leg.origin.latitude,leg.origin.longitude,leg.destination.latitude,leg.destination.longitude)}
    var routes by remember {mutableStateOf<Map<String,WalkingDirectionsState>>(emptyMap())}
    LaunchedEffect(settings.ignoreWalking,settings.detailedWalkingNavigation,geometryKey,settings.walkingSpeed,settings.walkingRoutingEndpoint,language,source,retry) {
        routes=emptyMap()
        if(settings.ignoreWalking || !settings.detailedWalkingNavigation) return@LaunchedEffect
        routes=walks.associate {it.first to WalkingDirectionsState.Loading}
        for((id,leg) in walks) {
            val result=try {
                source?.directions(leg,settings.walkingSpeed,settings.walkingRoutingEndpoint,language)
                    ?.let {WalkingDirectionsState.Ready(it)} ?: WalkingDirectionsState.Unavailable
            } catch(_:TimeoutCancellationException) {WalkingDirectionsState.Unavailable}
              catch(e:CancellationException) {throw e}
              catch(_:Exception) {WalkingDirectionsState.Unavailable}
            routes=routes+(id to result)
        }
    }
    return if(!settings.ignoreWalking && settings.detailedWalkingNavigation) routes else emptyMap()
}

/**
 * Build 116 · the walk of a change that has no walking leg of its own (the timetable only gives a
 * transfer time): from the arrival platform to the departure platform, with the same routing service
 * and settings as any walk. Nothing for one and the same place (under 15 m) or without routing.
 */
@Composable fun rememberTransferWalk(node:TransferNode):WalkingDirectionsState? {
    val settings=LocalAppSettings.current
    val source=LocalWalkingDirectionsSource.current
    val from=node.arrivalLocation;val to=node.departureLocation
    val language=if(LocalConfiguration.current.locales[0].language=="de") "de-DE" else "en-US"
    val apart=from!=null && to!=null && (distanceMeters(from,to) ?: 0.0)>=15.0
    val state by produceState<WalkingDirectionsState?>(null,source,from?.latitude,from?.longitude,to?.latitude,to?.longitude,
        settings.ignoreWalking,settings.detailedWalkingNavigation,settings.walkingRoutingEndpoint,language) {
        if(source==null || !apart || settings.ignoreWalking || !settings.detailedWalkingNavigation) {value=null;return@produceState}
        value=WalkingDirectionsState.Loading
        val leg=TripSegment("transfer-${node.departingLegId}",from!!,to!!,0L,0L,LineType.WALK,tr("Fußweg"))
        value=try {WalkingDirectionsState.Ready(source.directions(leg,settings.walkingSpeed,settings.walkingRoutingEndpoint,language))}
            catch(e:CancellationException) {throw e} catch(_:Exception) {WalkingDirectionsState.Unavailable}
    }
    return state
}

fun walkingSectionTitle(section:WalkingSection):String {
    val road=section.streetNames.joinToString(" / ").ifBlank {tr("Fußweg")}
    val facilities=section.facilities.mapNotNull {when(it){WalkingTurn.STAIRS->tr("Treppe");WalkingTurn.ELEVATOR->tr("Aufzug");WalkingTurn.ROUNDABOUT->tr("Kreisverkehr");else->null}}
    val name=(listOf(road)+facilities).joinToString(" · ")
    return when(section.turn) {
        WalkingTurn.LEFT,WalkingTurn.SLIGHT_LEFT->tr("Links: %1\$s",name)
        WalkingTurn.RIGHT,WalkingTurn.SLIGHT_RIGHT->tr("Rechts: %1\$s",name)
        WalkingTurn.UTURN->tr("Wenden: %1\$s",name)
        WalkingTurn.STAIRS->tr("Treppe")
        WalkingTurn.ELEVATOR->tr("Aufzug")
        WalkingTurn.ROUNDABOUT->tr("Kreisverkehr")
        else->name
    }
}
fun walkingSectionDistance(meters:Double):String=if(meters>=1000) tr("%1\$s km folgen",java.lang.String.format(java.util.Locale.getDefault(),"%.1f",meters/1000))
    else tr("%1\$s m folgen",meters.roundToInt())

@Composable fun PinnedWalkingDirectionRows(id:String,state:WalkingDirectionsState,color:Color,active:Boolean,phase:()->Float,onRetry:()->Unit,
    presence:DevicePresence?=null,completed:Boolean=false) {
    var selected by remember(id) {mutableStateOf<Pair<String,List<WalkingMapPart>>?>(null)}
    when(state) {
        WalkingDirectionsState.Loading->WalkingInstructionRow("$id-loading",color,active,phase) {
            Text(tr("Geh-Anweisungen werden geladen …"),style=MaterialTheme.typography.bodyMedium)
        }
        WalkingDirectionsState.Unavailable->WalkingInstructionRow("$id-unavailable",color,active,phase) {
            Text(tr("Geh-Anweisungen derzeit nicht verfügbar."),style=MaterialTheme.typography.bodyMedium)
            TextButton(onRetry,Modifier.testTag("walking-directions-retry-$id")) {Text(tr("Erneut versuchen"))}
        }
        is WalkingDirectionsState.Ready->{
            val sections=remember(state.route) {state.route.sections()}
            var passed by remember(id,state.route) {mutableStateOf<Set<Int>>(emptySet())}
            LaunchedEffect(presence,state.route,completed) {
                passed=if(completed) sections.indices.toSet() else passed+passedWalkingSections(state.route,presence,System.currentTimeMillis())
            }
            sections.forEachIndexed {index,section->
                val parts=remember(state.route,index) {state.route.mapParts(sections,index)}
                val title=walkingSectionTitle(section)
                AnimatedVisibility(index !in passed,enter=fadeIn(calmSpec(tween(200))),exit=fadeOut(calmSpec(tween(240)))+shrinkVertically(calmSpec(tween(300)))) {
                WalkingInstructionRow("$id-$index",color,active,phase,map={
                    WalkingRouteMap(parts,Modifier.width(104.dp).testTag("walking-direction-node-$id-$index"),circular=true,
                        turn=section.turn,onClick={selected=title to parts},tag="walking-mini-map-$id-$index")
                }) {
                    Text(title,style=MaterialTheme.typography.bodyLarge,maxLines=2,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Text(walkingSectionDistance(section.meters),style=MaterialTheme.typography.labelLarge,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                }
            }
        }
    }
    selected?.let {(title,parts)->WalkingRouteMapDialog(title,parts,onDismiss={selected=null})}
}

/** A common axis keeps origin/destination stops, larger maps and transfer nodes connected. */
@Composable private fun WalkingInstructionRow(id:String,color:Color,active:Boolean,phase:()->Float,
    map:(@Composable ()->Unit)?=null,content:@Composable ColumnScope.()->Unit) {
    val quiet=LocalAppSettings.current.reduceMotion
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).heightIn(min=if(map==null) 76.dp else 144.dp).testTag("walking-instruction-$id")) {
        Box(Modifier.width(116.dp).fillMaxHeight(),contentAlignment=Alignment.TopCenter) {
            Canvas(Modifier.fillMaxSize()) {
                val x=size.width/2
                val path=Path().apply {for(i in 0..40) {
                    val y=size.height*i/40f
                    val dx=if(active && !quiet) sin(y/size.height*2*PI+phase()).toFloat()*sin(y/size.height*PI).toFloat()*1.7.dp.toPx() else 0f
                    if(i==0) moveTo(x+dx,y) else lineTo(x+dx,y)
                }}
                val railWidth=TIMELINE_STROKE_WIDTH_PX+(if(active) 3.dp.toPx() else 0f)
                if(active && !quiet) drawPath(path,color.copy(alpha=.14f),style=Stroke(railWidth+14.dp.toPx(),cap=StrokeCap.Round))
                drawPath(path,color,style=Stroke(railWidth,cap=StrokeCap.Round))
            }
            map?.invoke()
        }
        Column(Modifier.weight(1f).padding(top=if(map==null) 6.dp else 22.dp,bottom=22.dp),verticalArrangement=Arrangement.spacedBy(8.dp),content=content)
    }
}
