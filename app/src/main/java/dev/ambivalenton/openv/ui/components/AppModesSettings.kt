// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.localization.tr
import dev.ambivalenton.openv.ui.theme.LocalAppSettings
import dev.ambivalenton.openv.ui.theme.appCorner
import kotlin.math.*

/**
 * The page "Modi & Verhalten" (formerly the fold-out "Modi & Extras"). Every switch and
 * setting has a frame of its own with a small picture of what it does; [footer] (backup) comes last.
 * Changing one mode never overwrites another choice.
 */
@Composable fun AppModesSettings(settings:AppSettings,update:((AppSettings)->AppSettings)->Unit,place:Location?=null,
    dayNightAnchor:String?=null,onAnchorShown:()->Unit={},footer:@Composable ColumnScope.()->Unit={}) {
    Column(Modifier.fillMaxWidth().testTag("app-modes"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        MonochromeCard(settings.monochrome) {v->update {it.copy(monochrome=v)}}
        ModeSwitchCard(tr("Ruhemodus"),tr("Weniger Bewegung, keine verspielten Effekte und ein eigenes ruhiges Design."),
            settings.quietMode,{v->update {it.withQuietMode(v)}},"mode-quiet") {on,m->QuietPicture(on,m)}
        ModeSwitchCard(tr("Stromsparmodus"),tr("Schont den Akku: Standort nur etwa alle fünf Minuten, Fahrplanzeiten der Anbieter, keine Animationen und Wetterabfragen."),
            settings.powerSaving,{v->update {it.copy(powerSaving=v)}},"mode-power") {on,m->BatteryPicture(on,m)}
        ModeSwitchCard(tr("Minimalismus"),tr("Nur das Wesentliche: blendet Hinweise, Infotexte, Suchchroniken und den Kopftext über dem Balkendiagramm aus. Unter Abfahrten füllt die Anzeigetafel den Bildschirm."),
            settings.minimalistMode,{v->update {it.copy(minimalistMode=v)}},"mode-minimal") {on,m->MinimalPicture(on,m)}
        // Search and plan only, without the pill, the other pages and any position request.
        ModeSwitchCard(tr("Planermodus"),tr("Nur Suchen, Favoriten und Verlauf auf einer Seite, ohne Navigationsleiste, Übersicht und Abfahrten. Die App fragt nie nach deinem Standort: Den Start gibst du selbst ein, die Reisebegleitung folgt allein den Fahrplandaten."),
            settings.plannerMode,{v->update {it.copy(plannerMode=v)}},"mode-planner") {on,m->PlannerPicture(on,m)}
        // 2.10: when the day and the night design change, below "Minimalismus".
        val dayNightFrame=remember {androidx.compose.foundation.relocation.BringIntoViewRequester()}
        LaunchedEffect(dayNightAnchor) {if(dayNightAnchor=="day-night") {kotlinx.coroutines.delay(260);runCatching {dayNightFrame.bringIntoView()};onAnchorShown()}}
        Box(Modifier.bringIntoViewRequester(dayNightFrame)) {DayNightFrame(settings,update,place)}
        // Build 115: wordless hints in the first hours of use (pulses, hold rings, small gesture films).
        ModeSwitchCard(tr("Bedienhinweise"),tr("In den ersten Stunden zeigen kleine Animationen, was sich antippen, halten oder wischen lässt. Jeder Hinweis endet, sobald du die Geste einmal genutzt hast."),
            settings.gestureHintsEnabled,{v->update {it.copy(gestureHintsEnabled=v)}},"mode-hints") {on,m->HintPicture(on,m)}
        ModeFrame("mode-bubble",{m->BubblePicture(settings.pinnedBubble,m)}) {PinnedBubbleSetting(settings,update)}
        ModeFrame("mode-long-press",{m->LongPressPicture(m)}) {
            ChartLongPressSetting(settings.chartLongPress) {action->update {it.copy(chartLongPress=action)}}
        }
        footer()
    }
}

@Composable internal fun ChartLongPressSetting(selected:ChartLongPressAction,onSelect:(ChartLongPressAction)->Unit) {ChartLongPressBubbleSetting(selected,onSelect,framed=false)}

/** A setting in its own frame: the picture on the left, the setting itself beside or below it. */
@Composable internal fun ModeFrame(tag:String,picture:@Composable (Modifier)->Unit,content:@Composable ColumnScope.()->Unit) {
    val colors=MaterialTheme.colorScheme
    Surface(Modifier.fillMaxWidth().testTag(tag),shape=appCorner(24.dp),color=colors.surfaceContainerLow,
        border=BorderStroke(1.dp,colors.outlineVariant)) {
        Row(Modifier.padding(14.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            picture(Modifier.padding(top=4.dp).size(64.dp))
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp),content=content)
        }
    }
}

