// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import dev.ambivalenton.openv.R
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.data.repository.WalkingMapTileSource
import dev.ambivalenton.openv.localization.tr
import dev.ambivalenton.openv.ui.theme.LocalAppSettings
import dev.ambivalenton.openv.ui.tutorial.LocalTutorialSurfaceActive
import kotlinx.coroutines.*
import kotlin.math.*

val LocalWalkingMapTiles=staticCompositionLocalOf<WalkingMapTileSource?> {null}
/** Build 116: the bundled stop catalog, for stop signs on maps (no network). */
val LocalStopCatalog=staticCompositionLocalOf<dev.ambivalenton.openv.data.repository.StaticStopCatalog?> {null}

/** Five physical pixels, regardless of Android density, as requested. */
const val WALKING_MAP_CONTOUR_PX=5f

@Composable fun WalkingRouteMap(parts:List<WalkingMapPart>,modifier:Modifier=Modifier,circular:Boolean=false,
    turn:WalkingTurn=WalkingTurn.FORWARD,onClick:(()->Unit)?=null,interactive:Boolean=false,tag:String="walking-route-map",compactAttribution:Boolean=false,
    /** Build 116: public transport first – the base map is muted (its shop symbols fade), stops of the local catalog get stop signs. */
    transit:Boolean=false,
    /** Build 116: the places of a change: the onward platform highlighted, the arrival quietly. */
    arrivalPoint:Location?=null,departurePoint:Location?=null) {
    var viewport by remember {mutableStateOf(IntSize.Zero)}
    var camera by remember(parts) {mutableStateOf<WalkingMapViewport?>(null)}
    var visible by remember {mutableStateOf(false)}
    val source=LocalWalkingMapTiles.current;val template=LocalAppSettings.current.mapTileTemplate
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val density=LocalDensity.current.density
    val surfaceActive=LocalTutorialSurfaceActive.current
    val window=LocalWindowInfo.current
    val tiles=remember(template) {mutableStateMapOf<MapTileId,ImageBitmap>()}
    var error by remember(parts,template) {mutableStateOf(false)}
    val fit=remember(parts,viewport,density) {fitWalkingMap(parts,(viewport.width/density).roundToInt(),(viewport.height/density).roundToInt())}
    val view=camera ?: fit
    val latestView by rememberUpdatedState(view)
    val requested=remember(view,viewport,density) {view.visibleTiles((viewport.width/density).roundToInt(),(viewport.height/density).roundToInt())}
    LaunchedEffect(source,template,requested,visible,surfaceActive) {
        if(!visible) {tiles.clear();return@LaunchedEffect}
        if(!surfaceActive || source==null || viewport.width<=0) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            error=false
            requested.forEach {tile->
                if(tile !in tiles) try {tiles[tile]=source.tile(tile,template)}
                catch(e:CancellationException) {throw e} catch(_:Exception) {error=true}
            }
            // A composed full-screen map may pan for a long time; release offscreen bitmaps.
            if(tiles.size>48) tiles.keys.toList().filterNot {it in requested}.take(tiles.size-36).forEach {tiles.remove(it)}
            awaitCancellation()
        }
    }
    val color=MaterialTheme.colorScheme.primary;val background=MaterialTheme.colorScheme.surfaceContainer
    val tint=MaterialTheme.colorScheme.surfaceContainerHigh
    val arrow=MaterialTheme.colorScheme.onSurface
    val shapeKey=remember(parts) {parts.hashCode()}
    // Build 116: stops around the change from the bundled catalog (no network request).
    val catalog=LocalStopCatalog.current
    val centre=departurePoint ?: arrivalPoint ?: parts.flatMap {it.points}.firstOrNull()
    val nearbyStops by produceState(emptyList<Location>(),transit,catalog,centre?.latitude,centre?.longitude) {
        value=if(transit && catalog!=null && centre?.hasCoordinates==true) catalog.nearby(centre.latitude!!,centre.longitude!!,350) else emptyList()
    }
    val muted=remember(transit) {if(transit) androidx.compose.ui.graphics.ColorFilter.colorMatrix(
        androidx.compose.ui.graphics.ColorMatrix().apply {setToSaturation(.18f)}) else null}
    val stopFill=Color(0xFFF4C400);val stopInk=Color(0xFF1C6B35)
    val accentPoint=MaterialTheme.colorScheme.primary
    Column(modifier) {
        Box(if(circular) Modifier.fillMaxWidth().aspectRatio(1f) else Modifier.weight(1f).fillMaxWidth()) {
            val input=if(interactive) Modifier.pointerInput(shapeKey) {
                var scale=1f
                detectTransformGestures {_,pan,zoom,_->
                    val before=camera ?: latestView;val pixels=256.0*density*(1 shl before.zoom)
                    scale*=zoom
                    var z=before.zoom
                    if(scale>1.5f) {z=(z+1).coerceAtMost(19);scale=1f}
                    if(scale<.67f) {z=(z-1).coerceAtLeast(3);scale=1f}
                    camera=before.copy(x=before.x-pan.x/pixels,y=(before.y-pan.y/pixels).coerceIn(0.0,1.0),zoom=z)
                }
            } else Modifier
            Canvas(Modifier.fillMaxSize().onSizeChanged {viewport=it}.onGloballyPositioned {
                val bounds=it.boundsInWindow();visible=bounds.width>1 && bounds.height>1 && bounds.bottom>0 && bounds.top<window.containerSize.height
            }.then(input).then(if(onClick!=null) Modifier.clickable(onClick=onClick) else Modifier)
                .testTag(tag).semantics {contentDescription=tr("Karte des Fußweg-Abschnitts");if(interactive) stateDescription=tr("Zoom %1\$s",view.zoom)}) {
                val contour=Path().apply {if(circular) addOval(Rect(2.5f,2.5f,size.width-2.5f,size.height-2.5f)) else addRect(Rect(Offset.Zero,size))}
                clipPath(contour) {
                    drawRect(background)
                    val n=1 shl view.zoom;val tilePixels=256.0*density;val world=tilePixels*n
                    val left=view.x*world-size.width/2;val top=view.y*world-size.height/2
                    val heading=if(circular && turn!=WalkingTurn.FORWARD)walkingIncomingBearing(parts)?:0f else 0f
                    rotate(-heading,Offset(size.width/2,size.height/2)) {
                    for((id,bitmap) in tiles) {
                        if(id.z!=view.zoom) continue
                        var x=id.x*tilePixels-left
                        while(x < -world/2) x+=world
                        while(x > world/2) x-=world
                        val y=id.y*tilePixels-top
                        if(x+tilePixels>=0 && y+tilePixels>=0 && x<size.width && y<size.height)
                            drawImage(bitmap,srcSize=IntSize(bitmap.width,bitmap.height),dstOffset=IntOffset(x.roundToInt(),y.roundToInt()),dstSize=IntSize(ceil(tilePixels).toInt()+1,ceil(tilePixels).toInt()+1),
                                alpha=if(transit) .82f else 1f,colorFilter=muted)
                    }
                    for(part in parts) {
                        val path=Path();var valid=false
                        part.points.mapNotNull(::mercatorPoint).forEachIndexed {index,p->
                            val x=(p.x*world-left).toFloat();val y=(p.y*world-top).toFloat()
                            if(index==0) path.moveTo(x,y) else {path.lineTo(x,y);valid=true}
                        }
                        if(valid) {
                            drawPath(path,Color.White.copy(alpha=.92f),style=Stroke(if(circular) 6.dp.toPx() else 9.dp.toPx(),cap=StrokeCap.Round,join=StrokeJoin.Round))
                            drawPath(path,color.copy(alpha=if(part.incoming) .62f else 1f),style=Stroke(if(circular) 3.5.dp.toPx() else 5.dp.toPx(),cap=StrokeCap.Round,join=StrokeJoin.Round))
                        }
                    }
                    }
                    // Build 116: stop signs (yellow disc, green H) for the stops around, then the two platforms of the change.
                    fun at(place:Location)=mercatorPoint(place)?.let {Offset((it.x*world-left).toFloat(),(it.y*world-top).toFloat())}
                    val sign=7.dp.toPx()
                    for(stop in nearbyStops) at(stop)?.let {c->
                        drawCircle(Color.Black.copy(alpha=.18f),sign+1.dp.toPx(),c+Offset(0f,1.dp.toPx()))
                        drawCircle(stopFill,sign,c);drawCircle(stopInk,sign,c,style=Stroke(1.5.dp.toPx()))
                        val h=sign*.55f;val w=sign*.42f;val stroke=1.6.dp.toPx()
                        drawLine(stopInk,Offset(c.x-w,c.y-h),Offset(c.x-w,c.y+h),stroke);drawLine(stopInk,Offset(c.x+w,c.y-h),Offset(c.x+w,c.y+h),stroke)
                        drawLine(stopInk,Offset(c.x-w,c.y),Offset(c.x+w,c.y),stroke)
                    }
                    arrivalPoint?.let(::at)?.let {c->drawCircle(Color.White,6.dp.toPx(),c);drawCircle(color.copy(alpha=.7f),6.dp.toPx(),c,style=Stroke(2.5.dp.toPx()))}
                    departurePoint?.let(::at)?.let {c->
                        drawCircle(accentPoint.copy(alpha=.22f),15.dp.toPx(),c)
                        drawCircle(Color.White,9.dp.toPx(),c);drawCircle(accentPoint,7.dp.toPx(),c)
                    }
                    if(circular && turn!=WalkingTurn.FORWARD) {
                        drawRect(tint.copy(alpha=.6f));drawDirectionArrow(turn,arrow,Offset(size.width/2,size.height/2),size.minDimension*.32f)
                    }
                }
                if(circular) drawPath(contour,color,style=Stroke(WALKING_MAP_CONTOUR_PX))
            }
            if(interactive) Column(Modifier.align(Alignment.TopEnd).padding(10.dp).width(48.dp)
                .testTag("walking-map-controls"),horizontalAlignment=Alignment.End,verticalArrangement=Arrangement.spacedBy(8.dp)) {
                FilledTonalIconButton({camera=view.copy(zoom=(view.zoom+1).coerceAtMost(19))},
                    modifier=Modifier.size(48.dp).testTag("walking-map-zoom-in")) {AppIcon(R.drawable.ic_add,tr("Vergrößern"))}
                FilledTonalIconButton({camera=view.copy(zoom=(view.zoom-1).coerceAtLeast(3))},
                    modifier=Modifier.size(48.dp).testTag("walking-map-zoom-out")) {AppIcon(R.drawable.ic_remove,tr("Verkleinern"))}
                FilledTonalIconButton({camera=null},modifier=Modifier.size(48.dp).testTag("walking-map-recenter")) {
                    AppIcon(R.drawable.ic_map_fit,tr("Abschnitt anzeigen"))
                }
            }
        }
        val uri=LocalUriHandler.current
        if(circular) CurvedMapAttribution() else {
            val pill=MaterialTheme.colorScheme.surface.copy(alpha=.94f)
            OutlinedMapAttribution(Modifier.align(Alignment.CenterHorizontally).padding(top=3.dp)
                .background(pill,androidx.compose.foundation.shape.RoundedCornerShape(50))
                .clickable {uri.openUri("https://www.openstreetmap.org/copyright")}.padding(horizontal=8.dp,vertical=3.dp),
                style=MaterialTheme.typography.labelSmall.copy(fontSize=if(compactAttribution)10.sp else 12.sp),background=pill)
        }
        if(error) Text(tr("Kartenhintergrund nicht verfügbar"),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable fun WalkingRouteMapDialog(title:String,parts:List<WalkingMapPart>,transit:Boolean=false,arrivalPoint:Location?=null,
    departurePoint:Location?=null,onDismiss:()->Unit) {
    AppDialog(onDismiss,DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxWidth(.96f).fillMaxHeight(.88f).testTag("walking-map-dialog"),shape=MaterialTheme.shapes.large) {
            Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    FittedText(title,Modifier.weight(1f),style=MaterialTheme.typography.titleLarge)
                    IconButton(onDismiss) {AppIcon(R.drawable.ic_close,tr("Karte schließen"))}
                }
                WalkingRouteMap(parts,Modifier.weight(1f).fillMaxWidth(),interactive=true,tag="walking-map-expanded",
                    transit=transit,arrivalPoint=arrivalPoint,departurePoint=departurePoint)
            }
        }
    }
}

