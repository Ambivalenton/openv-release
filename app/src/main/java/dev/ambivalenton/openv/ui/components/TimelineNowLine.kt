// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.localization.tr
import dev.ambivalenton.openv.ui.theme.*
import java.time.Instant
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.sin

/** Exactly the projection used by the chart's axis, including its diagonal pan and zoom. */
internal fun timelineNowY(time:Long,origin:Long,minutePx:Float,paddingPx:Float,
    horizontalPx:Float,alignmentPx:Float,scrollPx:Int):Float =
    paddingPx+((time-origin)/60_000.0*minutePx).toFloat()-
        horizontalPx*TIMELINE_DIAGONAL_RATIO+alignmentPx-scrollPx

/** Last drawing layer: leaves every tap and swipe to the chart beneath it. */
@Composable internal fun TimelineNowLine(origin:Long,minuteDp:Float,padding:Dp,vertical:ScrollState,
    horizontal:()->Float,alignment:()->Float) {
    val density=LocalDensity.current
    TimelineNowLine {now->timelineNowY(now,origin,minuteDp*density.density,with(density){padding.toPx()},horizontal(),alignment(),vertical.value)}
}

/** The design the now line belongs to: the bar chart's style, or the app's construction when it follows the app. */
@Composable internal fun resolvedNowLine(prefs:NowLinePreferences=LocalAppSettings.current.nowLine):ResolvedNowLine =
    prefs.resolve(LocalTimelineTheme.current.style,LocalAppVisuals.current.language)

/** The ink of the now line (Build 114: accent, second colour or text colour). */
@Composable internal fun nowLineInk(prefs:NowLinePreferences):Color {
    val scheme=MaterialTheme.colorScheme
    val glass=(LocalAppVisuals.current.theme?.glassOpacity ?: 1f).coerceIn(.3f,1f)
    return when(prefs.color) {NowColor.ACCENT->scheme.primary;NowColor.SECONDARY->scheme.secondary;NowColor.TEXT->scheme.onSurface}.copy(alpha=glass)
}

/** The now line at [yOf] (px for a time), read while drawing, so it glides with the chart. */
@Composable internal fun TimelineNowLine(yOf:(Long)->Float) {
    val prefs=LocalAppSettings.current.nowLine
    if(!prefs.visible) return
    val now=rememberWallClock(tickMillis=1000).toInstant().toEpochMilli()
    var height by remember {mutableIntStateOf(0)}
    NowLineRow(resolvedNowLine(prefs),prefs,now,nowLineInk(prefs),Modifier.fillMaxWidth().graphicsLayer {
        translationY=yOf(now)-height/2f
    }.onSizeChanged {height=it.height}.padding(end=20.dp).testTag("timeline-now-line"))
}

/** The line and its clock; also the live sample in the settings. */
@Composable internal fun NowLineRow(look:ResolvedNowLine,prefs:NowLinePreferences,now:Long,ink:Color,modifier:Modifier=Modifier) {
    val weight=prefs.weight.factor
    Row(modifier,verticalAlignment=Alignment.CenterVertically) {
        Canvas(Modifier.weight(1f).height((10f*weight.coerceAtLeast(1f)).dp).clearAndSetSemantics {}) {
            drawNowStroke(look.stroke,ink,2.5.dp.toPx()*weight)
        }
        if(look.clock!=NowClockStyle.NONE) {
            Spacer(Modifier.width(12.dp))
            NowClock(look.clock,now,ink,prefs.seconds,look.stroke)
        }
    }
}