/**
 * The one frame of the settings pages (as "Strecken als Liste zeigen" and the frames of
 * "Modi & Verhalten"): the theme's corners, the low container colour and a thin outline.
 */
@Composable internal fun SettingsCard(modifier:Modifier=Modifier,content:@Composable ()->Unit) {
    val colors=MaterialTheme.colorScheme
    Surface(modifier.fillMaxWidth(),shape=appCorner(24.dp),color=colors.surfaceContainerLow,contentColor=colors.onSurface,
        border=BorderStroke(1.dp,colors.outlineVariant),content=content)
}

/** A switch in its own frame; the whole frame toggles it. */
@Composable internal fun ModeSwitchCard(title:String,description:String,checked:Boolean,onChange:(Boolean)->Unit,tag:String,
    container:Color=MaterialTheme.colorScheme.surfaceContainerLow,ink:Color=MaterialTheme.colorScheme.onSurface,
    border:Color=MaterialTheme.colorScheme.outlineVariant,modifier:Modifier=Modifier,picture:@Composable (Boolean,Modifier)->Unit) {
    Surface(modifier.fillMaxWidth(),shape=appCorner(24.dp),color=container,contentColor=ink,border=BorderStroke(1.dp,border)) {
        Row(Modifier.fillMaxWidth().testTag(tag).toggleable(checked,role=Role.Switch,onValueChange=onChange).padding(14.dp),
            verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            picture(checked,Modifier.size(64.dp))
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)) {
                FittedText(title,style=MaterialTheme.typography.titleMedium,color=ink)
                Text(description,style=MaterialTheme.typography.bodySmall,color=ink.copy(alpha=.78f))
            }
            Switch(checked,null)
        }
    }
}

/** Grey shades for everything drawn inside (the frame stays black and white in every design). */
private fun Modifier.greyscale():Modifier=drawWithContent {
    val paint=Paint().apply {colorFilter=ColorFilter.colorMatrix(ColorMatrix().apply {setToSaturation(0f)})}
    drawIntoCanvas {canvas->
        canvas.saveLayer(Rect(Offset.Zero,size),paint)
        drawContent()
        canvas.restore()
    }
}

@Composable internal fun MonochromeCard(checked:Boolean,
    description:String=tr("Zeigt die ganze App in Graustufen, zum Beispiel für weniger Ablenkung. Dein Farbdesign bleibt gespeichert."),
    onChange:(Boolean)->Unit) {
    // Paper and ink: black on white in light designs, white on black in dark ones.
    val dark=MaterialTheme.colorScheme.background.luminance()<.5f
    val paper=if(dark) Color.Black else Color.White;val ink=if(dark) Color.White else Color.Black
    MaterialTheme(colorScheme=MaterialTheme.colorScheme.copy(primary=ink,onPrimary=paper,primaryContainer=ink,surfaceContainerHighest=paper,
        outline=ink,onSurface=ink,onSurfaceVariant=ink)) {
        ModeSwitchCard(tr("Schwarz/Weiß"),description,
            checked,onChange,"mode-monochrome",paper,ink,ink,Modifier.greyscale()) {_,m->MonochromePicture(ink,paper,m)}
    }
}