internal fun DrawScope.drawDirectionArrow(turn:WalkingTurn,color:Color,center:Offset,radius:Float) {
    val p=Path();fun move(x:Float,y:Float)=p.moveTo(center.x+x*radius,center.y+y*radius)
    fun line(x:Float,y:Float)=p.lineTo(center.x+x*radius,center.y+y*radius)
    val side=if(turn in setOf(WalkingTurn.LEFT,WalkingTurn.SLIGHT_LEFT)) -1 else 1
    when(turn) {
        WalkingTurn.LEFT,WalkingTurn.RIGHT->{move(0f,.8f);line(0f,0f);p.quadraticTo(center.x,center.y-radius*.5f,center.x+side*radius*.5f,center.y-radius*.5f);line(side*.9f,-.5f);move(side*.5f,-.9f);line(side*.9f,-.5f);line(side*.5f,-.1f)}
        WalkingTurn.SLIGHT_LEFT,WalkingTurn.SLIGHT_RIGHT->{move(0f,.8f);line(0f,0f);line(side*.8f,-.8f);move(side*.2f,-.8f);line(side*.8f,-.8f);line(side*.8f,-.2f)}
        WalkingTurn.UTURN->{move(-.5f,.8f);line(-.5f,-.2f);p.cubicTo(center.x-radius*.5f,center.y-radius,center.x+radius*.5f,center.y-radius,center.x+radius*.5f,center.y-radius*.2f);line(.5f,.4f);move(.1f,.1f);line(.5f,.5f);line(.9f,.1f)}
        WalkingTurn.STAIRS->{move(-.8f,.8f);line(-.3f,.8f);line(-.3f,.2f);line(.25f,.2f);line(.25f,-.3f);line(.8f,-.3f);line(.8f,-.8f)}
        WalkingTurn.ELEVATOR->{move(-.3f,.8f);line(-.3f,-.8f);move(-.7f,-.4f);line(-.3f,-.8f);line(.1f,-.4f);move(.5f,-.8f);line(.5f,.8f);move(.1f,.4f);line(.5f,.8f);line(.9f,.4f)}
        WalkingTurn.ROUNDABOUT->{p.addArc(Rect(center-Offset(radius*.6f,radius*.6f),center+Offset(radius*.6f,radius*.6f)),45f,280f);move(.1f,-.9f);line(.6f,-.6f);line(.1f,-.25f)}
        else->{move(0f,.8f);line(0f,-.8f);move(-.5f,-.3f);line(0f,-.8f);line(.5f,-.3f)}
    }
    drawPath(p,color,style=Stroke(radius*.14f,cap=StrokeCap.Round,join=StrokeJoin.Round))
}