/** One now line across the whole width, centred vertically; [t] is the line's thickness in px. */
internal fun DrawScope.drawNowStroke(stroke:NowStroke,ink:Color,t:Float) {
    val y=size.height/2f;val w=size.width
    val shade=Color.Black.copy(alpha=.14f*ink.alpha)
    fun line(color:Color,width:Float,dy:Float=0f,effect:PathEffect?=null,cap:StrokeCap=StrokeCap.Round)=
        drawLine(color,Offset(0f,y+dy),Offset(w,y+dy),width,cap,effect)
    when(stroke) {
        NowStroke.AUTO,NowStroke.DASHED-> {
            val dash=PathEffect.dashPathEffect(floatArrayOf(22.dp.toPx(),8.dp.toPx()+t))
            line(shade,t+1.5.dp.toPx(),1.5.dp.toPx(),dash);line(ink,t,0f,dash)
        }
        NowStroke.SOLID->{line(shade,t+1.dp.toPx(),1.dp.toPx());line(ink,t)}
        NowStroke.DOTTED-> {
            val step=t*3.2f;var x=t
            while(x<w) {drawCircle(shade,t*.62f,Offset(x,y+1.dp.toPx()));drawCircle(ink,t*.62f,Offset(x,y));x+=step}
        }
        NowStroke.DASH_DOT-> {
            val dash=PathEffect.dashPathEffect(floatArrayOf(16.dp.toPx(),5.dp.toPx()+t,.01f,5.dp.toPx()+t))
            line(shade,t+1.dp.toPx(),1.dp.toPx(),dash);line(ink,t,0f,dash)
        }
        NowStroke.DOUBLE->{line(ink,t*.55f,-t*.9f,cap=StrokeCap.Butt);line(ink,t*.55f,t*.9f,cap=StrokeCap.Butt)}
        NowStroke.RAIL-> {
            val gauge=t*1.5f;var x=t
            while(x<w) {drawLine(ink.copy(alpha=ink.alpha*.55f),Offset(x,y-gauge*1.45f),Offset(x,y+gauge*1.45f),t*.55f);x+=7.dp.toPx()}
            line(ink,t*.5f,-gauge,cap=StrokeCap.Butt);line(ink,t*.5f,gauge,cap=StrokeCap.Butt)
        }
        NowStroke.WAVE-> {
            val path=Path();val length=14.dp.toPx();val amp=t*1.1f;var x=0f
            path.moveTo(0f,y)
            while(x<=w) {path.lineTo(x,y+amp*sin(x/length*2f*PI.toFloat()));x+=1.5f}
            drawPath(path,shade,style=Stroke(t*.9f,cap=StrokeCap.Round),alpha=1f)
            drawPath(path,ink,style=Stroke(t*.8f,cap=StrokeCap.Round))
        }
        NowStroke.GLOW-> {
            line(ink.copy(alpha=ink.alpha*.12f),t*5f);line(ink.copy(alpha=ink.alpha*.26f),t*2.6f)
            line(lerp(ink,Color.White,.35f).copy(alpha=ink.alpha),t*.9f)
        }
        NowStroke.FADE->drawLine(Brush.horizontalGradient(listOf(ink.copy(alpha=0f),ink.copy(alpha=ink.alpha*.55f),ink),0f,w),
            Offset(0f,y),Offset(w,y),t,StrokeCap.Round)
        NowStroke.ARROWS-> {
            val step=12.dp.toPx();val s=t*1.5f;var x=step/2
            while(x<w) {
                val p=Path().apply {moveTo(x-s*.6f,y-s);lineTo(x+s*.6f,y);lineTo(x-s*.6f,y+s)}
                drawPath(p,ink,style=Stroke(t*.65f,cap=StrokeCap.Round,join=StrokeJoin.Round));x+=step
            }
        }
        NowStroke.PIXEL-> {
            val unit=(t*1.2f).coerceAtLeast(2f);var x=0f
            while(x<w) {
                drawRect(shade,Offset(x+unit*.5f,y-unit/2+unit*.5f),Size(unit*2,unit))
                drawRect(ink,Offset(x,y-unit/2),Size(unit*2,unit));x+=unit*3
            }
        }
        NowStroke.STITCH-> {
            val step=9.dp.toPx();val len=6.dp.toPx();var x=t
            while(x<w) {
                drawLine(shade,Offset(x,y+t*.6f+1.dp.toPx()),Offset(x+len,y-t*.6f+1.dp.toPx()),t*.75f,StrokeCap.Round)
                drawLine(ink,Offset(x,y+t*.6f),Offset(x+len,y-t*.6f),t*.75f,StrokeCap.Round);x+=step
            }
        }
    }
}