/** Half black, half white, with a strip of greys below. */
@Composable private fun MonochromePicture(ink:Color,paper:Color,modifier:Modifier) = Canvas(modifier) {
    val r=size.minDimension*.32f;val c=Offset(size.width/2,size.height*.4f)
    drawArc(ink,90f,180f,true,c-Offset(r,r),Size(r*2,r*2))
    drawArc(paper,-90f,180f,true,c-Offset(r,r),Size(r*2,r*2))
    drawCircle(ink,r,c,style=Stroke(1.5.dp.toPx()))
    drawCircle(paper,r*.28f,c-Offset(r*.5f,0f));drawCircle(ink,r*.28f,c+Offset(r*.5f,0f))
    val steps=6;val w=size.width*.8f/steps;val top=size.height*.82f
    for(i in 0 until steps) {
        val v=i/(steps-1f);drawRect(lerp(ink,paper,v),Offset(size.width*.1f+i*w,top),Size(w,size.height*.12f))
    }
    drawRect(ink,Offset(size.width*.1f,top),Size(w*steps,size.height*.12f),style=Stroke(1.dp.toPx()))
}

/** Three dots hop while the app is lively; in quiet mode they lie still in a row under a moon. */
/** A finger rests on a dot while a ring fills around it; off, the dot alone. */
@Composable private fun HintPicture(on:Boolean,modifier:Modifier) {
    val colors=MaterialTheme.colorScheme
    val still=!on||LocalAppSettings.current.reduceMotion
    val t=if(still) .7f else rememberInfiniteTransition(label="hint-picture").animateFloat(0f,1f,
        infiniteRepeatable(tween(2200,easing=LinearEasing)),label="hint-phase").value
    Canvas(modifier) {
        val c=Offset(size.width/2,size.height/2);val r=size.minDimension*.16f
        drawCircle(colors.primary,r,c)
        if(on) {
            val fill=(t/.7f).coerceIn(0f,1f);val pop=((t-.7f)/.3f).coerceIn(0f,1f)
            drawArc(colors.primary.copy(alpha=1f-pop),-90f,360f*fill,false,Offset(c.x-r*1.9f,c.y-r*1.9f),
                androidx.compose.ui.geometry.Size(r*3.8f,r*3.8f),style=Stroke(2.5.dp.toPx(),cap=StrokeCap.Round))
            if(pop>0f) drawCircle(colors.primary.copy(alpha=.5f*(1f-pop)),r*(1.9f+pop),c,style=Stroke(2.dp.toPx()))
        }
    }
}

@Composable internal fun QuietPicture(on:Boolean,modifier:Modifier) {
    val colors=MaterialTheme.colorScheme
    val calm=on||LocalAppSettings.current.reduceMotion
    val settle by animateFloatAsState(if(on) 1f else 0f,tween(500,easing=FastOutSlowInEasing),label="quiet-settle")
    val phase=if(calm) 0f else rememberInfiniteTransition(label="quiet-dots").animateFloat(0f,1f,
        infiniteRepeatable(tween(1100,easing=LinearEasing)),label="quiet-phase").value
    Canvas(modifier) {
        val base=size.height*.72f;val r=size.minDimension*.08f
        for(i in 0..2) {
            val hop=(1-settle)*abs(sin((phase+i/3f)*PI.toFloat()))*size.height*.32f
            drawCircle(colors.primary,r,Offset(size.width*(.25f+.25f*i),base-hop))
        }
        drawLine(colors.outline,Offset(size.width*.12f,base+r*1.4f),Offset(size.width*.88f,base+r*1.4f),1.5.dp.toPx(),StrokeCap.Round)
        if(settle>0f) {
            val m=Offset(size.width*.72f,size.height*.24f);val mr=size.minDimension*.14f
            val moon=Path().apply {
                addOval(Rect(m,mr))
                op(this,Path().apply {addOval(Rect(m+Offset(mr*.55f,-mr*.35f),mr*.95f))},PathOperation.Difference)
            }
            drawPath(moon,colors.tertiary.copy(alpha=settle))
        }
    }
}

/** A battery: in power saving almost full and green with a leaf, otherwise half full with a flash. */
@Composable private fun BatteryPicture(on:Boolean,modifier:Modifier) {
    val colors=MaterialTheme.colorScheme
    val level by animateFloatAsState(if(on) .86f else .5f,tween(600,easing=FastOutSlowInEasing),label="battery-level")
    val green=Color(0xFF2E9D57)
    val fill=lerp(colors.primary,green,((level-.5f)/.36f).coerceIn(0f,1f))
    Canvas(modifier) {
        val body=Rect(size.width*.12f,size.height*.28f,size.width*.8f,size.height*.72f)
        val corner=CornerRadius(size.minDimension*.08f)
        drawRoundRect(colors.onSurface,body.topLeft,body.size,corner,style=Stroke(2.dp.toPx()))
        drawRoundRect(colors.onSurface,Offset(body.right+1.dp.toPx(),body.center.y-size.height*.08f),Size(size.width*.06f,size.height*.16f),CornerRadius(2.dp.toPx()))
        val inset=3.dp.toPx();val inner=Rect(body.left+inset,body.top+inset,body.right-inset,body.bottom-inset)
        drawRoundRect(fill,inner.topLeft,Size(inner.width*level,inner.height),CornerRadius(corner.x*.6f))
        val c=Offset(inner.left+inner.width*.5f,inner.center.y);val s=inner.height*.36f
        if(on) {
            // A leaf.
            val leaf=Path().apply {moveTo(c.x-s,c.y+s*.7f);cubicTo(c.x-s,c.y-s,c.x+s*.2f,c.y-s*1.1f,c.x+s,c.y-s);cubicTo(c.x+s*1.1f,c.y+s*.3f,c.x,c.y+s*.9f,c.x-s,c.y+s*.7f);close()}
            drawPath(leaf,Color.White)
            drawLine(green,Offset(c.x-s*.8f,c.y+s*.55f),Offset(c.x+s*.5f,c.y-s*.5f),1.2.dp.toPx(),StrokeCap.Round)
        } else {
            val bolt=Path().apply {moveTo(c.x+s*.2f,c.y-s*1.1f);lineTo(c.x-s*.6f,c.y+s*.15f);lineTo(c.x,c.y+s*.15f);lineTo(c.x-s*.2f,c.y+s*1.1f);lineTo(c.x+s*.6f,c.y-s*.15f);lineTo(c.x,c.y-s*.15f);close()}
            drawPath(bolt,colors.onPrimary)
        }
    }
}

/** A tiny screen: its hint lines and info circle fade out in minimalism, the content stays. */
@Composable internal fun MinimalPicture(on:Boolean,modifier:Modifier) {
    val colors=MaterialTheme.colorScheme
    val hints by animateFloatAsState(if(on) 0f else 1f,tween(450),label="minimal-hints")
    Canvas(modifier) {
        val phone=Rect(size.width*.22f,size.height*.06f,size.width*.78f,size.height*.94f)
        drawRoundRect(colors.onSurface,phone.topLeft,phone.size,CornerRadius(size.minDimension*.1f),style=Stroke(2.dp.toPx()))
        val left=phone.left+phone.width*.14f;val width=phone.width*.72f;var y=phone.top+phone.height*.12f
        fun line(w:Float,h:Float,color:Color) {drawRoundRect(color,Offset(left,y),Size(width*w,h),CornerRadius(h/2));y+=h+phone.height*.06f}
        line(.8f,phone.height*.07f,colors.primary)
        val hint=colors.onSurfaceVariant.copy(alpha=.55f*hints)
        if(hints>0f) {
            drawCircle(hint,phone.height*.035f,Offset(left+phone.height*.035f,y+phone.height*.035f))
            drawRoundRect(hint,Offset(left+phone.height*.1f,y+phone.height*.02f),Size(width*.6f,phone.height*.03f),CornerRadius(4f))
        }
        y+=phone.height*.13f
        line(1f,phone.height*.16f,colors.primary.copy(alpha=.75f))
        line(1f,phone.height*.16f,colors.primary.copy(alpha=.75f))
        if(hints>0f) {
            drawRoundRect(hint,Offset(left,y),Size(width*.9f,phone.height*.03f),CornerRadius(4f))
            drawRoundRect(hint,Offset(left,y+phone.height*.06f),Size(width*.55f,phone.height*.03f),CornerRadius(4f))
        }
    }
}