/** The clock at the end of the line: digits, a small dial or both, in the look of the line. */
@Composable internal fun NowClock(style:NowClockStyle,now:Long,ink:Color,seconds:Boolean,stroke:NowStroke) {
    val time=Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault())
    val text=String.format(java.util.Locale.ROOT,if(seconds)"%02d:%02d:%02d" else "%02d:%02d",time.hour,time.minute,time.second)
    val density=LocalDensity.current
    Row(Modifier.clearAndSetSemantics {contentDescription=tr("Jetzt %1\$s",clockTime(now))},
        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
        if(style==NowClockStyle.ANALOG || style==NowClockStyle.BOTH) {
            val face=MaterialTheme.colorScheme.surface
            val hand=MaterialTheme.colorScheme.onSurface
            val size=with(density) {(26.dp.toPx()*fontScale.coerceIn(1f,1.4f)).toDp()}
            Canvas(Modifier.size(size).testTag("timeline-now-analog")) {
                drawAnalogClock(time.hour,time.minute,time.second,seconds,face,hand,ink)
            }
        }
        if(style==NowClockStyle.DIGITAL || style==NowClockStyle.BOTH) {
            val pixel=stroke==NowStroke.PIXEL
            val glow=stroke==NowStroke.GLOW
            Text(text,Modifier.testTag("timeline-now-clock"),color=ink,maxLines=1,softWrap=false,
                style=MaterialTheme.typography.labelLarge.copy(fontWeight=FontWeight.Bold,
                    fontFamily=if(pixel || LocalAppVisuals.current.language==DesignLanguage.TERMINAL) FontFamily.Monospace else MaterialTheme.typography.labelLarge.fontFamily,
                    shadow=Shadow(if(glow) ink.copy(alpha=.8f) else ink.copy(alpha=.35f),
                        if(glow) Offset.Zero else Offset(0f,with(density) {2.dp.toPx()}),with(density) {(if(glow) 8.dp else 3.dp).toPx()})))
        }
    }
}

/** A small station dial: twelve marks, hour and minute hand, an optional second hand in the line's colour. */
internal fun DrawScope.drawAnalogClock(hour:Int,minute:Int,second:Int,showSeconds:Boolean,face:Color,hand:Color,ink:Color) {
    val r=size.minDimension/2f;val c=Offset(size.width/2f,size.height/2f)
    drawCircle(Color.Black.copy(alpha=.14f),r*.96f,c+Offset(0f,r*.08f))
    drawCircle(face,r*.94f,c)
    drawCircle(ink,r*.94f,c,style=Stroke(r*.12f))
    for(i in 0 until 12) rotate(i*30f,c) {
        val long=i%3==0
        drawLine(hand,Offset(c.x,c.y-r*.74f),Offset(c.x,c.y-r*(if(long).54f else .64f)),r*(if(long).1f else .06f))
    }
    val minutes=minute+second/60f
    rotate(((hour%12)+minutes/60f)*30f,c) {drawLine(hand,c,Offset(c.x,c.y-r*.42f),r*.14f,StrokeCap.Round)}
    rotate(minutes*6f,c) {drawLine(hand,c,Offset(c.x,c.y-r*.64f),r*.09f,StrokeCap.Round)}
    if(showSeconds) rotate(second*6f,c) {drawLine(ink,Offset(c.x,c.y+r*.16f),Offset(c.x,c.y-r*.7f),r*.05f,StrokeCap.Round)}
    drawCircle(ink,r*.1f,c)
}