/** A floating bubble with a pin at the edge of a screen; faint while switched off. */
@Composable private fun BubblePicture(on:Boolean,modifier:Modifier) {
    val colors=MaterialTheme.colorScheme
    val shown by animateFloatAsState(if(on) 1f else .3f,tween(400),label="bubble-shown")
    Canvas(modifier) {
        val phone=Rect(size.width*.12f,size.height*.1f,size.width*.62f,size.height*.9f)
        drawRoundRect(colors.onSurfaceVariant.copy(alpha=.6f),phone.topLeft,phone.size,CornerRadius(size.minDimension*.08f),style=Stroke(1.5.dp.toPx()))
        val c=Offset(size.width*.66f,size.height*.42f);val r=size.minDimension*.2f
        drawCircle(Color.Black.copy(alpha=.18f*shown),r,c+Offset(0f,2.dp.toPx()))
        drawCircle(colors.primary.copy(alpha=shown),r,c)
        val pin=Path().apply {
            val p=c+Offset(0f,r*.45f);moveTo(p.x,p.y)
            cubicTo(p.x-r*.5f,p.y-r*.35f,p.x-r*.45f,p.y-r*.95f,p.x,p.y-r*.95f)
            cubicTo(p.x+r*.45f,p.y-r*.95f,p.x+r*.5f,p.y-r*.35f,p.x,p.y);close()
        }
        drawPath(pin,colors.onPrimary.copy(alpha=shown))
        drawCircle(colors.primary.copy(alpha=shown),r*.13f,c-Offset(0f,r*.15f))
    }
}

/** A bar with a finger pressing on it: rings spread from the touch. */
@Composable private fun LongPressPicture(modifier:Modifier) {
    val colors=MaterialTheme.colorScheme
    Canvas(modifier) {
        drawRoundRect(colors.primary,Offset(size.width*.34f,size.height*.08f),Size(size.width*.32f,size.height*.84f),CornerRadius(size.minDimension*.08f))
        val t=Offset(size.width*.5f,size.height*.52f)
        for(i in 1..3) drawCircle(colors.onSurface.copy(alpha=.5f/i),size.minDimension*(.1f+.08f*i),t,style=Stroke(1.5.dp.toPx()))
        drawCircle(colors.onSurface,size.minDimension*.09f,t)
    }
}

/** Arrows into and out of a document (backup). */
@Composable internal fun BackupPicture(modifier:Modifier) {
    val colors=MaterialTheme.colorScheme
    Canvas(modifier) {
        val doc=Rect(size.width*.2f,size.height*.08f,size.width*.62f,size.height*.92f)
        val fold=doc.width*.3f
        val page=Path().apply {moveTo(doc.left,doc.top);lineTo(doc.right-fold,doc.top);lineTo(doc.right,doc.top+fold);lineTo(doc.right,doc.bottom);lineTo(doc.left,doc.bottom);close()}
        drawPath(page,colors.surfaceContainerHighest);drawPath(page,colors.onSurface,style=Stroke(2.dp.toPx()))
        for(i in 0..2) drawLine(colors.onSurfaceVariant,Offset(doc.left+doc.width*.18f,doc.top+doc.height*(.38f+.16f*i)),
            Offset(doc.right-doc.width*.18f,doc.top+doc.height*(.38f+.16f*i)),1.5.dp.toPx(),StrokeCap.Round)
        fun arrow(x:Float,up:Boolean,color:Color) {
            val top=size.height*.3f;val bottom=size.height*.78f;val head=size.minDimension*.09f
            drawLine(color,Offset(x,top),Offset(x,bottom),3.dp.toPx(),StrokeCap.Round)
            val tip=if(up) top else bottom;val dir=if(up) 1f else -1f
            drawLine(color,Offset(x,tip),Offset(x-head,tip+head*dir),3.dp.toPx(),StrokeCap.Round)
            drawLine(color,Offset(x,tip),Offset(x+head,tip+head*dir),3.dp.toPx(),StrokeCap.Round)
        }
        arrow(size.width*.74f,true,colors.primary);arrow(size.width*.9f,false,colors.tertiary)
    }
}

/** A start and a destination field with the swap arrows between them; on, the position mark is crossed out. */
@Composable internal fun PlannerPicture(on:Boolean,modifier:Modifier) {
    val colors=MaterialTheme.colorScheme
    val cross by animateFloatAsState(if(on) 1f else 0f,tween(420,easing=FastOutSlowInEasing),label="planner-cross")
    Canvas(modifier) {
        val w=size.width;val h=size.height;val stroke=1.6.dp.toPx();val r=CornerRadius(h*.09f)
        val fieldH=h*.2f;val left=w*.08f;val width=w*.84f
        listOf(h*.2f,h*.6f).forEachIndexed {i,top->
            drawRoundRect(colors.surfaceContainerHighest,Offset(left,top),Size(width,fieldH),r)
            drawRoundRect(colors.outline,Offset(left,top),Size(width,fieldH),r,style=Stroke(stroke))
            val dot=Offset(left+fieldH*.55f,top+fieldH/2)
            if(i==0) drawCircle(colors.primary,fieldH*.16f,dot) else drawCircle(colors.tertiary,fieldH*.2f,dot)
            drawLine(colors.outlineVariant,Offset(left+fieldH,top+fieldH/2),Offset(left+width*.62f,top+fieldH/2),stroke*1.4f,StrokeCap.Round)
        }
        // The swap arrows between the fields.
        val mid=h*.5f;val ax=w*.5f;val len=h*.09f;val head=len*.55f
        drawLine(colors.primary,Offset(ax-w*.05f,mid+len),Offset(ax-w*.05f,mid-len),stroke*1.3f,StrokeCap.Round)
        drawLine(colors.primary,Offset(ax-w*.05f,mid-len),Offset(ax-w*.05f-head*.7f,mid-len+head),stroke*1.3f,StrokeCap.Round)
        drawLine(colors.primary,Offset(ax-w*.05f,mid-len),Offset(ax-w*.05f+head*.7f,mid-len+head),stroke*1.3f,StrokeCap.Round)
        drawLine(colors.primary,Offset(ax+w*.05f,mid-len),Offset(ax+w*.05f,mid+len),stroke*1.3f,StrokeCap.Round)
        drawLine(colors.primary,Offset(ax+w*.05f,mid+len),Offset(ax+w*.05f-head*.7f,mid+len-head),stroke*1.3f,StrokeCap.Round)
        drawLine(colors.primary,Offset(ax+w*.05f,mid+len),Offset(ax+w*.05f+head*.7f,mid+len-head),stroke*1.3f,StrokeCap.Round)
        // The position mark in the corner, crossed out while the mode is on.
        val g=Offset(w*.84f,h*.1f);val gr=h*.075f
        drawCircle(colors.onSurfaceVariant,gr,g,style=Stroke(stroke))
        drawCircle(colors.onSurfaceVariant,gr*.35f,g)
        if(cross>0f) drawLine(colors.error,g+Offset(-gr*1.5f,-gr*1.5f),g+Offset(-gr*1.5f+gr*3f*cross,-gr*1.5f+gr*3f*cross),stroke*1.4f,StrokeCap.Round)
    }
}
